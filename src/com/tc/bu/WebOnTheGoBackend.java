package com.tc.bu;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;

import org.hibernate.Query;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tc.bu.dao.Account;
import com.tc.bu.dao.CustBalance;
import com.tc.bu.dao.CustNotification;
import com.tc.bu.db.HibernateUtil;
import com.tc.bu.exception.CustomerException;
import com.tc.bu.exception.NetworkException;
import com.tc.bu.exception.PaymentException;
import com.tc.bu.exception.ProcessException;
import com.tc.bu.util.email.EmailHelper;
import com.tc.bu.util.email.MailClient;
import com.tc.bu.util.email.Recipient;
import com.tscp.mvne.CreditCard;
import com.tscp.mvne.CustPmtMap;
import com.tscp.mvne.CustTopUp;
import com.tscp.mvne.Customer;
import com.tscp.mvne.NetworkException_Exception;
import com.tscp.mvne.NetworkInfo;
import com.tscp.mvne.PaymentUnitResponse;
import com.tscp.mvne.ServiceInstance;
import com.tscp.mvne.TSCPMVNA;
import com.tscp.mvne.TSCPMVNAService;

@SuppressWarnings("unchecked")
public class WebOnTheGoBackend {
  private static final String wsdl = "http://10.10.30.188:8080/TSCPMVNA/TSCPMVNAService?WSDL";
  private static final String nameSpace = "http://mvne.tscp.com/";
  private static final String serviceName = "TSCPMVNAService";

  private static final String USERNAME = "TCBU";
  private static final String EMAIL_ERROR = "truconnect_alerts@telscape.net";

  public static final String SWITCH_STATUS_ACTIVE = "A";
  public static final String SWITCH_STATUS_SUSPENDED = "S";
  public static final String SWITCH_STATUS_DISCONNECTED = "C";

  static final Logger logger = LoggerFactory.getLogger(WebOnTheGoBackend.class);
  static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
  static final SimpleDateFormat legibleDate = new SimpleDateFormat("MM/dd/yyyy");
  static final DecimalFormat df = new DecimalFormat("0.00");

  TSCPMVNAService service;
  TSCPMVNA port;

  List<Account> chargeList;
  List<Account> suspendList;
  List<Account> restoreList;

  com.tscp.mvne.Account tscpMvneAccount;
  NetworkInfo networkInfo;
  Customer customer;

  public WebOnTheGoBackend() {
    try {
      service = new TSCPMVNAService(new URL(wsdl), new QName(nameSpace, serviceName));
      port = service.getTSCPMVNAPort();
    } catch (MalformedURLException url_ex) {
      logger.error("Unable to reach webservice. {}", url_ex.getMessage());
    }
  }

