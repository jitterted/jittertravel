package dev.ted.jittertravel.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class PostgresPersister {
    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public PostgresPersister(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    public void saveCommand(UUID commandId, Object dto) {
        try {
            String payload = jsonMapper.writeValueAsString(dto);
            jdbcClient.sql("INSERT INTO command_log (command_id, timestamp, type, payload) VALUES (?, ?, ?, ?::jsonb)")
                    .params(commandId, Timestamp.from(Instant.now()), dto.getClass().getName(), payload)
                    .update();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save command to WAL", e);
        }
    }

    public void appendEvents(List<StoredEvent> events, UUID commandId) {
        for (StoredEvent event : events) {
            try {
                String payload = jsonMapper.writeValueAsString(event.payload());
                jdbcClient.sql("INSERT INTO event_log (sequence, event_id, command_id, timestamp, type, payload) VALUES (?, ?, ?, ?, ?, ?::jsonb)")
                        .params(event.sequence(), event.eventId(), commandId, Timestamp.from(event.timestamp()), event.type().getName(), payload)
                        .update();
            } catch (Exception e) {
                throw new RuntimeException("Failed to persist event", e);
            }
        }
    }

    public void linkCommandToEvents(UUID commandId, List<UUID> eventIds) {
        jdbcClient.sql("UPDATE command_log SET event_ids = ? WHERE command_id = ?")
                .params(eventIds.toArray(new UUID[0]), commandId)
                .update();
    }

    public long getMaxSequence() {
        return jdbcClient.sql("SELECT COALESCE(MAX(sequence), 0) FROM event_log")
                .query(Long.class)
                .single();
    }

    public List<StoredEvent> loadAllEvents() {
        return jdbcClient.sql("SELECT sequence, event_id, command_id, timestamp, type, payload FROM event_log ORDER BY sequence")
                .query((rs, rowNum) -> {
                    try {
                        long sequence = rs.getLong("sequence");
                        UUID eventId = (UUID) rs.getObject("event_id");
                        UUID commandId = (UUID) rs.getObject("command_id");
                        Instant timestamp = rs.getTimestamp("timestamp").toInstant();
                        String typeName = rs.getString("type");
                        String payloadJson = rs.getString("payload");
                        
                        Class<?> type = Class.forName(typeName);
                        Object payload = jsonMapper.readValue(payloadJson, type);
                        
                        return new StoredEvent(sequence, (Class)type, eventId, timestamp, (dev.ted.jittertravel.domain.Event)payload, commandId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).list();
    }
}
