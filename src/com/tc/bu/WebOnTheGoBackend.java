package com.tc.bu;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
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
import com.tc.bu.exception.ProcessException;
import com.tc.bu.util.email.EmailHelper;
import com.tc.bu.util.email.MailClient;
import com.tc.bu.util.email.Recipient;
import com.tscp.mvne.CreditCard;
import com.tscp.mvne.CustPmtMap;
import com.tscp.mvne.CustTopUp;
import com.tscp.mvne.Customer;
import com.tscp.mvne.NetworkInfo;
import com.tscp.mvne.PaymentUnitResponse;
import com.tscp.mvne.TSCPMVNA;
import com.tscp.mvne.TSCPMVNAService;


@SuppressWarnings("unchecked")
public class WebOnTheGoBackend {
	
	
  private static final String wsdlLocation = "http://10.10.30.190:8080/TSCPMVNA/TSCPMVNAService?WSDL";
  private static final String nameSpace = "http://mvne.tscp.com/";
  private static final String serviceName = "TSCPMVNAService";	
  private static final String EMAIL_ERROR = "truconnect_alerts@telscape.net";
  private static final Logger logger = LoggerFactory.getLogger(WebOnTheGoBackend.class);
  private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
  private static final SimpleDateFormat legibleDate = new SimpleDateFormat("MM/dd/yyyy");
  private static final DecimalFormat df = new DecimalFormat("0.00");

  private TSCPMVNAService service;
  private TSCPMVNA port;
  
  private com.tscp.mvne.Account tscpMvneAccount;
  private NetworkInfo networkInfo;
  
  public WebOnTheGoBackend() {
    init();
  }
  
  public static void main(String[] args) {
		WebOnTheGoBackend wotgbe = new WebOnTheGoBackend();
		int acctNo = -100;
		logger.info("Start topup charge process.");
	    try {
	      if(acctNo < 0) 
	    	  wotgbe.chargeAccounts(wotgbe.getAccountToChargeList());
	      else
	    	  wotgbe.manualChargeAccount(acctNo);
	    } 
	    catch (WebServiceException wsException) {
	      if (wsException.getMessage().indexOf("Attempted to read or write protected memory") >= 0) {
	        System.err.println("Memory corrupt. Exiting the process.");
	        System.exit(1);
	      }
	      else
	    	logger.error(wsException.getMessage()) ;	      
	    }
	    catch (Exception exception) {
		   	logger.error(exception.getMessage()) ;	      
	    }
	    logger.info("Finished topup charge process.");
	    System.out.println("");
  }
  
  /**
   * 1. Load the customer info and account info 2. Get their set top-up amount
   * 3. Calculate the total number of top-ups required 4. Submit the payment
   * 
   * @param accountList
   */
  private void chargeAccounts(List<Account> accountList) {
	
	com.tscp.mvne.Account tscpMvneAccount = new com.tscp.mvne.Account();
	Customer customer = null;	  
    for (Account account : accountList) {
      try {
          customer = getCustomerInfo(account);
          int defaultPaymentId = getCustomerPaymentDefault(customer);            
          Double chargeAmount = determinCustomerTopUpAmount(customer, tscpMvneAccount);
          try {
              makePayment(customer, defaultPaymentId, tscpMvneAccount, null, df.format(chargeAmount));
          } 
          catch (PaymentException payment_ex) {
              logger.info("Sending failure notification to {}", EMAIL_ERROR);
              String remainingBalance = tscpMvneAccount.getBalance();
              CreditCard paymentInfo = getPaymentMethod(customer.getId(), defaultPaymentId);
              String paymentMethod = determineCreditCardType(paymentInfo.getCreditCardNumber().substring(0, 1));
              String body = EmailHelper.getPaymentFailureBody(tscpMvneAccount.getFirstname(), Integer.toString(tscpMvneAccount.getAccountNo()), account
                  .getMdn(), "", chargeAmount.toString(), paymentMethod, paymentInfo.getCreditCardNumber(), legibleDate.format(new Date()), payment_ex
                  .getMessage(), remainingBalance);
              sendEmail(EMAIL_ERROR, "Error processing payment for Account " + tscpMvneAccount.getAccountNo(), body);
              logger.debug("Email sent");
          }      
      } 
      catch (CustomerException cust_ex) {
            logger.warn("Skipping Account " + account.getAccountNo() + "Error was " + cust_ex.getMessage(), cust_ex);
            ProcessException process_ex = new ProcessException("Payment Processing", cust_ex.getMessage(), cust_ex);
            process_ex.setAccountNo(Integer.toString(tscpMvneAccount.getAccountNo()));
            process_ex.setMdn(account.getMdn());
            process_ex.setAccount(tscpMvneAccount);
            process_ex.setNetworkInfo(networkInfo);
            //throw process_ex;
            logger.error("Error:: " + process_ex.getMessage() + ". Formerly we sent emails here...server is handling this action now. ");
      } catch (ProcessException process_ex) {
        // previously sent emails to the customer from here. This is now done in TSCPMVNE.TruConnect.submitPaymentById
        logger.error("Error:: " + process_ex.getMessage() + ". Formerly we sent emails here...server is handling this action now. ");
      }
      logger.info("Done with Account {}", account.getAccountNo());
    }    
  }

