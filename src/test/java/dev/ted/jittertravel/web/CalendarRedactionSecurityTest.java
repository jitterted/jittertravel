package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import dev.ted.jittertravel.application.SubtitleLine;
import dev.ted.jittertravel.application.ViewerZonePolicy;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/**
 * Verifies that the calendar applies redaction for anonymous users and shows
 * full details for authenticated users. Unlike the standard @WebMvcTest slice
 * tests, these assert on response body content because the behavior under test
 * is security-driven (which code path the controller takes), not rendering.
 */
// The secured chain is the only chain, active by default — exactly the production security
// path this test exercises.
@WebMvcTest(CalendarController.class)
@Import({SecurityConfig.class, ViewerZonePolicy.class, WebTodayTestConfig.class})
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass"})
class CalendarRedactionSecurityTest {

    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 7, 1, 15, 0);
    private static final LocalDateTime CHECK_OUT = LocalDateTime.of(2026, 7, 3, 11, 0);
    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 7, 5, 9, 0);
    private static final LocalDateTime ARRIVAL = LocalDateTime.of(2026, 7, 5, 17, 0);

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    CalendarAggregator calendarAggregator;

    @BeforeEach
    void setUp() {
        given(calendarAggregator.allEntries()).willReturn(List.of(new CalendarEntry(
                EntryKind.LODGING, CHECK_IN, CHECK_OUT,
                "Grand Hotel", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "Grand Hotel cont'd", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "https://maps.google.com/grand-hotel"
        )));
    }

    @Test
    @WithMockUser(username = "ted", roles = "OWNER")
    void tedSeesFullHotelName() {
        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText().contains("Grand Hotel");
    }

    @Test
    @WithMockUser(username = "family", roles = "FAMILY")
    void familySeesFullHotelName() {
        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText().contains("Grand Hotel");
    }

    @Test
    void anonymousUserSeesRedactedHotel() {
        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("Hotel")
                .doesNotContain("Grand Hotel");
    }

    @Test
    void anonymousUserDoesNotSeeHotelEditLink() {
        // Hotels now carry an OWNER-only editPath; redaction must drop it so the anonymous
        // calendar never exposes the deep link to the booking's edit page.
        given(calendarAggregator.allEntries()).willReturn(List.of(new CalendarEntry(
                EntryKind.LODGING, CHECK_IN, CHECK_OUT,
                "Grand Hotel", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "Grand Hotel cont'd", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "https://maps.google.com/grand-hotel",
                "/booked-hotels/abc"
        )));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                // The href is the secret — the `.edit-pencil` CSS class is inlined on every
                // calendar page, so assert on the deep link itself, not the class name.
                .doesNotContain("/booked-hotels/");
    }

    @Test
    void anonymousUserDoesNotSeeItineraryLinks() {
        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("href=\"/itinerary");
    }

    @Test
    void anonymousCalendarNavExposesNoOwnerOrFamilySurfaces() {
        // The shared view-nav collapses to the home link only for anonymous viewers —
        // it must never link to a page an anonymous visitor would 403 on (which would
        // also reveal the page exists).
        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("href=\"/itinerary")
                .doesNotContain("href=\"/booked-flights")
                .doesNotContain("href=\"/booked-trains")
                .doesNotContain("href=\"/booked-hotels")
                .doesNotContain("href=\"/planned-gatherings")
                .doesNotContain("href=\"/tentative-conferences")
                .doesNotContain("href=\"/schedule-problems");
    }

    @Test
    @WithMockUser(username = "ted", roles = "OWNER")
    void ownerCalendarNavLinksToTheOtherViews() {
        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .contains("href=\"/booked-flights")
                .contains("href=\"/booked-hotels");
    }

    @Test
    @WithMockUser(username = "ted", roles = "OWNER")
    void ownerCalendarNavShowsScheduleProblemsLink() {
        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .contains("href=\"/schedule-problems");
    }

    @Test
    void anonymousUserSeesFlightRouteWithoutTimesOrMapsUrl() {
        given(calendarAggregator.allEntries()).willReturn(List.of(new CalendarEntry(
                EntryKind.FLIGHT, DEPARTURE, ARRIVAL,
                "✈️ SFO→JFK", List.of(new SubtitleLine.Text("9:00 AM → 5:00 PM")),
                null, null,
                "https://maps.google.com/sfo-terminal-2",
                "/booked-flights/abc"
        )));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("SFO")
                .doesNotContain("maps.google.com")
                .doesNotContain("9:00 AM")
                .doesNotContain("/booked-flights/abc");
    }

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final LocalDateTime PE_DATE = LocalDateTime.of(2026, 7, 10, 19, 0);

    @Test
    @WithMockUser(username = "ted", roles = "OWNER")
    void ownerSeesFullPrivateEventDetail() {
        given(calendarAggregator.allEntries()).willReturn(List.of(privateEvent()));

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .contains("Dinner with the Smiths")
                .contains("Alo");
    }

    @Test
    void anonymousUserSeesBusyWithoutTitleOrVenue() {
        given(calendarAggregator.allEntries()).willReturn(List.of(privateEvent()));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                // Public by decision: "Busy", the city, and the time in the event's own zone.
                .contains("Busy")
                .contains("Toronto, Canada")
                .contains("7:00 PM")
                .contains("EDT")
                // Private: the title, the venue, and any owner edit link never reach anonymous eyes.
                .doesNotContain("Dinner with the Smiths")
                .doesNotContain("Alo")
                .doesNotContain("/planned-private-events");
    }

    private static CalendarEntry privateEvent() {
        return new CalendarEntry(
                EntryKind.PRIVATE_EVENT, PE_DATE, PE_DATE.plusHours(3),
                "Dinner with the Smiths", List.of(
                        new SubtitleLine.Text("Alo"),
                        new SubtitleLine.Text("Toronto, Canada"),
                        new SubtitleLine.Range(
                                ZonedTimestamp.fromLocal(PE_DATE, TORONTO),
                                ZonedTimestamp.fromLocal(PE_DATE.plusHours(3), TORONTO))),
                null, null, null,
                "/planned-private-events/abc");
    }

    private static final LocalDateTime GATHERING_START = LocalDateTime.of(2026, 7, 5, 18, 0);
    private static final LocalDateTime GATHERING_END = LocalDateTime.of(2026, 7, 5, 21, 0);

    @Test
    void anonymousUserSeesSpeakingBadgeOnPublicGathering() {
        // That Ted is speaking at a gathering is public by decision, so redaction must keep the
        // badge for anonymous viewers — assert it survives the real security chain.
        given(calendarAggregator.allEntries()).willReturn(List.of(new CalendarEntry(
                EntryKind.GATHERING, GATHERING_START, GATHERING_END,
                "London Java Community", List.of(new SubtitleLine.Text("London, GB")),
                null, null, "https://meetup.com/ljc/events/123",
                true, "/planned-gatherings/abc"
        )));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("A Ted Talk")
                // The owner edit link is still never public.
                .doesNotContain("/planned-gatherings/abc");
    }

    @Test
    @WithMockUser(username = "family", roles = "FAMILY")
    void authenticatedUserSeesItineraryLinks() {
        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .contains("href=\"/itinerary");
    }

    // The owner day-menu only renders on strictly-future days. The slice pins the clock (via
    // WebTodayTestConfig) to 2026-06-25, so a few days out is both future and inside the default
    // window — a far-future entry would instead stretch that window across decades.
    private static final LocalDateTime FUTURE_CHECK_IN = LocalDateTime.of(2026, 7, 1, 15, 0);
    private static final LocalDateTime FUTURE_CHECK_OUT = LocalDateTime.of(2026, 7, 3, 11, 0);

    @Test
    @WithMockUser(username = "ted", roles = "OWNER")
    void ownerSeesDatedCreateMenuForFutureDay() {
        given(calendarAggregator.allEntries()).willReturn(List.of(new CalendarEntry(
                EntryKind.LODGING, FUTURE_CHECK_IN, FUTURE_CHECK_OUT,
                "Grand Hotel", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "Grand Hotel cont'd", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "https://maps.google.com/grand-hotel"
        )));

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                // Assert on the actual disclosure markup + dated link, not the ".day-menu"
                // CSS selector (which is inlined on every calendar page regardless of viewer).
                .contains("<details class=\"day-menu\"")
                .contains("href=\"/book-flight?date=2026-07-");
    }

    @Test
    void anonymousUserSeesNoCreateLinksEvenForFutureDays() {
        given(calendarAggregator.allEntries()).willReturn(List.of(new CalendarEntry(
                EntryKind.LODGING, FUTURE_CHECK_IN, FUTURE_CHECK_OUT,
                "Grand Hotel", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "Grand Hotel cont'd", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "https://maps.google.com/grand-hotel"
        )));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("<details class=\"day-menu\"")
                .doesNotContain("href=\"/book-flight")
                .doesNotContain("href=\"/plan-gathering")
                .doesNotContain("href=\"/plan-conference");
    }

    @Test
    @WithMockUser(username = "family", roles = "FAMILY")
    void familyUserSeesNoCreateLinksEvenForFutureDays() {
        given(calendarAggregator.allEntries()).willReturn(List.of(new CalendarEntry(
                EntryKind.LODGING, FUTURE_CHECK_IN, FUTURE_CHECK_OUT,
                "Grand Hotel", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "Grand Hotel cont'd", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "https://maps.google.com/grand-hotel"
        )));

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("<details class=\"day-menu\"")
                .doesNotContain("href=\"/book-flight")
                .doesNotContain("href=\"/plan-gathering");
    }
}
