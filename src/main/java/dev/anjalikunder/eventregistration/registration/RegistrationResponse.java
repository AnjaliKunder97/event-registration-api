package dev.anjalikunder.eventregistration.registration;

public record RegistrationResponse(Long id, Long eventId, String eventTitle, Long userId, RegistrationStatus status) {

    static RegistrationResponse from(Registration r) {
        return new RegistrationResponse(
                r.getId(),
                r.getEvent().getId(),
                r.getEvent().getTitle(),
                r.getUser().getId(),
                r.getStatus());
    }
}
