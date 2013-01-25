package com.tc.bu.util;

public enum DeviceStatus {
	 
	ACTIVE("A"), SUSPENDED("S"), DISCONNECTED("C");
	 
	private String value;
	 
	private DeviceStatus(String value){
	   this.value = value;
	}
}
