package dev.ted.jittertravel.web;

import java.time.Instant;
import java.util.List;

/**
 * A single calendar event to be written into an iCalendar (RFC 5545) feed by {@link ICalWriter}.
 * This is the presentation-layer value type the calendar feed is assembled into — the iCal analogue
 * of the j2html view records.
 * <p>
 * {@code start}/{@code end} are UTC instants (the writer emits them in {@code …Z} form, unambiguous
 * regardless of the device's zone). {@code alarmTriggers} are RFC 5545 {@code TRIGGER} durations
 * relative to the event start (e.g. {@code -PT24H} = 24 hours before); each becomes its own
 * {@code VALARM} block that the device fires locally. An empty list means no alarms.
 */
public record ICalEvent(
        String uid,
        Instant start,
        Instant end,
        String summary,
        String description,
        List<String> alarmTriggers
) {
    public ICalEvent {
        alarmTriggers = alarmTriggers == null ? List.of() : List.copyOf(alarmTriggers);
        description = description == null ? "" : description;
    }
}
