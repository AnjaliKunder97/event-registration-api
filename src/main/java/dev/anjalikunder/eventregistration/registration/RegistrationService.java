package dev.anjalikunder.eventregistration.registration;

import dev.anjalikunder.eventregistration.error.ConflictException;
import dev.anjalikunder.eventregistration.error.NotFoundException;
import dev.anjalikunder.eventregistration.event.Event;
import dev.anjalikunder.eventregistration.event.EventRepository;
import dev.anjalikunder.eventregistration.security.AuthUser;
import dev.anjalikunder.eventregistration.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static dev.anjalikunder.eventregistration.registration.RegistrationStatus.CONFIRMED;
import static dev.anjalikunder.eventregistration.registration.RegistrationStatus.WAITLISTED;

@Service
public class RegistrationService {

    /** Statuses that count as "currently registered". */
    private static final List<RegistrationStatus> ACTIVE = List.of(CONFIRMED, WAITLISTED);

    private final RegistrationRepository registrations;
    private final EventRepository events;
    private final UserRepository users;

    public RegistrationService(RegistrationRepository registrations, EventRepository events, UserRepository users) {
        this.registrations = registrations;
        this.events = events;
        this.users = users;
    }

    /**
     * Registers the caller. Gets a seat if there is one, otherwise goes on the waitlist.
     *
     * The event row is locked first (SELECT ... FOR UPDATE). Without the lock, two requests could both count
     * 4 confirmed seats out of 5 and both take the last one. With the lock, they run one after another.
     */
    @Transactional
    public RegistrationResponse register(AuthUser caller, Long eventId) {
        Event event = lockEvent(eventId);

        if (!event.getStartsAt().isAfter(Instant.now())) {
            throw new ConflictException("Event has already started");
        }
        if (registrations.existsByEventIdAndUserIdAndStatusIn(eventId, caller.id(), ACTIVE)) {
            throw new ConflictException("You are already registered for this event");
        }

        long confirmed = registrations.countByEventIdAndStatus(eventId, CONFIRMED);
        RegistrationStatus status = confirmed < event.getCapacity() ? CONFIRMED : WAITLISTED;

        Registration saved = registrations.save(new Registration(event, users.getReferenceById(caller.id()), status));
        return RegistrationResponse.from(saved);
    }

    /**
     * Cancels the caller's registration. If it held a confirmed seat, the longest-waiting person on the
     * waitlist gets that seat, inside the same transaction, so the seat is never left empty or given twice.
     */
    @Transactional
    public void cancelMine(AuthUser caller, Long eventId) {
        lockEvent(eventId);

        Registration registration = registrations
                .findByEventIdAndUserIdAndStatusIn(eventId, caller.id(), ACTIVE)
                .orElseThrow(() -> new NotFoundException("You are not registered for this event"));

        boolean freesSeat = registration.getStatus() == CONFIRMED;
        registration.cancel();

        if (freesSeat) {
            registrations.findFirstByEventIdAndStatusOrderByIdAsc(eventId, WAITLISTED)
                    .ifPresent(Registration::confirm);
        }
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> mine(AuthUser caller) {
        return registrations.findByUserIdOrderByIdDesc(caller.id()).stream()
                .map(RegistrationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> forEvent(Long eventId) {
        if (!events.existsById(eventId)) {
            throw new NotFoundException("Event " + eventId + " not found");
        }
        return registrations.findByEventIdOrderByIdAsc(eventId).stream()
                .map(RegistrationResponse::from)
                .toList();
    }

    private Event lockEvent(Long eventId) {
        return events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("Event " + eventId + " not found"));
    }
}
