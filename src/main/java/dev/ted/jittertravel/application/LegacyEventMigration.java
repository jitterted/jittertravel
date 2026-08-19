package dev.ted.jittertravel.application;

import dev.ted.jittertravel.infrastructure.EventPayloadUpcaster;
import dev.ted.jittertravel.infrastructure.EventTypes;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupEventRow;
import dev.ted.jittertravel.infrastructure.PostgresPersister.MigratedEventRow;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Eager legacy-event migration (see {@code docs/LegacyEventEagerMigrationPlan.md}). Bakes the current
 * payload shape and per-type {@code schema_version} stamp into {@code event_log} <em>once</em>, so the
 * read-time upcaster — and the {@link LocationZoneResolver} it calls — leave the boot-replay path.
 *
 * <p><strong>What a row needs.</strong> For each stored event this runs the exact same
 * {@link EventPayloadUpcaster#upcast} the read path uses. A row is rewritten iff either
 * <ul>
 *   <li>its <em>payload changes</em> under upcast (a legacy bare-scalar datetime → {@code {utc,zone}}),
 *       or</li>
 *   <li>its <em>stamp is missing or wrong</em> — a row written before the {@code schema_version}
 *       column existed, whose payload may already be current-shape but carries no version.</li>
 * </ul>
 * A row that is already current-shape <em>and</em> correctly stamped is skipped, which makes the whole
 * migration idempotent: a second run rewrites nothing.
 *
 * <p><strong>Validate-then-apply.</strong> {@link #preview()} and {@link #migrate()} share one pass
 * that upcasts and <em>bind-checks</em> every candidate row, writing nothing; a single row that cannot
 * bind is reported and aborts the whole migration with zero writes. {@link #migrate()} then applies the
 * rewrites in one transaction (in the persister).
 *
 * <p><strong>Not through {@link CommandExecutor}.</strong> Like restore, this rewrites existing rows
 * rather than appending new events from a command, so it performs the read-only check up front (via
 * {@link CommandExecutor#isReadOnly()}) and refuses before any write.
 */
public class LegacyEventMigration {

    private final PostgresPersister persister;
    private final EventPayloadUpcaster upcaster;
    private final JsonMapper jsonMapper;
    private final CommandExecutor commandExecutor;

    public LegacyEventMigration(PostgresPersister persister, EventPayloadUpcaster upcaster,
                                JsonMapper jsonMapper, CommandExecutor commandExecutor) {
        this.persister = persister;
        this.upcaster = upcaster;
        this.jsonMapper = jsonMapper;
        this.commandExecutor = commandExecutor;
    }

    /** Describes what {@link #migrate()} would do — scans and bind-checks every row, writing nothing. */
    public MigrationReport preview() {
        Plan plan = plan();
        int alreadyCurrent = plan.scanned - plan.rows.size() - plan.errors.size();
        return new MigrationReport(plan.scanned, plan.payloadRewrites, plan.stampsOnly,
                alreadyCurrent, plan.errors);
    }

    /**
     * Bakes the current shape and stamp into every stale row in one transaction. Refuses in read-only
     * mode; refuses (writing nothing) if any row fails to bind, reporting all failures.
     */
    public MigrationResult migrate() {
        if (commandExecutor.isReadOnly()) {
            return MigrationResult.readOnlyRefusal();
        }
        Plan plan = plan();
        if (!plan.errors.isEmpty()) {
            return MigrationResult.failed(plan.errors);  // nothing written: fix the data and re-run
        }
        persister.migrateEventPayloads(plan.rows);
        return new MigrationResult(false, plan.payloadRewrites, plan.stampsOnly, List.of());
    }

    private Plan plan() {
        int scanned = 0;
        int payloadRewrites = 0;
        int stampsOnly = 0;
        List<MigratedEventRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (BackupEventRow row : persister.findAllEventsForBackup()) {
            scanned++;
            // Everything a single row needs — version lookup, upcast (zone resolution), bind — runs
            // inside one try, so an unresolvable location or unknown type is reported and aborts the
            // whole migration, never thrown mid-scan. That is the 2026-08-16 boot failure turned into
            // a clean, all-or-nothing report.
            try {
                int currentVersion = EventTypes.currentSchemaVersion(row.type());
                JsonNode original = jsonMapper.readTree(row.payloadJson());
                // upcast mutates the node in place, so hand it a copy and compare against the original.
                JsonNode upcasted = upcaster.upcast(row.type(), original.deepCopy(), row.schemaVersion());

                boolean payloadChanged = !upcasted.equals(original);
                boolean stampChanged = row.schemaVersion() == null || row.schemaVersion() != currentVersion;
                if (!payloadChanged && !stampChanged) {
                    continue;  // already current-shape and correctly stamped
                }

                jsonMapper.treeToValue(upcasted, EventTypes.classFor(row.type()));  // must bind

                String newPayload = payloadChanged ? upcasted.toString() : row.payloadJson();
                rows.add(new MigratedEventRow(row.sequence(), newPayload, currentVersion));
                if (payloadChanged) {
                    payloadRewrites++;
                } else {
                    stampsOnly++;
                }
            } catch (Exception e) {
                errors.add("Event %d (%s) cannot be migrated: %s"
                        .formatted(row.sequence(), row.type(), e.getMessage()));
            }
        }
        return new Plan(scanned, payloadRewrites, stampsOnly, rows, errors);
    }

    /** What one pass found: the rows to write, and why the rest are not written. */
    private record Plan(int scanned, int payloadRewrites, int stampsOnly,
                        List<MigratedEventRow> rows, List<String> errors) {}

    /**
     * A dry-run summary: how many rows would be payload-rewritten, stamp-only stamped, are already
     * current, and every row that could not be migrated.
     */
    public record MigrationReport(int scanned, int toRewrite, int toStamp, int alreadyCurrent,
                                  List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public int totalToMigrate() {
            return toRewrite + toStamp;
        }
    }

    /** Outcome of an applied migration: rows rewritten/stamped, or why nothing was written. */
    public record MigrationResult(boolean refusedReadOnly, int rewritten, int stamped,
                                  List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        static MigrationResult readOnlyRefusal() {
            return new MigrationResult(true, 0, 0, List.of(
                    "Migration refused: the application is in read-only mode, so nothing was written."));
        }

        static MigrationResult failed(List<String> errors) {
            return new MigrationResult(false, 0, 0, errors);
        }
    }
}
