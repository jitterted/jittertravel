package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GroundTransferEndpointChoices;
import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.application.TransferEndpointOption;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Fills the ground-transfer form in from the missing-travel gap that sent Ted to it: the two
 * endpoints already chosen, and the date and times that go with them.
 * <p>
 * The banner above the form already says which gap this is — "No travel — Johannesberg → Frankfurt",
 * with the stay at each end named underneath. Leaving the selects on "Choose a place…" underneath
 * that made Ted read the two lists and match them back to the banner by city, which is work the
 * schedule has already done, and which fails outright when two stays share a city (Ted,
 * 2026-08-21).
 * <p>
 * Whether an end <em>can</em> be chosen is {@link GroundTransferEndpointChoices#originFor} decision,
 * and it answers with exactly one candidate or none. An end it cannot answer for is simply left
 * unselected — never a best guess, because a wrong endpoint writes a transfer that deletes the very
 * gap it was entered to close.
 */
public class GroundTransferPreselection {

    /**
     * The same 45 minutes the form's inline prefill script uses when a chosen time would invert the
     * pair. Kept the same so a preselected transfer and a hand-picked one behave alike.
     */
    private static final int GAP_MINUTES = 45;

    private final GroundTransferEndpointChoices choices;
    private final ScheduleProblem.MissingTravel gap;

    public GroundTransferPreselection(GroundTransferEndpointChoices choices,
                                      ScheduleProblem.MissingTravel gap) {
        this.choices = choices;
        this.gap = gap;
    }

    /**
     * Applies whatever this gap can settle, leaving everything else as the controller set it — an
     * unmatched end keeps its empty select, and an untouched time keeps its default.
     * <p>
     * Returns a new request rather than mutating one: the form bean is a record, so what was a run
     * of conditional setters is a run of conditional locals, settled once at the end. The order the
     * decisions are made in is unchanged, and so is which of them wins.
     */
    public PlanGroundTransferRequest applyTo(PlanGroundTransferRequest request) {
        String origin = request.origin();
        String destination = request.destination();
        LocalDate date = request.date();
        LocalTime departureTime = request.departureTime();
        LocalTime arrivalTime = request.arrivalTime();

        Optional<TransferEndpointOption> originEnd = choices.originFor(gap);
        if (originEnd.isPresent()) {
            origin = originEnd.get().token();
            date = dayOf(originEnd.get()).orElse(date);
            departureTime = timeOf(originEnd.get()).orElse(departureTime);
        }

        Optional<TransferEndpointOption> destinationEnd = choices.destinationFor(gap);
        if (destinationEnd.isPresent()) {
            destination = destinationEnd.get().token();
            if (origin == null) {
                // Only the far end is known, so its own day is the best the form can say. Read
                // after the origin block, exactly as the setter version read it back off the
                // request: this asks whether that block fired, not what arrived.
                date = dayOf(destinationEnd.get()).orElse(date);
            }
            arrivalTime = timeOf(destinationEnd.get()).orElse(arrivalTime);
        }

        return new PlanGroundTransferRequest(request.groundTransferId(), origin, destination,
                                             request.mode(), date, departureTime,
                                             keepTheRangeValid(departureTime, arrivalTime));
    }

    /**
     * A transfer must arrive after it departs, or the POST comes back with
     * {@code InvalidGroundTransferTimeRange} — for a range this class produced rather than one Ted
     * typed. It happens honestly: a stay checked out of at 11:00 whose far end checks in at 15:00 is
     * fine, but reach an <em>airport</em> whose flight left at 09:00 and the pair inverts.
     */
    private LocalTime keepTheRangeValid(LocalTime departure, LocalTime arrival) {
        if (departure != null && arrival != null && !arrival.isAfter(departure)) {
            return departure.plusMinutes(GAP_MINUTES);
        }
        return arrival;
    }

    private Optional<LocalDate> dayOf(TransferEndpointOption option) {
        return option.prefillDate().isBlank()
                ? Optional.empty()
                : Optional.of(LocalDate.parse(option.prefillDate()));
    }

    private Optional<LocalTime> timeOf(TransferEndpointOption option) {
        return option.prefillTime().isBlank()
                ? Optional.empty()
                : Optional.of(LocalTime.parse(option.prefillTime()));
    }
}
