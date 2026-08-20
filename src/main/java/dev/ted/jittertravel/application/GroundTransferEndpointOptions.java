package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The endpoints the ground-transfer form offers: the flight legs Ted is already on, and the hotels
 * he has already booked.
 * <p>
 * <strong>Flight legs, split by direction.</strong> "From" lists arrivals and "To" lists
 * departures, because you only travel away from an airport you landed at and toward one you fly out
 * of (Ted, 2026-08-20). Each option carries the leg's own date and time so the form can fill them
 * in — the whole reason this is a leg and not just an airport. The submitted <em>token</em> is
 * still {@code airport:<CODE>}: a transfer is between places, not between flights, so nothing about
 * the stored event changes.
 * <p>
 * <strong>Everything from today onward, with no date window (D10, widened by D14).</strong> "Near
 * that date" was considered and dropped — it was undefined, and on a plain GET the server has no
 * date to be near. What replaced it, {@code relevantUntil} not yet past, turned out to be too tight
 * to the minute for how transfers are actually entered: you land at 11:30 and record the taxi that
 * evening, by which time the arrival is "past" and the airport has vanished from the form. The same
 * hole swallowed the most common transfer of all — you check out at 11:00, ride to the airport, and
 * the hotel is gone by the time you write it down.
 * <p>
 * So an endpoint is offered while its own local day is <strong>today or later</strong>, judged in
 * <em>that endpoint's</em> zone: the zone of the airport you just landed at is the zone you are
 * standing in. Day granularity is what the rest of the app already reasons in, and it makes the
 * form survive the whole of the day it is about. Yesterday still drops off.
 * <p>
 * A flight leg is scoped by its <em>own</em> moment rather than by the flight's departure, so a
 * flight still in the air offers the airport it is about to land at. {@code ?date=} prefills the
 * date input only; it never filters these options.
 */
public class GroundTransferEndpointOptions {

    private static final DateTimeFormatter LEG_MOMENT =
            DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter INPUT_TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final BookedFlightsProjector bookedFlights;
    private final BookedHotelsProjector bookedHotels;
    private final AirportCityResolver airportCities;

    public GroundTransferEndpointOptions(BookedFlightsProjector bookedFlights,
                                         BookedHotelsProjector bookedHotels,
                                         AirportCityResolver airportCities) {
        this.bookedFlights = bookedFlights;
        this.bookedHotels = bookedHotels;
        this.airportCities = airportCities;
    }

    public GroundTransferEndpointChoices choicesAt(Instant now) {
        // ALL, then filtered below: the shared FUTURE filter judges a flight by its departure (so
        // it drops the arrival airport of a flight still in the air) and judges both to the minute
        // (so today's endpoints vanish part-way through today). See the class comment.
        List<BookedFlightView> flights = bookedFlights.views(TimeView.ALL, now);
        return new GroundTransferEndpointChoices(
                legOptions(flights, now, LegEnd.ARRIVAL),
                legOptions(flights, now, LegEnd.DEPARTURE),
                hotelOptions(now));
    }

    private List<TransferEndpointOption> legOptions(List<BookedFlightView> flights, Instant now,
                                                    LegEnd end) {
        List<TransferEndpointOption> options = new ArrayList<>();
        for (BookedFlightView flight : flights) {
            ZonedTimestamp moment = end.momentOf(flight);
            if (isBeforeToday(moment, now)) {
                continue;
            }
            options.add(new TransferEndpointOption(
                    GroundTransferEndpointResolver.AIRPORT_PREFIX + end.airportCodeOf(flight),
                    label(flight, end, moment),
                    moment.localDateTime().toLocalDate().toString(),
                    INPUT_TIME.format(moment.localDateTime())));
        }
        // Chronological, so the leg Ted is thinking about is where he expects it. Sorting on the
        // prefill strings works because both are fixed-width and already zero-padded.
        options.sort(Comparator.comparing(TransferEndpointOption::prefillDate)
                             .thenComparing(TransferEndpointOption::prefillTime));
        return List.copyOf(options);
    }

    /** e.g. {@code DEN — Denver · arrive Sun Sep 14, 11:30 AM (UA 59)}. */
    private String label(BookedFlightView flight, LegEnd end, ZonedTimestamp moment) {
        String code = end.airportCodeOf(flight);
        return code + " — " + airportCities.cityFor(code)
               + " · " + end.verb() + " " + LEG_MOMENT.format(moment.localDateTime())
               + " (" + flight.airline() + " " + flight.flightNumber() + ")";
    }

    private List<TransferEndpointOption> hotelOptions(Instant now) {
        return bookedHotels.views(TimeView.ALL, now).stream()
                // Checked out this morning still counts: the ride to the airport is the transfer
                // being recorded, and it is normally written down long after the taxi door shut.
                .filter(hotel -> !isBeforeToday(hotel.checkOut(), now))
                // A cancelled stay keeps a tombstone row on /booked-hotels so the cancellation is
                // visible; it is not a place Ted can be dropped off.
                .filter(hotel -> !hotel.cancelled())
                .sorted(Comparator.comparing(BookedHotelView::hotelName))
                .map(hotel -> new TransferEndpointOption(
                        GroundTransferEndpointResolver.HOTEL_PREFIX + hotel.hotelBookingId().id(),
                        hotel.hotelName() + " — " + hotel.city()))
                .toList();
    }

    /**
     * Whether {@code moment} fell on a day already gone, read in <em>its own</em> zone — the zone of
     * the airport or hotel it belongs to, which is where Ted is standing when it matters. Day
     * granularity, not instant: an endpoint stays offered for the whole of its own day, so a
     * transfer entered that evening still finds both of its ends.
     */
    private boolean isBeforeToday(ZonedTimestamp moment, Instant now) {
        return moment.localDateTime().toLocalDate()
                .isBefore(LocalDate.ofInstant(now, moment.zone()));
    }

    /**
     * Which end of a flight a transfer can touch. {@code BookedFlightView} exposes its airports
     * only through the display {@code route} ({@code "DEN→SJC"}), which
     * {@code BookedFlightsProjector} builds from the two codes — so that is where they are read
     * back from.
     */
    private enum LegEnd {
        ARRIVAL {
            @Override ZonedTimestamp momentOf(BookedFlightView flight) { return flight.arrivalDateTime(); }
            @Override String airportCodeOf(BookedFlightView flight) { return codes(flight)[1]; }
            @Override String verb() { return "arrive"; }
        },
        DEPARTURE {
            @Override ZonedTimestamp momentOf(BookedFlightView flight) { return flight.departureDateTime(); }
            @Override String airportCodeOf(BookedFlightView flight) { return codes(flight)[0]; }
            @Override String verb() { return "depart"; }
        };

        abstract ZonedTimestamp momentOf(BookedFlightView flight);

        abstract String airportCodeOf(BookedFlightView flight);

        abstract String verb();

        static String[] codes(BookedFlightView flight) {
            return flight.route().split("→");
        }
    }
}
