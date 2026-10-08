package dev.anjalikunder.eventregistration.event;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final int MAX_PAGE_SIZE = 50;

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<EventResponse> upcoming(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return service.upcoming(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EventResponse create(@Valid @RequestBody EventRequest request) {
        return service.create(request);
    }
}
