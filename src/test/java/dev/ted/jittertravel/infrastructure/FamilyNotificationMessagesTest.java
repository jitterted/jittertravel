package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyNotificationMessagesTest {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final ZoneId OTTAWA = ZoneId.of("America/Toronto");

    private final FamilyNotificationMessages messages =
            new FamilyNotificationMessages(new StaticAirportCityResolver());

    private final FlightBooked sfoOrd = leg("UA2091", "SFO", at(PACIFIC, 18, 6, 10), "ORD", at(CHICAGO, 18, 12, 45));
    private final FlightBooked ordYow = leg("UA3509", "ORD", at(CHICAGO, 18, 14, 0), "YOW", at(OTTAWA, 18, 17, 5));
    private final FlightBooked yowOrd = leg("UA3510", "YOW", at(OTTAWA, 25, 9, 0), "ORD", at(CHICAGO, 25, 10, 30));
    private final FlightBooked ordSfo = leg("UA2092", "ORD", at(CHICAGO, 25, 13, 0), "SFO", at(PACIFIC, 25, 15, 55));

    @Test
    void aSingleFlightHasItsRouteInTheSubject() {
        FamilyMessage message = messages.messageFor(NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd));

        assertThat(message.subject())
                .isEqualTo("Ted booked a flight: SFO → ORD");
    }

    @Test
    void aSingleFlightBodyNamesTheCitiesAndLabelsBothZones() {
        FamilyMessage message = messages.messageFor(NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd));

        assertThat(message.textContent().lines().toList())
                .containsExactly(
                        "UA2091",
                        "San Francisco (SFO) → Chicago (ORD)",
                        "Departs  Sun 18 Oct 2026, 6:10 AM (SFO)",
                        "Arrives  Sun 18 Oct 2026, 12:45 PM (ORD)");
    }

    @Test
    void aTripSubjectChainsTheAirportsOfEveryLeg() {
        FamilyMessage message = messages.messageFor(NotifiedFact.ITINERARY_BOOKED,
                List.of(sfoOrd, ordYow, yowOrd, ordSfo));

        assertThat(message.subject())
                .isEqualTo("Ted booked a trip: SFO → ORD → YOW → ORD → SFO");
    }

    @Test
    void aTripSubjectBreaksTheChainWhereALegDoesNotStartWhereTheLastEnded() {
        FlightBooked frankfurtHamburg = leg("LH100", "FRA", at(ZoneId.of("Europe/Berlin"), 20, 9, 0),
                "HAM", at(ZoneId.of("Europe/Berlin"), 20, 10, 0));

        FamilyMessage message = messages.messageFor(NotifiedFact.ITINERARY_BOOKED,
                List.of(sfoOrd, frankfurtHamburg));

        assertThat(message.subject())
                .as("a leg that does not continue the last one starts a new run, not a false connection")
                .isEqualTo("Ted booked a trip: SFO → ORD, FRA → HAM");
    }

    @Test
    void aTripBodyListsEveryLegNumberedAndSeparatedByABlankLine() {
        FamilyMessage message = messages.messageFor(NotifiedFact.ITINERARY_BOOKED, List.of(sfoOrd, ordYow));

        assertThat(message.textContent().lines().toList())
                .containsExactly(
                        "Ted booked a trip with 2 flights.",
                        "",
                        "1. UA2091",
                        "   San Francisco (SFO) → Chicago (ORD)",
                        "   Departs  Sun 18 Oct 2026, 6:10 AM (SFO)",
                        "   Arrives  Sun 18 Oct 2026, 12:45 PM (ORD)",
                        "",
                        "2. UA3509",
                        "   Chicago (ORD) → Ottawa (YOW)",
                        "   Departs  Sun 18 Oct 2026, 2:00 PM (ORD)",
                        "   Arrives  Sun 18 Oct 2026, 5:05 PM (YOW)");
    }

    @Test
    void anAirportNotInTheCityTableIsShownByItsCode() {
        FlightBooked unknown = leg("XX1", "ZZZ", at(PACIFIC, 18, 6, 10), "ORD", at(CHICAGO, 18, 12, 45));

        assertThat(messages.messageFor(NotifiedFact.FLIGHT_BOOKED, List.of(unknown)).textContent().lines().toList())
                .contains("ZZZ (ZZZ) → Chicago (ORD)");
    }

    @Test
    void noTripMessageNamesTheConfirmationCodeAnAirlineOrAPassenger() {
        FamilyMessage message = messages.messageFor(NotifiedFact.ITINERARY_BOOKED, List.of(sfoOrd, ordYow));

        assertThat(message.subject() + "\n" + message.textContent())
                .doesNotContain("MD7LKB")
                .doesNotContain("United")
                .doesNotContain("Confirmation");
    }

    private static FlightBooked leg(String number, String from, ZonedTimestamp departs,
                                    String to, ZonedTimestamp arrives) {
        return new FlightBooked(FlightId.random(), "United Airlines", number,
                AirportCode.of(from), departs, AirportCode.of(to), arrives);
    }

    private static ZonedTimestamp at(ZoneId zone, int day, int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, day, hour, minute), zone);
    }
}
