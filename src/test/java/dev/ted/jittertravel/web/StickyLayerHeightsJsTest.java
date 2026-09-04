package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for {@link StickyLayerHeights}, the inline script that publishes each sticky
 * layer's measured height as a CSS variable.
 * <p>
 * The contract these pin is the one a stylesheet cannot express: the layers' heights depend on the
 * viewport and the font, and the jump anchors' {@code scroll-margin-top} is the sum of them. So what
 * matters is that each published value tracks its real box, and keeps tracking it when the box
 * changes.
 * <p>
 * Note {@code site.css} is not loaded here ({@code page.setContent} runs no server), so the nav is
 * laid out by the browser's own defaults. Deliberate, and it does not weaken these cases: they
 * assert the value <em>equals the element's measured height</em>, not any particular pixel count.
 */
class StickyLayerHeightsJsTest extends JsBehaviorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    private String ownerCalendar() {
        return CalendarRenderer.render(List.of(), TODAY, false, true);
    }

    private String published(String variable) {
        return (String) page.evaluate(
                "name => getComputedStyle(document.documentElement).getPropertyValue(name).trim()",
                variable);
    }

    private int measured(String selector) {
        return ((Number) page.evaluate(
                "selector => document.querySelector(selector).offsetHeight", selector)).intValue();
    }

    @Test
    void publishesTheNavsMeasuredHeightOnLoad() {
        loadRendered(ownerCalendar());

        assertThat(published("--nav-height"))
                .as("the variable anything sticking below the bar reads")
                .isEqualTo(measured("nav.view-nav") + "px");
        assertThat(measured("nav.view-nav"))
                .as("and it is a real box, not a collapsed one")
                .isGreaterThan(0);
    }

    /**
     * The second layer, and the reason the script is not called {@code StickyNavScript} any more.
     * The header is one non-wrapping line, so it looked safe as a {@code 47px} literal — but its
     * height still moves with font size and zoom, and the jump anchors offset by nav + header, so
     * being a few px out lands a jumped-to month underneath the bars.
     */
    @Test
    void publishesTheCalendarWeekdayHeadersMeasuredHeightToo() {
        loadRendered(ownerCalendar());

        assertThat(published("--calendar-weekday-header-height"))
                .isEqualTo(measured(".calendar-header") + "px");
        assertThat(measured(".calendar-header")).isGreaterThan(0);
    }

    /** Both layers feed one number, so it has to be their real sum rather than either estimate. */
    @Test
    void theJumpAnchorsRestingOffsetIsTheSumOfBothMeasuredLayers() {
        loadRendered(ownerCalendar());

        int restingTop = ((Number) page.evaluate(
                "() => parseFloat(getComputedStyle("
                + "document.querySelector('.day-label-cell.is-month-start')).scrollMarginTop)"))
                .intValue();

        assertThat(restingTop).isEqualTo(measured("nav.view-nav") + measured(".calendar-header"));
    }

    /**
     * The case a resize listener alone would get wrong is the bar <em>rewrapping</em>. Narrowing the
     * viewport forces the owner's ten links onto more lines, and the published height has to follow
     * — otherwise the weekday header parks over the bar it was meant to sit under.
     */
    @Test
    void republishesTheHeightWhenTheBarRewrapsOnANarrowerViewport() {
        page.setViewportSize(1400, 900);
        loadRendered(ownerCalendar());
        int wide = measured("nav.view-nav");

        page.setViewportSize(360, 900);
        page.waitForFunction(
                "expected => getComputedStyle(document.documentElement)"
                + ".getPropertyValue('--nav-height').trim() !== expected",
                wide + "px");

        assertThat(measured("nav.view-nav"))
                .as("ten links do not fit on one line at 360px, so the bar is taller")
                .isGreaterThan(wide);
        assertThat(published("--nav-height"))
                .as("and the published value followed it rather than keeping the wide figure")
                .isEqualTo(measured("nav.view-nav") + "px");
    }

    /**
     * An anonymous viewer's bar carries one link, and the script still has to run: the weekday
     * header offsets by {@code --nav-height} for every viewer, so leaving it unset on the public
     * page would park the header under a bar that is there.
     */
    @Test
    void publishesTheHeightForTheAnonymousOneLinkBarToo() {
        loadRendered(CalendarRenderer.render(List.of(), TODAY, true, false));

        assertThat(published("--nav-height")).isEqualTo(measured("nav.view-nav") + "px");
    }

    /**
     * A page with a nav but no calendar. The header's variable is simply not published, so its
     * stylesheet fallback stands — a missing layer must not publish {@code 0px} and collapse an
     * offset somewhere else.
     */
    @Test
    void aLayerThatIsNotOnThePagePublishesNothing() {
        loadRendered("<!DOCTYPE html><html><body>"
                     + Page.viewNav(Page.NavAudience.OWNER, "/booked-hotels").render()
                     + "</body></html>");

        assertThat(published("--nav-height")).isNotEmpty();
        assertThat(published("--calendar-weekday-header-height")).isEmpty();
    }
}