  /****** This method is used to charge topup for a given account number ******/
  private void manualChargeAccount(int accountNo) {
	  List<Account> accountList = Collections.emptyList();  
      Account acct = new Account();
	  acct.setAccountNo(accountNo);
	  accountList.add(acct);
	  chargeAccounts(accountList);
  }	
  
  private Customer getCustomerInfo(Account account) throws ProcessException {
	Customer customer = null;  
    ProcessException process_ex = null;
    try {
      logger.info("Getting Customer Info for Account " + account.getAccountNo());
      customer = getCustomerFromAccount(account.getAccountNo());
      if (customer.getId() == 0) {
        throw new CustomerException("Customer Info not be found for Account " + account.getAccountNo());
      }
    } catch (CustomerException cust_ex) {
      process_ex = new ProcessException("CustomerInformation Retrieval", cust_ex);
    }
    try {
      logger.debug("Creating TSCPMVNE.Account Object for customer " + customer.getId());
      tscpMvneAccount = getAccount(account.getAccountNo());
    } catch (CustomerException cust_ex) {
      if (process_ex == null) {
        process_ex = new ProcessException("AccountInformation Retrieval", cust_ex);
      }
    }
    if (process_ex != null) {
      process_ex.setAccountNo(Integer.toString(account.getAccountNo()));
      process_ex.setMdn(account.getMdn());
      process_ex.setAccount(tscpMvneAccount);
      process_ex.setNetworkInfo(networkInfo);
      throw process_ex;
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
    } else if (account.getContactEmail() == null || account.getContactEmail().trim().length() == 0) {
      throw new CustomerException("Error fetching Email Address for account " + account.getAccountNo());
    }
    return account;
  }
  
  private Double determinCustomerTopUpAmount(Customer customer, com.tscp.mvne.Account tscpMvneAccount) throws CustomerException {
	    
		logger.debug("Getting top-up amount for customer {}", customer.getId());  
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
	    logger.debug("Customer will be topped up. Total charge is {}.", topUpQuantity, NumberFormat.getCurrencyInstance().format(chargeAmount));
	    return chargeAmount;
  }

  private int getCustomerPaymentDefault(Customer customer) throws CustomerException {
    // PaymentInformation paymentInfo = new PaymentInformation();
	logger.debug("Getting default payment method for customer {}", customer.getId());  
    int paymentId = 0;
    List<CustPmtMap> custPaymentMap = port.getCustPaymentList(customer.getId(), 0);
    if (custPaymentMap != null && custPaymentMap.size() > 0) {
      paymentId = custPaymentMap.get(0).getPaymentid();
    } else {
      throw new CustomerException("Error retrieving Payments for Customer " + customer.getId());
    }
    return paymentId;
  }

  private Customer getCustomerFromAccount(int accountno) throws ProcessException {
    Customer customer = new Customer();
    try {
      customer.setId(port.getCustFromAccount(accountno).getCustId());
    } catch (NullPointerException np_ex) {
      logger.info(np_ex.getMessage());
      throw new ProcessException("CustomerRetrieval", "Unable to get customer information from map against account " + accountno);
    }
    if (customer.getId() == 0) {
      logger.info("Customer information from map against account " + accountno + " returned a 0 CustID");
      throw new ProcessException("CustomerRetrieval", "Customer information from map against account " + accountno + " returned a 0 CustID");
    }
    return customer;
  }

  private void makePayment(Customer customer, int paymentId, com.tscp.mvne.Account account, CreditCard creditCard, String amount)
      throws PaymentException {
    logger.info("Making payment for CustomerId " + customer.getId() + " against Pmt ID " + paymentId + " in the Amount of $" + df.format(amount) + ".");
    String sessionid = "CID" + customer.getId() + "T" + getTimeStamp() + "AUTO";
    PaymentUnitResponse response = null;
    try {
        response = port.submitPaymentByPaymentId(sessionid, customer, paymentId, account, amount);
    } 
    catch (WebServiceException wse) {
      logger.warn("WebService Exception thrown :: " + wse.getMessage());
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
        if(accountList == null)
        	logger.info("   ...{} accounts will be topped-up", 0);
        else 
            logger.info("   ...{} accounts will be topped-up", accountList.size());
        session.getTransaction().commit();
    }   
    catch(HibernateException e){
    	//session.getTransaction().rollback();
    	logger.error("Failed on executing sp_fetch_accts_to_charge, due to: " +e.getMessage());
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
	  catch (MalformedURLException url_ex) {
	      logger.error("Unable to reach webservice. {}", url_ex.getMessage());
	  }	  
  }
  
  private static String getTimeStamp() {
    return sdf.format(new Date());
  }  
  
  private void sendEmail(String emailAddress, String subject, String body) {
	    MailClient mail = new MailClient();
	    Vector<Recipient> recipients = new Vector<Recipient>();
	    Recipient recipient = new Recipient();
	    recipient.setEmailAddress(emailAddress);
	    recipients.add(recipient);
	    try {
	      body = EmailHelper.getEmailHeader() + body + EmailHelper.getEmailFooter();
	      mail.postMail(recipients, subject, body, MailClient.SYSTEM_SENDER);
	    } catch (Exception ex) {
	      ex.printStackTrace();
	    }
	  }
}
