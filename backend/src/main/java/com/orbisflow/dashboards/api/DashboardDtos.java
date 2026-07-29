package com.orbisflow.dashboards.api;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record TeamActivity(long pending, long approved, long rejected) {
    }
}
