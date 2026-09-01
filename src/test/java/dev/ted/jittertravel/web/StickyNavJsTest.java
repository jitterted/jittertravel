package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for {@link StickyNavScript}, the inline script that publishes the sticky nav's
 * rendered height as {@code --nav-height}.
 * <p>
 * The contract these pin is the one a stylesheet cannot express: the bar <em>wraps</em>, so its
 * height depends on the viewport, and the calendar stacks two more sticky layers whose offsets are
 * sums of it. A literal in the CSS would be wrong on exactly the narrow screens hardest to check —
 * so what matters is that the published value tracks the real box, and keeps tracking it when the
 * bar rewraps.
 * <p>
 * Note {@code site.css} is not loaded here ({@code page.setContent} runs no server), so the nav is
 * laid out by the browser's own defaults. That is deliberate and does not weaken these cases: they
 * assert the value <em>equals the element's measured height</em>, whatever that height happens to
 * be, rather than any particular number of pixels.
 */
class StickyNavJsTest extends JsBehaviorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    private String ownerCalendar() {
        return CalendarRenderer.render(List.of(), TODAY, false, true);
    }

    private String navHeight() {
        return (String) page.evaluate(
                "getComputedStyle(document.documentElement).getPropertyValue('--nav-height').trim()");
    }

    private int navOffsetHeight() {
        return ((Number) page.evaluate(
                "document.querySelector('nav.view-nav').offsetHeight")).intValue();
    }

    @Test
    void publishesTheNavsMeasuredHeightOnLoad() {
        loadRendered(ownerCalendar());

        assertThat(navHeight())
                .as("the variable anything sticking below the bar reads")
                .isEqualTo(navOffsetHeight() + "px");
        assertThat(navOffsetHeight())
                .as("and it is a real box, not a collapsed one")
                .isGreaterThan(0);
    }

    /**
     * The case a resize listener alone would get wrong is the bar <em>rewrapping</em>. Narrowing
     * the viewport forces the owner's ten links onto more lines, and the published height has to
     * follow — otherwise the calendar's weekday header parks over the bar it was meant to sit
     * under.
     */
    @Test
    void republishesTheHeightWhenTheBarRewrapsOnANarrowerViewport() {
        page.setViewportSize(1400, 900);
        loadRendered(ownerCalendar());
        int wide = navOffsetHeight();

        page.setViewportSize(360, 900);
        page.waitForFunction(
                "expected => getComputedStyle(document.documentElement)"
                + ".getPropertyValue('--nav-height').trim() !== expected",
                wide + "px");

        assertThat(navOffsetHeight())
                .as("ten links do not fit on one line at 360px, so the bar is taller")
                .isGreaterThan(wide);
        assertThat(navHeight())
                .as("and the published value followed it rather than keeping the wide figure")
                .isEqualTo(navOffsetHeight() + "px");
    }

    /**
     * An anonymous viewer's bar carries one link, and the script still has to run: the calendar's
     * weekday header offsets by this variable for every viewer, so leaving it unset on the public
     * page would park the header under a bar that is there.
     */
    @Test
    void publishesTheHeightForTheAnonymousOneLinkBarToo() {
        loadRendered(CalendarRenderer.render(List.of(), TODAY, true, false));

        assertThat(navHeight()).isEqualTo(navOffsetHeight() + "px");
    }
}
