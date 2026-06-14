package dev.ted.jittertravel.application;

import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.web.ImportableCommand;
import dev.ted.jittertravel.web.ImportableCommandTypes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CommandImporter {

    private final PostgresPersister persister;
    private final EventStore eventStore;
    private final JsonMapper jsonMapper;

    public CommandImporter(PostgresPersister persister, EventStore eventStore, JsonMapper jsonMapper) {
        this.persister = persister;
        this.eventStore = eventStore;
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
        List<String> errors = new ArrayList<>();
        int importedCount = 0;
        try {
            JsonNode root = jsonMapper.readTree(json);
            for (JsonNode entry : root) {
                String type = entry.get("type").asText();
                JsonNode payload = entry.get("payload");
                try {
                    importEntry(type, payload);
                    importedCount++;
                } catch (Exception e) {
                    errors.add("Failed to import %s: %s".formatted(type, e.getMessage()));
                }
            }
        } catch (Exception e) {
            errors.add("Failed to parse JSON: " + e.getMessage());
        }
        return new ImportResult(importedCount, errors);
    }

    public record ImportResult(int importedCount, List<String> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    private void importEntry(String type, JsonNode payloadNode) {
        Class<? extends ImportableCommand> commandType = ImportableCommandTypes.classFor(type);
        ImportableCommand command = jsonMapper.readValue(payloadNode.toString(), commandType);
        UUID commandId = command.commandId();
        persister.saveCommand(commandId, command);     // command persisted first (FK target for events)
        eventStore.append(command.events(), commandId);
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
}
