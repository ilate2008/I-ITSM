package com.iitsm.service;

public record Overview(int total, int open, int critical, int resolved, int slaViolations) {
    @Override public String toString() {
        return "Total=%d  Open=%d  Critical(P1)=%d  Resolved=%d  SLA violations=%d".formatted(total, open, critical, resolved, slaViolations);
    }
}
