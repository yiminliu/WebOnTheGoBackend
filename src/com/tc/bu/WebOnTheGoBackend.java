package com.tc.bu;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tc.bu.dao.Account;
import com.tc.bu.db.HibernateUtil;
import com.tc.bu.exception.CustomerException;
import com.tc.bu.exception.PaymentException;
import com.tc.bu.util.email.EmailHelper;
import com.tc.bu.util.email.MailClient;
import com.tc.bu.util.email.Recipient;
import com.tscp.mvne.CreditCard;
import com.tscp.mvne.CustPmtMap;
import com.tscp.mvne.CustTopUp;
import com.tscp.mvne.Customer;
import com.tscp.mvne.PaymentUnitResponse;
import com.tscp.mvne.TSCPMVNA;
import com.tscp.mvne.TSCPMVNAService;


@SuppressWarnings("unchecked")
public class WebOnTheGoBackend {
	
  private static final String wsdlLocation = "http://10.10.30.190:8080/TSCPMVNA/TSCPMVNAService?WSDL";
  private static final String nameSpace = "http://mvne.tscp.com/";
  private static final String serviceName = "TSCPMVNAService";	

  private static final String EMAIL_ERROR = "WOTG_Alerts@telscape.net";
  
  private static final Logger logger = LoggerFactory.getLogger(WebOnTheGoBackend.class);
  private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
  private static final SimpleDateFormat legibleDate = new SimpleDateFormat("MM/dd/yyyy");
  private static final DecimalFormat df = new DecimalFormat("0.00");

  private static TSCPMVNAService service;
  private static TSCPMVNA port;
  
  public WebOnTheGoBackend() {
    init();
  }
  
  public static void main(String[] args) {
		WebOnTheGoBackend wotgbe = new WebOnTheGoBackend();
		int acctNo = -100;
		logger.info("********** Start topup charge process *************");
		long startTime = System.currentTimeMillis();
	    try {
	      if(acctNo < 0) 
	    	  wotgbe.chargeAccounts(wotgbe.getAccountToChargeList());
	      else
	    	  wotgbe.manualChargeAccount(acctNo);
	    } 
	    catch (WebServiceException wsException) {
	      if (wsException.getMessage().indexOf("Attempted to read or write protected memory") >= 0) {
	        logger.error("Memory corrupt. Exiting the process{}.", wsException.getMessage());
	        System.exit(1);
	      }
	      else
	    	logger.error("Error occured in the topup process {}.", wsException.getMessage()) ;	      
	    }
	    catch (Exception exception) {
		   	logger.error("Error occured in the topup process {}.", exception.getMessage()) ;	      
	    }
	    logger.info("Total time spent to process topup: {} (ms)",  System.currentTimeMillis() - startTime);
	    logger.info("********** Finished topup charge process **********");
	    System.out.println("\r\n");
  }
  
  /**
   * 1. Load the customer info and account info 2. Get their set top-up amount
   * 3. Calculate the total number of top-ups required 4. Submit the payment
   * 
   * @param accountList
   */
  private void chargeAccounts(List<Account> accountList) {
	
	if(accountList == null || accountList.size() == 0){
	   logger.info("accountList is empty, No account to topup");
	   System.exit(0);
    }
	Customer customer = null;	  
	com.tscp.mvne.Account tscpMvneAccount = null;
	int defaultPaymentId = -1;            
    Double chargeAmount = null;
    boolean failureNotificationSent = false;
    for (Account account : accountList) {
      try {
          customer = getCustomerInfo(account);
          tscpMvneAccount = getAccount(account.getAccountNo());
          defaultPaymentId = getCustomerPaymentDefault(customer);            
          chargeAmount = determinCustomerTopUpAmount(customer, tscpMvneAccount);
          try {
              makePayment(customer, defaultPaymentId, tscpMvneAccount, null, df.format(chargeAmount));
          } 
          catch (PaymentException paymentEx) {
             	 sendPaymentFailureNotification(tscpMvneAccount, customer, account, defaultPaymentId, chargeAmount, paymentEx); 
             	 failureNotificationSent = true;
          }      
      } 
      catch (CustomerException custEx) {
            logger.error("Skipping Account " + account.getAccountNo() + "Error was " + custEx.getMessage(), custEx);
            //we may need to notify the error to a technical group
            if(failureNotificationSent == false)
            try{
               sendPaymentFailureNotification(tscpMvneAccount, customer, account, defaultPaymentId, chargeAmount, new PaymentException(custEx.getMessage())); 
               failureNotificationSent = true;
            }
            catch(Exception e){}
      }  
      logger.info("Done with Account {}", account.getAccountNo());
    }    
  }

  /****** This method is used to charge topup for a given account number ******/
  private void manualChargeAccount(int accountNo) {
	  List<Account> accountList = new ArrayList<Account>();  
      Account acct = new Account();
	  acct.setAccountNo(accountNo);
	  accountList.add(acct);
	  chargeAccounts(accountList);
  }	
  
