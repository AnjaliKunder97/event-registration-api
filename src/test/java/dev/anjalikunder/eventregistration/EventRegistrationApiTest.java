package dev.anjalikunder.eventregistration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.anjalikunder.eventregistration.event.Event;
import dev.anjalikunder.eventregistration.event.EventRepository;
import dev.anjalikunder.eventregistration.registration.RegistrationRepository;
import dev.anjalikunder.eventregistration.registration.RegistrationService;
import dev.anjalikunder.eventregistration.registration.RegistrationStatus;
import dev.anjalikunder.eventregistration.security.AuthUser;
import dev.anjalikunder.eventregistration.security.JwtService;
import dev.anjalikunder.eventregistration.user.Role;
import dev.anjalikunder.eventregistration.user.User;
import dev.anjalikunder.eventregistration.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests against a real PostgreSQL started by Testcontainers (needs Docker).
 * Real PostgreSQL matters: the concurrency test depends on SELECT ... FOR UPDATE row locking.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EventRegistrationApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired RegistrationService registrationService;
    @Autowired JwtService jwtService;

    // ---------- registration rules ----------

    @Test
    void firstRegistrationsGetSeatsThenWaitlist() throws Exception {
        long eventId = newEvent(2);
        String a = tokenFor(newUser());
        String b = tokenFor(newUser());
        String c = tokenFor(newUser());

        register(a, eventId).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        register(b, eventId).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        register(c, eventId).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("WAITLISTED"));
    }

    @Test
    void registeringTwiceReturns409() throws Exception {
        long eventId = newEvent(5);
        String token = tokenFor(newUser());

        register(token, eventId).andExpect(status().isCreated());
        register(token, eventId).andExpect(status().isConflict());
    }

    @Test
    void cancellingConfirmedSeatPromotesFirstWaitlisted() throws Exception {
        long eventId = newEvent(1);
        User first = newUser();
        User second = newUser();
        User third = newUser();

        register(tokenFor(first), eventId).andExpect(jsonPath("$.status").value("CONFIRMED"));
        register(tokenFor(second), eventId).andExpect(jsonPath("$.status").value("WAITLISTED"));
        register(tokenFor(third), eventId).andExpect(jsonPath("$.status").value("WAITLISTED"));

        mvc.perform(delete("/api/events/" + eventId + "/registrations/me").header("Authorization", bearer(first)))
                .andExpect(status().isNoContent());

        // "second" waited longer than "third", so "second" gets the seat.
        assertThat(statusOf(eventId, second)).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(statusOf(eventId, third)).isEqualTo(RegistrationStatus.WAITLISTED);
    }

    @Test
    void cancellingWaitlistSpotPromotesNobody() throws Exception {
        long eventId = newEvent(1);
        User first = newUser();
        User second = newUser();

        register(tokenFor(first), eventId);
        register(tokenFor(second), eventId);

        mvc.perform(delete("/api/events/" + eventId + "/registrations/me").header("Authorization", bearer(second)))
                .andExpect(status().isNoContent());

        assertThat(statusOf(eventId, first)).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED)).isEqualTo(1);
    }

    @Test
    void canRegisterAgainAfterCancelling() throws Exception {
        long eventId = newEvent(3);
        String token = tokenFor(newUser());

        register(token, eventId).andExpect(status().isCreated());
        mvc.perform(delete("/api/events/" + eventId + "/registrations/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        register(token, eventId).andExpect(status().isCreated());
    }

    @Test
    void cancellingWithoutRegistrationReturns404() throws Exception {
        long eventId = newEvent(3);

        mvc.perform(delete("/api/events/" + eventId + "/registrations/me").header("Authorization", bearer(newUser())))
                .andExpect(status().isNotFound());
    }

    // ---------- concurrency ----------

    @Test
    void parallelRegistrationsNeverExceedCapacity() throws Exception {
        int capacity = 5;
        int people = 20;
        long eventId = newEvent(capacity);

        List<AuthUser> callers = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            User user = newUser();
            callers.add(new AuthUser(user.getId(), user.getEmail(), Role.USER));
        }

        ExecutorService pool = Executors.newFixedThreadPool(people);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (AuthUser caller : callers) {
            futures.add(pool.submit(() -> {
                startSignal.await();                       // all threads start at the same moment
                return registrationService.register(caller, eventId);
            }));
        }
        startSignal.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);              // rethrows if any registration failed
        }
        pool.shutdown();

        assertThat(registrations.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED)).isEqualTo(capacity);
        assertThat(registrations.countByEventIdAndStatus(eventId, RegistrationStatus.WAITLISTED)).isEqualTo(people - capacity);
    }

    // ---------- events, security, pagination ----------

    @Test
    void adminCanCreateEventButUserCannot() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "Spring Boot Meetup",
                "description", "Talks and pizza",
                "startsAt", Instant.now().plus(10, ChronoUnit.DAYS).toString(),
                "capacity", 30);

        mvc.perform(post("/api/events").header("Authorization", bearer(newUser(Role.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.confirmed").value(0));

        mvc.perform(post("/api/events").header("Authorization", bearer(newUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void eventListIsPagedAndShowsCounts() throws Exception {
        long id1 = newEvent(1);
        newEvent(1);
        newEvent(1);
        String token = tokenFor(newUser());
        register(token, id1);
        register(tokenFor(newUser()), id1);

        mvc.perform(get("/api/events?page=0&size=2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.size").value(2));

        mvc.perform(get("/api/events/" + id1).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.confirmed").value(1))
                .andExpect(jsonPath("$.waitlisted").value(1));
    }

    // ---------- helpers ----------

    private long newEvent(int capacity) {
        Event event = events.save(new Event("Event " + UUID.randomUUID(), null,
                Instant.now().plus(7, ChronoUnit.DAYS), capacity));
        return event.getId();
    }

    private User newUser() {
        return newUser(Role.USER);
    }

    private User newUser(Role role) {
        // The password hash is irrelevant here: tests authenticate with a JWT, not with a login.
        return users.save(new User(UUID.randomUUID() + "@example.com", "not-a-real-hash", role));
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    private String bearer(User user) {
        return "Bearer " + tokenFor(user);
    }

    private ResultActions register(String token, long eventId) throws Exception {
        return mvc.perform(post("/api/events/" + eventId + "/registrations")
                .header("Authorization", "Bearer " + token));
    }

    private RegistrationStatus statusOf(long eventId, User user) {
        return registrations
                .findByEventIdAndUserIdAndStatusIn(eventId, user.getId(), List.of(RegistrationStatus.values()))
                .orElseThrow()
                .getStatus();
    }
}
