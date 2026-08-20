package dev.ted.jittertravel.web;

import j2html.tags.DomContent;

import static j2html.TagCreator.a;
import static j2html.TagCreator.div;

/**
 * The "List / Calendar" selector at the top of the schedule-problems report. Renders two links
 * back to the same page with {@code ?view=list} / {@code ?view=calendar}, marking the active one.
 * <p>
 * Deliberately <em>not</em> {@link TimeFilterToggle}: that one renders a {@code TimeView} and
 * writes {@code ?filter=}, a different parameter answering a different question. Only the styling
 * is shared — {@code site.css} groups {@code .view-toggle} with {@code .time-toggle}, so the two
 * controls cannot drift apart visually.
 */
public final class ProblemViewToggle {

    private static final String BASE_PATH = "/schedule-problems";

    private ProblemViewToggle() {
    }

    public static DomContent render(ProblemView active) {
        return div().withClass("view-toggle").with(
                viewLink("List", ProblemView.LIST, active),
                viewLink("Calendar", ProblemView.CALENDAR, active)
        );
    }

    private static DomContent viewLink(String label, ProblemView view, ProblemView active) {
        String href = BASE_PATH + "?view=" + view.param();
        return view == active
                ? a(label).withHref(href).withClass("active")
                : a(label).withHref(href);
    }
}
