package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.ConferenceNews;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FamilyNotificationMessagesTest {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final ZoneId OTTAWA = ZoneId.of("America/Toronto");

    private final FamilyNotificationMessages messages =
            new FamilyNotificationMessages(new StaticAirportCityResolver(), "https://jittertravel.com");

    private final FlightBooked sfoOrd = leg("UA2091", "SFO", at(PACIFIC, 18, 6, 10), "ORD", at(CHICAGO, 18, 12, 45));
    private final FlightBooked ordYow = leg("UA3509", "ORD", at(CHICAGO, 18, 14, 0), "YOW", at(OTTAWA, 18, 17, 5));
    private final FlightBooked yowOrd = leg("UA3510", "YOW", at(OTTAWA, 25, 9, 0), "ORD", at(CHICAGO, 25, 10, 30));
    private final FlightBooked ordSfo = leg("UA2092", "ORD", at(CHICAGO, 25, 13, 0), "SFO", at(PACIFIC, 25, 15, 55));

    @Test
    void aSingleFlightHasItsRouteInTheSubject() {
        FamilyMessage message = messages.messageFor(NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd));

        assertThat(message.subject())
                .isEqualTo("(JitterTravel) Ted booked a new flight: SFO → ORD");
    }

    @Test
    void aSingleFlightBodyNamesTheCitiesAndLabelsBothZones() {
        FamilyMessage message = messages.messageFor(NotifiedFact.FLIGHT_BOOKED, List.of(sfoOrd));

        assertThat(message.textContent().lines().toList())
                .containsExactly(
                        "Hi, JitterTravel here. Ted booked a single new flight as follows, and he wanted you to know.",
                        "",
                        "You can see the flight in his calendar here: https://jittertravel.com/calendar?day=2026-10-18",
                        "",
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
                .isEqualTo("(JitterTravel) Ted booked a new trip: SFO → ORD → YOW → ORD → SFO");
    }

    @Test
    void aTripSubjectBreaksTheChainWhereALegDoesNotStartWhereTheLastEnded() {
        FlightBooked frankfurtHamburg = leg("LH100", "FRA", at(ZoneId.of("Europe/Berlin"), 20, 9, 0),
                "HAM", at(ZoneId.of("Europe/Berlin"), 20, 10, 0));

        FamilyMessage message = messages.messageFor(NotifiedFact.ITINERARY_BOOKED,
                List.of(sfoOrd, frankfurtHamburg));

        assertThat(message.subject())
                .as("a leg that does not continue the last one starts a new run, not a false connection")
                .isEqualTo("(JitterTravel) Ted booked a new trip: SFO → ORD, FRA → HAM");
    }

    @Test
    void aTripBodyListsEveryLegNumberedAndSeparatedByABlankLine() {
        FamilyMessage message = messages.messageFor(NotifiedFact.ITINERARY_BOOKED, List.of(sfoOrd, ordYow));

        assertThat(message.textContent().lines().toList())
                .containsExactly(
                        "Hi, JitterTravel here. Ted booked a new trip with 2 flights listed below, and he wanted you to know.",
                        "",
                        "You can see the trip in his calendar here: https://jittertravel.com/calendar?day=2026-10-18",
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
    void aCancelledTripSaysSoInTheSubjectWithTheSameAirportChain() {
        FamilyMessage message = messages.messageFor(NotifiedFact.ITINERARY_CANCELLED,
                List.of(sfoOrd, ordYow, yowOrd, ordSfo));

        assertThat(message.subject())
                .isEqualTo("(JitterTravel) Ted cancelled a trip: SFO → ORD → YOW → ORD → SFO");
    }

    @Test
    void aCancelledTripBodySaysWhatHappenedAndListsEachLegAsNoLongerHappening() {
        FamilyMessage message = messages.messageFor(NotifiedFact.ITINERARY_CANCELLED, List.of(sfoOrd, ordYow));

        assertThat(message.textContent().lines().toList())
                .containsExactly(
                        "Hi, JitterTravel here. Ted canceled the trip below, and he wanted to let you know.",
                        "",
                        "1. UA2091",
                        "   San Francisco (SFO) → Chicago (ORD)",
                        "   Was due to depart  Sun 18 Oct 2026, 6:10 AM (SFO)",
                        "   Was due to arrive  Sun 18 Oct 2026, 12:45 PM (ORD)",
                        "",
                        "2. UA3509",
                        "   Chicago (ORD) → Ottawa (YOW)",
                        "   Was due to depart  Sun 18 Oct 2026, 2:00 PM (ORD)",
                        "   Was due to arrive  Sun 18 Oct 2026, 5:05 PM (YOW)");
    }

    @Test
    void aCancelledTripSaysWhatHappenedAndNotWhyAndNamesNoCodeOrAirline() {
        FamilyMessage message = messages.messageFor(NotifiedFact.ITINERARY_CANCELLED, List.of(sfoOrd, ordYow));

        assertThat(message.subject() + "\n" + message.textContent())
                .doesNotContain("MD7LKB")
                .doesNotContain("United")
                .doesNotContain("Confirmation")
                .doesNotContain("reason")
                .doesNotContain("schedule change");
    }

    // ---- the calendar link a template can include ----------------------------------------------

    @Test
    void theCalendarLinkJumpsToTheDayOfASingleFlight() {
        assertThat(messages.calendarUrl(List.of(sfoOrd)))
                .contains("https://jittertravel.com/calendar?day=2026-10-18");
    }

    @Test
    void forATripTheCalendarLinkJumpsToTheDayOfTheFirstFlightWhateverOrderTheyAreGivenIn() {
        assertThat(messages.calendarUrl(List.of(ordSfo, yowOrd, sfoOrd, ordYow)))
                .contains("https://jittertravel.com/calendar?day=2026-10-18");
    }

    @Test
    void theDayIsTheDeparturesOwnLocalDateNotTheUtcOne() {
        // 11:30 PM on the 18th in San Francisco is already the 19th in UTC; the calendar puts the
        // flight in the column of the day it leaves on, which is the 18th.
        FlightBooked lateFlight = leg("UA1", "SFO", at(PACIFIC, 18, 23, 30), "ORD", at(CHICAGO, 19, 5, 0));

        assertThat(messages.calendarUrl(List.of(lateFlight)))
                .contains("https://jittertravel.com/calendar?day=2026-10-18");
    }

    @Test
    void aTrailingSlashOnTheBaseUrlDoesNotDoubleUp() {
        FamilyNotificationMessages withSlash =
                new FamilyNotificationMessages(new StaticAirportCityResolver(), "https://jittertravel.com/");

        assertThat(withSlash.calendarUrl(List.of(sfoOrd)))
                .contains("https://jittertravel.com/calendar?day=2026-10-18");
    }

    @Test
    void withNoBaseUrlThereIsNoLinkRatherThanAGuessedOne() {
        FamilyNotificationMessages unconfigured =
                new FamilyNotificationMessages(new StaticAirportCityResolver(), "");

        assertThat(unconfigured.calendarUrl(List.of(sfoOrd)))
                .isEmpty();
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

    // ---- conferences: the wording Ted approved on 2026-10-05, line for line --------------------

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static ConferenceNews socrates(ConferenceNews.SpeakingLine speaking) {
        return new ConferenceNews("SoCraTes 2026", "Seminarzentrum Rückersbach", "Johannesberg", "DE",
                berlin(2026, 8, 24), berlin(2026, 8, 27), "https://socrates-conference.de", speaking);
    }

    private static ZonedTimestamp berlin(int year, int month, int day) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(year, month, day, 9, 0), BERLIN);
    }

    @Test
    void theGoingEmailIsTheApprovedText() {
        FamilyMessage message = messages.conferenceGoing(socrates(ConferenceNews.SpeakingLine.TALK_ACCEPTED));

        assertThat(message.subject())
                .isEqualTo("(JitterTravel) Ted is going to a conference: SoCraTes 2026");
        assertThat(message.textContent().lines().toList())
                .containsExactly(
                        "Hi, JitterTravel here. Ted is going to the conference below, and he wanted you to know.",
                        "",
                        "You can see the conference in his calendar here: https://jittertravel.com/calendar?day=2026-08-24",
                        "",
                        "SoCraTes 2026",
                        "Seminarzentrum Rückersbach, Johannesberg, DE",
                        "Mon 24 Aug – Thu 27 Aug 2026",
                        "His talk was accepted.",
                        "https://socrates-conference.de");
    }

    @Test
    void aBaseUrlEndingInASlashGivesOneSlashInTheConferenceLink() {
        FamilyNotificationMessages withSlash =
                new FamilyNotificationMessages(new StaticAirportCityResolver(), "https://jittertravel.com/");

        assertThat(withSlash.conferenceGoing(socrates(ConferenceNews.SpeakingLine.NONE)).textContent())
                .contains("here: https://jittertravel.com/calendar?day=2026-08-24");
    }

    @Test
    void anInvitedSpeakerIsToldInTheInvitedWords() {
        assertThat(messages.conferenceGoing(socrates(ConferenceNews.SpeakingLine.INVITED)).textContent().lines().toList())
                .contains("He was invited to speak.")
                .doesNotContain("His talk was accepted.");
    }

    @Test
    void aGoingEmailLeavesOutEveryLineItHasNothingFor() {
        ConferenceNews bare = new ConferenceNews("Open Space Night", "", "", "",
                berlin(2026, 8, 24), berlin(2026, 8, 24), "", ConferenceNews.SpeakingLine.NONE);

        assertThat(new FamilyNotificationMessages(new StaticAirportCityResolver(), "")
                .conferenceGoing(bare).textContent().lines().toList())
                .containsExactly(
                        "Hi, JitterTravel here. Ted is going to the conference below, and he wanted you to know.",
                        "",
                        "Open Space Night",
                        "Mon 24 Aug 2026");
    }

    @Test
    void aVenueWithOnlyACityIsNotPaddedWithEmptyParts() {
        ConferenceNews cityOnly = new ConferenceNews("Devoxx", "", "Antwerp", "",
                berlin(2026, 10, 5), berlin(2026, 10, 9), "", ConferenceNews.SpeakingLine.NONE);

        assertThat(messages.conferenceGoing(cityOnly).textContent().lines().toList())
                .contains("Antwerp")
                .contains("Mon 5 Oct – Fri 9 Oct 2026");
    }

    @Test
    void aConferenceSpanningNewYearNamesTheYearOnBothDates() {
        ConferenceNews overNewYear = new ConferenceNews("Winter Camp", "", "", "",
                berlin(2026, 12, 30), berlin(2027, 1, 2), "", ConferenceNews.SpeakingLine.NONE);

        assertThat(messages.conferenceGoing(overNewYear).textContent().lines().toList())
                .contains("Wed 30 Dec 2026 – Sat 2 Jan 2027");
    }

    @Test
    void theDeclinedAndRejectedExitsEndTheApprovedWay() {
        ConferenceNews news = socrates(ConferenceNews.SpeakingLine.NONE);

        FamilyMessage declined = messages.conferenceNotGoing(news, ConferenceNews.Exit.DECLINED);
        FamilyMessage rejected = messages.conferenceNotGoing(news, ConferenceNews.Exit.REJECTED);

        assertThat(declined.subject())
                .isEqualTo("(JitterTravel) Ted is no longer going to SoCraTes 2026");
        assertThat(declined.textContent().lines().toList())
                .containsExactly(
                        "Hi, JitterTravel here. Ted is no longer going to the conference below, and he wanted to let you know.",
                        "",
                        "SoCraTes 2026",
                        "Mon 24 Aug – Thu 27 Aug 2026");
        assertThat(rejected.textContent().lines().toList())
                .containsExactly(
                        "Hi, JitterTravel here. Ted is no longer going to the conference below, and he wanted to let you know.",
                        "",
                        "SoCraTes 2026",
                        "Mon 24 Aug – Thu 27 Aug 2026",
                        "His talk was rejected, so he is not going.");
    }

    @Test
    void anOrganizerCancellationEndsTheApprovedWay() {
        FamilyMessage cancelled = messages.conferenceNotGoing(socrates(ConferenceNews.SpeakingLine.NONE),
                ConferenceNews.Exit.CANCELLED);

        assertThat(cancelled.subject())
                .isEqualTo("(JitterTravel) Ted is no longer going to SoCraTes 2026");
        assertThat(cancelled.textContent().lines().toList())
                .containsExactly(
                        "Hi, JitterTravel here. The organizers cancelled the conference below, so Ted is no longer going, and he wanted to let you know.",
                        "",
                        "SoCraTes 2026",
                        "Mon 24 Aug – Thu 27 Aug 2026");
    }

    @Test
    void anExitNamesNoVenueAndNoLink() {
        assertThat(messages.conferenceNotGoing(socrates(ConferenceNews.SpeakingLine.TALK_ACCEPTED),
                ConferenceNews.Exit.DECLINED).textContent())
                .doesNotContain("Seminarzentrum")
                .doesNotContain("socrates-conference.de")
                .doesNotContain("jittertravel.com")
                .doesNotContain("accepted");
    }

    @Test
    void aConferenceFactIsNotToldFromFlights() {
        assertThatThrownBy(() -> messages.messageFor(NotifiedFact.CONFERENCE_GOING, List.of(sfoOrd)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> messages.messageFor(NotifiedFact.CONFERENCE_NOT_GOING, List.of(sfoOrd)))
                .isInstanceOf(IllegalArgumentException.class);
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
