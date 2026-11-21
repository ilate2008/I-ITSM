package com.iitsm.exception;

public class MissingSlaConfigurationException extends ItsmException {
    public MissingSlaConfigurationException(String message) { super(message); }
    public MissingSlaConfigurationException(String message, Throwable cause) { super(message, cause); }
}
