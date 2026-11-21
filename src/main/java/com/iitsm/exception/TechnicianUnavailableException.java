package com.iitsm.exception;

public class TechnicianUnavailableException extends ItsmException {
    public TechnicianUnavailableException(String message) { super(message); }
    public TechnicianUnavailableException(String message, Throwable cause) { super(message, cause); }
}
