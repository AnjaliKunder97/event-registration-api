package dev.anjalikunder.eventregistration.registration;

import dev.anjalikunder.eventregistration.event.Event;
import dev.anjalikunder.eventregistration.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "registrations")
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Registration() {
        // required by JPA
    }

    public Registration(Event event, User user, RegistrationStatus status) {
        this.event = event;
        this.user = user;
        this.status = status;
    }

    /** Moves a waitlisted registration to confirmed. */
    public void confirm() {
        this.status = RegistrationStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = RegistrationStatus.CANCELLED;
    }

    public Long getId() { return id; }
    public Event getEvent() { return event; }
    public User getUser() { return user; }
    public RegistrationStatus getStatus() { return status; }
}