  /**
   * 1. Load the customer info and account info 2. Get their set top-up amount
   * 3. Calculate the total number of top-ups required 4. Submit the payment
   * 
   * @param accountList
   */
  private void chargeAccounts(List<Account> accountList) {
    for (Account account : accountList) {
      try {
        if (true /* account.getAccountno() == 693931 */) {
          tscpMvneAccount = new com.tscp.mvne.Account();
          networkInfo = new NetworkInfo();
          customer = new Customer();

          getCustomerInfo(account);
          try {
            logger.debug("Getting default payment method for customer {}", customer.getId());
            int defaultPaymentId = getCustomerPaymentDefault(customer);
            logger.debug("Getting top-up amount for customer {}", customer.getId());
            CustTopUp topup = getCustomerTopUpAmount(customer, tscpMvneAccount);

            logger.debug("Calculating total top-up amount");
            int topUpQuantity = 0;
            //CustBalance currentBalance = getCustBalance(account.getAccountno());
            //tscpMvneAccount.setBalance(Double.toString(currentBalance.getRealBalance() * -1));
                        
            while (Double.parseDouble(tscpMvneAccount.getBalance()) < 2.0) {
              ++topUpQuantity;
              tscpMvneAccount.setBalance(Double.toString(Double.parseDouble(tscpMvneAccount.getBalance()) + Double.parseDouble(topup.getTopupAmount())));
            }
            Double chargeAmount = Double.parseDouble(topup.getTopupAmount()) * topUpQuantity;
            logger.debug("Customer will be topped up {} times. Total charge is {}.", topUpQuantity, NumberFormat.getCurrencyInstance().format(chargeAmount));

            try {
              Object[] loggingArgs = { customer.getId(), tscpMvneAccount.getAccountNo(), defaultPaymentId, df.format(chargeAmount) };
              logger.info("Submitting Payment for Customer {} for account {} with pmtId {} for {}.", loggingArgs);
              PaymentUnitResponse response = makePayment(customer, defaultPaymentId, tscpMvneAccount, null, df.format(chargeAmount));
              if (response != null) {
                logger.info("PaymentUnit Response ");
                logger.info("AuthCode   :: " + response.getAuthcode());
                logger.info("ConfCode   :: " + response.getConfcode());
                logger.info("ConfDescr  :: " + response.getConfdescr());
                logger.info("CvvCode    :: " + response.getCvvcode());
                logger.info("TransId    :: " + response.getTransid());
              } else {
                logger.error("PaymentUnit returned no response");
              }
            } catch (PaymentException payment_ex) {
              logger.info("Sending failure notification to {}", EMAIL_ERROR);
              String remainingBalance = tscpMvneAccount.getBalance();
              CreditCard paymentInfo = getPaymentMethod(customer.getId(), defaultPaymentId);
              String paymentMethod = "unknown";
              if (paymentInfo.getCreditCardNumber().substring(0, 1).equals("3")) {
                paymentMethod = "American Express";
              } else if (paymentInfo.getCreditCardNumber().substring(0, 1).equals("4")) {
                paymentMethod = "Visa";
              } else if (paymentInfo.getCreditCardNumber().substring(0, 1).equals("5")) {
                paymentMethod = "Master Card";
              } else if (paymentInfo.getCreditCardNumber().substring(0, 1).equals("6")) {
                paymentMethod = "Discover";
              }
              String body = EmailHelper.getPaymentFailureBody(tscpMvneAccount.getFirstname(), Integer.toString(tscpMvneAccount.getAccountNo()), account
                  .getMdn(), "", topup.getTopupAmount(), paymentMethod, paymentInfo.getCreditCardNumber(), legibleDate.format(new Date()), payment_ex
                  .getMessage(), remainingBalance);
              sendEmail(EMAIL_ERROR, "Error processing payment for Account " + tscpMvneAccount.getAccountNo(), body);
              logger.debug("Email sent");
            }
            logger.info("Done with Account {}", account.getAccountno());
          } catch (CustomerException cust_ex) {
            logger.warn("Skipping Account " + account.getAccountno() + "Error was " + cust_ex.getMessage(), cust_ex);
            ProcessException process_ex = new ProcessException("Payment Processing", cust_ex.getMessage(), cust_ex);
            process_ex.setAccountNo(Integer.toString(tscpMvneAccount.getAccountNo()));
            process_ex.setMdn(account.getMdn());
            process_ex.setAccount(tscpMvneAccount);
            process_ex.setNetworkInfo(networkInfo);
            throw process_ex;
          }
        }
      } catch (ProcessException process_ex) {
        // previously sent emails to the customer from here. This is now done in
        // TSCPMVNE.TruConnect.submitPaymentById
        // logger.debug("Formerly we sent emails here...server is handling this action now");
      }
    }
  }

  public void chargeAccounts() {
    chargeAccounts(getAccountToChargeList());
  }

