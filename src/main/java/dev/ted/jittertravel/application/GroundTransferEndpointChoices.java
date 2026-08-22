package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What the ground-transfer form offers at each end.
 * <p>
 * The two ends offer <strong>different</strong> flight legs, and that is the point (Ted,
 * 2026-08-20): you only ever travel <em>from</em> an airport you have landed at and <em>to</em> one
 * you are flying out of. So the "From" select gets {@link #arrivals} and the "To" select gets
 * {@link #departures} — never both. That removes the ambiguity a plain per-airport list had (two
 * trips through SFO collapsed into one unexplained choice) and lets each option carry the one time
 * that matters for it.
 * <p>
 * Hotels are split the same way as of 2026-08-21, and for the same reason: leaving a hotel, the
 * moment that applies is its {@link #checkOuts check-out}; arriving at one, its {@link #checkIns
 * check-in}. The same stays appear at both ends — a hotel is a place at either end of a hop — but
 * each side carries and names its own moment, so choosing one fills the form in the way choosing a
 * flight leg does.
 *
 * @param arrivals  flight legs for the "From" select — the airport you landed at
 * @param departures flight legs for the "To" select — the airport you fly out of
 * @param checkOuts hotels for the "From" select, each carrying its check-out
 * @param checkIns  hotels for the "To" select, each carrying its check-in
 */
public record GroundTransferEndpointChoices(List<TransferEndpointOption> arrivals,
                                            List<TransferEndpointOption> departures,
                                            List<TransferEndpointOption> checkOuts,
                                            List<TransferEndpointOption> checkIns) {

    public boolean isEmpty() {
        return arrivals.isEmpty() && departures.isEmpty()
               && checkOuts.isEmpty() && checkIns.isEmpty();
    }

    /**
     * The option this gap leaves <em>from</em>, when there is exactly one it can be — the place the
     * schedule says Ted is stuck, on a day inside the gap.
     */
    public Optional<TransferEndpointOption> originFor(ScheduleProblem.MissingTravel gap) {
        return theOnlyCandidate(concat(arrivals, checkOuts), gap.fromCity(), gap);
    }

    /** The option this gap has to reach, on the same rule. */
    public Optional<TransferEndpointOption> destinationFor(ScheduleProblem.MissingTravel gap) {
        return theOnlyCandidate(concat(departures, checkIns), gap.toCity(), gap);
    }

    /**
     * <strong>Exactly one, or none at all</strong> (Ted, 2026-08-21). A candidate is an endpoint in
     * the gap's own city whose moment falls inside the gap's days: leaving Johannesberg on Sep 13
     * there is one stay checking out that day, and arriving in Frankfurt one checking in.
     * <p>
     * Two candidates means two hotels in one city over those days, and there the form must ask
     * rather than guess. A wrong endpoint is not a wasted click: it writes a
     * {@code GroundTransferPlanned} that <em>removes the very gap</em> it was entered to close, and
     * nothing afterwards says the schedule is still broken. That asymmetry — a silent wrong answer
     * against one more click — is why this never falls back to a best guess.
     * <p>
     * No match is the ordinary case for a gap the app holds no endpoint for at all (a train
     * station, a conference venue), and it simply leaves "Choose a place…" showing.
     */
    private Optional<TransferEndpointOption> theOnlyCandidate(List<TransferEndpointOption> offered,
                                                              String city,
                                                              ScheduleProblem.MissingTravel gap) {
        LocalDate opened = localDay(gap.arrivedAt());
        LocalDate closed = localDay(gap.nextDepartureAt());
        LocalDate from = opened.isBefore(closed) ? opened : closed;
        LocalDate until = opened.isBefore(closed) ? closed : opened;
        List<TransferEndpointOption> candidates = offered.stream()
                .filter(option -> option.city().equalsIgnoreCase(city))
                .filter(option -> withinDays(option, from, until))
                .toList();
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private boolean withinDays(TransferEndpointOption option, LocalDate from, LocalDate until) {
        if (option.prefillDate().isBlank()) {
            return false;
        }
        LocalDate day = LocalDate.parse(option.prefillDate());
        return !day.isBefore(from) && !day.isAfter(until);
    }

    /**
     * The gap's two ends sit in different zones, so each is read in its own — the day Ted lives at
     * that end, which is the day the endpoint's own moment is stamped with.
     */
    private LocalDate localDay(ZonedTimestamp moment) {
        return moment.localDateTime().toLocalDate();
    }

    private List<TransferEndpointOption> concat(List<TransferEndpointOption> legs,
                                                List<TransferEndpointOption> stays) {
        return Stream.concat(legs.stream(), stays.stream()).toList();
    }
}
