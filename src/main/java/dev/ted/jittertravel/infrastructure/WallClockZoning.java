package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.ZonedTimestamp;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The zone-migration mechanics shared by the {@code *TimeZoneUpcaster}s: turning a stored bare
 * wall-clock scalar into the {@link ZonedTimestamp} {@code {utc, zone}} object shape, plus the JSON
 * navigation helpers that go with it. It carries no event-type knowledge — each upcaster owns which
 * field resolves from which location — so it is an injected collaborator, not a base class.
 *
 * <p>Holds the {@link JsonMapper} used to render a {@code ZonedTimestamp} to a tree; the pure JSON
 * helpers ({@link #isLegacyScalar}, {@link #nestedText}) live here too so the timezone rungs share
 * one home for their JSON-shape knowledge rather than each restating it.
 */
class WallClockZoning {

    private final JsonMapper jsonMapper;

    WallClockZoning(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** A bare wall-clock scalar is the pre-migration shape; a {@code {utc, zone}} object is current. */
    boolean isLegacyScalar(JsonNode node) {
        return node != null && node.isString();
    }

    /** The value of {@code field} under {@code node} as text, or {@code ""} when either is absent. */
    String nestedText(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null ? "" : value.asString();
    }

    JsonNode toZoned(String wallClock, ZoneId zone) {
        return toZoned(LocalDateTime.parse(wallClock), zone);
    }

    JsonNode toZoned(LocalDateTime wallClock, ZoneId zone) {
        return jsonMapper.valueToTree(ZonedTimestamp.fromLocal(wallClock, zone));
    }
}
