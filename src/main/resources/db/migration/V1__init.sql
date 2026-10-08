CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(2000),
    starts_at   TIMESTAMPTZ   NOT NULL,
    capacity    INT           NOT NULL CHECK (capacity > 0),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE registrations (
    id         BIGSERIAL PRIMARY KEY,
    event_id   BIGINT      NOT NULL REFERENCES events (id),
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT registrations_status_check CHECK (status IN ('CONFIRMED', 'WAITLISTED', 'CANCELLED'))
);

-- A user can have at most one ACTIVE registration per event. Cancelled ones do not count,
-- so a user may register again after cancelling. This is a safety net: the service checks first.
CREATE UNIQUE INDEX uq_registrations_active
    ON registrations (event_id, user_id)
    WHERE status <> 'CANCELLED';

-- Serves "count confirmed" and "oldest waitlisted" lookups for one event.
CREATE INDEX idx_registrations_event_status ON registrations (event_id, status, id);
CREATE INDEX idx_registrations_user_id ON registrations (user_id);
CREATE INDEX idx_events_starts_at ON events (starts_at);
