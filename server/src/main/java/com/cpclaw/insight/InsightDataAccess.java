package com.cpclaw.insight;

/**
 * Request-scoped CloudPivot access used by insight execution. Credentials are
 * deliberately kept out of task DTOs and persistence; callers must provide
 * them only for the lifetime of the current execution.
 */
public record InsightDataAccess(String baseUrl, String username, String password) {
    public InsightDataAccess {
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        username = username == null ? "" : username.trim();
        password = password == null ? "" : password;
    }

    public boolean usable() {
        return !baseUrl.isBlank() && !username.isBlank() && !password.isBlank();
    }
}
