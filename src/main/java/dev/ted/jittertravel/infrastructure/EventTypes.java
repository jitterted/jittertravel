package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.DifferentCityConflictCleared;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
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
 * <p>This mirrors {@code ImportableCommandTypes}, which does the same for commands in the backup
 * format. Where that registry keys on the wire id read from {@code command_log}, the event write
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
 * </ul>
 */
public final class EventTypes {

    private static final Map<String, Class<? extends Event>> LOGICAL_TO_CLASS = new LinkedHashMap<>();
    private static final Map<Class<? extends Event>, String> CLASS_TO_LOGICAL = new LinkedHashMap<>();
    private static final Map<String, String> WIRE_ID_TO_LOGICAL = new LinkedHashMap<>();

    static {
        register("FlightBooked", FlightBooked.class);
        register("FlightChanged", FlightChanged.class);
        register("TrainBooked", TrainBooked.class);
        register("TrainChanged", TrainChanged.class);
        register("HotelBooked", HotelBooked.class);
        register("ConferenceTentativelyPlanned", ConferenceTentativelyPlanned.class);
        register("ConferenceCancelled", ConferenceCancelled.class);
        register("GatheringPlanned", GatheringPlanned.class);
        register("DifferentCityConflictCleared", DifferentCityConflictCleared.class);
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

    public static boolean isRegistered(Class<? extends Event> type) {
        return CLASS_TO_LOGICAL.containsKey(type);
    }

    private static void register(String logicalName, Class<? extends Event> type) {
        if (LOGICAL_TO_CLASS.putIfAbsent(logicalName, type) != null) {
            throw new IllegalStateException("Duplicate logical event name: " + logicalName);
        }
        CLASS_TO_LOGICAL.put(type, logicalName);
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