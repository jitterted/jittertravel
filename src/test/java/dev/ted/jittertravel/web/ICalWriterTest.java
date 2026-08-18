package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ICalWriterTest {

    private final ICalWriter writer = new ICalWriter();

    @Test
    void goldenSampleOfASingleDeadlineEventWithTwoAlarms() {
        String ics = writer.write(
                "JitterTravel deadlines",
                Instant.parse("2026-07-01T12:00:00Z"),
                List.of(new ICalEvent(
                        "abc-cancelby@jittertravel",
                        Instant.parse("2026-07-10T09:30:00Z"),
                        Instant.parse("2026-07-10T09:45:00Z"),
                        "Free-cancel deadline: Grand Hotel",
                        "Berlin",
                        List.of("-PT24H", "-PT4H"))));

        assertThat(ics).isEqualTo(String.join("\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//JitterTravel//Calendar Feed//EN",
                "CALSCALE:GREGORIAN",
                "X-WR-CALNAME:JitterTravel deadlines",
                "BEGIN:VEVENT",
                "UID:abc-cancelby@jittertravel",
                "DTSTAMP:20260701T120000Z",
                "LAST-MODIFIED:20260701T120000Z",
                "SEQUENCE:0",
                "DTSTART:20260710T093000Z",
                "DTEND:20260710T094500Z",
                "SUMMARY:Free-cancel deadline: Grand Hotel",
                "DESCRIPTION:Berlin",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "DESCRIPTION:Free-cancel deadline: Grand Hotel",
                "TRIGGER;RELATED=START:-PT24H",
                "END:VALARM",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "DESCRIPTION:Free-cancel deadline: Grand Hotel",
                "TRIGGER;RELATED=START:-PT4H",
                "END:VALARM",
                "END:VEVENT",
                "END:VCALENDAR",
                ""));   // trailing CRLF after END:VCALENDAR
    }

    @Test
    void emitsUtcBasicFormatWithTrailingZForTimestamps() {
        String ics = writeOneEvent("uid", Instant.parse("2026-12-31T23:59:00Z"),
                Instant.parse("2027-01-01T00:14:00Z"), "s", "", List.of());

        assertThat(ics)
                .contains("DTSTART:20261231T235900Z")
                .contains("DTEND:20270101T001400Z");
    }

    @Test
    void escapesCommaSemicolonBackslashAndNewlineInTextValues() {
        String ics = writeOneEvent("uid", Instant.EPOCH, Instant.EPOCH,
                "a,b;c\\d\ne", "", List.of());

        // Backslash escaped first, so c\d becomes c\\d, then the newline becomes \n.
        assertThat(ics).contains("SUMMARY:a\\,b\\;c\\\\d\\ne");
    }

    @Test
    void writesOneValarmPerTriggerRelatedToStart() {
        String ics = writeOneEvent("uid", Instant.EPOCH, Instant.EPOCH, "s", "",
                List.of("-PT48H", "-PT24H", "-PT4H"));

        assertThat(ics)
                .contains("TRIGGER;RELATED=START:-PT48H")
                .contains("TRIGGER;RELATED=START:-PT24H")
                .contains("TRIGGER;RELATED=START:-PT4H");
        assertThat(countOccurrences(ics, "BEGIN:VALARM")).isEqualTo(3);
    }

    @Test
    void omitsDescriptionLineWhenDescriptionIsBlank() {
        String ics = writeOneEvent("uid", Instant.EPOCH, Instant.EPOCH, "Summary", "", List.of());

        assertThat(ics).doesNotContain("\r\nDESCRIPTION:\r\n");
    }

    @Test
    void foldsLinesLongerThan75OctetsAndUnfoldingRestoresTheValue() {
        String longSummary = "X".repeat(200);

        String ics = writeOneEvent("uid", Instant.EPOCH, Instant.EPOCH, longSummary, "", List.of());

        for (String physicalLine : ics.split("\r\n", -1)) {
            assertThat(physicalLine.getBytes(StandardCharsets.UTF_8).length)
                    .as("no physical line may exceed 75 octets: <%s>", physicalLine)
                    .isLessThanOrEqualTo(75);
        }
        // Unfolding (CRLF + leading space) must restore the original SUMMARY exactly.
        assertThat(unfold(ics)).contains("SUMMARY:" + longSummary);
    }

    @Test
    void foldingNeverSplitsAMultiByteCharacter() {
        // Each 😀 is 4 UTF-8 octets; 40 of them = 160 octets, forcing several folds.
        String emojiSummary = "😀".repeat(40);

        String ics = writeOneEvent("uid", Instant.EPOCH, Instant.EPOCH, emojiSummary, "", List.of());

        for (String physicalLine : ics.split("\r\n", -1)) {
            assertThat(physicalLine.getBytes(StandardCharsets.UTF_8).length)
                    .isLessThanOrEqualTo(75);
        }
        // If any fold had split an emoji, the reassembled summary would be corrupted.
        assertThat(unfold(ics)).contains("SUMMARY:" + emojiSummary);
    }

    private String writeOneEvent(String uid, Instant start, Instant end, String summary,
                                 String description, List<String> alarms) {
        return writer.write("cal", Instant.EPOCH,
                List.of(new ICalEvent(uid, start, end, summary, description, alarms)));
    }

    private static String unfold(String ics) {
        return ics.replace("\r\n ", "");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) != -1) {
            count++;
            from += needle.length();
        }
        return count;
    }
}
