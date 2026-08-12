package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import dev.ted.jittertravel.application.SubtitleLine;
import dev.ted.jittertravel.application.ViewerZonePolicy;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
@Import({SecurityConfig.class, ViewerZonePolicy.class})
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
    void anonymousUserDoesNotSeeItineraryLinks() {
        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("href=\"/itinerary");
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

    @Test
    @WithMockUser(username = "family", roles = "FAMILY")
    void authenticatedUserSeesItineraryLinks() {
        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .contains("href=\"/itinerary");
    }

    // The owner day-menu only renders on strictly-future days. The controller reads the real
    // clock, so these three tests use a far-future entry to keep the future-day path live
    // regardless of when the suite runs.
    private static final LocalDateTime FUTURE_CHECK_IN = LocalDateTime.of(2099, 7, 1, 15, 0);
    private static final LocalDateTime FUTURE_CHECK_OUT = LocalDateTime.of(2099, 7, 3, 11, 0);

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
                .contains("href=\"/book-flight?date=2099-");
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
