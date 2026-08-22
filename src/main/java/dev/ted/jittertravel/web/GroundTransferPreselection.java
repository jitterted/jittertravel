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
     */
    public void applyTo(PlanGroundTransferRequest request) {
        choices.originFor(gap).ifPresent(origin -> {
            request.setOrigin(origin.token());
            dayOf(origin).ifPresent(request::setDate);
            timeOf(origin).ifPresent(request::setDepartureTime);
        });
        choices.destinationFor(gap).ifPresent(destination -> {
            request.setDestination(destination.token());
            if (request.getOrigin() == null) {
                // Only the far end is known, so its own day is the best the form can say.
                dayOf(destination).ifPresent(request::setDate);
            }
            timeOf(destination).ifPresent(request::setArrivalTime);
        });
        keepTheRangeValid(request);
    }

    /**
     * A transfer must arrive after it departs, or the POST comes back with
     * {@code InvalidGroundTransferTimeRange} — for a range this class produced rather than one Ted
     * typed. It happens honestly: a stay checked out of at 11:00 whose far end checks in at 15:00 is
     * fine, but reach an <em>airport</em> whose flight left at 09:00 and the pair inverts.
     */
    private void keepTheRangeValid(PlanGroundTransferRequest request) {
        LocalTime departure = request.getDepartureTime();
        LocalTime arrival = request.getArrivalTime();
        if (departure != null && arrival != null && !arrival.isAfter(departure)) {
            request.setArrivalTime(departure.plusMinutes(GAP_MINUTES));
        }
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
