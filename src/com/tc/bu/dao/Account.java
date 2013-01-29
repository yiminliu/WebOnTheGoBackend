package com.tc.bu.dao;

public class Account {
  int accountNo;
  String mdn;

  String realBalance;

  public Account() {

  }

  public int getAccountNo() {
    return accountNo;
  }

  public void setAccountNo(int accountNo) {
    this.accountNo = accountNo;
  }

  public String getMdn() {
    return mdn;
  }

  public void setMdn(String mdn) {
    this.mdn = mdn;
  }

  public String getRealBalance() {
    return realBalance;
  }

  public void setRealBalance(String realBalance) {
    this.realBalance = realBalance;
  }

  @Override
  public String toString() {
    // TODO Auto-generated method stub
    return "AccountNo :: " + getAccountNo() + " || MDN :: " + getMdn() + " || REAL_BALANCE :: " + getRealBalance();
  }

  @Override
  public boolean equals(Object obj) {
    // TODO Auto-generated method stub
    if (obj instanceof Account) {
      if (((Account) obj).getAccountNo() == getAccountNo() && ((Account) obj).getMdn().equals(getMdn())
          && ((Account) obj).getRealBalance().equals(getRealBalance())) {
        return true;
      }
    }
    return super.equals(obj);
  }
}
