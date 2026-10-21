package com.org.playboard.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Store-review username/password credential exchanged for a Playboard session. */
public record ReviewerSignInRequest(
        @NotBlank @Size(max = 200) String username,
        @NotBlank @Size(max = 200) String password) {

    // Keep the password out of logs and any accidental toString() (e.g. exception messages).
    @Override
    public String toString() {
        return "ReviewerSignInRequest[username=" + username + ", password=***]";
    }
}
