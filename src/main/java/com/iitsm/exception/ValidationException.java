package com.iitsm.exception;

public class ValidationException extends ItsmException {
    public ValidationException(String message) { super(message); }
    public ValidationException(String message, Throwable cause) { super(message, cause); }
}
