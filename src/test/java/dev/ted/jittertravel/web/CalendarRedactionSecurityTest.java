package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.CalendarAggregator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryDetails;
import dev.ted.jittertravel.application.PublicCalendarProjector;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.SubtitleLine;
import dev.ted.jittertravel.application.ViewerZonePolicy;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/**
 * Verifies that an anonymous visitor gets the redacted calendar and an authenticated one the full
 * details. Unlike the standard @WebMvcTest slice tests, these assert on response body content,
 * because the behavior under test is security-driven — which code path the controller takes — not
 * rendering.
 * <p>
 * <strong>This test is the primary guard on the public calendar.</strong> With
 * {@code CalendarEntryRedactor} gone there is no compile-time forcing function left downstream of
 * the projectors, so what stands between an anonymous visitor and Ted's travel details is
 * {@code PublicCalendarProjector} plus the cases named here. Extend it whenever a kind is added.
 * <p>
 * Anonymous fixtures are built by driving <strong>real domain events through a real
 * {@link PublicCalendarProjector}</strong> and handing the result to the mocked bean — so the
 * projection logic under test is the production one, not a hand-written entry that could assert
 * whatever the test wished. Owner and family fixtures stay hand-built {@link CalendarEntry}s,
 * because that is what the owner's own projectors produce.
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

    @MockitoBean
    PublicCalendarProjector publicCalendarProjector;

    @MockitoBean
    ScheduleGapProjector scheduleGapProjector;

    @BeforeEach
    void setUp() {
        given(calendarAggregator.allEntries()).willReturn(List.of(new CalendarEntry(
                CHECK_IN, CHECK_OUT,
                "Grand Hotel", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "Grand Hotel cont'd", List.of(new SubtitleLine.Text("Berlin, Germany")),
                new EntryDetails.Lodging("https://maps.google.com/grand-hotel", null)
        )));
        anonymousSees(grandHotelBooked(CHECK_IN, CHECK_OUT));
    }

    /**
     * Point the public projection at these events. The projector is the real one, so what an
     * anonymous request renders is what production would render for the same event stream.
     */
    private void anonymousSees(Event... events) {
        PublicCalendarProjector projector = new PublicCalendarProjector();
        projector.handle(Stream.of(events).map(CalendarRedactionSecurityTest::stored));
        given(publicCalendarProjector.entries()).willReturn(projector.entries());
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }

    private static HotelBooked grandHotelBooked(LocalDateTime checkIn, LocalDateTime checkOut) {
        return new HotelBooked(HotelBookingId.random(), "Grand Hotel",
                new Address("1 Hotel St", "Berlin", "", "10115", "Germany", null),
                ZonedTimestamp.fromLocal(checkIn, BERLIN),
                ZonedTimestamp.fromLocal(checkOut, BERLIN),
                BookingIntent.FINAL, "https://maps.google.com/grand-hotel", null);
    }

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

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
    void anonymousUserSeesTheAwayBand() {
        // The away band is public by decision: it aggregates day-granularity travel facts that
        // are already published, and assembling them by eye takes no effort. This is the case
        // that would go red if the band ever grew a viewer check — see docs/archived/CalendarAwayBandPlan.md.
        given(scheduleGapProjector.awayDays()).willReturn(Set.of(LocalDate.of(2026, 7, 5)));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("<div class=\"day-label-cell month-tint-odd is-away\"><span class=\"day-number\">5</span>");
    }

    @Test
    void anonymousUserDoesNotSeeHotelEditLinkOrMapLink() {
        // The owner's calendar hangs an edit link and a map link off a stay. Neither is built for
        // an anonymous viewer — EntryDetails.PublicLodging has nowhere to put either — and this
        // asserts it through the real chain. The href is the secret: the `.edit-pencil` CSS class
        // is inlined on every calendar page, so assert on the deep link, not the class name.
        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("/booked-hotels/")
                .doesNotContain("maps.google.com");
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
                .doesNotContain("href=\"/conferences")
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
    void anonymousUserSeesFlightRouteWithoutTimesFlightNumberOrEditLink() {
        anonymousSees(new FlightBooked(FlightId.random(), "United", "UA123",
                new AirportCode("SFO"), ZonedTimestamp.fromLocal(DEPARTURE, DENVER),
                new AirportCode("JFK"), ZonedTimestamp.fromLocal(ARRIVAL, DENVER)));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                // Public: the airport codes and the day column.
                .contains("SFO")
                // Private: the times of day, the carrier's identifier for the flight, and the
                // deep link to the booking.
                .doesNotContain("9:00 AM")
                .doesNotContain("UA123")
                .doesNotContain("/booked-flights/");
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
                .contains("Alo")
                // The utensils glyph fronting the title is the owner's half of the pair below.
                .contains("<span class=\"entry-kind-icon\">");
    }

    @Test
    void anonymousUserSeesBusyWithoutTitleOrVenue() {
        anonymousSees(new PrivateEventPlanned(PrivateEventId.random(),
                "Dinner with the Smiths", "Alo",
                new Address("5 Dine Way", "Toronto", "ON", "M5V", "Canada", null),
                ZonedTimestamp.fromLocal(PE_DATE, TORONTO),
                ZonedTimestamp.fromLocal(PE_DATE.plusHours(3), TORONTO)));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                // Public by decision: "Busy", the city, and the time in the event's own zone.
                .contains("Busy")
                .contains("Toronto, Canada")
                .contains("7:00 PM")
                .contains("EDT")
                // Private: the title and the venue. The public projector never reads either, so
                // the words cannot appear however the entry is rendered.
                .doesNotContain("Dinner with the Smiths")
                .doesNotContain("Alo")
                // And private for the same reason: a fork and knife says the block is a MEAL,
                // which is the kind of evening — a fifth thing beyond the four allowed above.
                // The class rule itself is in every page's stylesheet, so assert on the element
                // and on the glyph's own path data, never on the bare class name.
                .doesNotContain("<span class=\"entry-kind-icon\">")
                .doesNotContain("M33.1 0C42");
    }

    private static CalendarEntry privateEvent() {
        return new CalendarEntry(
                PE_DATE, PE_DATE.plusHours(3),
                "Dinner with the Smiths", List.of(
                        new SubtitleLine.Text("Alo"),
                        new SubtitleLine.Text("Toronto, Canada"),
                        new SubtitleLine.Range(
                                ZonedTimestamp.fromLocal(PE_DATE, TORONTO),
                                ZonedTimestamp.fromLocal(PE_DATE.plusHours(3), TORONTO))),
                new EntryDetails.PrivateEvent());
    }

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final LocalDateTime GT_DEPARTS = LocalDateTime.of(2026, 9, 14, 12, 0);
    private static final LocalDateTime GT_ARRIVES = LocalDateTime.of(2026, 9, 14, 12, 45);
    private static final UUID GT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @WithMockUser(username = "ted", roles = "OWNER")
    void ownerSeesTheHotelAtTheEndOfAGroundTransfer() {
        given(calendarAggregator.allEntries()).willReturn(List.of(groundTransfer()));

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .contains("Marriott Lone Tree")
                .contains("12:00 PM")
                .as("the owner reads the title and the times, not the journey spelled out twice")
                .doesNotContain("DEN → Lone Tree, CO, US");
    }

    @Test
    void anonymousUserSeesGroundTransferWithoutTheHotelOrAnyTime() {
        anonymousSees(transferToTheMarriott());

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                // Public: that a hop happened, and each end as a code or a city.
                .contains("\uD83D\uDE95 Ground transfer")
                .contains("DEN → Lone Tree, CO, US")
                // Private: the hotel Ted sleeps in, and the times of day.
                .doesNotContain("Marriott Lone Tree")
                .doesNotContain("12:00 PM")
                .doesNotContain("12:45 PM");
    }

    /**
     * The owner's cancel link is an action on an OWNER-only surface, and the href is the secret: it
     * would tell a stranger that the surface exists and hand them the transfer's internal id. Not a
     * greyed control either — hiding by permission stays hiding (CLAUDE.md).
     */
    @Test
    void anonymousGroundTransferCarriesNoCancelLink() throws Exception {
        anonymousSees(transferToTheMarriott());

        assertThat(mockMvc.get().uri("/calendar").with(anonymous())
                .exchange().getResponse().getContentAsString())
                .doesNotContain("/ground-transfers/")
                .doesNotContain(GT_ID.toString())
                .doesNotContain("cancel-bin\" href");
    }

    /**
     * Redaction rule 2, through the real chain: {@code ZonedTimeTag} writes the UTC instant into a
     * {@code datetime} attribute, so a surviving time leaks in the markup even when nothing visible
     * shows a clock. Asserted on the raw response, not the text, because that is where it would be.
     */
    @Test
    void anonymousGroundTransferMarkupCarriesNoDatetimeInstant() throws Exception {
        anonymousSees(transferToTheMarriott());

        assertThat(mockMvc.get().uri("/calendar").with(anonymous())
                .exchange().getResponse().getContentAsString())
                .doesNotContain("2026-09-14T18:00")
                .doesNotContain("2026-09-14T18:45");
    }

    /**
     * The station half of rule 1, through the real chain. A station name reaches a transfer as of
     * 2026-08-23 (a {@code train:} endpoint puts "Hamburg Hbf" in {@code originName}), and it is
     * private for the same reason a hotel name is: it says where Ted physically was. No rule
     * changed, which is exactly why this is asserted rather than assumed.
     */
    @Test
    void anonymousUserSeesAStationHopWithoutTheStationName() {
        anonymousSees(transferFromHamburgHbf());

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("🚕 Ground transfer")
                .contains("Hamburg, DE → Hamburg, DE")
                .doesNotContain("Hamburg Hbf")
                .doesNotContain("Reichshof");
    }

    /** The station-to-hotel hop, whose owner title reads "Hamburg Hbf → Reichshof". */
    private static GroundTransferPlanned transferFromHamburgHbf() {
        return new GroundTransferPlanned(GroundTransferId.of(GT_ID),
                "", "Hamburg Hbf",
                new Address("", "Hamburg", "", "", "DE", "Hamburg"),
                "", "Reichshof",
                new Address("Kirchenallee 34", "Hamburg", "", "20099", "DE", "Hamburg"),
                ZonedTimestamp.fromLocal(GT_DEPARTS, BERLIN),
                ZonedTimestamp.fromLocal(GT_ARRIVES, BERLIN), "");
    }

    /**
     * Rule 1 again, for the mode: a line name is a service identifier and a driver is a person, so
     * neither may reach the one page a stranger can load. The route around it <em>is</em> public,
     * which is what makes this the interesting case — the mode would leak into a subtitle that
     * already exists rather than needing a new one.
     */
    @Test
    void anonymousUserSeesATransferWithoutHowTedIsGettingThere() {
        anonymousSees(transferToTheMarriott());

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("🚕 Ground transfer")
                .contains("DEN → Lone Tree, CO, US")
                .doesNotContain("U3 with Susan");
    }

    /**
     * The airport-to-hotel hop, whose owner title reads "DEN → Marriott Lone Tree". It carries a
     * mode so every anonymous assertion above runs against a transfer that has one to leak.
     */
    private static GroundTransferPlanned transferToTheMarriott() {
        return new GroundTransferPlanned(GroundTransferId.of(GT_ID),
                "DEN", "", new Address("", "Denver", "CO", "", "US", null),
                "", "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                ZonedTimestamp.fromLocal(GT_DEPARTS, DENVER),
                ZonedTimestamp.fromLocal(GT_ARRIVES, DENVER),
                "U3 with Susan");
    }

    private static CalendarEntry groundTransfer() {
        return new CalendarEntry(
                GT_DEPARTS, GT_ARRIVES,
                "\uD83D\uDE95 DEN → Marriott Lone Tree",
                List.of(new SubtitleLine.Range(
                        ZonedTimestamp.fromLocal(GT_DEPARTS, DENVER),
                        ZonedTimestamp.fromLocal(GT_ARRIVES, DENVER))),
                new EntryDetails.GroundTransfer("/ground-transfers/" + GT_ID + "/cancel"));
    }

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final LocalDateTime GATHERING_START = LocalDateTime.of(2026, 7, 5, 18, 0);
    private static final LocalDateTime GATHERING_END = LocalDateTime.of(2026, 7, 5, 21, 0);

    @Test
    void anonymousUserSeesSpeakingBadgeOnPublicGathering() {
        // That Ted is speaking at a gathering is public by decision, so the badge must reach
        // anonymous viewers — assert it survives the real security chain.
        anonymousSees(new GatheringPlanned(GatheringId.random(), "London Java Community",
                "Skills Matter",
                new Address("3 Meet Ln", "London", "", "EC1A 1BB", "GB", null),
                ZonedTimestamp.fromLocal(GATHERING_START, LONDON),
                ZonedTimestamp.fromLocal(GATHERING_END, LONDON),
                true, "https://meetup.com/ljc/events/123"));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("A Ted Talk")
                // A gathering is public in full, so the venue and time are public too.
                .contains("Skills Matter")
                // The owner edit link is still never public.
                .doesNotContain("/planned-gatherings/");
    }

    private static final LocalDateTime CONF_START = LocalDateTime.of(2026, 11, 5, 9, 0);
    private static final LocalDateTime CONF_END = LocalDateTime.of(2026, 11, 5, 18, 0);

    private static CalendarEntry conference(AttendanceCommitment commitment) {
        return new CalendarEntry(
                CONF_START, CONF_END,
                "J-Fall", List.of(new SubtitleLine.Text("Ede, Netherlands")),
                new EntryDetails.Conference(commitment, false, null));
    }

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final ConferenceId J_FALL = ConferenceId.random();

    private static ConferencePlanned jFallPlanned() {
        return jFallPlanned("");
    }

    private static ConferencePlanned jFallPlanned(String infoUrl) {
        return new ConferencePlanned(J_FALL, "J-Fall",
                ZonedTimestamp.fromLocal(CONF_START, AMSTERDAM),
                ZonedTimestamp.fromLocal(CONF_END, AMSTERDAM),
                "Reehorst",
                new Address("1 Conf St", "Ede", "", "6710", "Netherlands", null),
                ConferenceFormat.CALL_FOR_PAPERS,
                infoUrl);
    }

    private static final Instant RECORDED_ON = Instant.parse("2026-05-01T00:00:00Z");

    /** A conference Ted has committed to, reached the way production reaches it. */
    private static ConferenceAttendanceConfirmed jFallConfirmed(AttendanceBasis basis) {
        return new ConferenceAttendanceConfirmed(J_FALL, basis,
                Instant.parse("2026-05-01T00:00:00Z"));
    }

    // The chip's own CSS comment names it, so the bare word "Maybe" appears in every calendar
    // response whether or not a chip is rendered. Both directions assert the whole element.
    private static final String MAYBE_CHIP = "<span class=\"entry-maybe-badge\">Maybe</span>";

    @Test
    void anonymousUserSeesMaybeChipOnSpeculativeConference() {
        // The commitment level is public by decision: an anonymous reader is meant to learn that
        // Ted might be at this one, so the chip must survive the real security chain.
        anonymousSees(jFallPlanned());

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains(MAYBE_CHIP);
    }

    /**
     * A conference's own page is public by decision, so an anonymous visitor gets the link — the
     * venue and the times already are, and CLAUDE.md lists {@code infoUrl} among what a conference
     * publishes in full. Asserted through the real security chain, on the whole anchor.
     */
    @Test
    void anonymousUserGetsTheConferencesOwnPageLink() {
        anonymousSees(jFallPlanned("https://jfall.nl/"));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("href=\"https://jfall.nl/\"")
                .contains("J-Fall");
    }

    /**
     * <strong>The CFP never reaches an anonymous page — its submission URL least of all.</strong>
     * A deadline says Ted is considering a conference he has not committed to; a link to the page
     * he would submit on says it more loudly, and it is the kind of value that looks harmless in
     * markup. Both are recorded here as real events so the claim is about what the projector does
     * with them, not about a fixture that never carried them.
     * <p>
     * The public link is asserted alongside deliberately: this is the case where the two URLs sit
     * on the same conference, which is where confusing them would show.
     */
    @Test
    void anonymousUserNeverSeesTheCfpDeadlineOrItsSubmissionUrl() {
        anonymousSees(jFallPlanned("https://jfall.nl/"),
                      new CfpOpened(J_FALL,
                                    ZonedTimestamp.fromLocal(
                                            LocalDateTime.of(2026, 9, 12, 23, 59), AMSTERDAM),
                                    "https://sessionize.com/jfall-2027/"));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("href=\"https://jfall.nl/\"")
                .doesNotContain("sessionize.com")
                .doesNotContain("https://sessionize.com/jfall-2027/")
                // The deadline itself, in every form the markup could carry it: the rendered day,
                // and the UTC instant a <time datetime=...> would emit.
                .doesNotContain("2026-09-12")
                .doesNotContain("2026-09-12T21:59")
                .doesNotContain("CFP");
    }

    @Test
    void anonymousUserSeesNoChipOnCommittedConference() {
        anonymousSees(jFallPlanned(), jFallConfirmed(AttendanceBasis.SPEAKING_ACCEPTED));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("J-Fall")
                .doesNotContain(MAYBE_CHIP);
    }

    @Test
    void anonymousUserNeverSeesWhyTedIsGoing() {
        // AttendanceBasis is the private half, and the confirmation event genuinely carries one
        // here — the public projector reads the event and never reads that field, so no wording or
        // enum name of it can reach the anonymous page however the entry is rendered.
        anonymousSees(jFallPlanned(), jFallConfirmed(AttendanceBasis.SPEAKING_ACCEPTED));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("SPEAKING_ACCEPTED")
                .doesNotContain("SPEAKING_INVITED")
                .doesNotContain("TICKET_PURCHASED")
                .doesNotContain("Ticket purchased")
                .doesNotContain("Invited to speak");
    }

    // The badge's own CSS comment names it, so both directions assert the whole element.
    private static final String SPEAKING_BADGE =
            "<span class=\"entry-speaking-badge\">A Ted Talk</span>";

    /**
     * That Ted speaks at a conference is public by decision — the venue and the time already are.
     * Driven all the way from the events that make it true: a submission, then an acceptance, which
     * commits attendance on its own.
     */
    @Test
    void anonymousUserSeesTheSpeakingBadgeOnAnAcceptedTalk() {
        anonymousSees(jFallPlanned(),
                      new TalkSubmitted(J_FALL, RECORDED_ON),
                      new TalkAccepted(J_FALL, RECORDED_ON));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains(SPEAKING_BADGE)
                .doesNotContain(MAYBE_CHIP);
    }

    /**
     * <strong>The redaction claim this slice turns on.</strong> An invitation Ted has not answered
     * is speaking evidence, and publishing it would tell a stranger he had been asked to speak
     * somewhere he has not decided about — the submission pipeline leaking one bit. Gated on
     * commitment, the entry is indistinguishable from any other "Maybe".
     */
    @Test
    void anonymousUserNeverLearnsOfAnUnansweredSpeakingInvitation() {
        anonymousSees(jFallPlanned(), new InvitedToSpeak(J_FALL, RECORDED_ON));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains(MAYBE_CHIP)
                .doesNotContain(SPEAKING_BADGE)
                .doesNotContain("Invited")
                .doesNotContain("invitation");
    }

    /** Saying yes to the invitation is what makes it publishable — and only then. */
    @Test
    void anonymousUserSeesTheBadgeOnceAnInvitationIsAccepted() {
        anonymousSees(jFallPlanned(),
                      new InvitedToSpeak(J_FALL, RECORDED_ON),
                      jFallConfirmed(AttendanceBasis.SPEAKING_INVITED));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains(SPEAKING_BADGE);
    }

    /**
     * Going to a conference he was invited to, on a bought ticket, is attending — not speaking. The
     * badge must not appear, or it would say something untrue about the same public facts.
     */
    @Test
    void anInvitationTakenUpAsAPlainTicketWearsNoBadge() {
        anonymousSees(jFallPlanned(),
                      new InvitedToSpeak(J_FALL, RECORDED_ON),
                      jFallConfirmed(AttendanceBasis.TICKET_PURCHASED));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("J-Fall")
                .doesNotContain(SPEAKING_BADGE);
    }

    /**
     * A talk out for review leaves no public mark at all: submission status is OWNER-only, so the
     * anonymous page cannot tell this conference from one Ted never submitted to.
     */
    @Test
    void anonymousUserCannotTellASubmittedTalkFromNoSubmissionAtAll() {
        anonymousSees(jFallPlanned(), new TalkSubmitted(J_FALL, RECORDED_ON));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains(MAYBE_CHIP)
                .doesNotContain(SPEAKING_BADGE)
                .doesNotContain("Submitted")
                .doesNotContain("submitted");
    }

    /** A rejection is the most private thing on the axis, and it leaves no mark either. */
    @Test
    void anonymousUserNeverLearnsOfARejection() {
        anonymousSees(jFallPlanned(),
                      new TalkSubmitted(J_FALL, RECORDED_ON),
                      new TalkRejected(J_FALL, RECORDED_ON));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains(MAYBE_CHIP)
                .doesNotContain(SPEAKING_BADGE)
                // Capitalised only: the word appears lowercase in a stylesheet comment on every
                // calendar page, so asserting on it would fail for the wrong reason.
                .doesNotContain("Rejected");
    }

    /**
     * Withdrawing a talk moves one axis only: Ted still goes, so the entry stays — it simply stops
     * saying he speaks there.
     */
    @Test
    void withdrawingAnAcceptedTalkTakesTheBadgeAwayButNotTheEntry() {
        anonymousSees(jFallPlanned(),
                      new TalkSubmitted(J_FALL, RECORDED_ON),
                      new TalkAccepted(J_FALL, RECORDED_ON),
                      new TalkWithdrawn(J_FALL, RECORDED_ON));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("J-Fall")
                .doesNotContain(SPEAKING_BADGE)
                .doesNotContain(MAYBE_CHIP);
    }

    @Test
    @WithMockUser(username = "ted", roles = "OWNER")
    void ownerSeesTheSameMaybeChipAsAnonymousViewers() {
        // One rendering path, one collapse: richer per-conference status belongs on the conference
        // dashboard, not on the calendar, so there is nothing for the redactor to get wrong here.
        given(calendarAggregator.allEntries()).willReturn(
                List.of(conference(AttendanceCommitment.WATCHING)));

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .contains(MAYBE_CHIP);
    }

    /**
     * The year overview is not rendered for an anonymous viewer at all — not hidden with CSS, not
     * gated in JavaScript: absent, trigger included.
     * <p>
     * Every fact in it is individually public. What is not public is the <em>aggregate</em>: a year
     * of them drawn as one 13px-per-day picture is a legible pattern — how often Ted travels, how
     * long he is gone, how far ahead he plans — and that is a different artefact from the same facts
     * spread over 150 scrolled weeks. Deny-by-default costs nothing here, so nothing says the
     * surface exists.
     */
    @Test
    void anonymousViewersGetNoYearOverviewAtAll() {
        anonymousSees(jFallPlanned(),
                      new TalkSubmitted(J_FALL, RECORDED_ON),
                      new TalkAccepted(J_FALL, RECORDED_ON));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .as("the panel")
                .doesNotContain("year-overview")
                .doesNotContain("yo-month")
                .doesNotContain("yo-grid")
                .as("the trigger, so nothing even says the surface exists")
                .doesNotContain("Jump to month")
                // Not hidden in the script either: the script names the panel's classes, so it is
                // withheld from an anonymous render rather than left inert.
                .doesNotContain("yearOverviewCurrentMonth");
    }

    @Test
    @WithMockUser(username = "family", roles = "FAMILY")
    void familyGetsTheYearOverviewBecauseTheyAlreadySeeTheOwnersEntries() {
        // The gate is isPublicUser, never isOwner. The trap is next door — dayMenu is owner-only —
        // and gating this the same way would lose FAMILY the overlay with nothing failing.
        given(calendarAggregator.allEntries()).willReturn(
                List.of(conference(AttendanceCommitment.GOING)));

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .contains("class=\"disclosure-menu year-overview\"")
                .contains("Jump to month");
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
                FUTURE_CHECK_IN, FUTURE_CHECK_OUT,
                "Grand Hotel", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "Grand Hotel cont'd", List.of(new SubtitleLine.Text("Berlin, Germany")),
                new EntryDetails.Lodging("https://maps.google.com/grand-hotel", null)
        )));

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                // Assert on the actual disclosure markup + dated link, not the ".disclosure-menu"
                // CSS selector (which is inlined on every calendar page regardless of viewer).
                .contains("<details class=\"disclosure-menu\"")
                .contains("href=\"/book-flight?date=2026-07-");
    }

    @Test
    void anonymousUserSeesNoCreateLinksEvenForFutureDays() {
        anonymousSees(grandHotelBooked(FUTURE_CHECK_IN, FUTURE_CHECK_OUT));

        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("<details class=\"disclosure-menu\"")
                .doesNotContain("href=\"/book-flight")
                .doesNotContain("href=\"/plan-gathering")
                .doesNotContain("href=\"/plan-conference");
    }

    @Test
    @WithMockUser(username = "family", roles = "FAMILY")
    void familyUserSeesNoCreateLinksEvenForFutureDays() {
        given(calendarAggregator.allEntries()).willReturn(List.of(new CalendarEntry(
                FUTURE_CHECK_IN, FUTURE_CHECK_OUT,
                "Grand Hotel", List.of(new SubtitleLine.Text("Berlin, Germany")),
                "Grand Hotel cont'd", List.of(new SubtitleLine.Text("Berlin, Germany")),
                new EntryDetails.Lodging("https://maps.google.com/grand-hotel", null)
        )));

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("<details class=\"disclosure-menu\"")
                .doesNotContain("href=\"/book-flight")
                .doesNotContain("href=\"/plan-gathering");
    }
}
