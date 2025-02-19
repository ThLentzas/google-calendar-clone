CREATE TABLE IF NOT EXISTS time_events (
    id uuid DEFAULT uuid_generate_v4(),
    organizer_id BIGINT NOT NULL,
    start_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    start_time_zone_id TEXT NOT NULL,
    end_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_time_zone_id TEXT NOT NULL,
    -- It can not be null. For non-recurring events NEVER should be provided
    recurrence_frequency recurrence_frequency NOT NULL,
    recurrence_step INTEGER NULL,
    weekly_recurrence_days VARCHAR(56) NULL,
    monthly_recurrence_type monthly_recurrence_type NULL,
    recurrence_duration recurrence_duration NULL,
    recurrence_end_date DATE NULL,
    number_of_occurrences INTEGER NULL,
    CONSTRAINT pk_time_events PRIMARY KEY (id),
    CONSTRAINT fk_time_events_users_id FOREIGN KEY (organizer_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS time_event_slots(
    id uuid DEFAULT uuid_generate_v4(),
    event_id uuid NOT NULL,
    title VARCHAR(50) NULL,
    location VARCHAR(50) NULL,
    description TEXT NULL,
    start_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    start_time_zone_id TEXT NOT NULL,
    end_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_time_zone_id TEXT NOT NULL,
    CONSTRAINT pk_time_event_slots PRIMARY KEY (id),
    CONSTRAINT fk_time_event_slots_time_events_id FOREIGN KEY (event_id) REFERENCES time_events ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS time_event_slot_guest_emails (
    event_slot_id uuid,
    email TEXT NOT NULL,
    CONSTRAINT pk_time_event_slot_guest_emails PRIMARY KEY (event_slot_id, email),
    CONSTRAINT fk_time_event_slot_guest_emails_time_event_slots_id FOREIGN key (event_slot_id) REFERENCES time_event_slots(id) ON DELETE CASCADE
);