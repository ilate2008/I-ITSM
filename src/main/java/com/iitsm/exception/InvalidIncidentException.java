package com.iitsm.exception;

public class InvalidIncidentException extends ItsmException {
    public InvalidIncidentException(String message) { super(message); }
    public InvalidIncidentException(String message, Throwable cause) { super(message, cause); }
}