  private Customer getCustomerInfo(Account account) throws CustomerException {
	Customer customer = null;  
    try {
       logger.debug("Getting Customer Info for Account " + account.getAccountNo());
       customer = getCustomerFromAccount(account.getAccountNo());
       if (customer.getId() == 0) {
          throw new CustomerException("Customer Info not be found for Account " + account.getAccountNo());
       }
    } 
    catch (CustomerException custEx) {
      throw custEx;	
    }
    return customer;
  }
    
  private CreditCard getPaymentMethod(int custId, int pmtId) throws CustomerException {
     CreditCard creditCard = port.getCreditCardDetail(pmtId);
     if (creditCard == null || creditCard.getCreditCardNumber() == null || creditCard.getCreditCardNumber().trim().length() == 0) {
         throw new CustomerException("Error retrieving Credit Card for customer " + custId + " pmt " + pmtId);
     }
     return creditCard;
  }

  private com.tscp.mvne.Account getAccount(int accountNo) throws CustomerException {
     com.tscp.mvne.Account account = port.getAccountInfo(accountNo);
     if (account == null) {
        throw new CustomerException("Error fetching Account " + accountNo);
     } 
     else if (account.getContactEmail() == null || account.getContactEmail().trim().length() == 0) {
        throw new CustomerException("Error fetching Email Address for account " + account.getAccountNo());
     }
     return account;
  }
  
  private Double determinCustomerTopUpAmount(Customer customer, com.tscp.mvne.Account tscpMvneAccount) throws CustomerException {
	    logger.info("Getting top-up amount for customer {}", customer.getId());  
		CustTopUp custTopUp = new CustTopUp();
	    custTopUp = port.getCustTopUpAmount(customer, tscpMvneAccount);
	    if (custTopUp == null || custTopUp.getTopupAmount() == null || custTopUp.getTopupAmount().trim().length() == 0) {
	      throw new CustomerException("Customer topup amount has not been set");
	    }
	    logger.debug("Calculating total top-up amount");
	    int topUpQuantity = 0;
	    //CustBalance currentBalance = getCustBalance(account.getAccountno());
	    //tscpMvneAccount.setBalance(Double.toString(currentBalance.getRealBalance() * -1));
	    while (Double.parseDouble(tscpMvneAccount.getBalance()) < 2.0) {
	        ++topUpQuantity;
	        tscpMvneAccount.setBalance(Double.toString(Double.parseDouble(tscpMvneAccount.getBalance()) + Double.parseDouble(custTopUp.getTopupAmount())));
	    }
	    Double chargeAmount = Double.parseDouble(custTopUp.getTopupAmount()) * topUpQuantity;
	    logger.info("Customer will be topped up. Total charge is {}.", topUpQuantity, NumberFormat.getCurrencyInstance().format(chargeAmount));
	    return chargeAmount;
  }

  private int getCustomerPaymentDefault(Customer customer) throws CustomerException {
     logger.debug("Getting default payment method for customer {}", customer.getId());  
     int paymentId = 0;
     List<CustPmtMap> custPaymentMap = port.getCustPaymentList(customer.getId(), 0);
     if (custPaymentMap != null && custPaymentMap.size() > 0) {
         paymentId = custPaymentMap.get(0).getPaymentid();
     } 
     else {
      throw new CustomerException("Error retrieving Payments for Customer " + customer.getId());
     }
     return paymentId;
  }

  private Customer getCustomerFromAccount(int accountNo) throws CustomerException {
     Customer customer = new Customer();
     customer.setId(port.getCustFromAccount(accountNo).getCustId());
     if (customer.getId() == 0) {
       logger.error("Customer information from map against account " + accountNo + " returned a 0 CustID");
       throw new CustomerException("Customer information from map against account " + accountNo + " returned a 0 CustID");
     }
     return customer;
  }

  private void makePayment(Customer customer, int paymentId, com.tscp.mvne.Account account, CreditCard creditCard, String amount)
      throws PaymentException {
     //logger.info("Making payment for CustomerId " + customer.getId() + " against Pmt ID " + paymentId + " in the Amount of $" + df.format(amount) + ".");
     //logger.info("Making payment for account {}, in the Amount of ${}. ", account.getAccountNo(), df.format(amount));
     String sessionid = "CID" + customer.getId() + "T" + getTimeStamp() + "AUTO";
     PaymentUnitResponse response = null;
     try {
    //    response = port.submitPaymentByPaymentId(sessionid, customer, paymentId, account, amount);
     } 
     catch (WebServiceException wse) {
       logger.warn("WebService Exception occured: {} ", wse.getMessage());
       // will catch this exception at main()
       if (wse.getMessage().indexOf("Attempted to read or write protected memory") >= 0) {
         throw wse;
       }
       if (wse.getCause() != null) {
         logger.warn("Immediate WSException Cause was :: " + wse.getCause().getMessage());
       }
       throw new PaymentException(wse.getMessage());
     }
     if (response != null) {
        logger.info("PaymentUnit Response ");
        logger.info("AuthCode   :: " + response.getAuthcode());
        logger.info("ConfCode   :: " + response.getConfcode());
        logger.info("ConfDescr  :: " + response.getConfdescr());
        logger.info("CvvCode    :: " + response.getCvvcode());
        logger.info("TransId    :: " + response.getTransid());
     } 
     else {
        logger.error("PaymentUnit returned no response");
     }
  }

