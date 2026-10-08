package dev.anjalikunder.eventregistration.event;

import java.time.Instant;

public record EventResponse(Long id, String title, String description, Instant startsAt,
                            int capacity, long confirmed, long waitlisted) {

    static EventResponse from(Event event, long confirmed, long waitlisted) {
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(),
                event.getStartsAt(), event.getCapacity(), confirmed, waitlisted);
    }
}
