package com.iitsm.exception;

public class WorkloadExceededException extends TechnicianUnavailableException {
    public WorkloadExceededException(String message) { super(message); }
}
