package com.tc.bu.util;

public enum TscpMvnaWebService {

	WSDL("http://10.10.30.190:8080/TSCPMVNA/TSCPMVNAService?WSDL"),
	NAMESPACE("http://mvne.tscp.com/"),
	SERVICENAME("TSCPMVNAService");
	
	private String value;
	
	private TscpMvnaWebService(String value){
		this.value = value;
	}	
}