  @Deprecated
  protected void hotlineAccounts() {
    logger.info("Retrieving accounts to hotline");
    // get list of accounts to hotline
    List<Account> accountList = getAccountsToHotLineList();

    if (accountList != null) {
      for (Account account : accountList) {

        try {
          tscpMvneAccount = new com.tscp.mvne.Account();
          networkInfo = new NetworkInfo();

          getCustomerInfo(account);

          if (true/* account.getAccountno() == 681941 */) {
            try {
              if (networkInfo.getStatus().equals(SWITCH_STATUS_ACTIVE)) {
                // submit hotline request
                ServiceInstance serviceInstance = new ServiceInstance();
                serviceInstance.setExternalId(account.getMdn());
                try {
                  // Method no longer exposed as webmethod. Use suspendAccount
                  // instead.
                  // port.suspendService(serviceInstance);
                } catch (WebServiceException ws_ex) {
                  logger.warn("WebService Exception thrown when suspending MDN " + account.getMdn());
                  logger.warn("Error: " + ws_ex.getMessage());
                  if (ws_ex.getMessage().indexOf("does not exist") > 0) {
                    throw new CustomerException("MDN " + serviceInstance.getExternalId() + "is currently not active and was not suspended...");
                  } else {
                    throw new CustomerException(ws_ex.getMessage(), ws_ex.getCause());
                  }
                }
              } else if (networkInfo.getStatus().equals(SWITCH_STATUS_SUSPENDED)) {
                logger.info("MDN " + account.getMdn() + " skipped suspend because it is already in suspend status ");
              } else {
                // logger.info("MDN "+account.getMdn()+" skipped suspend because it is currently in status "+networkInfo.getStatus());
                throw new CustomerException("MDN " + account.getMdn() + " was not suspended because it is currently in status " + networkInfo.getStatus());
              }

              // send notification
              logger.info("notifications are no longer sent through this medium");
              // String body =
              // getSuspendedAccountNotification(tscpMvneAccount.getFirstname(),Integer.toString(tscpMvneAccount.getAccountno()),account.getMdn(),networkInfo.getEsnmeiddec(),legibleDate.format(new
              // Date()));
              // sendEmail(tscpMvneAccount.getContactEmail(),"Your Account "+tscpMvneAccount.getAccountno()+" has been suspended",body);
              logger.info("Account " + account.getAccountno() + " and Mdn " + account.getMdn() + " has been suspended");

            } catch (WebServiceException ws_ex) {
              logger.warn("WebService Exception thrown when getting networkInfo for MDN " + account.getMdn());
              logger.warn("Error: " + ws_ex.getMessage());
              ProcessException process_ex = new ProcessException("Account Hotline Processing", ws_ex.getMessage(), ws_ex);
              process_ex.setAccountNo(Integer.toString(tscpMvneAccount.getAccountNo()));
              process_ex.setMdn(account.getMdn());
              process_ex.setNetworkInfo(networkInfo);
              process_ex.setAccount(tscpMvneAccount);
              throw process_ex;
            } catch (CustomerException customer_ex) {
              logger.warn("CustomerException thrown :: " + customer_ex.getMessage() + "...skipping account " + account.getAccountno());
              ProcessException process_ex = new ProcessException("Account Hotline Processing", customer_ex.getMessage(), customer_ex);
              process_ex.setAccountNo(Integer.toString(tscpMvneAccount.getAccountNo()));
              process_ex.setMdn(account.getMdn());
              process_ex.setNetworkInfo(networkInfo);
              process_ex.setAccount(tscpMvneAccount);
              throw process_ex;
            }
          }
        } catch (ProcessException process_ex) {
          if (process_ex.getSubject() == null) {
            process_ex.setSubject("Hotline Processing Exception Error");
          }
          if (process_ex.getAccount() == null) {
            process_ex.setAccount(new com.tscp.mvne.Account());
          }
          process_ex.getAccount().setFirstname("TruConnect Support Team");
          process_ex.getAccount().setContactEmail(EMAIL_ERROR);
          String body = EmailHelper.getErrorBody(process_ex.getAccount().getFirstname(), process_ex.getAccountNo(), process_ex.getMdn(), process_ex
              .getNetworkInfo().getEsnmeiddec(), process_ex.getAction(), process_ex.getMessage());
          sendEmail(process_ex.getAccount().getContactEmail(), process_ex.getSubject(), body);
        }
      }

    } else {
      logger.info("No accounts to hotline");
    }
  }

