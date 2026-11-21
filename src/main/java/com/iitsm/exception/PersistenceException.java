package com.iitsm.exception;

public class PersistenceException extends ItsmException {
    public PersistenceException(String message) { super(message); }
    public PersistenceException(String message, Throwable cause) { super(message, cause); }
}
