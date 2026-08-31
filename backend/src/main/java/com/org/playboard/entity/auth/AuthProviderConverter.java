package com.org.playboard.entity.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link AuthProvider} using its stable lowercase API value. */
@Converter(autoApply = true)
public class AuthProviderConverter implements AttributeConverter<AuthProvider, String> {

    @Override
    public String convertToDatabaseColumn(AuthProvider provider) {
        return provider == null ? null : provider.apiValue();
    }

    @Override
    public AuthProvider convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        for (AuthProvider provider : AuthProvider.values()) {
            if (provider.apiValue().equals(value)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown auth provider: " + value);
    }
}
