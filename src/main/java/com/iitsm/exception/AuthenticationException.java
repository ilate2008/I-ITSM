package com.iitsm.exception;

public class AuthenticationException extends ItsmException {
    public AuthenticationException(String message) { super(message); }
    public AuthenticationException(String message, Throwable cause) { super(message, cause); }
}