  @Deprecated
  protected void restoreAccounts() {
    // get list of accounts to restore
    List<Account> accountList = getAccountsToRestoreList();

    if (accountList != null) {
      for (Account account : accountList) {
        try {
          tscpMvneAccount = new com.tscp.mvne.Account();
          networkInfo = new NetworkInfo();

          getCustomerInfo(account);

          if (true/* account.getAccountno() == 681789 */) {
            try {
              if (networkInfo.getStatus().equals(SWITCH_STATUS_SUSPENDED)) {
                // submit restore request
                ServiceInstance serviceInstance = new ServiceInstance();
                serviceInstance.setExternalId(account.getMdn());
                try {
                  // Method no longer exposed as webmethod. Use restoreAccount
                  // instead.
                  // port.restoreService(serviceInstance);
                } catch (WebServiceException ws_ex) {
                  logger.warn("WebService Exception thrown when restoring MDN " + account.getMdn());
                  logger.warn("Error: " + ws_ex.getMessage());
                  if (ws_ex.getMessage().indexOf("does not exist") > 0) {
                    throw new CustomerException("MDN " + serviceInstance.getExternalId() + "is currently not active and was not restored...");
                  } else {
                    throw new CustomerException(ws_ex.getMessage(), ws_ex.getCause());
                  }
                }
              } else if (networkInfo.getStatus().equals(SWITCH_STATUS_ACTIVE)) {
                throw new CustomerException("Account " + account.getAccountno() + " is in the list to be restored however service is already in restored state");
              } else {
                // logger.info("MDN "+account.getMdn()+" skipped suspend because it is currently in status "+networkInfo.getStatus());
                throw new CustomerException("MDN " + account.getMdn() + " was not restored because it is currently in status " + networkInfo.getStatus());
              }

              // send notification
              logger.info("notifications are no longer sent through this medium");
              // String body =
              // getRestoredAccountNotification(tscpMvneAccount.getFirstname(),Integer.toString(tscpMvneAccount.getAccountno()),account.getMdn(),networkInfo.getEsnmeiddec(),legibleDate.format(new
              // Date()));
              // sendEmail(tscpMvneAccount.getContactEmail(),"Your Account "+tscpMvneAccount.getAccountno()+" has been Restored",body);
              logger.info("Account " + account.getAccountno() + " and Mdn " + account.getMdn() + " has been restored");

            } catch (WebServiceException ws_ex) {
              logger.warn("WebService Exception thrown when getting networkInfo for MDN " + account.getMdn());
              logger.warn("Error: " + ws_ex.getMessage());
              ProcessException process_ex = new ProcessException("Account Restore Processing", ws_ex.getMessage(), ws_ex);
              process_ex.setAccountNo(Integer.toString(tscpMvneAccount.getAccountNo()));
              process_ex.setMdn(account.getMdn());
              process_ex.setNetworkInfo(networkInfo);
              process_ex.setAccount(tscpMvneAccount);
              throw process_ex;
            } catch (CustomerException customer_ex) {
              logger.warn("CustomerException thrown :: " + customer_ex.getMessage() + "...skipping account " + account.getAccountno());
              ProcessException process_ex = new ProcessException("Account Restore Processing", customer_ex.getMessage(), customer_ex);
              process_ex.setAccountNo(Integer.toString(tscpMvneAccount.getAccountNo()));
              process_ex.setMdn(account.getMdn());
              process_ex.setNetworkInfo(networkInfo);
              process_ex.setAccount(tscpMvneAccount);
              throw process_ex;
            }
          }
        } catch (ProcessException process_ex) {
          if (process_ex.getSubject() == null) {
            process_ex.setSubject("Hotline Processing Exception Error");
          }
          if (process_ex.getAccount() == null) {
            process_ex.setAccount(new com.tscp.mvne.Account());
          }
          process_ex.getAccount().setFirstname("TruConnect Support Team");
          process_ex.getAccount().setContactEmail(EMAIL_ERROR);
          String body = EmailHelper.getErrorBody(process_ex.getAccount().getFirstname(), process_ex.getAccountNo(), process_ex.getMdn(), process_ex
              .getNetworkInfo().getEsnmeiddec(), process_ex.getAction(), process_ex.getMessage());
          sendEmail(process_ex.getAccount().getContactEmail(), process_ex.getSubject(), body);
          CustNotification notification = new CustNotification();
          notification.setAccountNo(Integer.parseInt(process_ex.getAccountNo()));
          notification.setMdn(process_ex.getMdn());
          notification.setEsn(process_ex.getNetworkInfo().getEsnmeiddec());
          // notification.setCustId(process_ex.getCustId());
          notification.setBody(body);
        }
      }

    } else {
      logger.info("No accounts to restore");
    }
  }

