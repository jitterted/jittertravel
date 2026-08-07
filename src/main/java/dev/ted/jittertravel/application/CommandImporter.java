package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.web.ImportableCommand;
import dev.ted.jittertravel.web.ImportableCommandTypes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Exports the command log to JSON and re-applies such an export.
 *
 * <p><strong>Import is validate-then-apply.</strong> Every entry is deserialized and its events
 * recomputed <em>before</em> anything is written; if any entry fails, nothing is written at all.
 * That matters because import failures are typically data problems affecting a handful of entries
 * (an address whose zone doesn't resolve, say), and a half-applied import leaves a database that
 * has to be wiped. Validating first also reports <em>every</em> bad entry in one pass, so the file
 * can be fixed in one editing round rather than one entry at a time.
 *
 * <p><strong>Import is resumable.</strong> Commands whose id is already in {@code command_log} are
 * skipped, so re-running the same file after fixing it — or after an infrastructure failure
 * mid-apply — imports only what is missing instead of colliding on the primary key.
 */
public class CommandImporter {

    private final PostgresPersister persister;
    private final CommandExecutor commandExecutor;
    private final JsonMapper jsonMapper;

    public CommandImporter(PostgresPersister persister, CommandExecutor commandExecutor, JsonMapper jsonMapper) {
        this.persister = persister;
        this.commandExecutor = commandExecutor;
        this.jsonMapper = jsonMapper;
    }

    public String exportJson() {
        try {
            List<ExportEntry> entries = persister.findAllCommandsForExport()
                    .stream()
                    .map(this::toExportEntry)
                    .toList();
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export commands", e);
        }
    }

    public ImportResult importJson(String json) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(json);
        } catch (Exception e) {
            return new ImportResult(0, 0, List.of(parseError(e)));
        }

        Validation validation = validate(root);
        if (validation.hasErrors()) {
            return new ImportResult(0, 0, validation.errors());  // nothing written: fix the file and re-run
        }
        return apply(validation.commands());
    }

    /**
     * Dry run: reports what {@link #importJson} would say about this file, writing nothing at all —
     * not even for a file that would import cleanly.
     *
     * <p>Exists because the errors that matter are data problems in the file itself (a location
     * whose zone doesn't resolve, a country field holding a city name), and the only other tool for
     * finding them, {@code /admin/zone-audit}, reads {@code event_log} — data that is *already
     * imported*. For a wipe-then-import workflow that is exactly backwards: the audit runs against
     * an empty database at the moment it would be useful. This runs pass one of the real import,
     * so it reports the same errors, all of them, against the file in hand.
     */
    public ValidationReport validateJson(String json) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(json);
        } catch (Exception e) {
            return new ValidationReport(0, List.of(parseError(e)));
        }
        Validation validation = validate(root);
        return new ValidationReport(validation.commands().size(), validation.errors());
    }

    private String parseError(Exception e) {
        return "Failed to parse JSON: " + e.getMessage();
    }

    public record ImportResult(int importedCount, int skippedCount, List<String> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    /** Outcome of a dry run: how many entries would import, and every problem found. */
    public record ValidationReport(int validCount, List<String> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    /**
     * Pass one: deserialize every entry and recompute its events, writing nothing. Collects an
     * error per unusable entry rather than stopping at the first, so one run reports the whole
     * list of problems.
     */
    private Validation validate(JsonNode root) {
        List<PreparedCommand> commands = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<UUID, Integer> firstEntryUsingId = new HashMap<>();
        int index = 0;
        for (JsonNode entry : root) {
            JsonNode typeNode = entry.get("type");
            JsonNode payloadNode = entry.get("payload");
            String type = typeNode == null ? "no type" : typeNode.asText();
            try {
                if (typeNode == null || payloadNode == null) {
                    throw new IllegalArgumentException("entry needs both a 'type' and a 'payload'");
                }
                PreparedCommand command = prepare(index, type, payloadNode);
                Integer duplicateOf = firstEntryUsingId.putIfAbsent(command.commandId(), index);
                if (duplicateOf == null) {
                    commands.add(command);
                } else {
                    errors.add("Entry %d (%s): command id %s is already used by entry %d"
                                       .formatted(index, type, command.commandId(), duplicateOf));
                }
            } catch (Exception e) {
                errors.add("Entry %d (%s) cannot be imported: %s".formatted(index, type, e.getMessage()));
            }
            index++;
        }
        return new Validation(commands, errors);
    }

    private PreparedCommand prepare(int index, String type, JsonNode payloadNode) {
        Class<? extends ImportableCommand> commandType = ImportableCommandTypes.classFor(type);
        ImportableCommand command = jsonMapper.readValue(payloadNode.toString(), commandType);
        return new PreparedCommand(index, type, command.commandId(), command, command.events().toList());
    }

    /**
     * Pass two: write the already-validated commands. Anything that fails here is an
     * infrastructure problem rather than a data one, so it stops the run — the entries written so
     * far stay put, and re-running the same file resumes from where it stopped.
     */
    private ImportResult apply(List<PreparedCommand> prepared) {
        Set<UUID> alreadyImported = persister.existingCommandIds(
                prepared.stream().map(PreparedCommand::commandId).toList());
        int importedCount = 0;
        int skippedCount = 0;
        for (PreparedCommand entry : prepared) {
            if (alreadyImported.contains(entry.commandId())) {
                skippedCount++;
                continue;
            }
            try {
                // via CommandExecutor: it write-ahead-logs the command, appends the events, and
                // marks the command FAILED_PERSIST if the append fails (no orphaned PENDING row)
                commandExecutor.appendEvents(entry.commandId(), entry.command(), entry.events().stream());
                importedCount++;
            } catch (RuntimeException e) {
                return new ImportResult(importedCount, skippedCount, List.of(
                        ("Import stopped at entry %d (%s): %s. The %d command(s) written before it were kept; "
                         + "re-run this same file to resume — already-imported commands are skipped.")
                                .formatted(entry.index(), entry.type(), e.getMessage(), importedCount)));
            }
        }
        return new ImportResult(importedCount, skippedCount, List.of());
    }

    private ExportEntry toExportEntry(PostgresPersister.CommandPayloadRow row) {
        try {
            return new ExportEntry(ImportableCommandTypes.logicalNameFor(row.type()),
                    jsonMapper.readTree(row.payloadJson()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse payload for type " + row.type(), e);
        }
    }

    record ExportEntry(String type, JsonNode payload) {}

    /** An entry that survived validation, with its events already computed. */
    private record PreparedCommand(int index,
                                   String type,
                                   UUID commandId,
                                   ImportableCommand command,
                                   List<? extends Event> events) {}

    /** Outcome of pass one: what is ready to write, and why the rest is not. */
    private record Validation(List<PreparedCommand> commands, List<String> errors) {
        boolean hasErrors() { return !errors.isEmpty(); }
    }
}
