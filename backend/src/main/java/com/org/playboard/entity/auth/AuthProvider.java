package com.org.playboard.entity.auth;

/**
 * Identity providers accepted by Playboard authentication. {@link #REVIEWER} is the
 * internal store-review credential (see ReviewerCredentialVerifier), not an external
 * provider, and is never exposed in {@code authProviders}.
 */
public enum AuthProvider {
    GOOGLE("google"),
    APPLE("apple"),
    REVIEWER("reviewer");

    private final String apiValue;

    AuthProvider(String apiValue) {
        this.apiValue = apiValue;
    }

    /** Stable lowercase value exposed in API responses and persisted in PostgreSQL. */
    public String apiValue() {
        return apiValue;
    }
}
