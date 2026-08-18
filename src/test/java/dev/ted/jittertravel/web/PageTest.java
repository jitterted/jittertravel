package dev.ted.jittertravel.web;

import dev.ted.jittertravel.web.Page.NavAudience;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageTest {

    @Test
    void ownerNavLinksToEveryViewPage() {
        String nav = Page.viewNav(NavAudience.OWNER, "/booked-hotels").render();

        assertThat(nav)
                .contains("href=\"/\"")
                .contains("href=\"/itinerary\"")
                .contains("href=\"/calendar\"")
                .contains("href=\"/booked-flights\"")
                .contains("href=\"/booked-trains\"")
                .contains("href=\"/planned-gatherings\"")
                .contains("href=\"/tentative-conferences\"")
                // Always present: the bar reflects the viewer's tier, not what the
                // linked pages contain. An empty report is the report page's problem.
                .contains("href=\"/schedule-problems\"");
    }

    @Test
    void scheduleProblemsPageShowsItsOwnActiveMarker() {
        String nav = Page.viewNav(NavAudience.OWNER, "/schedule-problems").render();

        assertThat(nav)
                .contains("<span class=\"active\" aria-current=\"page\">Schedule Problems</span>")
                .doesNotContain("href=\"/schedule-problems\"");
    }

    @Test
    void currentPageRendersAsNonLinkActiveSpanNotAHref() {
        String nav = Page.viewNav(NavAudience.OWNER, "/booked-hotels").render();

        assertThat(nav)
                // The active page is a span carrying aria-current, never a self-link.
                .contains("<span class=\"active\" aria-current=\"page\">Hotels</span>")
                .doesNotContain("href=\"/booked-hotels\"");
    }

    @Test
    void familyNavHasOnlyItineraryAndCalendarNotOwnerViews() {
        String nav = Page.viewNav(NavAudience.FAMILY, "/itinerary").render();

        assertThat(nav)
                .contains("href=\"/\"")
                .contains("href=\"/calendar\"")
                // On the itinerary page itself the itinerary link is the active span.
                .contains("<span class=\"active\" aria-current=\"page\">Itinerary</span>")
                // Family cannot reach the booking/planning views — never link to them,
                // Schedule Problems included.
                .doesNotContain("/booked-flights")
                .doesNotContain("/booked-trains")
                .doesNotContain("/booked-hotels")
                .doesNotContain("/planned-gatherings")
                .doesNotContain("/tentative-conferences")
                .doesNotContain("/schedule-problems");
    }

    @Test
    void anonymousNavHasOnlyTheHomeLink() {
        String nav = Page.viewNav(NavAudience.ANONYMOUS, "/calendar").render();

        assertThat(nav)
                .contains("href=\"/\"")
                .contains("JitterTravel")
                // Nothing that reveals an owner/family surface exists.
                .doesNotContain("href=\"/itinerary\"")
                .doesNotContain("/booked-")
                .doesNotContain("/planned-")
                .doesNotContain("/tentative-")
                .doesNotContain("/schedule-problems");
    }

    @Test
    void navAudienceIsDerivedFromViewerFlags() {
        assertThat(NavAudience.of(true, false)).isEqualTo(NavAudience.ANONYMOUS);
        // isOwner is meaningless when public; public always wins.
        assertThat(NavAudience.of(true, true)).isEqualTo(NavAudience.ANONYMOUS);
        assertThat(NavAudience.of(false, false)).isEqualTo(NavAudience.FAMILY);
        assertThat(NavAudience.of(false, true)).isEqualTo(NavAudience.OWNER);
    }
}