  private void getCustomerInfo(Account account) throws ProcessException {
    ProcessException process_ex = null;
    try {
      logger.info("Getting Customer Info for Account " + account.getAccountno());
      customer = getCustomerFromAccount(account.getAccountno());
      if (customer.getId() == 0) {
        throw new CustomerException("Customer Info not be found for Account " + account.getAccountno());
      }
    } catch (CustomerException cust_ex) {
      process_ex = new ProcessException("CustomerInformation Retrieval", cust_ex);
    }

    try {
      logger.debug("Creating TSCPMVNE.Account Object for customer " + customer.getId());
      tscpMvneAccount = getAccount(account.getAccountno());
    } catch (CustomerException cust_ex) {
      if (process_ex == null) {
        process_ex = new ProcessException("AccountInformation Retrieval", cust_ex);
      }
    }
    if (process_ex != null) {
      process_ex.setAccountNo(Integer.toString(account.getAccountno()));
      process_ex.setMdn(account.getMdn());
      process_ex.setAccount(tscpMvneAccount);
      process_ex.setNetworkInfo(networkInfo);
      throw process_ex;
    }
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

  private CustTopUp getCustomerTopUpAmount(Customer customer, com.tscp.mvne.Account tscpMvneAccount) throws CustomerException {
    CustTopUp custTopUp = new CustTopUp();
    custTopUp = port.getCustTopUpAmount(customer, tscpMvneAccount);
    if (custTopUp == null || custTopUp.getTopupAmount() == null || custTopUp.getTopupAmount().trim().length() == 0) {
      throw new CustomerException("Customer topup amount has not been set");
    }
    return custTopUp;
  }

  private NetworkInfo getNetworkInfo(Account account) throws NetworkException {
    try {
      NetworkInfo networkInfo = port.getNetworkInfo(null, account.getMdn());
      if (networkInfo == null || networkInfo.getEsnmeiddec() == null || networkInfo.getEsnmeiddec().trim().length() == 0) {
        throw new NetworkException("Unable to get NetworkInfo for MDN " + account.getMdn());
      }
      return networkInfo;
    } catch (NetworkException_Exception e) {
      throw new NetworkException(e);
    }
  }

  private int getCustomerPaymentDefault(Customer customer) throws CustomerException {
    // PaymentInformation paymentInfo = new PaymentInformation();
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

  private PaymentUnitResponse makePayment(Customer customer, int paymentId, com.tscp.mvne.Account account, CreditCard creditCard, String amount)
      throws PaymentException {
    logger.info("Making payment for CustomerId " + customer.getId() + " against Pmt ID " + paymentId + " in the Amount of $" + amount + ".");
    String sessionid = "CID" + customer.getId() + "T" + getTimeStamp() + "AUTO";
    try {
      PaymentUnitResponse response = port.submitPaymentByPaymentId(sessionid, customer, paymentId, account, amount);
      return response;
    } catch (WebServiceException wse) {
      logger.warn("WebService Exception thrown :: " + wse.getMessage());
      // will catch this exception at main()
      if (wse.getMessage().indexOf("Attempted to read or write protected memory") >= 0) {
        throw wse;
      }
      // wse.printStackTrace();
      if (wse.getCause() != null) {
        logger.warn("Immediate WSException Cause was :: " + wse.getCause().getMessage());
      }
      throw new PaymentException(wse.getMessage());

    }
  }

  private List<Account> getAccountToChargeList() {
    logger.info("Fetching accounts to charge...");
    Session session = HibernateUtil.getSessionFactory().getCurrentSession();
    session.beginTransaction();
    Query q = session.getNamedQuery("sp_fetch_accts_to_charge");
    List<Account> accountList = q.list();
    logger.info("   ...{} accounts will be topped-up", accountList.size());
    session.getTransaction().commit();
    return accountList;
  }

  /**
   * Now handled in TSCPMVNE.TruConnect.submitPaymentById.
   * 
   * @return
   */

  @Deprecated
  private List<Account> getAccountsToHotLineList() {
    logger.info("Fetching accounts to suspend...");
    Session session = HibernateUtil.getSessionFactory().getCurrentSession();
    session.beginTransaction();
    Query q = session.getNamedQuery("sp_fetch_accts_to_hotline");
    List<Account> accountList = q.list();
    logger.info("   ...{} accounts will be suspended", accountList.size());
    for (Account account : accountList) {
      logger.info(account.toString());
    }
    session.getTransaction().commit();
    return accountList;
  }

  /**
   * Now handled in TSCPMVNE.TruConnect.submitPaymentById.
   * 
   * @return
   */

  @Deprecated
  private List<Account> getAccountsToRestoreList() {
    logger.info("Fetching accounts to restore...");
    Session session = HibernateUtil.getSessionFactory().getCurrentSession();
    session.beginTransaction();
    Query q = session.getNamedQuery("sp_fetch_accts_to_restore");
    List<Account> accountList = q.list();
    logger.info("   ...{} accounts will be restored", accountList.size());
    for (Account account : accountList) {
      logger.info(account.toString());
    }
    session.getTransaction().commit();
    return accountList;
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

  public static void main(String[] args) {
	  WebOnTheGoBackend tcb = new WebOnTheGoBackend();
    System.out.println("Timestamp " + getTimeStamp());
    try {
      tcb.chargeAccounts();
    } catch (WebServiceException wsException) {
      if (wsException.getMessage().indexOf("Attempted to read or write protected memory") >= 0) {
        System.err.println("Memory corrupt. Exiting the process.");
        System.exit(1);
      }
    }
    // tcb.hotlineAccounts();
    // tcb.restoreAccounts();
  }

  private static String getTimeStamp() {
    return sdf.format(new Date());
  }

  /**
    * The balance should already be retrieved when fetching the Account.
    * @param accountNo
    * @return
    */
  //@Deprecated 
  /* private CustBalance getCustBalance(int accountNo) {
    Session session = HibernateUtil.getSessionFactory().getCurrentSession();
    session.beginTransaction();
    CustBalance custBalance = null;
    Query q = session.getNamedQuery("get_cust_balance");
    q.setParameter("in_user_name",  USERNAME);
    q.setParameter("in_account_no", accountNo);
    List<CustBalance> custBalanceList = q.list();
    if (custBalanceList != null && !custBalanceList.isEmpty()) {
      custBalance = custBalanceList.get(0);
    }
    session.getTransaction().commit();
    return custBalance;
  }*/
}
