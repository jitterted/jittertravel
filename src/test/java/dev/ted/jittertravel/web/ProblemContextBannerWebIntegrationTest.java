package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GroundTransferEndpointChoices;
import dev.ted.jittertravel.application.GroundTransferEndpointOptions;
import dev.ted.jittertravel.application.GroundTransferPlanning;
import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.application.ScheduleContext;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.application.TransferEndpointOption;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * The banner through the real thing: {@link ProblemContextAdvice} picked up as a
 * {@code @ControllerAdvice}, the reference resolved against the report, and the shared fragment
 * rendered by Thymeleaf on the page the fix link lands on.
 * <p>
 * Two controllers, because the fragment has to work on more than the page it was written against —
 * the rest are held by {@link ProblemContextFragmentConventionTest}, which cannot render anything.
 */
@WebMvcTest({BookHotelController.class, PlanGroundTransferController.class})
@WithMockUser(roles = "OWNER")
class ProblemContextBannerWebIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static final ScheduleProblem.MissingHotel MISSING_BED = new ScheduleProblem.MissingHotel(
            "Denver", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18), "dev2next");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    HotelBooking hotelBooking;

    @MockitoBean
    GroundTransferPlanning groundTransferPlanning;

    @MockitoBean
    GroundTransferEndpointOptions groundTransferEndpointOptions;

    @MockitoBean
    ScheduleGapProjector scheduleGapProjector;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneId.of("UTC"));
        given(groundTransferEndpointOptions.choicesAt(any()))
                .willReturn(new GroundTransferEndpointChoices(
                        List.of(), List.of(), List.of(), List.of(),
                        List.of(new TransferEndpointOption("hotel:seminarzentrum",
                                "SeminarZentrum Rückersbach — Johannesberg · check out Sun Sep 13, 11:00 AM",
                                "Johannesberg", "2026-09-13", "11:00")),
                        List.of(new TransferEndpointOption("hotel:holiday-inn",
                                "Holiday Inn Frankfurt - Alte Oper — Frankfurt · check in Sun Sep 13, 3:00 PM",
                                "Frankfurt", "2026-09-13", "15:00"))));
    }

    @Test
    void aFixLinkFromTheReportSaysWhyTedIsOnTheForm() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(MISSING_BED));
        given(scheduleGapProjector.context()).willReturn(List.of(
                new ScheduleContext.Conference("dev2next", "Denver",
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18))));

        assertThat(fixLink("/book-hotel", FixOrigin.PROBLEM_CALENDAR).param("city", "Denver"))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"problem-context problem-context--bed\">")
                .contains("<div class=\"problem-context-title\">No hotel — Denver</div>")
                .contains("<div class=\"problem-context-detail\">4 nights — dev2next</div>")
                .contains("<li>dev2next, Denver · Sep 14–18</li>")
                .contains("href=\"/schedule-problems?view=calendar\"");
    }

    /** Same banner, different form: the fragment is shared, not written per page. */
    @Test
    void theSameBannerRendersOnTheGroundTransferForm() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(MISSING_BED));
        given(scheduleGapProjector.context()).willReturn(List.of());

        assertThat(fixLink("/plan-ground-transfer", FixOrigin.ITINERARY).param("date", "2026-09-14"))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"problem-context-title\">No hotel — Denver</div>")
                .contains("href=\"/itinerary\"");
    }

    /**
     * The point of the whole "why are you here" chain, on Ted's own gap (2026-08-21): the fix link
     * lands on a form that has already chosen both ends. The banner says which gap, and the selects
     * say which two places — no reading two lists and matching them back by city.
     */
    @Test
    void aTravelGapOpensTheTransferFormOnBothOfItsEnds() {
        ScheduleProblem.MissingTravel gap = new ScheduleProblem.MissingTravel(
                "Johannesberg", ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-09T17:00"), BERLIN),
                "Frankfurt", ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-13T15:00"), BERLIN));
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(gap));
        given(scheduleGapProjector.context()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/plan-ground-transfer")
                .param("problem", ProblemRef.of(gap).key())
                .param("from", "calendar"))
                .hasStatusOk()
                .bodyText()
                // The opening tag sits on the line above in the template, so each assertion pins
                // the attributes to the label they belong to.
                .contains("data-date=\"2026-09-13\" data-time=\"11:00\" selected=\"selected\">"
                          + "SeminarZentrum Rückersbach — Johannesberg · check out Sun Sep 13, 11:00 AM</option>")
                .contains("data-date=\"2026-09-13\" data-time=\"15:00\" selected=\"selected\">"
                          + "Holiday Inn Frankfurt - Alte Oper — Frankfurt · check in Sun Sep 13, 3:00 PM</option>")
                .as("and the moments that go with them")
                .contains("id=\"date\" name=\"date\" value=\"2026-09-13\"")
                .contains("id=\"departureTime\" name=\"departureTime\" value=\"11:00\"")
                .contains("id=\"arrivalTime\" name=\"arrivalTime\" value=\"15:00\"");
    }

    /**
     * The whole error path. A hand-edited key, a bookmark from yesterday, a problem fixed in
     * another tab — the form is the form we shipped before the banner existed.
     */
    @Test
    void aKeyThatMatchesNothingLeavesTheFormExactlyAsItWas() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(MISSING_BED));

        assertThat(mockMvc.get().uri("/book-hotel")
                .param("city", "Denver")
                .param("problem", "hotel|Atlantis|2026-09-14|2026-09-18")
                .param("from", "list"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("class=\"problem-context")
                .contains("<h1>Book Hotel</h1>");
    }

    /**
     * The whole round trip, on the href the report actually renders: {@link ProblemFix} builds it,
     * percent-encoding and all, and the page it points at explains itself. The URI goes in already
     * encoded, so nothing here re-encodes what the link already escaped.
     */
    @Test
    void theHrefTheReportRendersResolvesToItsOwnBanner() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(MISSING_BED));
        given(scheduleGapProjector.context()).willReturn(List.of());
        String href = ProblemFix.forProblem(MISSING_BED, FixOrigin.PROBLEM_LIST).getFirst().href();

        assertThat(href).contains("&problem=hotel%7CDenver%7C2026-09-14%7C2026-09-18&from=list");
        assertThat(mockMvc.get().uri(URI.create(href)))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"problem-context-title\">No hotel — Denver</div>")
                .contains("href=\"/schedule-problems?view=list\"");
    }

    /** Reached from the index nav card, as every one of these forms still is. */
    @Test
    void aFormReachedWithoutAReferenceShowsNoBanner() {
        assertThat(mockMvc.get().uri("/book-hotel"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("class=\"problem-context")
                .contains("<h1>Book Hotel</h1>");
    }

    /**
     * A GET carrying the reference and the origin the fix link puts on it. The parameters go on as
     * values rather than as a hand-encoded query string: percent-encoding is
     * {@link ProblemFixTest}'s claim, and what is under test here is that the reference resolves.
     */
    private MockMvcRequestBuilder fixLink(String path, FixOrigin origin) {
        return mockMvc.get().uri(path)
                .param("problem", ProblemRef.of(MISSING_BED).key())
                .param("from", origin.param());
    }
}
