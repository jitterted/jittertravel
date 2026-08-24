package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCityResolver;
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
 * <strong>Hotels are split by direction too</strong> (Ted, 2026-08-21), and carry a moment for the
 * same reason: leaving a hotel the moment is its check-out, arriving at one its check-in, and the
 * label names which — {@code Reichshof — Hamburg · check out Wed Sep 16, 11:00 AM}. The date was
 * what forced this: a bare {@code Reichshof — Hamburg} cannot be matched against the schedule
 * problem that sent Ted here, and two stays in one city are the same line twice. Both lists hold
 * the same stays; only the moment differs.
 * <p>
 * The prefill is a weaker claim here than on a leg, and knowing that matters: a stay is a range,
 * so a ride to a gathering mid-stay happens on neither of those days, and choosing such a hotel
 * moves the date field to one that is merely plausible. It is on trial for exactly that reason
 * (Ted, 2026-08-21) — the label says out loud which moment it is filling in, so a wrong one is
 * visible rather than silent, and dropping it later is deleting the two prefill arguments.
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
                hotelOptions(now, StayEnd.CHECK_OUT),
                hotelOptions(now, StayEnd.CHECK_IN));
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
                    airportCities.cityFor(end.airportCodeOf(flight)),
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

    /**
     * The stays offered at one end. Both ends offer the same stays and filter them the same way —
     * on <em>check-out</em>, whichever end this is, because that is what says the stay is over.
     * Filtering the "To" list on check-in instead would drop the hotel you are riding to the moment
     * you had arrived, which is exactly when the ride gets written down.
     */
    private List<TransferEndpointOption> hotelOptions(Instant now, StayEnd end) {
        return bookedHotels.views(TimeView.ALL, now).stream()
                // Checked out this morning still counts: the ride to the airport is the transfer
                // being recorded, and it is normally written down long after the taxi door shut.
                .filter(hotel -> !isBeforeToday(hotel.checkOut(), now))
                // A cancelled stay keeps a tombstone row on /booked-hotels so the cancellation is
                // visible; it is not a place Ted can be dropped off.
                .filter(hotel -> !hotel.cancelled())
                // Chronological, like the legs above and for the same reason — now that each option
                // carries a moment, alphabetical order puts the stay Ted is thinking about anywhere.
                .sorted(Comparator.comparing(hotel -> end.momentOf(hotel).utc()))
                .map(hotel -> stayOption(hotel, end))
                .toList();
    }

    private TransferEndpointOption stayOption(BookedHotelView hotel, StayEnd end) {
        ZonedTimestamp moment = end.momentOf(hotel);
        return new TransferEndpointOption(
                GroundTransferEndpointResolver.HOTEL_PREFIX + hotel.hotelBookingId().id(),
                hotel.hotelName() + " — " + hotel.city()
                + " · " + end.verb() + " " + LEG_MOMENT.format(moment.localDateTime()),
                // The schedule's own location for the stay, which the label's city is not always:
                // a gap says Johannesberg where the address says Rückersbach.
                hotel.locationForMatching(),
                moment.localDateTime().toLocalDate().toString(),
                INPUT_TIME.format(moment.localDateTime()));
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
     * Which end of a stay a transfer can touch: you leave a hotel at check-out and reach one at
     * check-in. The label says which, because unlike a flight's the moment is a guess.
     */
    private enum StayEnd {
        CHECK_OUT {
            @Override ZonedTimestamp momentOf(BookedHotelView hotel) { return hotel.checkOut(); }
            @Override String verb() { return "check out"; }
        },
        CHECK_IN {
            @Override ZonedTimestamp momentOf(BookedHotelView hotel) { return hotel.checkIn(); }
            @Override String verb() { return "check in"; }
        };

        abstract ZonedTimestamp momentOf(BookedHotelView hotel);

        abstract String verb();
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
