package com.orbisflow.requests.domain;

public enum RequestStatus {
    UPLOADED_EXTRACTING,
    EMPLOYEE_REVIEW,
    MANAGER_REVIEW,
    REJECTED,
    FINANCE_REVIEW,
    PROCESSED;

    public String value() {
        return name().toLowerCase();
    }

    public static RequestStatus fromDatabase(String value) {
        return valueOf(value.toUpperCase());
    }
}
