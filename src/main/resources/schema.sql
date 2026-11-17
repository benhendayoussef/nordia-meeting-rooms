CREATE TABLE IF NOT EXISTS room (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    floor INTEGER NOT NULL,
    equipment TEXT NOT NULL
);
