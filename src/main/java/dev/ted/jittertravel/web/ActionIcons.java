package dev.ted.jittertravel.web;

import j2html.tags.DomContent;

import static j2html.TagCreator.a;
import static j2html.TagCreator.rawHtml;

/**
 * The two action icons, drawn once for every page that offers them. A pencil means edit and nothing
 * else; a bin means cancel or remove (CLAUDE.md, "an icon means one thing, app-wide"). An icon that
 * means one thing everywhere has one source: the glyphs were hand-kept in three renderers, which is
 * how a pencil comes to look or behave differently on one page. Their CSS is in {@code site.css}
 * for the same reason.
 * <p>
 * Both stroke in {@code currentColor}, at one weight, so they pick up each entry kind's tint and
 * read as a pair in the same slot.
 */
final class ActionIcons {

    private static final String PENCIL_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M12 20h9\"/><path d=\"M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z\"/></svg>";

    private static final String TRASH_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M3 6h18\"/><path d=\"M8 6V4h8v2\"/><path d=\"M19 6l-1 14H6L5 6\"/><path d=\"M10 11v6\"/><path d=\"M14 11v6\"/></svg>";

    private ActionIcons() {
    }

    /** A pencil linking to where {@code label} says the thing is edited. */
    static DomContent editPencil(String href, String label) {
        return a(rawHtml(PENCIL_SVG)).withClass("edit-pencil").withHref(href).withTitle(label);
    }

    /** A bin linking to where {@code label} says the thing is cancelled or removed. */
    static DomContent cancelBin(String href, String label) {
        return a(rawHtml(TRASH_SVG)).withClass("cancel-bin").withHref(href).withTitle(label);
    }
}
