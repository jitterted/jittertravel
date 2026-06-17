package dev.ted.jittertravel.web;

import j2html.TagCreator;
import j2html.tags.DomContent;

import static j2html.TagCreator.*;

/**
 * Shared scaffolding for the standalone HTML documents our j2html renderers
 * produce: the {@code <head>} wiring (charset, page title, the global
 * {@code site.css} link, and a per-page {@code <style>} block) and the common
 * top {@code nav}. Keeps every renderer from repeating the same boilerplate.
 * <p>
 * Chrome styling shared across {@code .page} views (nav, h1, empty-state) lives
 * in {@code site.css}; each renderer's own {@code <style>} carries only the CSS
 * unique to that view (and its {@code .page} max-width).
 */
public final class Page {

    private Page() {
    }

    /**
     * The document {@code <head>}: UTF-8 charset, {@code title}, the shared
     * {@code site.css} stylesheet, and {@code pageCss} inlined as a
     * {@code <style>} block for view-specific rules.
     */
    public static DomContent head(String title, String pageCss) {
        return TagCreator.head(
                meta().withCharset("UTF-8"),
                title(title),
                link().withRel("stylesheet").withHref("/site.css"),
                rawHtml("<style>" + pageCss + "</style>")
        );
    }

    /** The standard {@code JitterTravel · Calendar} breadcrumb nav. */
    public static DomContent navHomeAndCalendar() {
        return nav(
                a("JitterTravel").withHref("/"),
                rawHtml("<span class=\"sep\">&middot;</span>"),
                a("Calendar").withHref("/calendar")
        );
    }
}
