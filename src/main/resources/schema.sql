CREATE TABLE IF NOT EXISTS room (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    floor INTEGER NOT NULL,
    equipment TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS booking (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    room_id INTEGER NOT NULL REFERENCES room (id),
    start_at TEXT NOT NULL,
    end_at TEXT NOT NULL,
    booking_date TEXT NOT NULL,
    organiser TEXT NOT NULL,
    subject TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at TEXT NOT NULL,
    cancelled_at TEXT,
    CHECK (end_at > start_at)
);

CREATE INDEX IF NOT EXISTS idx_booking_room_date_status ON booking (room_id, booking_date, status);

CREATE INDEX IF NOT EXISTS idx_booking_organiser_date_status ON booking (organiser, booking_date, status);

CREATE TABLE IF NOT EXISTS app_user (
    email TEXT PRIMARY KEY,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL
);
