package com.iitsm.model;

public enum Priority {
    P1("Critical"), P2("High"), P3("Medium"), P4("Low");
    private final String label;
    Priority(String label) { this.label = label; }
    public String label() { return label; }
}
