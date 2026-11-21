package com.iitsm.exception;

/** Root of all domain-specific (unchecked) exceptions. */
public class ItsmException extends RuntimeException {
    public ItsmException(String message) { super(message); }
    public ItsmException(String message, Throwable cause) { super(message, cause); }
}
