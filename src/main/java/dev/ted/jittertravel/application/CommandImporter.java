package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.web.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CommandImporter {
    private static final LocalDateTime BYPASS_NOW = LocalDateTime.MIN;

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

    private void importEntry(String type, JsonNode payloadNode) throws Exception {
        String payloadJson = payloadNode.toString();
        if (type.equals(BookHotelRequest.class.getName())) {
            BookHotelRequest request = jsonMapper.readValue(payloadJson, BookHotelRequest.class);
            BookHotelCommand command = new BookHotelHandler().handle(request);
            var events = command.execute(new BookHotelContext(BYPASS_NOW));
            persister.saveCommand(command.hotelBookingId().id(), request);
            eventStore.append(events, command.hotelBookingId().id());
        } else if (type.equals(BookFlightRequest.class.getName())) {
            BookFlightRequest request = jsonMapper.readValue(payloadJson, BookFlightRequest.class);
            UUID commandId = UUID.fromString(request.getFlightId());
            var events = new BookFlightCommand().execute(request, BYPASS_NOW);
            persister.saveCommand(commandId, request);
            eventStore.append(events, commandId);
        } else if (type.equals(PlanTentativeConferenceRequest.class.getName())) {
            PlanTentativeConferenceRequest request = jsonMapper.readValue(payloadJson, PlanTentativeConferenceRequest.class);
            UUID commandId = UUID.fromString(request.getConferenceId());
            var events = new PlanTentativeConferenceCommand().execute(request, BYPASS_NOW);
            persister.saveCommand(commandId, request);
            eventStore.append(events, commandId);
        } else if (type.equals(ChangeFlightRequest.class.getName())) {
            ChangeFlightRequest request = jsonMapper.readValue(payloadJson, ChangeFlightRequest.class);
            UUID commandId = UUID.randomUUID();
            var events = new ChangeFlightCommand().execute(request, true, BYPASS_NOW);
            persister.saveCommand(commandId, request);
            eventStore.append(events, commandId);
        } else if (type.equals(BookTrainRequest.class.getName())) {
            BookTrainRequest request = jsonMapper.readValue(payloadJson, BookTrainRequest.class);
            UUID commandId = UUID.fromString(request.getTrainTripId());
            BookTrainCommand command = new BookTrainHandler().handle(request);
            var events = command.execute(new BookTrainContext(BYPASS_NOW));
            persister.saveCommand(commandId, request);
            eventStore.append(events, commandId);
        } else if (type.equals(PlanGatheringRequest.class.getName())) {
            PlanGatheringRequest request = jsonMapper.readValue(payloadJson, PlanGatheringRequest.class);
            UUID commandId = UUID.fromString(request.getGatheringId());
            PlanGatheringCommand command = new PlanGatheringHandler().handle(request);
            var events = command.execute(new GatheringPlanningContext(LocalDate.MIN));
            persister.saveCommand(commandId, request);
            eventStore.append(events, commandId);
        } else if (type.equals(MigrateConferenceToGathering.class.getName())) {
            MigrateConferenceToGathering command = jsonMapper.readValue(payloadJson, MigrateConferenceToGathering.class);
            UUID commandId = UUID.randomUUID();
            persister.saveCommand(commandId, command);
            eventStore.append(command.events(), commandId);
        } else if (type.equals(ClearDifferentCityConflict.class.getName())) {
            ClearDifferentCityConflict command = jsonMapper.readValue(payloadJson, ClearDifferentCityConflict.class);
            UUID commandId = UUID.randomUUID();
            persister.saveCommand(commandId, command);
            eventStore.append(command.events(), commandId);
        } else {
            throw new IllegalArgumentException("Unknown command type: " + type);
        }
    }

    private ExportEntry toExportEntry(PostgresPersister.CommandPayloadRow row) {
        try {
            return new ExportEntry(row.type(), jsonMapper.readTree(row.payloadJson()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse payload for type " + row.type(), e);
        }
    }

    record ExportEntry(String type, JsonNode payload) {}
}
