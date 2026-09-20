package dev.ted.jittertravel.domain;

import java.util.List;

/**
 * Everything wrong with one entered location, reported together — the whole, where
 * {@link InvalidLocationEntry} is one problem inside it.
 *
 * <p><strong>Why a carrier and not the entry itself.</strong> {@link EnteredLocation#check} used to
 * throw the first {@code InvalidLocationEntry} it found, so a stay entered with neither a hotel
 * name nor a city reported the name, and the city only on the next submit. That is the 2026-09-06
 * failure the two train forms were rebuilt to remove, still reachable on the hotel forms — and
 * removing their HTML {@code required} on 2026-09-20 is what made it ordinary rather than rare.
 *
 * <p><strong>It carries at most one problem per field</strong>, which is what lets a controller
 * loop over {@link #problems()} and call {@code rejectValue} once per entry without two messages
 * landing in one {@code <span class="error">}. That holds because of the rules
 * {@link EnteredLocation#problems} applies, not because anything here enforces it — see that
 * method for why, and for what would have to change if a new rule broke it.
 *
 * <p>Compare {@link InvalidTrainEntry}, which is the same idea one level up: a trip has two ends,
 * each of which can be an invalid location, <em>and</em> a zone question of its own.
 */
public class InvalidEnteredLocation extends RuntimeException {

    private final List<InvalidLocationEntry> problems;

    public InvalidEnteredLocation(List<InvalidLocationEntry> problems) {
        super(problems.stream()
                      .map(Throwable::getMessage)
                      .reduce((first, second) -> first + " " + second)
                      .orElse(""));
        this.problems = List.copyOf(problems);
    }

    /** Never empty: {@link EnteredLocation#check} throws this only when there is something to say. */
    public List<InvalidLocationEntry> problems() {
        return problems;
    }
}
