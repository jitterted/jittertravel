package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ChangeFlightItineraryCommand;
import dev.ted.jittertravel.domain.ChangeFlightItineraryContext;
import dev.ted.jittertravel.domain.ItineraryChangeKind;
import dev.ted.jittertravel.domain.ItineraryChangePlan;

import java.util.List;
import java.util.Optional;

/**
 * Everything the change-itinerary page needs to say about one submit, in the order it is decided:
 * whether the text could be read as an email for <em>this</em> itinerary ({@code parseProblems}),
 * which airports still need a zone ({@code unresolvedAirports}), who made the change ({@code kind},
 * null until Ted picks it), and the diff with every refusal ({@code plan}, null until the paste is
 * read).
 * <p>
 * The diff does not depend on {@code kind}, so it is shown even while "Who changed it?" is
 * unanswered; {@code command} needs both and is exactly what applying would execute.
 * {@code familyNews} is why family will be emailed, from the same rule {@link NotifyFamily} uses
 * (approved item 1), or empty when they will not be.
 */
public record ItineraryChangeEvaluation(
        List<String> parseProblems,
        List<AirportCode> unresolvedAirports,
        ItineraryChangeKind kind,
        ItineraryChangePlan plan,
        ChangeFlightItineraryContext context,
        ChangeFlightItineraryCommand command,
        Optional<ItineraryChangeNews.Difference> familyNews
) {

    public ItineraryChangeEvaluation {
        parseProblems = List.copyOf(parseProblems);
        unresolvedAirports = List.copyOf(unresolvedAirports);
        if (familyNews == null) {
            familyNews = Optional.empty();
        }
    }

    /** Without the family-email answer: nothing to compare until a plan exists. */
    public ItineraryChangeEvaluation(List<String> parseProblems, List<AirportCode> unresolvedAirports,
                                     ItineraryChangeKind kind, ItineraryChangePlan plan,
                                     ChangeFlightItineraryContext context, ChangeFlightItineraryCommand command) {
        this(parseProblems, unresolvedAirports, kind, plan, context, command, Optional.empty());
    }

    static ItineraryChangeEvaluation unread(List<String> problems, List<AirportCode> unresolved,
                                            ItineraryChangeKind kind) {
        return new ItineraryChangeEvaluation(problems, unresolved, kind, null, null, null);
    }

    public boolean kindMissing() {
        return kind == null;
    }

    public boolean planned() {
        return plan != null;
    }

    /** Read clean, nothing refused, something differs, and who made it is known: the only state worth a button. */
    public boolean applicable() {
        return command != null && plan.clean() && plan.hasChanges();
    }

    /** The email matches what is booked leg for leg: no problem, and nothing to record. */
    public boolean nothingToChange() {
        return plan != null && plan.clean() && !plan.hasChanges();
    }
}
