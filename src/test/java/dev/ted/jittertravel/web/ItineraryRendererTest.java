package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.*;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryRendererTest {

    private static final LocalDate JUN_1 = LocalDate.of(2026, 6, 1); // Monday
    private static final LocalDate JUN_2 = LocalDate.of(2026, 6, 2);
    private static final LocalDate JUN_3 = LocalDate.of(2026, 6, 3);
    private static final LocalDate MAY_31 = LocalDate.of(2026, 5, 31);
    private static final LocalDate JUN_10 = LocalDate.of(2026, 6, 10);

    private static final ZoneId SAN_FRANCISCO = ZoneId.of("America/Los_Angeles");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final ZoneId FRANKFURT = ZoneId.of("Europe/Berlin");

    // --- Date navigation ---

    @Test
    void prevDateAppearsInPreviousLink() {
        String html = renderEmpty();

        assertThat(html).contains("/itinerary?date=2026-05-31");
    }

    @Test
    void nextDateAppearsInNextLink() {
        String html = renderEmpty();

        assertThat(html).contains("/itinerary?date=2026-06-02");
    }

    @Test
    void todayLinkShownWhenTodayBeforeDisplayedRange() {
        String html = ItineraryRenderer.render(
                threeDays(List.of(), List.of(), List.of()), MAY_31, JUN_2, MAY_31, false);

        assertThat(html)
                .contains(">Today<")
                .contains("/itinerary?date=2026-05-31");
    }

    @Test
    void todayLinkShownWhenTodayAfterDisplayedRange() {
        String html = ItineraryRenderer.render(
                threeDays(List.of(), List.of(), List.of()), MAY_31, JUN_2, JUN_10, false);

        assertThat(html)
                .contains(">Today<")
                .contains("/itinerary?date=2026-06-10");
    }

    @Test
    void todayShownAsNonLinkWhenTodayWithinDisplayedRange() {
        String html = ItineraryRenderer.render(
                threeDays(List.of(), List.of(), List.of()), MAY_31, JUN_2, JUN_1, false);

        assertThat(html).contains("today-link--current");
        assertThat(html).doesNotContain("/itinerary?date=2026-06-01");
    }

    // --- Day headers ---

    @Test
    void dayHeadersAreFormattedWithDayNameAndMonthDay() {
        String html = renderEmpty();

        assertThat(html)
                .contains("Mon, Jun 1")
                .contains("Tue, Jun 2")
                .contains("Wed, Jun 3");
    }

    @Test
    void emptyDayShowsNothingScheduled() {
        String html = renderEmpty();

        assertThat(html).contains("Nothing scheduled");
    }

    @Test
    void emptyDayCoveredByAStayShowsWhereHeIsInsteadOfNothingScheduled() {
        String html = renderStayingDay();

        assertThat(html)
                .contains("<span>In Frankfurt, DE</span>")
                .contains("<div class=\"whereabouts-detail\">Grand Hotel Frankfurt</div>")
                .doesNotContain("<div class=\"empty-day\">Nothing scheduled</div>");
    }

    @Test
    void theStayRowIsTwoLinesPrefixedWithTheLodgingIconOnATintedGround() {
        String html = renderStayingDay();

        assertThat(html)
                .contains("<div class=\"whereabouts\"><div class=\"whereabouts-where\">"
                          + "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#166534\"")
                .contains(".whereabouts { background: #f0fdf4;")
                .doesNotContain(".whereabouts { background: #dcfce7;");
    }

    @Test
    void aDayAwayWithNoBedBookedSaysWhereHeIsAndThatNothingIsBooked() {
        String html = renderNightWithoutABed(true);

        assertThat(html)
                .contains("<span>In Denver</span>")
                .contains("<div class=\"whereabouts-detail\">No hotel booked</div>")
                .doesNotContain("<div class=\"empty-day\">Nothing scheduled</div>");
    }

    @Test
    void theNoBedRowIsAmberNotTheGreenOfANightThatIsSorted() {
        String html = renderNightWithoutABed(true);

        assertThat(html)
                .contains("<div class=\"whereabouts whereabouts--unbooked\">")
                .contains(".whereabouts--unbooked { background: #fffbeb; }");
    }

    @Test
    void theNoBedRowOffersTheSameBookHotelLinkTheProblemReportOffers() {
        String html = renderNightWithoutABed(true);

        assertThat(html)
                .contains("<a href=\"/book-hotel?city=Denver&amp;checkIn=2026-06-01&amp;checkOut=2026-06-05\">"
                          + "Book hotel &rarr;</a>");
    }

    @Test
    void familySeesWhereHeIsButNotTheOwnerOnlyBookingLink() {
        String html = renderNightWithoutABed(false);

        assertThat(html)
                .contains("<span>In Denver</span>")
                .contains("<div class=\"whereabouts-detail\">No hotel booked</div>")
                .doesNotContain("/book-hotel?city=Denver");
    }

    @Test
    void aMissingBedOutranksTheHomeRow() {
        String html = ItineraryRenderer.render(
                List.of(new ItineraryDay(JUN_1, List.of(), Optional.empty(),
                                Optional.of(missingHotel()), true),
                        new ItineraryDay(JUN_2, List.of(gathering("Some Meetup", false, ""))),
                        new ItineraryDay(JUN_3, List.of(gathering("Some Meetup", false, "")))),
                MAY_31, JUN_2, JUN_1, true);

        assertThat(html)
                .contains("<span>In Denver</span>")
                .doesNotContain("You&rsquo;re Home");
    }

    @Test
    void aBookedStayOutranksAMissingBed() {
        String html = ItineraryRenderer.render(
                List.of(new ItineraryDay(JUN_1, List.of(), Optional.of(ongoingStay()),
                                Optional.of(missingHotel()), false),
                        new ItineraryDay(JUN_2, List.of(gathering("Some Meetup", false, ""))),
                        new ItineraryDay(JUN_3, List.of(gathering("Some Meetup", false, "")))),
                MAY_31, JUN_2, JUN_1, true);

        assertThat(html)
                .contains("<div class=\"whereabouts-detail\">Grand Hotel Frankfurt</div>")
                .doesNotContain("No hotel booked");
    }

    @Test
    void aDayThatIsNeitherAwayNorInAStaySaysHeIsHome() {
        String html = renderHomeDay();

        assertThat(html)
                .contains("<span>You&rsquo;re Home</span>")
                .doesNotContain("<div class=\"empty-day\">Nothing scheduled</div>");
    }

    @Test
    void theHomeRowCarriesTheHouseIconAndNoSecondLine() {
        String html = renderHomeDay();

        assertThat(html)
                .contains("<path d=\"M10 20v-5h4v5\"/>")
                .doesNotContain("<div class=\"whereabouts-detail\">");
    }

    @Test
    void aStayWinsOverHomeSoAHotelBookedInAHomeCityStillNamesTheHotel() {
        String html = ItineraryRenderer.render(
                List.of(new ItineraryDay(JUN_1, List.of(), Optional.of(ongoingStay()), Optional.empty(), true),
                        new ItineraryDay(JUN_2, List.of(gathering("Some Meetup", false, ""))),
                        new ItineraryDay(JUN_3, List.of(gathering("Some Meetup", false, "")))),
                MAY_31, JUN_2, JUN_1, false);

        assertThat(html)
                .contains("<div class=\"whereabouts-detail\">Grand Hotel Frankfurt</div>")
                .doesNotContain("You&rsquo;re Home");
    }

    @Test
    void neitherWhereaboutsRowShowsOnADayThatHasItsOwnEntries() {
        ItineraryEntry entry = gathering("Some Meetup", true, "");
        String html = ItineraryRenderer.render(
                List.of(new ItineraryDay(JUN_1, List.of(entry), Optional.of(ongoingStay()), Optional.empty(), true),
                        new ItineraryDay(JUN_2, List.of()),
                        new ItineraryDay(JUN_3, List.of())),
                MAY_31, JUN_2, JUN_1, false);

        assertThat(html)
                .doesNotContain("In Frankfurt, DE")
                .doesNotContain("You&rsquo;re Home")
                .doesNotContain("<div class=\"whereabouts\">");
    }

    @Test
    void daysWithEntriesDoNotShowNothingScheduled() {
        ItineraryEntry entry = gathering("Some Meetup", true, "");
        String html = ItineraryRenderer.render(
                threeDays(List.of(entry), List.of(entry), List.of(entry)), MAY_31, JUN_2, JUN_1, false);

        assertThat(html).doesNotContain("Nothing scheduled");
    }

    // --- Flight ---

    @Test
    void flightDepartureShowsFlightLabel() {
        String html = renderWithEntry(flight(FlightDayRole.DEPARTURE));

        assertThat(html).contains("Flight");
    }

    @Test
    void flightArrivalShowsArrivingLabel() {
        String html = renderWithEntry(flight(FlightDayRole.ARRIVAL));

        assertThat(html).contains("Arriving");
    }

    @Test
    void flightShowsAirlineFlightNumberAndAirportCodes() {
        String html = renderWithEntry(flight(FlightDayRole.DEPARTURE));

        assertThat(html)
                .contains("British Airways")
                .contains("BA100")
                .contains("SFO")
                .contains("LHR");
    }

    @Test
    void flightShowsFormattedDepartureAndArrivalTimes() {
        String html = renderWithEntry(flight(FlightDayRole.DEPARTURE));

        assertThat(html)
                .contains("9:00 AM")
                .contains("5:15 PM");
    }

    @Test
    void flightTimesRenderAsTimeElementsCarryingEachAirportsOwnInstant() {
        // The two endpoints resolve independently: SFO 9:00 AM PDT is 16:00Z and LHR 5:15 PM BST
        // is 16:15Z — fifteen minutes apart in real time, where the bare wall-clocks read as
        // eight hours. Only the datetime attribute can carry that.
        String html = renderWithEntry(flight(FlightDayRole.DEPARTURE));

        assertThat(html)
                .contains("<time datetime=\"2026-06-01T16:00:00Z\" data-fmt=\"h:mm a\">9:00 AM</time>")
                .contains("<time datetime=\"2026-06-01T16:15:00Z\" data-fmt=\"h:mm a\">5:15 PM</time>");
    }

    // --- Train ---

    @Test
    void trainDepartureShowsTrainLabel() {
        String html = renderWithEntry(train(TrainDayRole.DEPARTURE, "", "", ""));

        assertThat(html).contains("Train");
    }

    @Test
    void trainArrivalShowsArrivingLabel() {
        String html = renderWithEntry(train(TrainDayRole.ARRIVAL, "", "", ""));

        assertThat(html).contains("Arriving");
    }

    @Test
    void trainWithServiceIdShowsServiceId() {
        String html = renderWithEntry(train(TrainDayRole.DEPARTURE, "Caledonian Sleeper", "", ""));

        assertThat(html).contains("Caledonian Sleeper");
    }

    @Test
    void trainWithBlankServiceIdOmitsServiceId() {
        String html = renderWithEntry(train(TrainDayRole.DEPARTURE, "", "", ""));

        assertThat(html).doesNotContain("Caledonian Sleeper");
    }

    @Test
    void trainTimesRenderAsTimeElementsCarryingTheUtcInstant() {
        // London 9:00 AM / 11:15 AM BST are 08:00Z / 10:15Z.
        String html = renderWithEntry(train(TrainDayRole.DEPARTURE, "", "", ""));

        assertThat(html)
                .contains("<time datetime=\"2026-06-01T08:00:00Z\" data-fmt=\"h:mm a\">9:00 AM</time>")
                .contains("<time datetime=\"2026-06-01T10:15:00Z\" data-fmt=\"h:mm a\">11:15 AM</time>");
    }

    @Test
    void trainStationWithMapsUrlRendersAsLink() {
        String html = renderWithEntry(train(TrainDayRole.DEPARTURE, "", "https://maps.example.com/euston", ""));

        assertThat(html).contains("href=\"https://maps.example.com/euston\"");
    }

    @Test
    void trainStationWithoutMapsUrlRendersStationNameAsText() {
        String html = renderWithEntry(train(TrainDayRole.DEPARTURE, "", "", ""));

        assertThat(html).contains("London Euston");
    }

    @Test
    void trainShowsFormattedDepartureAndArrivalTimes() {
        String html = renderWithEntry(train(TrainDayRole.DEPARTURE, "", "", ""));

        assertThat(html)
                .contains("9:00 AM")
                .contains("11:15 AM");
    }

    @Test
    void trainStationLineAppearsBeforeServiceId() {
        String html = renderWithEntry(train(TrainDayRole.DEPARTURE, "Caledonian Sleeper", "", ""));

        assertThat(html.indexOf("London Euston"))
                .as("station -> station must render above the service id")
                .isLessThan(html.indexOf("Caledonian Sleeper"));
    }

    @Test
    void trainShowsEditPencilLinkingToEditPageForOwner() {
        TrainTripId tripId = TrainTripId.random();
        TrainItineraryEntry entry = new TrainItineraryEntry(tripId, TrainDayRole.DEPARTURE, "",
                "London Euston", "London", "", zoned(JUN_1.atTime(9, 0), LONDON),
                "Manchester Piccadilly", "Manchester", "", zoned(JUN_1.atTime(11, 15), LONDON));

        String html = ItineraryRenderer.render(
                threeDays(List.of(entry), List.of(), List.of()), MAY_31, JUN_2, JUN_1, true);

        assertThat(html)
                .contains("class=\"edit-pencil\" href=\"/booked-trains/" + tripId.id() + "\"");
    }

    @Test
    void trainHasNoEditPencilForNonOwner() {
        String html = renderWithEntry(train(TrainDayRole.DEPARTURE, "Caledonian Sleeper", "", ""));

        assertThat(html)
                .doesNotContain("href=\"/booked-trains/");
    }

    @Test
    void flightShowsEditPencilLinkingToEditPageForOwner() {
        FlightId flightId = FlightId.random();
        FlightItineraryEntry entry = new FlightItineraryEntry(flightId, FlightDayRole.DEPARTURE,
                "British Airways", "BA100", "SFO", zoned(JUN_1.atTime(9, 0), SAN_FRANCISCO),
                "LHR", zoned(JUN_1.atTime(17, 15), LONDON));

        String html = ItineraryRenderer.render(
                threeDays(List.of(entry), List.of(), List.of()), MAY_31, JUN_2, JUN_1, true);

        assertThat(html)
                .contains("class=\"edit-pencil\" href=\"/booked-flights/" + flightId.id() + "\"");
    }

    // --- Hotel ---

    @Test
    void hotelCheckInShowsCheckInLabel() {
        String html = renderWithEntry(hotel(HotelDayRole.CHECK_IN, "Hessen"));

        assertThat(html).contains("Check-In");
    }

    @Test
    void hotelCheckOutShowsCheckOutLabel() {
        String html = renderWithEntry(hotel(HotelDayRole.CHECK_OUT, "Hessen"));

        assertThat(html).contains("Check-Out");
    }

    @Test
    void hotelShowsHotelNameAndMapsUrl() {
        String html = renderWithEntry(hotel(HotelDayRole.CHECK_IN, "Hessen"));

        assertThat(html)
                .contains("Grand Hotel Frankfurt")
                .contains("https://maps.example.com/hotel");
    }

    @Test
    void hotelWithRegionShowsCityRegionPostalCode() {
        String html = renderWithEntry(hotel(HotelDayRole.CHECK_IN, "Hessen"));

        assertThat(html).contains("Frankfurt, Hessen 60311");
    }

    @Test
    void hotelWithoutRegionShowsCityPostalCode() {
        String html = renderWithEntry(hotel(HotelDayRole.CHECK_IN, ""));

        assertThat(html).contains("Frankfurt 60311");
    }

    @Test
    void hotelShowsFormattedCheckInTime() {
        String html = renderWithEntry(hotel(HotelDayRole.CHECK_IN, ""));

        assertThat(html).contains("3:00 PM");
    }

    @Test
    void hotelCheckInTimeRendersAsTimeElementCarryingTheUtcInstant() {
        // Frankfurt 3:00 PM CEST is 13:00Z.
        String html = renderWithEntry(hotel(HotelDayRole.CHECK_IN, ""));

        assertThat(html)
                .contains("<time datetime=\"2026-06-01T13:00:00Z\" data-fmt=\"h:mm a\">3:00 PM</time>");
    }

    @Test
    void hotelShowsEditPencilLinkingToEditPageForOwner() {
        HotelBookingId bookingId = HotelBookingId.random();
        Address address = new Address("Kaiserstrasse 1", "Frankfurt", "Hessen", "60311", "DE", null);
        HotelItineraryEntry entry = new HotelItineraryEntry(bookingId, "Grand Hotel Frankfurt", address,
                BookingIntent.FINAL, HotelDayRole.CHECK_IN, zoned(JUN_1.atTime(15, 0), FRANKFURT),
                "https://maps.example.com/hotel");

        String html = ItineraryRenderer.render(
                threeDays(List.of(entry), List.of(), List.of()), MAY_31, JUN_2, JUN_1, true);

        assertThat(html)
                .contains("class=\"edit-pencil\" href=\"/booked-hotels/" + bookingId.id() + "\"");
    }

    @Test
    void hotelHasNoEditPencilForNonOwner() {
        String html = renderWithEntry(hotel(HotelDayRole.CHECK_IN, "Hessen"));

        assertThat(html)
                .doesNotContain("href=\"/booked-hotels/");
    }

    // --- Conference ---

    @Test
    void singleDayConferenceShowsConferenceLabel() {
        String html = renderWithEntry(conference(1, 1));

        assertThat(html).contains("Conference");
    }

    @Test
    void multiDayConferenceShowsDayOfNLabel() {
        String html = renderWithEntry(conference(2, 3));

        assertThat(html).contains("Day 2 of 3");
    }

    @Test
    void conferenceShowsNameVenueAndLocation() {
        String html = renderWithEntry(conference(1, 1));

        assertThat(html)
                .contains("JitterConf 2026")
                .contains("Moscone Center")
                .contains("San Francisco")
                .contains("US");
    }

    // --- Gathering ---

    @Test
    void gatheringShowsGatheringLabel() {
        String html = renderWithEntry(gathering("Some Meetup", false, ""));

        assertThat(html).contains("entry-kind--gathering");
        assertThat(html).contains(">Gathering<");
    }

    @Test
    void gatheringWithInfoUrlRendersTitleAsLink() {
        String html = renderWithEntry(gathering("London Java Community", false, "https://meetup.com/ljc/events/123"));

        assertThat(html).contains("href=\"https://meetup.com/ljc/events/123\"");
        assertThat(html).contains("London Java Community");
    }

    @Test
    void gatheringWithoutInfoUrlRendersTitleAsPlainText() {
        String html = renderWithEntry(gathering("London Java Community", false, ""));

        assertThat(html).contains("London Java Community");
        assertThat(html).doesNotContain("href=\"https://meetup.com");
    }

    @Test
    void gatheringSpeakingTrueRendersSpeakingBadge() {
        String html = renderWithEntry(gathering("Some Meetup", true, ""));

        assertThat(html).contains("Speaking");
    }

    @Test
    void gatheringSpeakingFalseOmitsSpeakingBadge() {
        String html = renderWithEntry(gathering("Some Meetup", false, ""));

        assertThat(html).doesNotContain("Speaking");
    }

    @Test
    void gatheringShowsEditPencilLinkingToEditPageForOwner() {
        GatheringId gatheringId = GatheringId.random();
        GatheringItineraryEntry entry = new GatheringItineraryEntry(
                gatheringId, "London Java Community", "Skills Matter", "London", "GB",
                false, "",
                zoned(JUN_1.atTime(LocalTime.of(18, 0)), LONDON),
                zoned(JUN_1.atTime(LocalTime.of(21, 0)), LONDON));

        String html = ItineraryRenderer.render(
                threeDays(List.of(entry), List.of(), List.of()), MAY_31, JUN_2, JUN_1, true);

        assertThat(html)
                .contains("class=\"edit-pencil\" href=\"/planned-gatherings/" + gatheringId.id() + "\"");
    }

    @Test
    void gatheringHasNoEditPencilForNonOwner() {
        String html = renderWithEntry(gathering("Some Meetup", false, ""));

        assertThat(html)
                .doesNotContain("href=\"/planned-gatherings/");
    }

    @Test
    void gatheringShowsVenueAndLocation() {
        String html = renderWithEntry(gathering("Some Meetup", false, ""));

        assertThat(html)
                .contains("Skills Matter")
                .contains("London, GB");
    }

    @Test
    void gatheringShowsTimeRange() {
        String html = renderWithEntry(gathering("Some Meetup", false, ""));

        assertThat(html).contains("6:00 PM");
        assertThat(html).contains("9:00 PM");
    }

    @Test
    void gatheringTimeRangeRendersAsTimeElementsCarryingTheUtcInstant() {
        // London 6:00–9:00 PM BST is 17:00–20:00Z.
        String html = renderWithEntry(gathering("Some Meetup", false, ""));

        assertThat(html)
                .contains("<time datetime=\"2026-06-01T17:00:00Z\" data-fmt=\"h:mm a\">6:00 PM</time>")
                .contains("<time datetime=\"2026-06-01T20:00:00Z\" data-fmt=\"h:mm a\">9:00 PM</time>");
    }

    // --- Private event ---

    @Test
    void privateEventShowsPrivateLabel() {
        String html = renderWithEntry(privateEvent("Dinner with the Smiths", "Chez Moi"));

        assertThat(html).contains("entry-kind--private-event");
        assertThat(html).contains(">Private<");
    }

    @Test
    void privateEventShowsTitleAsPlainTextWithNoLink() {
        String html = renderWithEntry(privateEvent("Dinner with the Smiths", "Chez Moi"));

        assertThat(html).contains("Dinner with the Smiths");
        // A private event has no public info URL, so the title is never a link.
        assertThat(html).doesNotContain("<a href=\"http");
    }

    @Test
    void privateEventShowsVenueAndLocation() {
        String html = renderWithEntry(privateEvent("Dinner with the Smiths", "Chez Moi"));

        assertThat(html)
                .contains("Chez Moi")
                .contains("London, GB");
    }

    @Test
    void privateEventWithBlankVenueShowsOnlyLocation() {
        String html = renderWithEntry(privateEvent("Evening out", ""));

        assertThat(html).contains("London, GB");
        assertThat(html).doesNotContain(" · ");
    }

    @Test
    void privateEventShowsTimeRange() {
        String html = renderWithEntry(privateEvent("Dinner with the Smiths", "Chez Moi"));

        assertThat(html).contains("7:00 PM");
        assertThat(html).contains("10:00 PM");
    }

    @Test
    void privateEventTimeRangeRendersAsTimeElementsCarryingTheUtcInstant() {
        // London 7:00–10:00 PM BST is 18:00–21:00Z.
        String html = renderWithEntry(privateEvent("Dinner with the Smiths", "Chez Moi"));

        assertThat(html)
                .contains("<time datetime=\"2026-06-01T18:00:00Z\" data-fmt=\"h:mm a\">7:00 PM</time>")
                .contains("<time datetime=\"2026-06-01T21:00:00Z\" data-fmt=\"h:mm a\">10:00 PM</time>");
    }

    // --- Ground transfer ---

    @Test
    void groundTransferShowsItsOwnKindLabelAndColour() {
        String html = renderWithEntry(groundTransfer());

        assertThat(html)
                .contains("entry-card entry-card--ground-transfer")
                .contains("entry-kind entry-kind--ground-transfer")
                .contains(">Ground transfer<");
    }

    @Test
    void groundTransferNamesBothEndsAndNothingElse() {
        // The itinerary is OWNER/FAMILY only, so the hotel is named in full here — redaction is an
        // anonymous-calendar concern. But the journey is stated once: naming the same hop again as
        // cities was noise on the card (Ted, 2026-08-20).
        String html = renderWithEntry(groundTransfer());

        assertThat(html)
                .contains("DEN")
                .contains("Marriott Lone Tree")
                .doesNotContain("Denver to Lone Tree")
                .doesNotContain("entry-detail entry-location");
    }

    @Test
    void groundTransferCarriesNoEditPencilBecauseThereIsNothingToEdit() {
        String html = renderWithEntry(groundTransfer());

        assertThat(html).doesNotContain("class=\"edit-pencil\"");
    }

    /**
     * The owner's action in the pencil's slot is a cancel: a wrong transfer is corrected by
     * removing it and entering it again, and left in place it keeps asserting a hop that never
     * happened.
     */
    @Test
    void groundTransferCarriesTheOwnersCancelBinInThePencilsSlot() {
        String html = ItineraryRenderer.render(
                threeDays(List.of(groundTransfer()), List.of(), List.of()), MAY_31, JUN_2, JUN_1, true);

        assertThat(html)
                .contains("<a class=\"cancel-bin\""
                          + " href=\"/ground-transfers/11111111-2222-3333-4444-555555555555/cancel\""
                          + " title=\"Cancel ground transfer\">");
    }

    @Test
    void familyViewersGetNoCancelBinAtAllRatherThanAGreyedOne() {
        // Hiding by permission stays hiding: a greyed control would itself disclose that the
        // OWNER-only cancel surface exists (CLAUDE.md, affordances vs authorization).
        String html = renderWithEntry(groundTransfer());

        assertThat(html)
                .doesNotContain("cancel-bin\" href=")
                .doesNotContain("/ground-transfers/");
    }

    @Test
    void groundTransferTimesRenderAsTimeElementsCarryingTheUtcInstant() {
        // London 12:00-12:45 PM BST is 11:00-11:45Z.
        String html = renderWithEntry(groundTransfer());

        assertThat(html)
                .contains("<time datetime=\"2026-06-01T11:00:00Z\" data-fmt=\"h:mm a\">12:00 PM</time>")
                .contains("<time datetime=\"2026-06-01T11:45:00Z\" data-fmt=\"h:mm a\">12:45 PM</time>");
    }

    // --- Helpers ---

    private static String renderEmpty() {
        return ItineraryRenderer.render(threeDays(List.of(), List.of(), List.of()), MAY_31, JUN_2, JUN_1, false);
    }

    private static String renderStayingDay() {
        return ItineraryRenderer.render(
                List.of(new ItineraryDay(JUN_1, List.of(), Optional.of(ongoingStay()), Optional.empty(), false),
                        new ItineraryDay(JUN_2, List.of(gathering("Some Meetup", false, ""))),
                        new ItineraryDay(JUN_3, List.of(gathering("Some Meetup", false, "")))),
                MAY_31, JUN_2, JUN_1, false);
    }

    private static String renderHomeDay() {
        return ItineraryRenderer.render(
                List.of(new ItineraryDay(JUN_1, List.of(), Optional.empty(), Optional.empty(), true),
                        new ItineraryDay(JUN_2, List.of(gathering("Some Meetup", false, ""))),
                        new ItineraryDay(JUN_3, List.of(gathering("Some Meetup", false, "")))),
                MAY_31, JUN_2, JUN_1, false);
    }

    private static String renderNightWithoutABed(boolean isOwner) {
        return ItineraryRenderer.render(
                List.of(new ItineraryDay(JUN_1, List.of(), Optional.empty(),
                                Optional.of(missingHotel()), false),
                        new ItineraryDay(JUN_2, List.of(gathering("Some Meetup", false, ""))),
                        new ItineraryDay(JUN_3, List.of(gathering("Some Meetup", false, "")))),
                MAY_31, JUN_2, JUN_1, isOwner);
    }

    private static ScheduleProblem.MissingHotel missingHotel() {
        return new ScheduleProblem.MissingHotel("Denver", JUN_1, LocalDate.of(2026, 6, 5), "ExploreDDD");
    }

    private static OngoingStay ongoingStay() {
        return new OngoingStay("Grand Hotel Frankfurt", "Frankfurt", "DE");
    }

    private static String renderWithEntry(ItineraryEntry entry) {
        return ItineraryRenderer.render(threeDays(List.of(entry), List.of(), List.of()), MAY_31, JUN_2, JUN_1, false);
    }

    private static List<ItineraryDay> threeDays(List<ItineraryEntry> day1,
                                                List<ItineraryEntry> day2,
                                                List<ItineraryEntry> day3) {
        return List.of(
                new ItineraryDay(JUN_1, day1),
                new ItineraryDay(JUN_2, day2),
                new ItineraryDay(JUN_3, day3));
    }

    private static FlightItineraryEntry flight(FlightDayRole role) {
        // A genuinely cross-zone flight: each endpoint's time is that airport's wall-clock.
        return new FlightItineraryEntry(FlightId.random(), role, "British Airways", "BA100",
                "SFO", zoned(JUN_1.atTime(9, 0), SAN_FRANCISCO),
                "LHR", zoned(JUN_1.atTime(17, 15), LONDON));
    }

    private static TrainItineraryEntry train(TrainDayRole role, String serviceId,
                                             String departureMapsUrl, String arrivalMapsUrl) {
        return new TrainItineraryEntry(TrainTripId.random(), role, serviceId,
                "London Euston", "London", departureMapsUrl,
                zoned(JUN_1.atTime(9, 0), LONDON),
                "Manchester Piccadilly", "Manchester", arrivalMapsUrl,
                zoned(JUN_1.atTime(11, 15), LONDON));
    }

    private static HotelItineraryEntry hotel(HotelDayRole dayRole, String region) {
        Address address = new Address("Kaiserstrasse 1", "Frankfurt", region, "60311", "DE", null);
        LocalDateTime anchorTime = dayRole == HotelDayRole.CHECK_IN
                ? JUN_1.atTime(15, 0) : JUN_1.atTime(11, 0);
        return new HotelItineraryEntry(HotelBookingId.random(), "Grand Hotel Frankfurt", address,
                BookingIntent.FINAL, dayRole, zoned(anchorTime, FRANKFURT),
                "https://maps.example.com/hotel");
    }

    private static ConferenceItineraryEntry conference(int dayNumber, int totalDays) {
        Address venue = new Address("747 Howard St", "San Francisco", "CA", "94103", "US", null);
        return new ConferenceItineraryEntry("JitterConf 2026", "Moscone Center", venue,
                dayNumber, totalDays, JUN_1.atTime(9, 0));
    }

    private static GatheringItineraryEntry gathering(String title, boolean speaking, String infoUrl) {
        return new GatheringItineraryEntry(GatheringId.random(), title, "Skills Matter", "London", "GB",
                speaking, infoUrl,
                zoned(JUN_1.atTime(LocalTime.of(18, 0)), LONDON),
                zoned(JUN_1.atTime(LocalTime.of(21, 0)), LONDON));
    }

    private static PrivateEventItineraryEntry privateEvent(String title, String venueName) {
        return new PrivateEventItineraryEntry(title, venueName, "London", "GB",
                zoned(JUN_1.atTime(LocalTime.of(19, 0)), LONDON),
                zoned(JUN_1.atTime(LocalTime.of(22, 0)), LONDON));
    }

    private static final GroundTransferId TRANSFER_ID =
            GroundTransferId.of(UUID.fromString("11111111-2222-3333-4444-555555555555"));

    private static GroundTransferItineraryEntry groundTransfer() {
        return new GroundTransferItineraryEntry(TRANSFER_ID, "DEN", "Marriott Lone Tree",
                zoned(JUN_1.atTime(LocalTime.of(12, 0)), LONDON),
                zoned(JUN_1.atTime(LocalTime.of(12, 45)), LONDON));
    }

    private static ZonedTimestamp zoned(LocalDateTime local, ZoneId zone) {
        return ZonedTimestamp.fromLocal(local, zone);
    }
}
