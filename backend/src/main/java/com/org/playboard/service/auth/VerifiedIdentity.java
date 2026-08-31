package com.org.playboard.service.auth;

/** Claims trusted only after an external provider token passes verification. */
public record VerifiedIdentity(String subject, String email, String displayName) {}
