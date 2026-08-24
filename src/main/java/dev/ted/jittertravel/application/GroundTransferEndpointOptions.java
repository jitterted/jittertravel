package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The endpoints the ground-transfer form offers, out of {@link TransferEndpointProjector}: what is
 * still worth showing today, in the order Ted will look for it, with the label he reads.
 * <p>
 * <strong>Everything that is a fact about the event now lives in the read model</strong> — which end
 * a flight leg or a stay can serve, its place, its moments. What is left here is everything that
 * needs {@code now} or is about the form: the day filter, the ordering, and the label. That split is
 * D4, and it is why this class no longer names a kind of travel anywhere.
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
 * flight still in the air offers the airport it is about to land at; a stay is scoped by its
 * check-out at both ends. Which moment answers that question per kind is
 * {@link TransferEndpointRow#offeredUntil()}, decided where the event is read. {@code ?date=}
 * prefills the date input only; it never filters these options.
 */
public class GroundTransferEndpointOptions {

    private static final DateTimeFormatter LEG_MOMENT =
            DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter INPUT_TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final TransferEndpointProjector endpoints;

    public GroundTransferEndpointOptions(TransferEndpointProjector endpoints) {
        this.endpoints = endpoints;
    }

    public GroundTransferEndpointChoices choicesAt(Instant now) {
        return new GroundTransferEndpointChoices(
                optionsFor(TransferEnd.FLIGHT_ARRIVAL, now),
                optionsFor(TransferEnd.FLIGHT_DEPARTURE, now),
                optionsFor(TransferEnd.HOTEL_CHECK_OUT, now),
                optionsFor(TransferEnd.HOTEL_CHECK_IN, now));
    }

    /**
     * Chronological by the endpoint's own instant, so the leg or stay Ted is thinking about is where
     * he expects it. It is the absolute order rather than a wall-clock one, which matters exactly
     * when a list spans zones: a London landing at 08:00 BST happened before a Denver landing at
     * 08:00 MDT, and reading the two local clocks side by side would say the opposite.
     */
    private List<TransferEndpointOption> optionsFor(TransferEnd end, Instant now) {
        return endpoints.rowsFor(end).stream()
                .filter(row -> !isBeforeToday(row.offeredUntil(), now))
                .sorted(Comparator.comparing(row -> row.moment().utc()))
                .map(this::option)
                .toList();
    }

    private TransferEndpointOption option(TransferEndpointRow row) {
        return new TransferEndpointOption(
                row.token(),
                label(row),
                // The schedule's own place for this endpoint, which the label's city is not always:
                // a gap says Johannesberg where the hotel's address says Rückersbach.
                row.place().value(),
                row.moment().localDateTime().toLocalDate().toString(),
                INPUT_TIME.format(row.moment().localDateTime()));
    }

    /**
     * e.g. {@code DEN — Denver · arrive Sun Sep 14, 11:30 AM (UA 59)}, or the same without the
     * parenthesis for a stay, which has no service to name.
     */
    private String label(TransferEndpointRow row) {
        String label = row.name() + " — " + row.city()
                       + " · " + row.end().verb() + " "
                       + LEG_MOMENT.format(row.moment().localDateTime());
        return row.detail().isBlank() ? label : label + " (" + row.detail() + ")";
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
}
