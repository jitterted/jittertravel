package dev.ted.jittertravel.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One column of the itinerary. Beyond its entries it carries what the schedule knows about
 * <em>where he is</em> that day, which is what a day with no entries has to say instead of
 * nothing: an ongoing stay, a night the schedule places him somewhere with no bed booked, or
 * being at home.
 * <p>
 * The three are separate facts rather than one either/or value, because two of them can hold at
 * once: a hotel booked in a home city — the night before a dawn flight — is both a stay and a
 * night at home. The renderer picks, in that order, most specific first.
 */
public record ItineraryDay(LocalDate date,
                           List<ItineraryEntry> entries,
                           Optional<OngoingStay> ongoingStay,
                           Optional<ScheduleProblem.MissingHotel> nightWithoutABed,
                           boolean atHome) {

    public ItineraryDay(LocalDate date, List<ItineraryEntry> entries) {
        this(date, entries, Optional.empty(), Optional.empty(), false);
    }

    public boolean hasEntries() { return !entries.isEmpty(); }
}
