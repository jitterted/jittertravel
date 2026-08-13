package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.ZonedTimestamp;
import j2html.tags.DomContent;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.span;
import static j2html.TagCreator.time;

/**
 * Renders a {@link ZonedTimestamp} as an HTML {@code <time>} element: the element text is the
 * entry-zone wall-clock (the server-side baseline, correct even with no JavaScript) while the
 * {@code datetime} attribute carries the UTC instant and {@code data-fmt} records the display
 * pattern.
 * <p>
 * The {@code datetime}/{@code data-fmt} pair is what a future browser-zone script reads to
 * re-render the same instant in a viewer's zone (see {@code docs/UtcDatetimeStoragePlan.md},
 * phase 4). OWNER-only views ship without that script, so they always show the entry-local text;
 * adopting the element now keeps the markup forward-compatible. Mirrors {@link TimeFilterToggle}
 * as a shared, single-source renderer helper.
 */
public final class ZonedTimeTag {

    private ZonedTimeTag() {
    }

    public static DomContent render(ZonedTimestamp timestamp, String displayPattern) {
        String entryLocalText = DateTimeFormatter.ofPattern(displayPattern, Locale.ENGLISH)
                .format(timestamp.atEntryZone());
        return time(entryLocalText)
                .attr("datetime", timestamp.utc().toString())
                .attr("data-fmt", displayPattern);
    }

    /**
     * Like {@link #render} but splits the date and the time into two {@code .nowrap} spans within
     * the single {@code <time>} element, so a narrow table cell drops the time onto its own line
     * (breaking only <em>between</em> date and time, never mid-value) while a wide cell keeps them
     * on one line. The UTC {@code datetime} and combined {@code data-fmt} are preserved.
     */
    public static DomContent renderDateTimeStacking(ZonedTimestamp timestamp,
                                                    String datePattern, String timePattern) {
        var entryZoned = timestamp.atEntryZone();
        String date = DateTimeFormatter.ofPattern(datePattern, Locale.ENGLISH).format(entryZoned);
        String clock = DateTimeFormatter.ofPattern(timePattern, Locale.ENGLISH).format(entryZoned);
        return time(
                span(date).withClass("nowrap"),
                rawHtml(" "),
                span(clock).withClass("nowrap"))
                .attr("datetime", timestamp.utc().toString())
                .attr("data-fmt", datePattern + " " + timePattern);
    }
}
