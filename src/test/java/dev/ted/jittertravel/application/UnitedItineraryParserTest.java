package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnitedItineraryParserTest {

    // Ted's 2026-09-29 sample, with the eTicket number, the frequent-flyer number and the name
    // replaced: this file is committed, and those are exactly what the parser must never read.
    private static final String SAMPLE = """
            Confirmation Number:
            MD7LKB
            Flight 1 of 4 UA2091        Class: United First (Z)
            Sun, Oct 18, 2026        Sun, Oct 18, 2026
            06:10 AM        12:45 PM
            San Francisco, CA, US (SFO)        Chicago, IL, US (ORD)
            Flight 2 of 4 UA3509        Class: United Business (Z)
            Sun, Oct 18, 2026        Sun, Oct 18, 2026
            01:55 PM        05:16 PM
            Chicago, IL, US (ORD)        Ottawa, ON, CA (YOW)
            Flight Operated by REPUBLIC AIRWAYS DBA UNITED EXPRESS.
            Flight 3 of 4 UA3659        Class: United Business (D)
            Sat, Oct 31, 2026        Sat, Oct 31, 2026
            04:46 PM        06:29 PM
            Ottawa, ON, CA (YOW)        Chicago, IL, US (ORD)
            Flight Operated by REPUBLIC AIRWAYS DBA UNITED EXPRESS.
            Flight 4 of 4 UA2059        Class: United First (D)
            Sat, Oct 31, 2026        Sat, Oct 31, 2026
            09:00 PM        11:56 PM
            Chicago, IL, US (ORD)        San Francisco, CA, US (SFO)
            Traveler Details
            TRAVELER/SAMPLE
            eTicket number: 0000000000000    Seats: SFO-ORD 04B
            Frequent Flyer: UA-XXXXX000 Premier Gold    ORD-YOW 02C
            YOW-ORD 02A
            ORD-SFO 02E
            """;

    private final UnitedItineraryParser parser = new UnitedItineraryParser();

    @Test
    void theSampleEmailReadsAsFourLegsWithItsConfirmationCode() {
        UnitedItineraryParser.Result result = parser.parse(SAMPLE);

        assertThat(result)
                .as("the sample parses")
                .isInstanceOf(UnitedItineraryParser.Parsed.class);
        PastedItinerary itinerary = ((UnitedItineraryParser.Parsed) result).itinerary();
        assertThat(itinerary.confirmationCode())
                .isEqualTo("MD7LKB");
        assertThat(itinerary.legs())
                .containsExactly(
                        leg(1, "UA2091", "SFO", LocalDateTime.of(2026, 10, 18, 6, 10),
                                "ORD", LocalDateTime.of(2026, 10, 18, 12, 45)),
                        leg(2, "UA3509", "ORD", LocalDateTime.of(2026, 10, 18, 13, 55),
                                "YOW", LocalDateTime.of(2026, 10, 18, 17, 16)),
                        leg(3, "UA3659", "YOW", LocalDateTime.of(2026, 10, 31, 16, 46),
                                "ORD", LocalDateTime.of(2026, 10, 31, 18, 29)),
                        leg(4, "UA2059", "ORD", LocalDateTime.of(2026, 10, 31, 21, 0),
                                "SFO", LocalDateTime.of(2026, 10, 31, 23, 56)));
    }

    @Test
    void theColumnsMayArriveOneUnderTheOtherAsAnIPadCopyLaysThemOut() {
        // Departure and arrival on separate lines, a tab or two where the columns were, the code on
        // the label line, and the narrow no-break space Apple puts before AM/PM.
        String stacked = """
                Confirmation Number: MD7LKB
                Flight 1 of 1\tUA2091
                Class: United First (Z)
                Sun, Oct 18, 2026
                Sun, Oct 18, 2026
                06:10 AM
                12:45 PM
                San Francisco, CA, US (SFO)
                Chicago, IL, US (ORD)
                """;

        assertThat(parser.parse(stacked))
                .isEqualTo(new UnitedItineraryParser.Parsed(new PastedItinerary("MD7LKB", List.of(
                        leg(1, "UA2091", "SFO", LocalDateTime.of(2026, 10, 18, 6, 10),
                                "ORD", LocalDateTime.of(2026, 10, 18, 12, 45))))));
    }

    @Test
    void theArrivalDateIsReadNotAssumedToBeTheDepartureDate() {
        String redEye = """
                Confirmation Number: ABC123
                Flight 1 of 1 UA58
                Sat, Oct 31, 2026        Sun, Nov 1, 2026
                10:30 PM        06:45 AM
                San Francisco, CA, US (SFO)        Frankfurt, DE (FRA)
                """;

        UnitedItineraryParser.Result result = parser.parse(redEye);

        assertThat(result)
                .isInstanceOf(UnitedItineraryParser.Parsed.class);
        assertThat(((UnitedItineraryParser.Parsed) result).itinerary().legs().getFirst().arrivalLocal())
                .isEqualTo(LocalDateTime.of(2026, 11, 1, 6, 45));
    }

    @Test
    void aLegMissingFromThePasteIsReportedRatherThanBookedShort() {
        String withoutLegThree = SAMPLE.replaceAll("(?s)Flight 3 of 4.*?(?=Flight 4 of 4)", "");

        assertThat(parser.parse(withoutLegThree))
                .isEqualTo(new UnitedItineraryParser.Unparseable(List.of(
                        "Flight 3 of 4 is missing from the paste")));
    }

    @Test
    void theLastLegMissingFromThePasteIsReportedToo() {
        String withoutLegFour = SAMPLE.replaceAll("(?s)Flight 4 of 4.*?(?=Traveler Details)", "");

        assertThat(parser.parse(withoutLegFour))
                .isEqualTo(new UnitedItineraryParser.Unparseable(List.of(
                        "Flight 4 of 4 is missing from the paste")));
    }

    @Test
    void aMissingLegIsStillReportedAlongsideAnUnrelatedProblem() {
        String broken = SAMPLE.replace("Confirmation Number:\nMD7LKB\n", "")
                              .replaceAll("(?s)Flight 3 of 4.*?(?=Flight 4 of 4)", "");

        assertThat(parser.parse(broken))
                .isEqualTo(new UnitedItineraryParser.Unparseable(List.of(
                        "No confirmation number found",
                        "Flight 3 of 4 is missing from the paste")));
    }

    @Test
    void blankLinesBetweenTheLabelAndTheCodeAreIgnored() {
        String spaced = SAMPLE.replace("Confirmation Number:\nMD7LKB\n", "Confirmation Number:\n\n   \nMD7LKB\n");

        UnitedItineraryParser.Result result = parser.parse(spaced);

        assertThat(result)
                .isInstanceOf(UnitedItineraryParser.Parsed.class);
        assertThat(((UnitedItineraryParser.Parsed) result).itinerary().confirmationCode())
                .isEqualTo("MD7LKB");
    }

    @Test
    void aConfirmationLabelWithNothingAfterItIsAProblemNotACrash() {
        String labelLast = SAMPLE.replace("Confirmation Number:\nMD7LKB\n", "") + "Confirmation Number:";

        assertThat(parser.parse(labelLast))
                .isEqualTo(new UnitedItineraryParser.Unparseable(List.of("No confirmation number found")));
    }

    @Test
    void everyProblemIsReportedTogether() {
        // No confirmation number, and flight 2 lost its times: both, in one result.
        String broken = SAMPLE.replace("Confirmation Number:\nMD7LKB\n", "")
                              .replace("01:55 PM        05:16 PM\n", "");

        assertThat(parser.parse(broken))
                .isEqualTo(new UnitedItineraryParser.Unparseable(List.of(
                        "No confirmation number found",
                        "Flight 2: expected two times")));
    }

    @Test
    void aWeekdayThatDoesNotMatchItsDateIsAProblemNotAGuess() {
        String wrongWeekday = SAMPLE.replace("Sun, Oct 18, 2026        Sun, Oct 18, 2026\n06:10 AM",
                                             "Mon, Oct 18, 2026        Sun, Oct 18, 2026\n06:10 AM");

        assertThat(parser.parse(wrongWeekday))
                .isEqualTo(new UnitedItineraryParser.Unparseable(List.of(
                        "Flight 1: could not read a date or time (Mon, Oct 18, 2026)")));
    }

    @Test
    void textWithNoFlightsSaysWhatWasExpected() {
        assertThat(parser.parse("Thanks for choosing United!"))
                .isEqualTo(new UnitedItineraryParser.Unparseable(List.of(
                        "No confirmation number found",
                        "No flights found. Paste the text of a United confirmation email.")));
    }

    @Test
    void aPartnerCarrierKeepsItsOwnCodeAsTheAirline() {
        String partner = """
                Confirmation Number: ABC123
                Flight 1 of 1 LH401
                Sat, Oct 31, 2026        Sun, Nov 1, 2026
                05:35 PM        07:25 AM
                New York, NY, US (JFK)        Frankfurt, DE (FRA)
                """;

        UnitedItineraryParser.Result result = parser.parse(partner);

        assertThat(result)
                .isInstanceOf(UnitedItineraryParser.Parsed.class);
        assertThat(((UnitedItineraryParser.Parsed) result).itinerary().legs().getFirst().airline())
                .as("only UA is named; guessing another carrier's name would be worse than its code")
                .isEqualTo("LH");
    }

    private static PastedItinerary.Leg leg(int number, String flightNumber,
                                           String from, LocalDateTime departs,
                                           String to, LocalDateTime arrives) {
        return new PastedItinerary.Leg(number, "United Airlines", flightNumber,
                AirportCode.of(from), departs, AirportCode.of(to), arrives);
    }
}
