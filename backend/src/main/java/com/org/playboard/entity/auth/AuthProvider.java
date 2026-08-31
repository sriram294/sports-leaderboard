package com.org.playboard.entity.auth;

/** External identity providers accepted by Playboard authentication. */
public enum AuthProvider {
    GOOGLE("google"),
    APPLE("apple");

    private final String apiValue;

    AuthProvider(String apiValue) {
        this.apiValue = apiValue;
    }

    /** Stable lowercase value exposed in API responses and persisted in PostgreSQL. */
    public String apiValue() {
        return apiValue;
    }
}
