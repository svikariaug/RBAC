CREATE TABLE passengers (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    email          VARCHAR(255) NOT NULL UNIQUE,
    phone          VARCHAR(64)  NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE drivers (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    license_number  VARCHAR(64)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'AVAILABLE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT drivers_status_chk CHECK (status IN ('AVAILABLE', 'BUSY', 'OFFLINE'))
);

CREATE TABLE trips (
    id             BIGSERIAL PRIMARY KEY,
    passenger_id   BIGINT NOT NULL REFERENCES passengers (id),
    driver_id      BIGINT REFERENCES drivers (id),
    status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    origin         VARCHAR(512) NOT NULL,
    destination    VARCHAR(512) NOT NULL,
    origin_lat     DOUBLE PRECISION NOT NULL DEFAULT 0,
    origin_lon     DOUBLE PRECISION NOT NULL DEFAULT 0,
    dest_lat       DOUBLE PRECISION NOT NULL DEFAULT 0,
    dest_lon       DOUBLE PRECISION NOT NULL DEFAULT 0,
    distance_km    DOUBLE PRECISION NOT NULL DEFAULT 0,
    price          NUMERIC(12, 2) NOT NULL DEFAULT 0,
    rating         SMALLINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT trips_status_chk CHECK (status IN (
        'PENDING', 'ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT trips_rating_chk CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5))
);

CREATE TABLE notification_tasks (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT NOT NULL REFERENCES trips (id),
    recipient_type  VARCHAR(32) NOT NULL,
    recipient_id    BIGINT NOT NULL,
    message         TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT notification_tasks_recipient_chk CHECK (recipient_type IN ('PASSENGER', 'DRIVER')),
    CONSTRAINT notification_tasks_status_chk CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_trips_status ON trips (status);
CREATE INDEX idx_trips_passenger ON trips (passenger_id);
CREATE INDEX idx_notification_tasks_status ON notification_tasks (status);
CREATE INDEX idx_notification_tasks_trip ON notification_tasks (trip_id);
