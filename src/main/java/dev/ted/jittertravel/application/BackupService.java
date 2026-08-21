package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.infrastructure.EventPayloadUpcaster;
import dev.ted.jittertravel.infrastructure.EventTypes;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupCommandRow;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupEventRow;
import dev.ted.jittertravel.infrastructure.PostgresPersister.RestoreCounts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Event-oriented backup and restore (see {@code docs/archived/EventOrientedBackupRestorePlan.md}). Writes a
 * single {@code version: 3} JSON document holding the whole {@code command_log} and
 * {@code event_log}, and restores those rows <em>verbatim</em> — same event ids, sequences and
 * timestamps. This supersedes the command-replay import: commands are opaque history here, never
 * re-executed, so nothing has to fake a decision context.
 *
 * <p><strong>Format versions.</strong> {@code version 3} adds the per-event {@code schemaVersion}
 * stamp (see {@code docs/archived/LegacyEventEagerMigrationPlan.md}); {@code version 2} is the pre-stamp
 * event-oriented format. Both restore — a v2 event simply carries no stamp (null), exactly like a
 * pre-migration {@code event_log} row — so an older backup is never orphaned by upgrading. Only the
 * long-dead {@code version 1} command-only format is unrestorable.
 *
 * <p><strong>Restore is validate-then-apply.</strong> Pass one reads the whole file and, writing
 * nothing, deserializes every event payload and checks referential integrity; if anything is wrong
 * it reports <em>every</em> problem at once and writes nothing. Pass two inserts commands then
 * events (that order satisfies {@code event_log}'s FK) in one transaction, skipping ids already
 * present so a partly-applied restore resumes on re-run.
 *
 * <p><strong>Read models are rebuilt by restarting.</strong> Restore writes durable rows only; the
 * projectors are rebuilt by the boot replay on the next start. After a successful restore, tell the
 * operator to restart.
 *
 * <p>Restore does not go through {@link CommandExecutor} — it is a bulk load of already-durable
 * events, not new events appended from a command — so the two guarantees the executor normally
 * enforces are re-honoured here: read-only mode is checked up front (via
 * {@link CommandExecutor#isReadOnly()}, without holding an {@code EventStore}), and
 * persist-before-notify holds because projectors only see the restored rows on the next boot.
 */
public class BackupService {

    /** The format this instance writes. */
    static final int VERSION = 3;

    /** Formats this instance can restore: the current stamped format and the pre-stamp one. */
    static final Set<Integer> RESTORABLE_VERSIONS = Set.of(2, 3);

    /** Filename stamp: the backup instant in UTC, colon-free so it is safe on every filesystem. */
    private static final DateTimeFormatter FILENAME_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss'Z'");

    private final PostgresPersister persister;
    private final CommandExecutor commandExecutor;
    private final EventPayloadUpcaster upcaster;
    private final JsonMapper jsonMapper;

    public BackupService(PostgresPersister persister, CommandExecutor commandExecutor,
                         EventPayloadUpcaster upcaster, JsonMapper jsonMapper) {
        this.persister = persister;
        this.commandExecutor = commandExecutor;
        this.upcaster = upcaster;
        this.jsonMapper = jsonMapper;
    }

    /**
     * The backup as a downloadable pair: the JSON document (with its {@code metadata} block) and the
     * filename to offer it under. {@code createdAt} and {@code source} are captured at the boundary
     * (see {@link BackupSource}); both are recorded verbatim in the metadata and echoed in the name.
     */
    public Backup createBackup(OffsetDateTime createdAt, String source) {
        return new Backup(filenameFor(createdAt, source), backupJson(createdAt, source));
    }

    public String backupJson(OffsetDateTime createdAt, String source) {
        try {
            BackupMetadata metadata = new BackupMetadata(toUtc(createdAt), source);
            List<BackupCommand> commands = persister.findAllCommandsForBackup().stream()
                    .map(this::toBackupCommand)
                    .toList();
            List<BackupEvent> events = persister.findAllEventsForBackup().stream()
                    .map(this::toBackupEvent)
                    .toList();
            BackupFile file = new BackupFile(VERSION, metadata, commands, events);
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(file);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write backup", e);
        }
    }

    private String filenameFor(OffsetDateTime createdAt, String source) {
        return "jittertravel-backup-" + source + "-" + toUtc(createdAt).format(FILENAME_TIMESTAMP) + ".json";
    }

    private OffsetDateTime toUtc(OffsetDateTime createdAt) {
        return createdAt.withOffsetSameInstant(ZoneOffset.UTC);
    }

    public RestoreResult restoreJson(String json) {
        if (commandExecutor.isReadOnly()) {
            return RestoreResult.refusedReadOnly();
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(json);
        } catch (Exception e) {
            return RestoreResult.failed(List.of(parseError(e)));
        }
        Validation validation = validate(root);
        if (validation.hasErrors()) {
            return RestoreResult.failed(validation.errors());  // nothing written: fix the file and re-run
        }
        return apply(validation.commands(), validation.events());
    }

    /**
     * Dry run: reports what {@link #restoreJson} would say about this file, writing nothing — not
     * even for a file that would restore cleanly. The errors that matter are data problems in the
     * file itself (an event payload that no longer binds to its class), so this runs the whole of
     * pass one against the file in hand.
     */
    public ValidationReport validateJson(String json) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(json);
        } catch (Exception e) {
            return new ValidationReport(0, 0, List.of(parseError(e)));
        }
        Validation validation = validate(root);
        return new ValidationReport(validation.commands().size(), validation.events().size(), validation.errors());
    }

    /**
     * Pass one: parse the document and, writing nothing, check it whole — the version, that every
     * event payload still deserializes, and referential integrity (every event's command is
     * present; sequences, event ids and command ids are unique). Collects an error per problem
     * rather than stopping at the first, so one run reports the whole list.
     */
    private Validation validate(JsonNode root) {
        List<String> errors = new ArrayList<>();

        JsonNode versionNode = root.get("version");
        if (versionNode == null || !versionNode.isNumber() || !RESTORABLE_VERSIONS.contains(versionNode.asInt())) {
            errors.add("Unsupported or missing backup version: expected one of " + RESTORABLE_VERSIONS
                    + " (command-only backups are no longer restorable)");
            return new Validation(List.of(), List.of(), errors);  // can't trust the rest of the file
        }

        BackupFile file;
        try {
            file = jsonMapper.treeToValue(root, BackupFile.class);
        } catch (Exception e) {
            errors.add("Backup file is not readable as version " + versionNode.asInt() + ": " + e.getMessage());
            return new Validation(List.of(), List.of(), errors);
        }

        List<BackupCommand> commands = file.commands() == null ? List.of() : file.commands();
        List<BackupEvent> events = file.events() == null ? List.of() : file.events();

        Set<UUID> commandIds = new HashSet<>();
        for (BackupCommand command : commands) {
            if (command.commandId() == null) {
                errors.add("A command entry is missing its commandId");
            } else if (!commandIds.add(command.commandId())) {
                errors.add("Command id %s appears more than once in commands".formatted(command.commandId()));
            }
        }

        Set<Long> sequences = new HashSet<>();
        Set<UUID> eventIds = new HashSet<>();
        for (BackupEvent event : events) {
            if (!sequences.add(event.sequence())) {
                errors.add("Event sequence %d appears more than once".formatted(event.sequence()));
            }
            if (event.eventId() != null && !eventIds.add(event.eventId())) {
                errors.add("Event id %s appears more than once".formatted(event.eventId()));
            }
            if (event.commandId() == null || !commandIds.contains(event.commandId())) {
                errors.add("Event %d references command %s, which is not in the backup"
                        .formatted(event.sequence(), event.commandId()));
            }
            validateEventPayloadBinds(event, errors);
        }

        return new Validation(commands, events, errors);
    }

    /**
     * Confirms the event's payload still deserializes to its current class — the check that catches
     * a corrupt or schema-incompatible backup before pass two writes anything. Upcasts a
     * <em>copy</em>: the upcaster mutates the node in place, and pass two must write the original
     * (verbatim) payload, not the upcasted one.
     */
    private void validateEventPayloadBinds(BackupEvent event, List<String> errors) {
        if (event.payload() == null) {
            errors.add("Event %d (%s) has no payload".formatted(event.sequence(), event.type()));
            return;
        }
        try {
            Class<? extends Event> eventClass = EventTypes.classFor(event.type());
            JsonNode upcasted = upcaster.upcast(event.type(), event.payload().deepCopy(), event.schemaVersion());
            jsonMapper.treeToValue(upcasted, eventClass);
        } catch (Exception e) {
            errors.add("Event %d (%s) payload cannot be restored: %s"
                    .formatted(event.sequence(), event.type(), e.getMessage()));
        }
    }

    /** Pass two: verbatim insert, commands before events, in one transaction (in the persister). */
    private RestoreResult apply(List<BackupCommand> commands, List<BackupEvent> events) {
        List<BackupCommandRow> commandRows = commands.stream().map(this::toCommandRow).toList();
        List<BackupEventRow> eventRows = events.stream().map(this::toEventRow).toList();
        RestoreCounts counts = persister.restoreCommandsAndEvents(commandRows, eventRows);
        return new RestoreResult(
                counts.commandsInserted(), commands.size() - counts.commandsInserted(),
                counts.eventsInserted(), events.size() - counts.eventsInserted(),
                List.of());
    }

    private String parseError(Exception e) {
        return "Failed to parse JSON: " + e.getMessage();
    }

    private BackupCommand toBackupCommand(BackupCommandRow row) {
        return new BackupCommand(row.commandId(), row.timestamp(), row.type(),
                jsonMapper.readTree(row.payloadJson()), row.eventIds(), row.status(), row.error());
    }

    private BackupEvent toBackupEvent(BackupEventRow row) {
        return new BackupEvent(row.sequence(), row.eventId(), row.commandId(), row.timestamp(),
                row.type(), jsonMapper.readTree(row.payloadJson()), row.schemaVersion());
    }

    private BackupCommandRow toCommandRow(BackupCommand command) {
        return new BackupCommandRow(command.commandId(), command.timestamp(), command.type(),
                command.payload().toString(), command.eventIds(), command.status(), command.error());
    }

    private BackupEventRow toEventRow(BackupEvent event) {
        return new BackupEventRow(event.sequence(), event.eventId(), event.commandId(),
                event.timestamp(), event.type(), event.payload().toString(), event.schemaVersion());
    }

    /** A ready-to-download backup: the JSON document and the filename to offer it under. */
    public record Backup(String filename, String json) {}

    /** The on-disk backup: {@code commands} precedes {@code events} to mirror restore order. */
    record BackupFile(int version, BackupMetadata metadata, List<BackupCommand> commands,
                      List<BackupEvent> events) {}

    /**
     * Informational header describing this backup: when it was taken (UTC) and from which instance
     * ({@code production}/{@code local}). Recorded on write and ignored on restore — it never touches
     * {@code command_log} or {@code event_log}.
     */
    record BackupMetadata(OffsetDateTime createdAt, String source) {}

    /** A command row on the wire — opaque history; {@code type} and {@code payload} are never run. */
    record BackupCommand(UUID commandId, OffsetDateTime timestamp, String type, JsonNode payload,
                         List<UUID> eventIds, String status, String error) {}

    /**
     * An event row on the wire, restored verbatim; {@code type} is the stable logical name.
     * {@code schemaVersion} is the per-event stamp — present in version-3 files, null in a version-2
     * file (which predates the stamp), restored as-is either way.
     */
    record BackupEvent(long sequence, UUID eventId, UUID commandId, OffsetDateTime timestamp,
                       String type, JsonNode payload, Integer schemaVersion) {}

    /** Outcome of a dry run: how many commands/events would restore, and every problem found. */
    public record ValidationReport(int validCommandCount, int validEventCount, List<String> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    /** Outcome of a restore: rows inserted, rows skipped as already present, and any errors. */
    public record RestoreResult(int restoredCommands, int skippedCommands,
                                int restoredEvents, int skippedEvents, List<String> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }

        static RestoreResult failed(List<String> errors) {
            return new RestoreResult(0, 0, 0, 0, errors);
        }

        static RestoreResult refusedReadOnly() {
            return failed(List.of(
                    "Restore refused: the application is in read-only mode, so nothing was written."));
        }
    }

    /** Outcome of pass one: what is ready to write, and why the rest is not. */
    private record Validation(List<BackupCommand> commands, List<BackupEvent> events, List<String> errors) {
        boolean hasErrors() { return !errors.isEmpty(); }
    }
}
