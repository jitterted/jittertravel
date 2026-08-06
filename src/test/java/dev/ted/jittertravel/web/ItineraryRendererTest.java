package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.*;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

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

    // --- Helpers ---

    private static String renderEmpty() {
        return ItineraryRenderer.render(threeDays(List.of(), List.of(), List.of()), MAY_31, JUN_2, JUN_1, false);
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
        return new GatheringItineraryEntry(title, "Skills Matter", "London", "GB",
                speaking, infoUrl,
                zoned(JUN_1.atTime(LocalTime.of(18, 0)), LONDON),
                zoned(JUN_1.atTime(LocalTime.of(21, 0)), LONDON));
    }

    private static ZonedTimestamp zoned(LocalDateTime local, ZoneId zone) {
        return ZonedTimestamp.fromLocal(local, zone);
    }
}
