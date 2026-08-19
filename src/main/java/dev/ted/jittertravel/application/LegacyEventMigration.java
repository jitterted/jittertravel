package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Event;
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
 * {@link EventPayloadUpcaster#upcast} the read path uses. A row is rewritten iff any of
 * <ul>
 *   <li>its <em>payload changes</em> under upcast (a legacy bare-scalar datetime → {@code {utc,zone}}),
 *       or</li>
 *   <li>its <em>stamp is missing or wrong</em> — a row written before the {@code schema_version}
 *       column existed, whose payload may already be current-shape but carries no version, or</li>
 *   <li>its <em>{@code type} is a retired wire id</em> — an old logical name or a legacy FQCN that
 *       {@link EventTypes} still aliases; it is normalized to the current logical name (see
 *       {@code docs/EventTypeColumnNormalizationPlan.md}).</li>
 * </ul>
 * A row that is already current-shape, correctly stamped <em>and</em> stored under the current logical
 * name is skipped, which makes the whole migration idempotent: a second run rewrites nothing.
 *
 * <p><strong>Normalizing {@code type} is a one-way door for rollback.</strong> Aliases teach today's
 * build yesterday's names, never the reverse, so once a row carries a name invented after an older
 * build shipped, that older build cannot replay it. Take — and keep — a backup immediately before
 * migrating: it is the artifact that restores into either build.
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
                plan.renames, plan.rows.size(), alreadyCurrent, plan.errors);
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
        return new MigrationResult(false, plan.payloadRewrites, plan.stampsOnly, plan.renames, List.of());
    }

    private Plan plan() {
        int scanned = 0;
        int payloadRewrites = 0;
        int stampsOnly = 0;
        int renames = 0;
        List<MigratedEventRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (BackupEventRow row : persister.findAllEventsForBackup()) {
            scanned++;
            // Everything a single row needs — version lookup, upcast (zone resolution), bind — runs
            // inside one try, so an unresolvable location or unknown type is reported and aborts the
            // whole migration, never thrown mid-scan. That is the 2026-08-16 boot failure turned into
            // a clean, all-or-nothing report.
            try {
                Class<? extends Event> eventClass = EventTypes.classFor(row.type());
                String currentType = EventTypes.logicalNameFor(eventClass);
                int currentVersion = EventTypes.currentSchemaVersion(eventClass);
                JsonNode original = jsonMapper.readTree(row.payloadJson());
                // upcast mutates the node in place, so hand it a copy and compare against the original.
                JsonNode upcasted = upcaster.upcast(row.type(), original.deepCopy(), row.schemaVersion());

                boolean payloadChanged = !upcasted.equals(original);
                boolean stampChanged = row.schemaVersion() == null || row.schemaVersion() != currentVersion;
                boolean typeChanged = !row.type().equals(currentType);
                if (!payloadChanged && !stampChanged && !typeChanged) {
                    continue;  // already current-shape, correctly stamped, current name
                }

                jsonMapper.treeToValue(upcasted, eventClass);  // must bind

                String newPayload = payloadChanged ? upcasted.toString() : row.payloadJson();
                rows.add(new MigratedEventRow(row.sequence(), currentType, newPayload, currentVersion));
                // The three counters overlap — one row can be rewritten, stamped and renamed at once —
                // so the number of rows actually written is rows.size(), never their sum.
                if (payloadChanged) {
                    payloadRewrites++;
                } else if (stampChanged) {
                    stampsOnly++;
                }
                if (typeChanged) {
                    renames++;
                }
            } catch (Exception e) {
                errors.add("Event %d (%s) cannot be migrated: %s"
                        .formatted(row.sequence(), row.type(), e.getMessage()));
            }
        }
        return new Plan(scanned, payloadRewrites, stampsOnly, renames, rows, errors);
    }

    /** What one pass found: the rows to write, and why the rest are not written. */
    private record Plan(int scanned, int payloadRewrites, int stampsOnly, int renames,
                        List<MigratedEventRow> rows, List<String> errors) {}

    /**
     * A dry-run summary: how many rows would be payload-rewritten, stamp-only stamped, renamed to the
     * current logical name, how many rows would be written in total, how many are already current, and
     * every row that could not be migrated.
     *
     * <p>{@code toRename} <em>overlaps</em> the other two — a row can be rewritten and renamed in one
     * write — so {@code toMigrate}, the row count, is the only true total.
     */
    public record MigrationReport(int scanned, int toRewrite, int toStamp, int toRename, int toMigrate,
                                  int alreadyCurrent, List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public int totalToMigrate() {
            return toMigrate;
        }
    }

    /**
     * Outcome of an applied migration: rows rewritten/stamped/renamed, or why nothing was written.
     * {@code renamed} overlaps {@code rewritten} and {@code stamped}, as in {@link MigrationReport}.
     */
    public record MigrationResult(boolean refusedReadOnly, int rewritten, int stamped, int renamed,
                                  List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        static MigrationResult readOnlyRefusal() {
            return new MigrationResult(true, 0, 0, 0, List.of(
                    "Migration refused: the application is in read-only mode, so nothing was written."));
        }

        static MigrationResult failed(List<String> errors) {
            return new MigrationResult(false, 0, 0, 0, errors);
        }
    }
}
