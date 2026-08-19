package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.AirportZoneResolver;
import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.domain.Event;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Read-time upcaster: the general-purpose mechanism for bringing a stored event payload up to its
 * current schema shape before it binds. It is a <em>composite</em> — it owns no migration logic of
 * its own but drives a ladder of registered {@link EventUpcaster} rungs, each of which advances one
 * event type by one version (see {@link #standard} for the production set). Splitting the rungs out
 * keeps each one cohesive: the datetime→{@link dev.ted.jittertravel.domain.ZonedTimestamp} rungs that
 * need a zone resolver no longer share a class with the {@code format}-field rung that needs nothing,
 * and the flight rung's {@link AirportZoneResolver} no longer sits in a class that also does hotels.
 *
 * <p><b>Version-driven, not shape-sniffing.</b> The climb starts from the row's stored
 * {@code schema_version} (a legacy row that predates the column reads as {@code null} ⇒ version 1)
 * and walks up to the type's {@link EventTypes#currentSchemaVersion current version}, applying the
 * one rung registered for each step. A row already stamped at the current version does no work. The
 * stored version is the driver; each rung additionally keeps its edit idempotent (an absence/shape
 * check) so a never-stamped legacy row whose payload is <em>already</em> in a later shape climbs from
 * version 1 through the intervening rungs as no-ops rather than being double-applied.
 *
 * <p><b>Retiring a rung</b> (once every stored row of that type has been permanently migrated past
 * it — see {@code /admin/migrate-legacy-events} and {@code docs/LegacyEventEagerMigrationPlan.md})
 * means deleting its {@link EventUpcaster} and dropping it from {@link #standard}. If a row is ever
 * read that still sits below a retired rung, the climb cannot reach the current version and this
 * fails loud rather than binding a stale shape — the signal that the migration was skipped.
 *
 * <p>The stored {@code type} may be a stable logical name (new rows) or a legacy FQCN (rows written
 * before logical names); it is normalized to the logical name (see {@link EventTypes}) before the
 * climb, so both reach the same rungs. The pre-migration zone audit ({@code /admin/zone-audit})
 * guarantees every legacy location resolves, so the datetime rungs never throw for stored data.
 */
public class EventPayloadUpcaster {

    /**
     * The floor of every climb. A row that predates the {@code schema_version} column reads as
     * {@code null}; {@link EventTypes} treats an absent stamp as version 1, so the ladder does too.
     */
    private static final int OLDEST_SCHEMA_VERSION = 1;

    private final List<EventUpcaster> upcasters;

    EventPayloadUpcaster(List<EventUpcaster> upcasters) {
        this.upcasters = List.copyOf(upcasters);
    }

    /**
     * The production ladder, assembled from the zone resolvers and mapper. Every timezone rung shares
     * one {@link WallClockZoning} collaborator; the conference {@code format} rung needs none.
     */
    public static EventPayloadUpcaster standard(LocationZoneResolver locationZoneResolver,
                                                AirportZoneResolver airportZoneResolver,
                                                JsonMapper jsonMapper) {
        WallClockZoning zoning = new WallClockZoning(jsonMapper);
        return new EventPayloadUpcaster(List.of(
                new HotelTimeZoneUpcaster(locationZoneResolver, zoning),
                new TrainTimeZoneUpcaster(locationZoneResolver, zoning),
                new FlightTimeZoneUpcaster(airportZoneResolver, zoning),
                new GatheringTimeZoneUpcaster(locationZoneResolver, zoning),
                new ConferenceTimeZoneUpcaster(locationZoneResolver, zoning),
                new ConferenceFormatUpcaster()));
    }

    /**
     * Upcast a payload whose stored schema version is unknown — climb from the oldest version. For
     * callers with no {@code schema_version} to hand; the read paths that have one use
     * {@link #upcast(String, JsonNode, Integer)}.
     */
    public JsonNode upcast(String wireType, JsonNode payload) {
        return upcast(wireType, payload, null);
    }

    /**
     * Bring {@code payload} from its {@code storedVersion} up to the current shape for {@code
     * wireType}, mutating it in place. A {@code null} {@code storedVersion} (a legacy row with no
     * stamp) is the oldest version.
     */
    public JsonNode upcast(String wireType, JsonNode payload, Integer storedVersion) {
        if (!(payload instanceof ObjectNode object)) {
            return payload;
        }
        Class<? extends Event> eventClass = EventTypes.classFor(wireType); // fail loud on an unknown type
        String logicalType = EventTypes.logicalNameFor(eventClass);
        int target = EventTypes.currentSchemaVersion(eventClass);

        int version = storedVersion == null ? OLDEST_SCHEMA_VERSION : storedVersion;
        while (version < target) {
            rungFor(logicalType, version).upcast(object, logicalType);
            version++;
        }
        return object;
    }

    private EventUpcaster rungFor(String logicalType, int version) {
        EventUpcaster found = null;
        for (EventUpcaster candidate : upcasters) {
            if (candidate.canHandle(logicalType, version)) {
                if (found != null) {
                    throw new IllegalStateException(
                            "Two upcasters both claim %s schema version %d".formatted(logicalType, version));
                }
                found = candidate;
            }
        }
        if (found == null) {
            throw new IllegalStateException(
                    "No upcaster advances %s from schema version %d — was a rung retired before its rows were migrated?"
                            .formatted(logicalType, version));
        }
        return found;
    }
}
