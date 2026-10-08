package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferenceNews;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * The fixed sample flights the email preview is built from (Ted, 2026-10-05): the same example every
 * time, so a preview is repeatable and shows a multi-leg trip even when none is booked. None of it is
 * real. The ids are fixed rather than random because the domain's {@code random()} factories are for
 * tests only.
 */
final class EmailPreviewSamples {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final ZoneId OTTAWA = ZoneId.of("America/Toronto");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** A flight booked on its own. */
    List<FlightBooked> singleFlight() {
        return List.of(trip().getFirst());
    }

    /** A four-leg trip, out and back by way of Chicago and Ottawa. */
    List<FlightBooked> trip() {
        return List.of(
                leg(1, "UA2091", "SFO", at(PACIFIC, 18, 6, 10), "ORD", at(CHICAGO, 18, 12, 45)),
                leg(2, "UA3509", "ORD", at(CHICAGO, 18, 14, 0), "YOW", at(OTTAWA, 18, 17, 5)),
                leg(3, "UA3510", "YOW", at(OTTAWA, 25, 9, 0), "ORD", at(CHICAGO, 25, 10, 30)),
                leg(4, "UA2092", "ORD", at(CHICAGO, 25, 13, 0), "SFO", at(PACIFIC, 25, 15, 55)));
    }

    /** A conference with every optional line present: a venue, a talk and a link. */
    ConferenceNews conference() {
        // "Germany", not "DE": a stored country is a code now, and the email names it, as
        // CityLabel.qualifier does — the preview shows what is actually sent.
        return new ConferenceNews("SoCraTes 2026", "Seminarzentrum Rückersbach", "Johannesberg", "Germany",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 8, 24, 9, 0), BERLIN),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 8, 27, 9, 0), BERLIN),
                "https://socrates-conference.de", ConferenceNews.SpeakingLine.TALK_ACCEPTED);
    }

    private FlightBooked leg(int number, String flightNumber, String from, ZonedTimestamp departs,
                             String to, ZonedTimestamp arrives) {
        return new FlightBooked(FlightId.of(new UUID(0L, number)), "United Airlines", flightNumber,
                AirportCode.of(from), departs, AirportCode.of(to), arrives);
    }

    private ZonedTimestamp at(ZoneId zone, int day, int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, day, hour, minute), zone);
    }
}
