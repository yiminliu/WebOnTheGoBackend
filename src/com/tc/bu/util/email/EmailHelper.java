package com.tc.bu.util.email;

import com.tc.bu.exception.ProcessException;

public class EmailHelper {

  public static String getEmailHeader() {
    StringBuffer header = new StringBuffer();
    header.append("<html>\n");
    header.append("<body>\n");
    header.append("    <div style=\"width:750px; margin:0px auto;\" \n");
    header.append("    <a href='https://manage.truconnect.com/TruConnect/login'> \n");
    header.append("    <img class='logo' src='https://activate.truconnect.com/TruConnect/static/images/logo_s1.jpg'> \n");
    header.append("    </a> \n");
    header.append("    <br>&nbsp;</br> \n");
    return header.toString();
  }

  public static String getEmailFooter() {
    StringBuffer footer = new StringBuffer();
    footer.append("    <div style=\"background:#EFEFEF \"> \n");
    footer.append("    <p> \n");
    footer.append("        Sincerely, \n");
    footer.append("    <br> \n");
    footer.append("    <b>TruConnect</b> \n");
    footer.append("    </p> \n");
    footer.append("    </div> \n");
    footer.append("    <br> \n");
    footer.append("    <div style=\"border-top:1px solid #999; margin-bottom:15px; font-size:15px;\"> \n");
    footer.append("    <br> \n");
    footer.append("    <div style=\"color:#6D7B8D; font-size:0.8em;\"> \n");
    footer.append("        <b>Please Do Not Reply to this Message</b> \n");
    footer
        .append("        <br>All replies are autmatically deleted. For questions regarding this message, refer to the contact information listed above.</br> \n");
    footer
        .append("        <p><a href=\"www.truconnect.com\">&copy2011 TruConnect Intellectual Property.</a> All rights reserved. TruConnect, the TruConnect logo and all other TruConnect marks contained herein are trademarks of TruConnect Intellectual Property and/or TruConnect affiliated companies. Subsidiaries and affiliates of TruConnect LLC provide products and services under the TruConnect Brand</p> \n");
    footer.append("        <p><a href=\"www.truconnect.com\">Privacy Policy</a></p> \n");
    footer.append("        <p>Questions? Please visit the <a href=\"www.truconnect.com/support\">Support Page</a></p> \n");
    footer.append("    </div> \n");
    footer.append("</div> \n");
    footer.append("</body> \n");
    footer.append("</html> \n");
    return footer.toString();
  }

  public static String getPaymentSuccessBody(String userName, String accountNo, String tn, String esn, String confirmationNumber, String amount,
      String paymentMethod, String paymentSource, String paymentDate, String balance) {
    StringBuffer body = new StringBuffer();
    body.append("<div style=\"color:#2554C7; font-size:1.25em; background:#EAEAEA\"> \n");
    body.append("<b>Your TruConnect Payment Processed</b> \n");
    body.append("</div> \n");
    body.append("<p> \n");
    body.append("   <b>Dear " + userName + ",</b> \n");
    body.append("</p> \n");
    body.append("<p>Thank you for your payment. Your payment has been successfully processed and will be noted immediately to your account. Below you will find the transaction information regarding your payment.</p> \n");
    body.append("");
    body.append("<p> \n");
    body.append("   <table border=1> \n");
    body.append("       <th>Account</th> \n");
    body.append("       <th>TN</th>");
    body.append("       <th>ESN</th>");
    body.append("       <th>Confirmation Number</th> \n");
    body.append("       <th>Amount</th> \n");
    body.append("       <th>Payment Method</th> \n");
    body.append("       <th>Payment Source</th> \n");
    body.append("       <th>Payment Date</th> \n");
    body.append("       <tr>");
    body.append("           <td>" + accountNo + "</td> \n");
    body.append("           <td>" + tn + "</td> \n");
    body.append("           <td>" + esn + "</td> \n");
    body.append("           <td>" + confirmationNumber + "</td> \n");
    body.append("           <td>$" + amount + "</td> \n");
    body.append("           <td>" + paymentMethod + "</td> \n");
    body.append("           <td>" + paymentSource + "</td> \n");
    body.append("           <td>" + paymentDate + "</td> \n");
    body.append("       </tr> \n");
    body.append("   </table> \n");
    body.append("</p> \n");
    body.append("");
    body.append("<p> \n");
    body.append("   <a href=\"https://manage.truconnect.com/truconnect/login\">Log in</a> and manage your billing and payment information \n");
    body.append("</p> \n");
    body.append("<p> \n");
    body.append("   Thank you for choosing TruConnect for your wireless and data needs. We value your business and look forward to serving you! \n");
    body.append("</p> \n");
    return body.toString();
  }

