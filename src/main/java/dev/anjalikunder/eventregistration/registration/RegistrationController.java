package dev.anjalikunder.eventregistration.registration;

import dev.anjalikunder.eventregistration.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RegistrationController {

    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    /** Result status tells the caller whether they got a seat (CONFIRMED) or a waitlist spot (WAITLISTED). */
    @PostMapping("/events/{eventId}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@AuthenticationPrincipal AuthUser caller, @PathVariable Long eventId) {
        return service.register(caller, eventId);
    }

    @DeleteMapping("/events/{eventId}/registrations/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthUser caller, @PathVariable Long eventId) {
        service.cancelMine(caller, eventId);
    }

    @GetMapping("/registrations/me")
    public List<RegistrationResponse> mine(@AuthenticationPrincipal AuthUser caller) {
        return service.mine(caller);
    }

    @GetMapping("/events/{eventId}/registrations")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RegistrationResponse> forEvent(@PathVariable Long eventId) {
        return service.forEvent(eventId);
    }
}
