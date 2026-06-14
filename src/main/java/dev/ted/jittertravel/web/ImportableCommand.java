package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Event;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * real "now". The {@code IMPORT_BYPASS_*} sentinels make the domains' future-dating checks pass
 * so old data round-trips unchanged; live booking paths use the real clock instead.
 */
public interface ImportableCommand {

    LocalDateTime IMPORT_BYPASS_NOW = LocalDateTime.MIN;
    LocalDate IMPORT_BYPASS_DATE = LocalDate.MIN;

    /** Id to persist this command's events under in {@code command_log}. */
    UUID commandId();

    /** The events this command emits, recomputed deterministically from its payload. */
    Stream<? extends Event> events();
}