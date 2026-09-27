package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import static j2html.TagCreator.div;
import static org.assertj.core.api.Assertions.assertThat;

class ListToolbarTest {

    /**
     * The filters come first and the create action last, so {@code margin-left: auto} on it is
     * what holds it at the right edge without a spacer element.
     */
    @Test
    void filtersComeFirstAndTheCreateLinkLast() {
        String html = ListToolbar.render("Book another hotel", "/book-hotel",
                                         div("first").withClass("one"),
                                         div("second").withClass("two"))
                                 .render();

        assertThat(html)
                .isEqualTo("<div class=\"list-toolbar\">"
                           + "<div class=\"one\">first</div>"
                           + "<div class=\"two\">second</div>"
                           + "<a class=\"create-link\" href=\"/book-hotel\">Book another hotel</a>"
                           + "</div>");
    }

    /**
     * The row owns the gap under the heading. The shared toggle's top margin must be cancelled — a
     * flex item's margin does not collapse, so the two stacked into empty space under the title.
     */
    @Test
    void theToolbarCancelsTheTogglesOwnTopMargin() {
        assertThat(ListToolbar.CSS)
                .contains(".list-toolbar .time-toggle { margin-top: 0; }");
    }

    @Test
    void theCreateLinkIsHeldAtTheRightEdge() {
        assertThat(ListToolbar.CSS)
                .contains(".create-link {\n    margin-left: auto;");
    }

    /**
     * Filled and a different hue from the filters beside it (Ted, 2026-08-22): outlined in the
     * accent colour it read as another filter, since the active time segment is accent-filled.
     * <p>
     * The border matching the fill is load-bearing, not decoration: it keeps the box exactly
     * {@code .time-toggle}'s size, so the controls stay one height.
     * <p>
     * <strong>The exact green is the claim, not just "green".</strong> CSS {@code forestgreen}
     * (#228B22) is 4.4:1 against white — under WCAG AA's 4.5:1 for text this size — and #1e7a1e is
     * the visually-identical shade that clears it at 5.4:1. A test asserting only that the fill is
     * some green would let that regress silently, which is the whole reason the value changed.
     */
    @Test
    void theCreateLinkIsFilledGreenRatherThanOutlinedInTheAccentColour() {
        assertThat(ListToolbar.CSS)
                .contains("padding: 6px 16px; font-size: 0.875rem; font-weight: 700;")
                .contains("border: 1px solid #1e7a1e; border-radius: 6px;")
                .contains("color: #fff; background: #1e7a1e;")
                .contains(".create-link:hover { background: #1b6f1b; border-color: #1b6f1b; }")
                // The shade that does not clear AA, in both places it could appear. Pinned as
                // whole declarations: the stylesheet's own comment explains why it went, so a
                // bare doesNotContain("forestgreen") would fail for the wrong reason.
                .doesNotContain("background: forestgreen;")
                .doesNotContain("solid forestgreen;")
                // The outlined-accent styling it replaced, which made it read as a filter.
                .doesNotContain("border: 1px solid var(--accent-color); border-radius: 6px;");
    }
}