  public static String getPaymentFailureBody(String userName, String accountNumber, String tn, String esn, String paymentAmount, String paymentMethod,
      String paymentSource, String paymentDate, String comments, String remainingBalance) {
    StringBuffer body = new StringBuffer();
    body.append("<div style=\"color:#306EFF; font-size:1.25em; background:#EAEAEA\">\n ");
    body.append("<b>Your TruConnect Payment Failed to Process</b>\n ");
    body.append("</div>\n ");
    body.append("<p>\n ");
    body.append("   <b>Dear " + userName + ",</b>\n ");
    body.append("</p>\n ");
    body.append("<p>Your payment has encountered issues when attempting to top up funds to your account. Below you will find the transaction information regarding your attempted payment.</p>\n ");
    body.append("\n ");
    body.append("<p>\n ");
    body.append("   <table border=1>\n ");
    body.append("       <th>Account</th>\n ");
    body.append("       <th>TN</th>\n ");
    body.append("       <th>ESN</th>\n ");
    body.append("       <th>Amount</th>\n ");
    body.append("       <th>Payment Method</th>\n ");
    body.append("       <th>Payment Source</th>\n ");
    body.append("       <th>Payment Date</th>\n ");
    body.append("       <th>Comments</th>\n ");
    body.append("       <tr>\n ");
    body.append("           <td>" + accountNumber + "</td>\n ");
    body.append("           <td>" + tn + "</td> \n");
    body.append("           <td>" + esn + "</td> \n");
    body.append("           <td>$" + paymentAmount + "</td>\n ");
    body.append("           <td>" + paymentMethod + "</td>\n ");
    body.append("           <td>" + paymentSource + "</td>\n ");
    body.append("           <td>" + paymentDate + "</td>\n ");
    body.append("           <td>" + comments + "</td>\n ");
    body.append("       </tr>\n ");
    body.append("   </table>\n ");
    body.append("</p>\n ");
    body.append("\n ");
    body.append("<p>\n ");
    body.append("   <a href=\"https://manage.truconnect.com/truconnect/login\">Log in</a> and manage your billing and payment information\n ");
    body.append("</p>\n ");
    body.append("<p>\n ");
    body.append("   Please make any necessary modifications to your payment information and add funds to your account to avoid service interruption. Your remaining balance is <b>"
        + remainingBalance + "</b> ");
    body.append("</p>\n ");
    return body.toString();
  }

  public static String getSuspendedAccountNotification(String userName, String accountno, String mdn, String esn, String suspendDate) {
    StringBuffer body = new StringBuffer();
    body.append("<div style=\"color:#306EFF; font-size:1.25em; background:#EAEAEA\"> \n");
    body.append("<b>Your TruConnect Service has been Suspended</b> \n");
    body.append("</div> \n");
    body.append("<p> \n");
    body.append("   <b>Dear " + userName + ",</b> \n");
    body.append("</p> \n");
    body.append("<p>Services associated with your account "
        + accountno
        + " have been temporarily suspended due to lack of funds. Please add more funds your account inorder to restore service. Below you will find the device information regarding this suspension.</p> \n");
    body.append(" \n");
    body.append("<p> \n");
    body.append("   <table border=\"1\"> \n");
    body.append("       <th>Account</th> \n");
    body.append("       <th>TN</th> \n");
    body.append("       <th>ESN</th> \n");
    body.append("       <th>Suspend Date</th> \n");
    body.append("       <tr> \n");
    body.append("           <td>" + accountno + "</td> \n");
    body.append("           <td>" + mdn + "</td> \n");
    body.append("           <td>" + esn + "</td> \n");
    body.append("           <td>" + suspendDate + "</td> \n");
    body.append("       </tr> \n");
    body.append("   </table> \n");
    body.append("</p> \n");
    body.append(" \n");
    body.append("<p> \n");
    body.append("   <a href=\"https://manage.truconnect.com/truconnect/login\">Log in</a> and manage your billing and payment information \n");
    body.append("</p> \n");
    body.append("<p> \n");
    body.append("   Thank you for choosing TruConnect for your wireless and data needs. We value your business and look forward to serving you! \n");
    body.append("</p>");
    return body.toString();
  }

  public static String getRestoredAccountNotification(String userName, String accountno, String mdn, String esn, String restoreDate) {
    StringBuffer body = new StringBuffer();
    body.append("<div style=\"color:#306EFF; font-size:1.25em; background:#EAEAEA\"> \n");
    body.append("<b>Your TruConnect Service has been Restored</b> \n");
    body.append("</div> \n");
    body.append("<p> \n");
    body.append("   <b>Dear " + userName + ",</b> \n");
    body.append("</p> \n");
    body.append("<p>Services associated with your account " + accountno
        + " have been restored. Below you will find the device information regarding this restoral transaction.</p> \n");
    body.append(" \n");
    body.append("<p> \n");
    body.append("   <table border=\"1\"> \n");
    body.append("       <th>Account</th> \n");
    body.append("       <th>TN</th> \n");
    body.append("       <th>ESN</th> \n");
    body.append("       <th>Restore Date</th> \n");
    body.append("       <tr> \n");
    body.append("           <td>" + accountno + "</td> \n");
    body.append("           <td>" + mdn + "</td> \n");
    body.append("           <td>" + esn + "</td> \n");
    body.append("           <td>" + restoreDate + "</td> \n");
    body.append("       </tr> \n");
    body.append("   </table> \n");
    body.append("</p> \n");
    body.append(" \n");
    body.append("<p> \n");
    body.append("   <a href=\"https://manage.truconnect.com/truconnect/login\">Log in</a> and manage your billing and payment information \n");
    body.append("</p> \n");
    body.append("<p> \n");
    body.append("   Thank you for choosing TruConnect for your wireless and data needs. We value your business and look forward to serving you! \n");
    body.append("</p>");
    return body.toString();
  }

