package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Event;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A command that can be re-applied from an exported backup. Each implementation knows how to
 * re-emit its own events ({@link #events()}) and supply the {@code command_log} id to persist
 * them under ({@link #commandId()}), so the importer dispatches generically — no per-type
 * branching. The implementing class is also the durable payload that round-trips through
 * export/import; its stable wire identity lives in {@link ImportableCommandTypes}.
 *
 * <p>Import replays historical commands whose dates are necessarily in the past relative to the
 * real "now". {@link #IMPORT_BYPASS_INSTANT} makes the domains' future-dating checks pass so old
 * data round-trips unchanged; live booking paths use the real clock instead. (Its
 * {@code LocalDateTime}/{@code LocalDate} predecessors went away with the conference migration —
 * every command now decides on instants.)
 */
public interface ImportableCommand {

    Instant IMPORT_BYPASS_INSTANT = Instant.MIN;

    /** Id to persist this command's events under in {@code command_log}. */
    UUID commandId();

    /** The events this command emits, recomputed deterministically from its payload. */
    Stream<? extends Event> events();
}