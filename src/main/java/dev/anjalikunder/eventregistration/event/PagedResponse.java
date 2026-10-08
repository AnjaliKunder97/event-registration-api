package dev.anjalikunder.eventregistration.event;

import org.springframework.data.domain.Page;

import java.util.List;

/** Our own page format, so the JSON shape does not depend on Spring Data's internal PageImpl. */
public record PagedResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PagedResponse<T> of(Page<?> page, List<T> items) {
        return new PagedResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
