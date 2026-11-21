package com.iitsm.exception;

public class DuplicateIncidentException extends ItsmException {
    public DuplicateIncidentException(String message) { super(message); }
    public DuplicateIncidentException(String message, Throwable cause) { super(message, cause); }
}
