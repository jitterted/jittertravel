package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.ConferenceFormat;
import tools.jackson.databind.node.ObjectNode;

/**
 * v2→v3 for {@code ConferencePlanned}: an absent {@code format} is injected as
 * {@link ConferenceFormat#CALL_FOR_PAPERS}, so the record's non-null field binds rather than reaching
 * a projector as a null. A field-default increment that needs no collaborators — which is why it was
 * its own rung, and why it outlived the datetime rungs beside it (retired 2026-10-08).
 */
class ConferenceFormatUpcaster implements EventUpcaster {

    @Override
    public boolean canHandle(String eventLogicalType, int eventVersion) {
        return eventVersion == 2 && eventLogicalType.equals("ConferencePlanned");
    }

    @Override
    public void upcast(ObjectNode payload, String eventLogicalType) {
        if (!payload.has("format")) {
            payload.put("format", ConferenceFormat.CALL_FOR_PAPERS.name());
        }
    }
}