  private List<Account> getAccountToChargeList() {
     logger.info("Fetching accounts to charge...");
     List<Account> accountList = null;
     Session session = null;
     try {
    	session = HibernateUtil.getSessionFactory().getCurrentSession();
        session.beginTransaction();
        Query q = session.getNamedQuery("sp_fetch_accts_to_charge");
        accountList = q.list();
        if(accountList == null || accountList.isEmpty())
        	logger.info("   ...{} accounts will be topped-up", 0);
        else 
            logger.info("   ...{} accounts will be topped-up", accountList.size());
        session.getTransaction().commit();
     }   
     catch(HibernateException e){
    	session.getTransaction().rollback();
    	logger.error("Failed on executing sp_fetch_accts_to_charge, due to {} ", e.getMessage());
    }
    return accountList;
  }
    
  private String determineCreditCardType(String firstCardNumber){
	  String cardType = "";
	  switch(Integer.parseInt(firstCardNumber)){
	     case 3:
	    	 cardType = "American Express";
	        break;	  
	     case 4:
	    	 cardType = "Visa";
	        break;
	     case 5:
	    	 cardType = "Master Card";
	        break;
	     case 6:
	    	 cardType = "Discover";
	        break;
         default:
        	 cardType = "Unknown";	  
	  }
	  return cardType;	  
  }
  
  
  /******************** Util Methods ***********************/
  private void init(){
	  try {
	      //service = new TSCPMVNAService(new URL(TscpMvnaWebService.WSDL.toString()), new QName(TscpMvnaWebService.NAMESPACE.toString(), TscpMvnaWebService.SERVICENAME.toString()));
		  service = new TSCPMVNAService(new URL(wsdlLocation), new QName(nameSpace, serviceName));
		  port = service.getTSCPMVNAPort();
	  } 
	  catch (MalformedURLException urlEx) {
	      logger.error("Unable to reach webservice. {}", urlEx.getMessage());
	      System.exit(1);
	  }	  
  }
  
  private static String getTimeStamp() {
     return sdf.format(new Date());
  }  
  
  private void sendPaymentFailureNotification(com.tscp.mvne.Account tscpMvneAccount, Customer customer, Account account,
		                                      int defaultPaymentId, Double chargeAmount, PaymentException paymentEx) 
                                              throws CustomerException {
	  String remainingBalance = "";
      CreditCard paymentInfo = null;
      String paymentMethod ="";
    	  
     logger.info("Sending failure notification to {}", EMAIL_ERROR);
     try {
        remainingBalance = tscpMvneAccount.getBalance();
        paymentInfo = getPaymentMethod(customer.getId(), defaultPaymentId);
        paymentMethod = determineCreditCardType(paymentInfo.getCreditCardNumber().substring(0, 1));
     }
     catch(Exception e){
    	 logger.warn("Exception orrcured: {}", e.getMessage());
     }
     String body = EmailHelper.getPaymentFailureBody(tscpMvneAccount.getFirstname(), 
    		                                         Integer.toString(tscpMvneAccount.getAccountNo()), 
    		                                         account.getMdn(), "", chargeAmount.toString(), 
    		                                         paymentMethod, paymentInfo.getCreditCardNumber(), 
    		                                         legibleDate.format(new Date()), 
    		                                         paymentEx.getMessage(), 
    		                                         remainingBalance);
     sendEmail(EMAIL_ERROR, "Error processing payment for Account " + tscpMvneAccount.getAccountNo(), body);
     logger.info("Email sent");
  }
  
  private void sendEmail(String emailAddress, String subject, String body) {
	 MailClient mail = new MailClient();
	 Vector<Recipient> recipients = new Vector<Recipient>();
	 Recipient recipient = new Recipient();
	 recipient.setEmailAddress(emailAddress);
	 recipients.add(recipient);
	 try {
	     //body = EmailHelper.getEmailHeader() + body + EmailHelper.getEmailFooter();
	     body = EmailHelper.getEmailHeader() + body;
	     mail.postMail(recipients, subject, body, MailClient.SYSTEM_SENDER);
	 } 
	 catch (Exception ex) {
	     logger.error("Error occured while sending notification email, due to {}", ex.getMessage());
	 }
  }
}
