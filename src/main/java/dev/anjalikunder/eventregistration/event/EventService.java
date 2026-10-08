package dev.anjalikunder.eventregistration.event;

import dev.anjalikunder.eventregistration.error.NotFoundException;
import dev.anjalikunder.eventregistration.registration.RegistrationRepository;
import dev.anjalikunder.eventregistration.registration.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventService {

    private final EventRepository events;
    private final RegistrationRepository registrations;

    public EventService(EventRepository events, RegistrationRepository registrations) {
        this.events = events;
        this.registrations = registrations;
    }

    @Transactional
    public EventResponse create(EventRequest request) {
        Event event = events.save(new Event(request.title(), request.description(), request.startsAt(), request.capacity()));
        return EventResponse.from(event, 0, 0);
    }

    /** Upcoming events, soonest first, one page at a time. */
    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> upcoming(int page, int size) {
        Page<Event> result = events.findByStartsAtAfterOrderByStartsAtAsc(Instant.now(), PageRequest.of(page, size));
        // One grouped query for the whole page, instead of two count queries per event.
        Map<Long, long[]> counts = loadCounts(result.getContent().stream().map(Event::getId).toList());
        List<EventResponse> items = result.getContent().stream()
                .map(e -> toResponse(e, counts))
                .toList();
        return PagedResponse.of(result, items);
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long id) {
        Event event = events.findById(id)
                .orElseThrow(() -> new NotFoundException("Event " + id + " not found"));
        return toResponse(event, loadCounts(List.of(id)));
    }

    private EventResponse toResponse(Event event, Map<Long, long[]> counts) {
        long[] c = counts.getOrDefault(event.getId(), new long[2]);
        return EventResponse.from(event, c[0], c[1]);
    }

    /** Returns eventId -> [confirmed, waitlisted]. */
    private Map<Long, long[]> loadCounts(Collection<Long> eventIds) {
        Map<Long, long[]> result = new HashMap<>();
        if (eventIds.isEmpty()) {
            return result;
        }
        List<RegistrationStatus> counted = List.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED);
        for (Object[] row : registrations.countByEventAndStatus(eventIds, counted)) {
            long[] c = result.computeIfAbsent((Long) row[0], k -> new long[2]);
            long count = (Long) row[2];
            if (row[1] == RegistrationStatus.CONFIRMED) {
                c[0] = count;
            } else {
                c[1] = count;
            }
        }
        return result;
    }
}
