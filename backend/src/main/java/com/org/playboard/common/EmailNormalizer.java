package com.org.playboard.common;

import java.util.Locale;

/**
 * Canonicalizes an email to a single stable form (trimmed + lowercased) used
 * for BOTH storage and lookup. {@code users.email} is unique and looked up by
 * exact match on the Google sign-in claim path ({@code findByEmail}), so a
 * casing/whitespace mismatch between an admin-entered address and the address
 * Google returns would miss the link and create a duplicate account. Normalizing
 * everywhere an email enters the system keeps the two in agreement.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {}

    /** Returns {@code null} for {@code null}; otherwise the trimmed, lowercased email. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
