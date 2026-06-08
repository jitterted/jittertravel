package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Event;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@SuppressWarnings("DataFlowIssue")
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
                            INSERT INTO command_log (command_id, timestamp, type, payload, status)
                            VALUES (:commandId, :timestamp, :type, CAST(:payload AS jsonb), 'PENDING')
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

    /**
     * Records a command that did not produce events: {@code FAILED_DOMAIN} when the
     * domain rejected it (execute threw), {@code FAILED_PERSIST} when the event write
     * failed. The command row already exists (written PENDING by {@link #saveCommand}).
     */
    public void markCommandFailed(UUID commandId, String status, String errorMessage) {
        jdbcClient.sql("""
                        UPDATE command_log
                        SET status = :status, error = :error
                        WHERE command_id = :commandId
                        """)
                .param("status", status)
                .param("error", clip(errorMessage))
                .param("commandId", commandId)
                .update();
    }

    private static String clip(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

    /**
     * Marks a still-PENDING command as ABANDONED. Guarded on the current status so a
     * command that has since completed (SUCCEEDED/FAILED) is never clobbered.
     */
    public void abandonCommand(UUID commandId) {
        jdbcClient.sql("""
                        UPDATE command_log
                        SET status = 'ABANDONED'
                        WHERE command_id = :commandId AND status = 'PENDING'
                        """)
                .param("commandId", commandId)
                .update();
    }

    @Transactional
    public void appendEvents(List<StoredEvent> events, UUID commandId) {
        StoredEvent currentEvent = null;
        try {
            for (StoredEvent event : events) {
                currentEvent = event;
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
            }
            currentEvent = null;
            linkCommandToEvents(commandId, events.stream().map(StoredEvent::eventId).toList());
        } catch (Exception e) {
            String context = currentEvent != null
                    ? "event type=%s, eventId=%s".formatted(currentEvent.type().getSimpleName(), currentEvent.eventId())
                    : "linking events to command";
            throw new RuntimeException(
                    "Failed to persist event and link to command; commandId=%s, failing step: %s"
                            .formatted(commandId, context), e);
        }
    }

    private void linkCommandToEvents(UUID commandId, List<UUID> eventIds) {
        jdbcClient.sql("""
                        UPDATE command_log
                        SET event_ids = :eventIds, status = 'SUCCEEDED'
                        WHERE command_id = :commandId
                        """)
                .param("eventIds", eventIds.toArray(new UUID[0]))
                .param("commandId", commandId)
                .update();
    }

    public int countEvents() {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM event_log")
                .query(Long.class)
                .single();
        return count.intValue();
    }

    public List<EventLogRow> loadEventPage(int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT sequence,
                               event_id     AS eventId,
                               command_id   AS commandId,
                               timestamp,
                               type,
                               payload::text AS payloadJson
                        FROM event_log
                        ORDER BY sequence DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, _) -> new EventLogRow(
                        rs.getLong("sequence"),
                        (UUID) rs.getObject("eventId"),
                        (UUID) rs.getObject("commandId"),
                        rs.getObject("timestamp", OffsetDateTime.class),
                        simpleTypeName(rs.getString("type")),
                        prettyJson(rs.getString("payloadJson"))
                ))
                .list();
    }

    public record EventLogRow(
            long sequence,
            UUID eventId,
            UUID commandId,
            OffsetDateTime timestamp,
            String type,
            String payloadJson
    ) {}

    private static String simpleTypeName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    public int countCommands() {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM command_log")
                .query(Long.class)
                .single();
        return count.intValue();
    }

    public int countPendingCommands() {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM command_log WHERE status = 'PENDING'")
                .query(Long.class)
                .single();
        return count.intValue();
    }

    /**
     * Loads all PENDING commands (oldest first) with their payloads pretty-printed,
     * for the admin pending-commands review page.
     */
    public List<TimelineCommand> findPendingCommands() {
        return jdbcClient.sql("""
                        SELECT command_id   AS commandId,
                               timestamp,
                               type,
                               payload::text AS payloadJson,
                               status
                        FROM command_log
                        WHERE status = 'PENDING'
                        ORDER BY timestamp ASC, command_id ASC
                        """)
                .query(TimelineCommand.class)
                .list()
                .stream()
                .map(c -> new TimelineCommand(c.commandId(), c.timestamp(), c.type(), prettyJson(c.payloadJson()), c.status()))
                .toList();
    }

    /**
     * Loads a page of commands (oldest first) along with their resulting events.
     * Within the returned page, an entry is marked {@code outOfOrder} if its events'
     * sequence numbers start before the running max sequence of previously-listed
     * commands (i.e. its events are interleaved with an earlier command's events).
     */
    public List<TimelineEntry> loadTimelinePage(int offset, int limit) {
        List<TimelineCommand> commands = jdbcClient.sql("""
                        SELECT command_id   AS commandId,
                               timestamp,
                               type,
                               payload::text AS payloadJson,
                               status
                        FROM command_log
                        ORDER BY timestamp ASC, command_id ASC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("limit", limit)
                .param("offset", offset)
                .query(TimelineCommand.class)
                .list()
                .stream()
                .map(c -> new TimelineCommand(c.commandId(), c.timestamp(), c.type(), prettyJson(c.payloadJson()), c.status()))
                .toList();

        if (commands.isEmpty()) {
            return List.of();
        }

        UUID[] commandIds = commands.stream().map(TimelineCommand::commandId).toArray(UUID[]::new);

        Map<UUID, List<TimelineEvent>> eventsByCommand = new HashMap<>();
        jdbcClient.sql("""
                        SELECT command_id   AS commandId,
                               sequence,
                               event_id     AS eventId,
                               timestamp,
                               type,
                               payload::text AS payloadJson
                        FROM event_log
                        WHERE command_id = ANY(:commandIds)
                        ORDER BY sequence
                        """)
                .param("commandIds", commandIds)
                .query((rs, _) -> {
                    UUID cmdId = (UUID) rs.getObject("commandId");
                    TimelineEvent event = new TimelineEvent(
                            rs.getLong("sequence"),
                            (UUID) rs.getObject("eventId"),
                            rs.getObject("timestamp", OffsetDateTime.class),
                            rs.getString("type"),
                            prettyJson(rs.getString("payloadJson"))
                    );
                    eventsByCommand.computeIfAbsent(cmdId, _ -> new ArrayList<>()).add(event);
                    return event;
                })
                .list();

        List<TimelineEntry> entries = new ArrayList<>(commands.size());
        long runningMaxSeq = Long.MIN_VALUE;
        for (TimelineCommand command : commands) {
            List<TimelineEvent> events = eventsByCommand.getOrDefault(command.commandId(), List.of());
            boolean failed = command.failed();
            boolean outOfOrder = false;
            if (!events.isEmpty()) {
                long minSeq = events.getFirst().sequence();
                long maxSeq = events.getLast().sequence();
                if (minSeq < runningMaxSeq) {
                    outOfOrder = true;
                }
                if (maxSeq > runningMaxSeq) {
                    runningMaxSeq = maxSeq;
                }
            }
            entries.add(new TimelineEntry(command, events, failed, outOfOrder));
        }
        return entries;
    }

    private String prettyJson(String rawJson) {
        if (rawJson == null) {
            return "";
        }
        try {
            return jsonMapper.readTree(rawJson).toPrettyString();
        } catch (Exception e) {
            return rawJson;
        }
    }

    public List<TableStat> tableStats() {
        List<TableStat> stats = new ArrayList<>();
        for (String table : List.of("command_log", "event_log")) {
            long count = jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                    .query(Long.class)
                    .single();
            stats.add(new TableStat(table, count));
        }
        return stats;
    }

    public record TableStat(String tableName, long rowCount) {}

    public void truncateAllTables() {
        jdbcClient.sql("TRUNCATE TABLE event_log, command_log").update();
    }

    public List<CommandPayloadRow> findAllCommandsForExport() {
        return jdbcClient.sql("""
                        SELECT type, payload::text AS payloadJson
                        FROM command_log
                        WHERE status = 'SUCCEEDED'
                        ORDER BY timestamp ASC, command_id ASC
                        """)
                .query(CommandPayloadRow.class)
                .list();
    }

    public record CommandPayloadRow(String type, String payloadJson) {}

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