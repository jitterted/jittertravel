package dev.ted.jittertravel.web;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled iCalendar (RFC 5545) writer — no external dependency, consistent with the hand-rolled
 * j2html renderers. Turns a {@code List<ICalEvent>} into a {@code VCALENDAR} document.
 * <p>
 * The RFC details this gets right: <b>CRLF</b> line endings, <b>75-octet line folding</b> (folded
 * lines continue with a leading space, and folding never splits a multi-byte UTF-8 character),
 * and escaping of {@code \ ; ,} and newlines in TEXT values. All timestamps are emitted in UTC
 * basic format ({@code yyyyMMdd'T'HHmmss'Z'}); the device re-displays them in its own zone.
 */
@Component
public class ICalWriter {

    private static final String CRLF = "\r\n";
    private static final int MAX_OCTETS = 75;
    private static final DateTimeFormatter UTC_BASIC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public String write(String calendarName, Instant generatedAt, List<ICalEvent> events) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//JitterTravel//Calendar Feed//EN");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("X-WR-CALNAME:" + escape(calendarName));
        events.forEach(event -> appendEvent(lines, event, generatedAt));
        lines.add("END:VCALENDAR");

        StringBuilder out = new StringBuilder();
        lines.forEach(line -> foldInto(out, line));
        return out.toString();
    }

    private void appendEvent(List<String> lines, ICalEvent event, Instant generatedAt) {
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + event.uid());
        lines.add("DTSTAMP:" + UTC_BASIC.format(generatedAt));
        lines.add("LAST-MODIFIED:" + UTC_BASIC.format(generatedAt));
        // A subscribed calendar refetches the whole document and replaces its copy each cycle, so a
        // monotonic SEQUENCE (needed for iTIP invites) is unnecessary here — the stable UID plus
        // full-document replace does the reconciliation. Emit a constant 0.
        lines.add("SEQUENCE:0");
        lines.add("DTSTART:" + UTC_BASIC.format(event.start()));
        lines.add("DTEND:" + UTC_BASIC.format(event.end()));
        lines.add("SUMMARY:" + escape(event.summary()));
        if (!event.description().isBlank()) {
            lines.add("DESCRIPTION:" + escape(event.description()));
        }
        event.alarmTriggers().forEach(trigger -> {
            lines.add("BEGIN:VALARM");
            lines.add("ACTION:DISPLAY");
            lines.add("DESCRIPTION:" + escape(event.summary()));
            lines.add("TRIGGER;RELATED=START:" + trigger);
            lines.add("END:VALARM");
        });
        lines.add("END:VEVENT");
    }

    /** Escape a TEXT value per RFC 5545 §3.3.11 — backslash first, then the rest. */
    private String escape(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }

    /**
     * Append {@code line} to {@code out}, folded to {@value #MAX_OCTETS} octets per physical line
     * with CRLF endings. Continuation lines begin with a single space (not part of the value), and
     * folding happens on character boundaries so a multi-byte UTF-8 character is never split.
     */
    private void foldInto(StringBuilder out, String line) {
        if (line.getBytes(StandardCharsets.UTF_8).length <= MAX_OCTETS) {
            out.append(line).append(CRLF);
            return;
        }
        int[] codePoints = line.codePoints().toArray();
        int index = 0;
        boolean firstPhysical = true;
        while (index < codePoints.length) {
            StringBuilder physical = new StringBuilder();
            int budget = MAX_OCTETS;
            if (!firstPhysical) {
                physical.append(' ');
                budget -= 1;
            }
            int used = 0;
            while (index < codePoints.length) {
                String character = new String(Character.toChars(codePoints[index]));
                int characterOctets = character.getBytes(StandardCharsets.UTF_8).length;
                if (used + characterOctets > budget) {
                    break;
                }
                physical.append(character);
                used += characterOctets;
                index++;
            }
            out.append(physical).append(CRLF);
            firstPhysical = false;
        }
    }
}
