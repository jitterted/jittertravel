package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Event;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
            jdbcClient.sql("""
                            INSERT INTO command_log (command_id, timestamp, type, payload)
                            VALUES (:commandId, :timestamp, :type, CAST(:payload AS jsonb))
                            """)
                    .param("commandId", commandId)
                    .param("timestamp", Instant.now().atOffset(ZoneOffset.UTC))
                    .param("type", dto.getClass().getName())
                    .param("payload", payload)
                    .update();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save command to WAL", e);
        }
    }

    public void appendEvents(List<StoredEvent> events, UUID commandId) {
        for (StoredEvent event : events) {
            try {
                StoredEventRow row = StoredEventRow.fromStoredEvent(event, jsonMapper);

                jdbcClient.sql("""
                                INSERT INTO event_log (sequence, event_id, command_id, timestamp, type, payload)
                                VALUES (:sequence, :eventId, :commandId, :timestamp, :type, CAST(:payloadJson AS jsonb))
                                """)
                        .param("sequence", row.sequence())
                        .param("eventId", row.eventId())
                        .param("commandId", row.commandId())
                        .param("timestamp", row.timestamp())
                        .param("type", row.type())
                        .param("payloadJson", row.payloadJson())
                        .update();
            } catch (Exception e) {
                throw new RuntimeException("Failed to persist event", e);
            }
        }
    }

    public void linkCommandToEvents(UUID commandId, List<UUID> eventIds) {
        jdbcClient.sql("""
                        UPDATE command_log
                        SET event_ids = :eventIds
                        WHERE command_id = :commandId
                        """)
                .param("eventIds", eventIds.toArray(new UUID[0]))
                .param("commandId", commandId)
                .update();
    }

    public long getMaxSequence() {
        // Returns max event sequence number, but if there are no events yet, returns 0.
        return jdbcClient.sql("SELECT COALESCE(MAX(sequence), 0) FROM event_log")
                .query(Long.class)
                .single();
    }

    public List<StoredEvent> loadAllEvents() {
        return jdbcClient.sql("""
                        SELECT sequence,
                               event_id AS eventId,
                               command_id AS commandId,
                               timestamp,
                               type,
                               payload::text AS payloadJson
                        FROM event_log
                        ORDER BY sequence
                        """)
                .query(StoredEventRow.class)
                .list() // materialize the JDBC stream (which is not a Collections Stream!) into a list
                .stream()
                .map(row -> row.toStoredEvent(jsonMapper))
                .toList();
    }

    private record StoredEventRow(
            long sequence,
            UUID eventId,
            UUID commandId,
            OffsetDateTime timestamp,
            String type,
            String payloadJson
    ) {
        static StoredEventRow fromStoredEvent(StoredEvent event, JsonMapper jsonMapper) {
            try {
                return new StoredEventRow(
                        event.sequence(),
                        event.eventId(),
                        event.commandId(),
                        event.timestamp().atOffset(ZoneOffset.UTC),
                        event.type().getName(),
                        jsonMapper.writeValueAsString(event.payload())
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert StoredEvent to a database StoredEventRow object", e);
            }
        }

        StoredEvent toStoredEvent(JsonMapper jsonMapper) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(type);

                Event payload = jsonMapper.readValue(payloadJson, eventClass);

                return new StoredEvent(
                        sequence,
                        eventClass,
                        eventId,
                        timestamp.toInstant(),
                        payload,
                        commandId
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert StoredEventRow database object to a StoredEvent", e);
            }
        }
    }
}