package com.org.playboard.dto.user;

/** Explicit confirmation required by the irreversible account-deletion endpoint. */
public record DeleteAccountRequest(String confirmation) {}
