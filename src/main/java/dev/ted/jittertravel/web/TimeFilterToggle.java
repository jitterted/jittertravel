package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TimeView;
import j2html.tags.DomContent;

import static j2html.TagCreator.a;
import static j2html.TagCreator.div;

/**
 * Shared "Upcoming / All" toggle for list views. Renders two links back to the
 * same page with {@code ?filter=future} / {@code ?filter=all}, marking the
 * active one. Styling lives in {@code site.css} ({@code .time-toggle}) so every
 * view shares one source.
 * <p>
 * A new list view gets the toggle by calling
 * {@code TimeFilterToggle.render("/its-path", activeFilter)} in its renderer.
 */
public final class TimeFilterToggle {

    private TimeFilterToggle() {
    }

    public static DomContent render(String basePath, TimeView active) {
        return div().withClass("time-toggle").with(
                toggleLink("Upcoming", basePath + "?filter=future", active == TimeView.FUTURE),
                toggleLink("All", basePath + "?filter=all", active == TimeView.ALL)
        );
    }

    private static DomContent toggleLink(String label, String href, boolean active) {
        return active
                ? a(label).withHref(href).withClass("active")
                : a(label).withHref(href);
    }
}