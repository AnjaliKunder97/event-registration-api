package dev.anjalikunder.eventregistration.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        // BCrypt only uses the first 72 bytes of a password, so longer input is rejected.
        @NotBlank @Size(min = 8, max = 72) String password) {
}
