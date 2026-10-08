package dev.anjalikunder.eventregistration.auth;

import dev.anjalikunder.eventregistration.error.ConflictException;
import dev.anjalikunder.eventregistration.error.InvalidCredentialsException;
import dev.anjalikunder.eventregistration.security.JwtService;
import dev.anjalikunder.eventregistration.user.Role;
import dev.anjalikunder.eventregistration.user.User;
import dev.anjalikunder.eventregistration.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public TokenResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), Role.USER));
        return new TokenResponse(jwtService.generateToken(user));
    }

    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                // Same message for "unknown email" and "wrong password" so the API does not reveal which emails exist.
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        return new TokenResponse(jwtService.generateToken(user));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
