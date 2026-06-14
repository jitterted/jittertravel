package dev.ted.jittertravel.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for the durable identity of every {@link ImportableCommand}. Maps a
 * <em>stable logical type name</em> — the {@code type} written into backup JSON — to its current
 * implementation class. This decouples the backup format from physical class names, so a command
 * class can be moved between packages or renamed without breaking previously exported backups.
 *
 * <p>Maintenance rules:
 * <ul>
 *   <li><b>Add a command:</b> implement {@link ImportableCommand}, add one {@link #register} line.
 *       The completeness test fails until you do.</li>
 *   <li><b>Move/rename a command's class:</b> nothing changes here but the {@code .class} reference;
 *       the logical name stays put, so old backups keep importing.</li>
 *   <li><b>Rename a logical concept, or read a backup whose class has since moved:</b> add an
 *       {@link #alias} line. Aliases are an append-only migration log — never edit or remove one.</li>
 * </ul>
 */
public final class ImportableCommandTypes {

    private static final Map<String, Class<? extends ImportableCommand>> LOGICAL_TO_CLASS = new LinkedHashMap<>();
    private static final Map<String, String> WIRE_ID_TO_LOGICAL = new LinkedHashMap<>();

    static {
        register("BookFlight", BookFlightRequest.class);
        register("ChangeFlight", ChangeFlightRequest.class);
        register("BookHotel", BookHotelRequest.class);
        register("BookTrain", BookTrainRequest.class);
        register("PlanTentativeConference", PlanTentativeConferenceRequest.class);
        register("PlanGathering", PlanGatheringRequest.class);
        register("MigrateConferenceToGathering", MigrateConferenceToGathering.class);
        register("ClearDifferentCityConflict", ClearDifferentCityConflict.class);

        // These two records moved web <- application; backups exported before the move carry the
        // old fully-qualified class name as their `type`. (Pre-logical-name backups for the six
        // *Request types are already covered: register() seeds each class's current FQCN, and those
        // classes did not move.)
        alias("dev.ted.jittertravel.application.MigrateConferenceToGathering", "MigrateConferenceToGathering");
        alias("dev.ted.jittertravel.application.ClearDifferentCityConflict", "ClearDifferentCityConflict");
    }

    private ImportableCommandTypes() {
    }

    /** Stable logical name to write into an export for a command stored under the given wire id. */
    public static String logicalNameFor(String wireTypeId) {
        String logical = WIRE_ID_TO_LOGICAL.get(wireTypeId);
        if (logical == null) {
            throw new IllegalArgumentException("Unknown command type id: " + wireTypeId);
        }
        return logical;
    }

    /** Implementation class for a {@code type} read from an export (logical name or legacy id). */
    public static Class<? extends ImportableCommand> classFor(String wireTypeId) {
        String logical = WIRE_ID_TO_LOGICAL.get(wireTypeId);
        Class<? extends ImportableCommand> type = logical == null ? null : LOGICAL_TO_CLASS.get(logical);
        if (type == null) {
            throw new IllegalArgumentException("Unknown command type: " + wireTypeId);
        }
        return type;
    }

    public static boolean isRegistered(Class<? extends ImportableCommand> type) {
        return LOGICAL_TO_CLASS.containsValue(type);
    }

    private static void register(String logicalName, Class<? extends ImportableCommand> type) {
        if (LOGICAL_TO_CLASS.putIfAbsent(logicalName, type) != null) {
            throw new IllegalStateException("Duplicate logical command name: " + logicalName);
        }
        mapWireId(logicalName, logicalName);     // a logical name resolves to itself on import
        mapWireId(type.getName(), logicalName);  // current FQCN resolves too (pre-logical-name backups)
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
                    "Command type id '" + wireId + "' already maps to '" + existing + "'");
        }
    }
}