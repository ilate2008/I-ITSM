package com.iitsm.model;

public class Employee extends User {
    public Employee(String id, String name, String email, String passwordHash) { super(id, name, email, passwordHash); }
    @Override public Role role() { return Role.EMPLOYEE; }
}
