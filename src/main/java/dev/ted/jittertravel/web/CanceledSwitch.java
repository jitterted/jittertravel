package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelledView;
import dev.ted.jittertravel.application.TimeView;
import j2html.tags.DomContent;

import java.util.Locale;

import static j2html.TagCreator.a;
import static j2html.TagCreator.b;
import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.span;
import static j2html.TagCreator.text;

/**
 * The "Show Canceled …" checkbox on {@code /booked-flights} and {@code /booked-hotels}, a filter in
 * the {@link ListToolbar} row beside the Upcoming/All toggle (Ted, 2026-11-14: one row of filters,
 * not a second bar under it).
 * <p>
 * The same control as the conferences' "Show dropped": the label never changes, only the box does,
 * so it reports what the page is showing rather than what a click would do. A link, so the view
 * stays server-rendered and shareable; {@code aria-pressed} carries the state for both the screen
 * reader and the CSS. The count is how many canceled rows the time filter admits, shown even while
 * they are hidden, since it says what the page is leaving out.
 * <p>
 * "Canceled", one l, in everything a reader sees (Ted, 2026-11-14). The code keeps its existing
 * {@code cancelled} names and the {@code ?cancelled=show} parameter.
 * <p>
 * The page adds {@link #CSS} to its own stylesheet, as with {@link ListToolbar#CSS}.
 */
public final class CanceledSwitch {

    static final String CSS = """
            /* Vertical padding, border and font size match .time-toggle's links (site.css), so
               the two filters sit at one height in the toolbar row. */
            .canceled-toggle {
                display: inline-flex; align-items: center; gap: 7px;
                padding: 6px 12px; border-radius: 6px; font-size: 0.9rem;
                color: var(--text-color); text-decoration: none; white-space: nowrap;
                border: 1px solid var(--border-color); background: var(--surface, #fff);
            }
            .canceled-toggle:hover { border-color: var(--accent-color); }
            .canceled-toggle[aria-pressed="true"] {
                border-color: var(--accent-color); background: #eef2ff;
            }
            .canceled-box {
                width: 14px; height: 14px; border-radius: 3px;
                display: inline-flex; align-items: center; justify-content: center;
                font-size: 10px; line-height: 1; color: #fff;
                border: 1px solid #adb5bd; background: var(--surface, #fff);
            }
            .canceled-toggle[aria-pressed="true"] .canceled-box {
                border-color: var(--accent-color); background: var(--accent-color);
            }
            """;

    private CanceledSwitch() {
    }

    /**
     * @param noun the rows' plural as the label shows it, e.g. {@code "Hotels"}
     */
    public static DomContent render(String basePath, String noun, TimeView activeFilter,
                                    CancelledView activeCancelled, int canceledCount) {
        boolean shown = activeCancelled == CancelledView.SHOW;
        String lowerNoun = noun.toLowerCase(Locale.ENGLISH);
        String filterQuery = "?filter=" + activeFilter.name().toLowerCase(Locale.ENGLISH);
        return a().withClass("canceled-toggle")
                  .attr("role", "button")
                  .attr("aria-pressed", String.valueOf(shown))
                  .withTitle(shown
                          ? "Canceled " + lowerNoun + " are shown. Click to hide them."
                          : "Canceled " + lowerNoun + " are hidden. Click to show them.")
                  .withHref(basePath + filterQuery + (shown ? "" : "&cancelled=show"))
                  .with(shown ? span(rawHtml("&#10003;")).withClass("canceled-box")
                              : span().withClass("canceled-box"),
                        text("Show Canceled " + noun),
                        b(String.valueOf(canceledCount)));
    }

    /** What the time toggle carries through, so changing it never resets this switch. */
    public static String keptQuery(CancelledView activeCancelled) {
        return activeCancelled == CancelledView.SHOW ? "&cancelled=show" : "";
    }
}
