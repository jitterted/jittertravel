package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.ConferenceFormat;
import tools.jackson.databind.node.ObjectNode;

/**
 * v2→v3 for {@code ConferenceTentativelyPlanned}: an absent {@code format} is injected as
 * {@link ConferenceFormat#CALL_FOR_PAPERS}, so the record's non-null field binds rather than reaching
 * a projector as a null. A field-default increment, not a datetime one — it needs no zone resolver
 * and no {@link WallClockZoning}, which is exactly why it is its own rung.
 */
class ConferenceFormatUpcaster implements EventUpcaster {

    @Override
    public boolean canHandle(String eventLogicalType, int eventVersion) {
        return eventVersion == 2 && eventLogicalType.equals("ConferenceTentativelyPlanned");
    }

    @Override
    public void upcast(ObjectNode payload, String eventLogicalType) {
        if (!payload.has("format")) {
            payload.put("format", ConferenceFormat.CALL_FOR_PAPERS.name());
        }
    }
}
