package dev.anjalikunder.eventregistration.registration;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);

    /** The waitlisted person who has waited longest (lowest id). */
    Optional<Registration> findFirstByEventIdAndStatusOrderByIdAsc(Long eventId, RegistrationStatus status);

    boolean existsByEventIdAndUserIdAndStatusIn(Long eventId, Long userId, Collection<RegistrationStatus> statuses);

    Optional<Registration> findByEventIdAndUserIdAndStatusIn(Long eventId, Long userId,
                                                             Collection<RegistrationStatus> statuses);

    // @EntityGraph loads the event in the same query. Without it, reading event.getTitle() for each row
    // would run one extra query per registration (the N+1 problem).
    @EntityGraph(attributePaths = "event")
    List<Registration> findByUserIdOrderByIdDesc(Long userId);

    @EntityGraph(attributePaths = "event")
    List<Registration> findByEventIdOrderByIdAsc(Long eventId);

    /** One grouped query for many events: rows of [eventId, status, count]. */
    @Query("""
            select r.event.id, r.status, count(r)
            from Registration r
            where r.event.id in :eventIds and r.status in :statuses
            group by r.event.id, r.status
            """)
    List<Object[]> countByEventAndStatus(@Param("eventIds") Collection<Long> eventIds,
                                         @Param("statuses") Collection<RegistrationStatus> statuses);
}
