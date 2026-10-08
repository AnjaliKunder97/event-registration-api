package dev.anjalikunder.eventregistration.security;

import dev.anjalikunder.eventregistration.user.Role;

/** The authenticated caller, taken from the JWT. Available in controllers via @AuthenticationPrincipal. */
public record AuthUser(Long id, String email, Role role) {
}