  public static String getErrorBody(String userName, String accountno, String mdn, String esn, String action, String error) {
    StringBuffer body = new StringBuffer();
    body.append(" <div style=\"color:#306EFF; font-size:1.25em; background:#EAEAEA\"> ");
    body.append(" <b>Your TruConnect Service has encountered an error</b> ");
    body.append(" </div> ");
    body.append(" <p> ");
    body.append("   <b>Dear " + userName + ",</b> ");
    body.append(" </p> ");
    body.append(" <p>An error was encountered when processing your service.</p> ");
    body.append(" <p>&nbsp;&nbsp;&nbsp;&nbsp;<i>" + error + "</i></p> ");
    body.append(" <p>Below are the service details along with the action that was attempted against your account:</p> ");
    body.append("  ");
    body.append(" <p> ");
    body.append("   <table border=\"1\"> ");
    body.append("       <th>Account</th> ");
    body.append("       <th>TN</th> ");
    body.append("       <th>ESN</th> ");
    body.append("       <th>Action</th> ");
    body.append("       <tr> ");
    body.append("           <td>" + accountno + "</td> ");
    body.append("           <td>" + mdn + "</td> ");
    body.append("           <td>" + esn + "</td> ");
    body.append("           <td>" + action + "</td> ");
    body.append("       </tr> ");
    body.append("   </table> ");
    body.append(" </p> ");
    body.append("  ");
    body.append(" <p> ");
    body.append("   Please <a href=\"https://manage.truconnect.com/truconnect/login\">log in</a> and correct the issue at your earliest convenience or contact TruConnect customer service at 1-855-878-2666. ");
    body.append(" </p> ");
    body.append(" <p> ");
    body.append("   Thank you for choosing TruConnect for your wireless and data needs. We value your business and look forward to serving you! ");
    body.append(" </p> ");

    return body.toString();
  }

  public static String getErrorBody(ProcessException process_ex) {
    StringBuffer body = new StringBuffer();
    body.append(" <div style=\"color:#306EFF; font-size:1.25em; background:#EAEAEA\"> ");
    body.append(" <b>Your TruConnect Service has encountered an error</b> ");
    body.append(" </div> ");
    body.append(" <p> ");
    body.append("   <b>Dear " + process_ex.getAccount().getFirstname() + ",</b> ");
    body.append(" </p> ");
    body.append(" <p>An error was encountered when processing your service.</p> ");
    body.append(" <p>&nbsp;&nbsp;&nbsp;&nbsp;<b><i>" + process_ex.getMessage() + "</i></b></p> ");
    body.append(" <p>Below are the service details along with the action that was attempted against your account:</p> ");
    body.append("  ");
    body.append(" <p> ");
    body.append("   <table border=\"1\"> ");
    body.append("       <th>Account</th> ");
    if (process_ex.getAccount().getFirstname() != null || process_ex.getAccount().getLastname() != null) {
      body.append("       <th>Customer Name</th> ");
    }
    body.append("       <th>TN</th> ");
    if (process_ex.getNetworkInfo() != null) {
      body.append("         <th>DEVICE</th> ");
    }
    if (process_ex.getAccount().getContactNumber() != null && process_ex.getAccount().getContactNumber().trim().length() > 0) {
      body.append("         <th>Contact Number</th> ");
    }
    body.append("       <th>Action</th> ");
    body.append("       <tr> ");
    body.append("           <td>" + process_ex.getAccountNo() + "</td> ");
    if (process_ex.getAccount().getFirstname() != null || process_ex.getAccount().getLastname() != null) {
      body.append("           <td>" + process_ex.getAccount().getFirstname() + " " + process_ex.getAccount().getLastname() + "</td> ");
    }
    body.append("           <td>" + process_ex.getMdn() + "</td> ");
    if (process_ex.getNetworkInfo() != null) {
      body.append("             <td>" + process_ex.getNetworkInfo().getEsnmeiddec() + "</td> ");
    }
    if (process_ex.getAccount().getContactNumber() != null && process_ex.getAccount().getContactNumber().trim().length() > 0) {
      body.append("             <td>" + process_ex.getAccount().getContactNumber() + "</td> ");
    }
    body.append("           <td>" + process_ex.getAction() + "</td> ");
    body.append("       </tr> ");
    body.append("   </table> ");
    body.append(" </p> ");
    body.append("  ");
    body.append(" <p> ");
    body.append("   Please <a href=\"https://manage.truconnect.com/truconnect/login\">log in</a> and correct the issue at your earliest convenience or contact TruConnect customer service at 1-855-878-2666. ");
    body.append(" </p> ");
    body.append(" <p> ");
    body.append("   Thank you for choosing TruConnect for your wireless and data needs. We value your business and look forward to serving you! ");
    body.append(" </p> ");

    return body.toString();
  }

}
