package dev.ted.jittertravel.infrastructure;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * What {@code /admin/eventlog} is narrowed to. Both halves are optional and combine with AND:
 * a row must be one of {@code types} (none means every type) and must carry one of
 * {@code identifiers} (none means any row). See {@code docs/archived/EventLogFilterPlan.md}.
 *
 * @param types       logical event names as {@link EventTypes} registers them, never a stored
 *                    {@code type}: how a type is spelled in old rows is the persister's business
 * @param identifiers text looked for as a row's event id, its command id, or anywhere in its
 *                    payload. Nothing checks that one is a UUID, so part of an id matches too
 */
public record EventLogFilter(List<String> types, List<String> identifiers) {

    public EventLogFilter {
        types = List.copyOf(types);
        identifiers = List.copyOf(identifiers);
    }

    public static EventLogFilter none() {
        return new EventLogFilter(List.of(), List.of());
    }

    /**
     * The filter a submitted form asks for. A type name that is not registered is dropped, not
     * refused: a bookmark made before an event was renamed should show everything else it named.
     * The identifier text is split on whitespace and commas.
     */
    public static EventLogFilter from(List<String> typeNames, String identifierText) {
        List<String> registered = EventTypes.logicalNames();
        List<String> types = typeNames.stream()
                .map(String::trim)
                .filter(registered::contains)
                .distinct()
                .toList();
        List<String> identifiers = Arrays.stream(identifierText.split("[\\s,]+"))
                .filter(Predicate.not(String::isBlank))
                .distinct()
                .toList();
        return new EventLogFilter(types, identifiers);
    }

    public boolean isEmpty() {
        return types.isEmpty() && identifiers.isEmpty();
    }
}
