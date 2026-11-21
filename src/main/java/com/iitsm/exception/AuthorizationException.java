package com.iitsm.exception;

public class AuthorizationException extends ItsmException {
    public AuthorizationException(String message) { super(message); }
    public AuthorizationException(String message, Throwable cause) { super(message, cause); }
}
