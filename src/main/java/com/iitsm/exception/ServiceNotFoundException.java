package com.iitsm.exception;

public class ServiceNotFoundException extends ItsmException {
    public ServiceNotFoundException(String message) { super(message); }
    public ServiceNotFoundException(String message, Throwable cause) { super(message, cause); }
}
