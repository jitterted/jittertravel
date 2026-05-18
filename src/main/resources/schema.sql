CREATE TABLE IF NOT EXISTS command_log (
    command_id UUID PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    type TEXT NOT NULL,
    payload JSONB NOT NULL,
    event_ids UUID[]
);

CREATE TABLE IF NOT EXISTS event_log (
    sequence BIGINT PRIMARY KEY,
    event_id UUID NOT NULL,
    command_id UUID REFERENCES command_log(command_id),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    type TEXT NOT NULL,
    payload JSONB NOT NULL
);
