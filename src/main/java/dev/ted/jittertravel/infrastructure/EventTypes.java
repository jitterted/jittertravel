package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.DifferentCityConflictCleared;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.OneOffTaskCompleted;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainChanged;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for the durable identity of every {@link Event}. Maps a
 * <em>stable logical type name</em> — the {@code type} written into the {@code event_log} — to its
 * current implementation class. This decouples the persisted event stream from physical class
 * names, so an event class can be moved between packages or renamed without breaking replay of
 * previously stored rows.
 *
 * <p>There is no command-side equivalent: the event-oriented backup restores events verbatim, so a
 * command's {@code type} is stored opaquely and never resolved back to a class. The event write
 * path holds the {@link Class} directly, so {@link #logicalNameFor(Class)} takes a class.
 *
 * <p>Maintenance rules:
 * <ul>
 *   <li><b>Add an event:</b> implement {@link Event}, add one {@link #register} line. The
 *       completeness test fails until you do.</li>
 *   <li><b>Move/rename an event's class:</b> nothing changes here but the {@code .class} reference;
 *       the logical name stays put, so old {@code event_log} rows keep resolving.</li>
 *   <li><b>Read a row whose class has since moved/renamed:</b> add an {@link #alias} line. Aliases
 *       are an append-only migration log — never edit or remove one.</li>
 *   <li><b>Rename an event's <em>logical</em> name:</b> only when the old name states something
 *       untrue — it costs an {@link #alias} for every wire id the type was ever stored under (the old
 *       logical name, plus its old FQCN if any row predates logical names) and leaves the log holding
 *       both spellings. Any {@link EventUpcaster} rung for the type keys on the logical name, so its
 *       {@code canHandle} must move to the new one in the same change. See {@code ConferencePlanned}
 *       (renamed 2026-08-19). Running {@code /admin/migrate-legacy-events} afterwards rewrites those
 *       rows to the new name — and costs the ability to roll the code back, because an alias teaches
 *       today's build yesterday's names and never the reverse. Take and keep a backup immediately
 *       before that run; see {@code docs/archived/EventTypeColumnNormalizationPlan.md}.</li>
 *   <li><b>Migrate an event's payload schema:</b> bump its {@code currentSchemaVersion} here (the
 *       third {@code register} argument) so new appends and the eager migration stamp the new
 *       number. See {@code docs/archived/LegacyEventEagerMigrationPlan.md}.</li>
 * </ul>
 *
 * <p><b>Schema versions are per type, not global.</b> A type's version counts <em>its own</em>
 * schema changes: the nine datetime-bearing types moved bare-scalar → {@link
 * dev.ted.jittertravel.domain.ZonedTimestamp} (version 2), while a type born after that change has
 * only ever had one shape (version 1). So {@code HotelBooked}=2 and {@code
 * ConferenceAttendanceDeclined}=1 are each "current for that type" — the numbers are not comparable
 * across types.
 */
public final class EventTypes {

    /** A type registered without an explicit version has only ever had one payload shape. */
    private static final int INITIAL_SCHEMA_VERSION = 1;

    /** The current schema version of the nine types that migrated to {@code ZonedTimestamp}. */
    private static final int ZONED_TIMESTAMP_SCHEMA_VERSION = 2;

    /**
     * {@code ConferencePlanned} alone advanced past {@code ZonedTimestamp}: v3 added the
     * {@code format} field ({@link dev.ted.jittertravel.domain.ConferenceFormat}), injected by the
     * upcaster into pre-v3 payloads.
     */
    private static final int CONFERENCE_FORMAT_SCHEMA_VERSION = 3;

    private static final Map<String, Class<? extends Event>> LOGICAL_TO_CLASS = new LinkedHashMap<>();
    private static final Map<Class<? extends Event>, String> CLASS_TO_LOGICAL = new LinkedHashMap<>();
    private static final Map<String, String> WIRE_ID_TO_LOGICAL = new LinkedHashMap<>();
    private static final Map<String, Integer> LOGICAL_TO_VERSION = new LinkedHashMap<>();

    static {
        register("FlightBooked", FlightBooked.class, ZONED_TIMESTAMP_SCHEMA_VERSION);
        register("FlightChanged", FlightChanged.class, ZONED_TIMESTAMP_SCHEMA_VERSION);
        register("TrainBooked", TrainBooked.class, ZONED_TIMESTAMP_SCHEMA_VERSION);
        register("TrainChanged", TrainChanged.class, ZONED_TIMESTAMP_SCHEMA_VERSION);
        register("HotelBooked", HotelBooked.class, ZONED_TIMESTAMP_SCHEMA_VERSION);
        register("HotelChanged", HotelChanged.class, ZONED_TIMESTAMP_SCHEMA_VERSION);
        register("HotelBookingCancelled", HotelBookingCancelled.class);
        register("ConferencePlanned", ConferencePlanned.class, CONFERENCE_FORMAT_SCHEMA_VERSION);
        register("ConferenceCancelled", ConferenceCancelled.class);
        register("ConferenceAttendanceConfirmed", ConferenceAttendanceConfirmed.class);
        register("ConferenceAttendanceDeclined", ConferenceAttendanceDeclined.class);
        // Born with a ZonedTimestamp, so there is no pre-zone form of it to upcast: version 1 is
        // the only shape this event has ever had.
        register("CfpOpened", CfpOpened.class);
        register("GatheringPlanned", GatheringPlanned.class, ZONED_TIMESTAMP_SCHEMA_VERSION);
        register("GatheringChanged", GatheringChanged.class, ZONED_TIMESTAMP_SCHEMA_VERSION);
        register("PrivateEventPlanned", PrivateEventPlanned.class);
        register("GroundTransferPlanned", GroundTransferPlanned.class);
        register("GroundTransferCancelled", GroundTransferCancelled.class);
        register("DifferentCityConflictCleared", DifferentCityConflictCleared.class);
        register("OneOffTaskCompleted", OneOffTaskCompleted.class);

        // Renamed 2026-08-19: ConferenceTentativelyPlanned → ConferencePlanned. "Tentative" became a
        // *derived* attendance status (WATCHING/GOING), so the old name asserted a state the model no
        // longer records. Stored rows are untouched, so both of its historical wire ids — the logical
        // name, and the FQCN written before logical names existed — must keep resolving.
        alias("ConferenceTentativelyPlanned", "ConferencePlanned");
        alias("dev.ted.jittertravel.domain.ConferenceTentativelyPlanned", "ConferencePlanned");
    }

    private EventTypes() {
    }

    /** Stable logical name to write into the {@code event_log} for the given event class. */
    public static String logicalNameFor(Class<? extends Event> eventClass) {
        String logical = CLASS_TO_LOGICAL.get(eventClass);
        if (logical == null) {
            throw new IllegalArgumentException("Unregistered event type: " + eventClass.getName());
        }
        return logical;
    }

    /** Implementation class for a {@code type} read from {@code event_log} (logical name or legacy FQCN). */
    public static Class<? extends Event> classFor(String wireTypeId) {
        String logical = WIRE_ID_TO_LOGICAL.get(wireTypeId);
        Class<? extends Event> type = logical == null ? null : LOGICAL_TO_CLASS.get(logical);
        if (type == null) {
            throw new IllegalArgumentException("Unknown event type: " + wireTypeId);
        }
        return type;
    }

    /** Current schema version stamped onto new appends and rewritten rows, for the given event class. */
    public static int currentSchemaVersion(Class<? extends Event> eventClass) {
        return LOGICAL_TO_VERSION.get(logicalNameFor(eventClass));
    }

    /**
     * Current schema version for a {@code type} read from {@code event_log} (logical name or legacy
     * FQCN) — what the eager migration stamps a row of this type with.
     */
    public static int currentSchemaVersion(String wireTypeId) {
        String logical = WIRE_ID_TO_LOGICAL.get(wireTypeId);
        Integer version = logical == null ? null : LOGICAL_TO_VERSION.get(logical);
        if (version == null) {
            throw new IllegalArgumentException("Unknown event type: " + wireTypeId);
        }
        return version;
    }

    public static boolean isRegistered(Class<? extends Event> type) {
        return CLASS_TO_LOGICAL.containsKey(type);
    }

    private static void register(String logicalName, Class<? extends Event> type) {
        register(logicalName, type, INITIAL_SCHEMA_VERSION);
    }

    private static void register(String logicalName, Class<? extends Event> type, int currentSchemaVersion) {
        if (LOGICAL_TO_CLASS.putIfAbsent(logicalName, type) != null) {
            throw new IllegalStateException("Duplicate logical event name: " + logicalName);
        }
        CLASS_TO_LOGICAL.put(type, logicalName);
        LOGICAL_TO_VERSION.put(logicalName, currentSchemaVersion);
        mapWireId(logicalName, logicalName);     // a logical name resolves to itself on read
        mapWireId(type.getName(), logicalName);  // current FQCN resolves too (rows written before logical names)
    }

    private static void alias(String legacyWireId, String logicalName) {
        if (!LOGICAL_TO_CLASS.containsKey(logicalName)) {
            throw new IllegalStateException("alias() target is not a registered logical name: " + logicalName);
        }
        mapWireId(legacyWireId, logicalName);
    }

    private static void mapWireId(String wireId, String logicalName) {
        String existing = WIRE_ID_TO_LOGICAL.putIfAbsent(wireId, logicalName);
        if (existing != null && !existing.equals(logicalName)) {
            throw new IllegalStateException(
                    "Event type id '" + wireId + "' already maps to '" + existing + "'");
        }
    }
}