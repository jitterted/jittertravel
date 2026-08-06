package dev.ted.jittertravel.web;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import dev.ted.jittertravel.application.DisplayZone;
import dev.ted.jittertravel.application.GatheringItineraryEntry;
import dev.ted.jittertravel.application.ItineraryDay;
import dev.ted.jittertravel.application.ItineraryEntry;
import dev.ted.jittertravel.application.ViewerZonePolicy;
import dev.ted.jittertravel.application.ZoneDisplay;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for {@link BrowserZoneScript}: the browser-zone upgrade that rewrites each
 * {@code <time data-fmt>} into the viewer's own zone, and the anonymous viewer's toggle between
 * that and entry-local.
 * <p>
 * This behavior only exists once a browser with a real zone runs the script, so no renderer or
 * {@code @WebMvcTest} could reach it: the server always emits entry-local text. Each case pins
 * the browser's zone explicitly — the test JVM is UTC (see {@code pom.xml}), so an unpinned
 * browser would agree with the server by accident and prove nothing.
 * <p>
 * Still no server, Spring, DB or auth: Playwright fulfills the page itself from renderer output.
 * A real origin (rather than {@code setContent}'s {@code about:blank}) is what makes
 * {@code localStorage} and reloads work, which the persistence cases need.
 */
class BrowserZoneJsTest extends JsBehaviorTest {

    private static final String PAGE_URL = "https://jittertravel.test/itinerary";
    private static final LocalDate JUN_1 = LocalDate.of(2026, 6, 1);
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    // London 6:00-9:00 PM BST is 17:00-20:00Z. On a New York clock that reads 1:00-4:00 PM the
    // same day; on a Tokyo clock, 2:00-5:00 AM the *next* day.
    private static final String ENTRY_LOCAL_START = "6:00 PM";
    private static final String NEW_YORK_START = "1:00 PM";
    private static final String TOKYO_START = "2:00 AM";

    private final ViewerZonePolicy viewerZonePolicy = new ViewerZonePolicy();

    @Test
    void familyViewerSeesTimesRewrittenIntoTheirOwnBrowserZone() {
        Page newYork = itineraryPage("America/New_York", Viewer.FAMILY);

        newYork.navigate(PAGE_URL);

        assertThat(startTimeOn(newYork))
                .as("the same instant read on a New York clock")
                .isEqualTo(NEW_YORK_START);
    }

    @Test
    void theSameInstantLocalizesDifferentlyInADifferentBrowserZone() {
        // The control for the case above: were the script echoing the server's text rather than
        // localizing, both zones would show 6:00 PM.
        Page tokyo = itineraryPage("Asia/Tokyo", Viewer.FAMILY);

        tokyo.navigate(PAGE_URL);

        assertThat(startTimeOn(tokyo))
                .as("the same instant read on a Tokyo clock, where it falls the next morning")
                .isEqualTo(TOKYO_START);
    }

    @Test
    void ownerPageShipsNoScriptSoTimesStayEntryLocal() {
        Page newYork = itineraryPage("America/New_York", Viewer.OWNER);

        newYork.navigate(PAGE_URL);

        assertThat(startTimeOn(newYork))
                .as("OWNER is the traveler: times stay in the zone the gathering happens in")
                .isEqualTo(ENTRY_LOCAL_START);
        assertThat(newYork.locator(".zone-toggle").count())
                .as("no choice is offered where the role decides the zone")
                .isZero();
    }

    @Test
    void anonymousToggleSwitchesToBrowserZoneAndBackToEntryLocal() {
        Page newYork = itineraryPage("America/New_York", Viewer.ANONYMOUS);
        newYork.navigate(PAGE_URL);
        assertThat(startTimeOn(newYork))
                .as("anonymous viewers start on the entry-local baseline")
                .isEqualTo(ENTRY_LOCAL_START);

        toggle(newYork, DisplayZone.BROWSER).click();

        assertThat(startTimeOn(newYork))
                .as("clicking 'My time' localizes to the viewer's zone")
                .isEqualTo(NEW_YORK_START);
        assertThat(toggle(newYork, DisplayZone.BROWSER).getAttribute("aria-pressed"))
                .isEqualTo("true");

        toggle(newYork, DisplayZone.ENTRY).click();

        assertThat(startTimeOn(newYork))
                .as("toggling back restores exactly the text the server rendered")
                .isEqualTo(ENTRY_LOCAL_START);
        assertThat(toggle(newYork, DisplayZone.ENTRY).getAttribute("aria-pressed"))
                .isEqualTo("true");
    }

    @Test
    void anonymousChoiceSurvivesAReload() {
        Page newYork = itineraryPage("America/New_York", Viewer.ANONYMOUS);
        newYork.navigate(PAGE_URL);
        toggle(newYork, DisplayZone.BROWSER).click();

        newYork.reload();

        assertThat(startTimeOn(newYork))
                .as("the choice is carried into the reload rather than snapping back")
                .isEqualTo(NEW_YORK_START);
    }

    @Test
    void togglingWritesTheChoiceIntoTheUrlSoTheLinkCarriesIt() {
        // Copying the address bar after toggling has to hand the recipient the same view;
        // localStorage is this browser's alone and travels with nobody.
        Page newYork = itineraryPage("America/New_York", Viewer.ANONYMOUS);
        newYork.navigate(PAGE_URL);

        toggle(newYork, DisplayZone.BROWSER).click();

        assertThat(newYork.url())
                .contains("tz=browser");
    }

    @Test
    void anonymousChoiceIsRememberedOnAFreshUrlWithNoTzParam() {
        // Navigating on to another page must keep the viewer's choice, even though that URL
        // carries no ?tz= of its own — the stored preference beats the entry-local default.
        Page newYork = itineraryPage("America/New_York", Viewer.ANONYMOUS);
        newYork.navigate(PAGE_URL);
        toggle(newYork, DisplayZone.BROWSER).click();

        newYork.navigate(PAGE_URL + "?date=2026-06-02");

        assertThat(startTimeOn(newYork))
                .isEqualTo(NEW_YORK_START);
    }

    @Test
    void explicitTzParamBeatsTheRememberedChoice() {
        // A shared link carrying ?tz=entry must show what the sender saw, not what this browser
        // last picked, so an explicit parameter wins over the stored preference.
        Page newYork = itineraryPage("America/New_York", Viewer.ANONYMOUS);
        newYork.navigate(PAGE_URL);
        toggle(newYork, DisplayZone.BROWSER).click();

        newYork.navigate(PAGE_URL + "?tz=entry");

        assertThat(startTimeOn(newYork))
                .isEqualTo(ENTRY_LOCAL_START);
    }

    private enum Viewer {
        OWNER(true, false),
        FAMILY(false, true),
        ANONYMOUS(false, false);

        private final boolean isOwner;
        private final boolean isFamily;

        Viewer(boolean isOwner, boolean isFamily) {
            this.isOwner = isOwner;
            this.isFamily = isFamily;
        }
    }

    /**
     * A page that answers any request with the itinerary as this viewer would receive it,
     * reading {@code ?tz=} out of the requested URL exactly as {@code ItineraryController} does.
     */
    private Page itineraryPage(String timezoneId, Viewer viewer) {
        Page page = pageInZone(timezoneId);
        page.route("**/*", route -> {
            if (route.request().url().endsWith(".css")) {
                route.fulfill(new Route.FulfillOptions().setContentType("text/css").setBody(""));
                return;
            }
            ZoneDisplay zoneDisplay = viewerZonePolicy.forViewer(
                    viewer.isOwner, viewer.isFamily, tzParamOf(route.request().url()));
            route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody(itineraryHtml(zoneDisplay)));
        });
        return page;
    }

    private static String tzParamOf(String url) {
        int index = url.indexOf("tz=");
        if (index < 0) {
            return null;
        }
        String value = url.substring(index + 3);
        int end = value.indexOf('&');
        return end < 0 ? value : value.substring(0, end);
    }

    private static Locator toggle(Page page, DisplayZone choice) {
        return page.locator(".zone-toggle button[data-zone-choice='" + choice.paramValue() + "']");
    }

    /** The gathering's start time — the first {@code <time>} inside the gathering card. */
    private static String startTimeOn(Page page) {
        return page.locator(".entry-card--gathering time").first().textContent();
    }

    private static String itineraryHtml(ZoneDisplay zoneDisplay) {
        ItineraryEntry gathering = new GatheringItineraryEntry(
                "London Java Community", "Skills Matter", "London", "GB", false, "",
                ZonedTimestamp.fromLocal(JUN_1.atTime(18, 0), LONDON),
                ZonedTimestamp.fromLocal(JUN_1.atTime(21, 0), LONDON));
        List<ItineraryDay> days = List.of(
                new ItineraryDay(JUN_1, List.of(gathering)),
                new ItineraryDay(JUN_1.plusDays(1), List.of()),
                new ItineraryDay(JUN_1.plusDays(2), List.of()));
        return ItineraryRenderer.render(days, JUN_1.minusDays(1), JUN_1.plusDays(1), JUN_1,
                                        false, zoneDisplay);
    }
}
