package com.orbisflow.users.domain;

public enum UserRole {
    EMPLOYEE,
    MANAGER,
    FINANCE;

    public String claimValue() {
        return name().toLowerCase();
    }

    public static UserRole fromDatabase(String value) {
        return valueOf(value.toUpperCase());
    }
}
