package dev.anjalikunder.eventregistration.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByStartsAtAfterOrderByStartsAtAsc(Instant now, Pageable pageable);

    /**
     * Loads the event with "SELECT ... FOR UPDATE". Other transactions that lock the same event wait here
     * until we commit, so "count confirmed, then decide" cannot be interleaved by two requests.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}
