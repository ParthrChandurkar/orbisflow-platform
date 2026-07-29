package com.orbisflow.requests.domain;

public enum PaymentStatus {
    PAID,
    SCHEDULED;

    public String value() {
        return name().toLowerCase();
    }

    public static PaymentStatus fromApi(String value) {
        return switch (value == null ? "" : value) {
            case "paid" -> PAID;
            case "scheduled" -> SCHEDULED;
            default -> throw new IllegalArgumentException(
                    "payment_status must be paid or scheduled.");
        };
    }
}
