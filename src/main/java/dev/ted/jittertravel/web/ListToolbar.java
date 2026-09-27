package dev.ted.jittertravel.web;

import j2html.tags.DomContent;

import static j2html.TagCreator.a;
import static j2html.TagCreator.div;

/**
 * The row under a list page's heading: its filters on the left, and the page's one create action
 * ("Book another hotel", "Plan another gathering") at the right edge.
 * <p>
 * <strong>The create action lives up here, never under the list</strong> (Ted, 2026-08-22 for
 * {@code /conferences}, every list page 2026-09-27). At the bottom it was reachable only by
 * scrolling past every row, and it grew further away the more rows there were — the one control
 * on the page whose distance depended on the data. Up here it sits at the same place on every list
 * page.
 * <p>
 * The page adds {@link #CSS} to its own stylesheet rather than this living in {@code site.css},
 * which every page loads, the anonymous calendar included.
 */
public final class ListToolbar {

    static final String CSS = """
            /* The row owns the gap under the heading. .time-toggle's own top margin is cancelled
               here, because a flex item's margin does not collapse and the two would add up. */
            .list-toolbar {
                display: flex; flex-wrap: wrap; gap: 1rem; align-items: center; margin-top: 1rem;
            }
            .list-toolbar .time-toggle { margin-top: 0; }
            /* margin-left: auto is what holds it at the right edge, with no spacer element and no
               change to the controls before it. When the row wraps on a narrow viewport it keeps
               the right edge of whatever line it lands on, which is where the eye looks for it
               either way. Padding matches .time-toggle's so they line up at one height.
               Filled green (Ted, 2026-08-22): everything else on the toolbar answers "which of
               these am I looking at?", and this one leaves the page entirely. Outlined in the
               accent colour it read as another filter, because the active time segment is
               accent-filled. A different hue, and solid rather than outlined, is what separates a
               create action from a filter — and green is not a colour this app's palette uses for
               anything else, so it carries no meaning it would have to fight. The border matches
               the fill so the box stays exactly .time-toggle's size.
               #1e7a1e rather than CSS `forestgreen` (#228B22), which this started as: white on
               forestgreen is 4.4:1, just under WCAG AA's 4.5:1 for text this size. Two shades
               darker is visually the same green and clears it at 5.4:1. Bold for the same reason —
               weight is the other half of legibility on a filled control. */
            .create-link {
                margin-left: auto;
                display: inline-flex; align-items: center;
                padding: 6px 16px; font-size: 0.875rem; font-weight: 700;
                border: 1px solid #1e7a1e; border-radius: 6px;
                color: #fff; background: #1e7a1e;
                text-decoration: none; white-space: nowrap;
            }
            /* Darker on hover rather than lighter: a lighter fill would walk the white text back
               under the contrast floor the colour was chosen to clear. */
            .create-link:hover { background: #1b6f1b; border-color: #1b6f1b; }
            """;

    private ListToolbar() {
    }

    /**
     * @param filters the page's filters, left to right — the shared {@link TimeFilterToggle} first
     */
    public static DomContent render(String createLabel, String createHref, DomContent... filters) {
        return div().withClass("list-toolbar").with(filters)
                    .with(a(createLabel).withClass("create-link").withHref(createHref));
    }
}
