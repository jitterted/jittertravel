package dev.ted.jittertravel.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * The "which building, in which city" pair every booking is entered as — a train station and its
 * city, a hotel and its city — checked on the way <em>in</em>, before the booking is written.
 *
 * <p><strong>Why this exists.</strong> The city is not decoration: {@link Place} derives a place
 * from it, and {@code /schedule-problems} asks whether two places name the same city. A station
 * name pasted into the city field is therefore a city that matches nothing — the report invents a
 * "no travel" gap between a city and itself, or misses a real one — and the mistake is invisible on
 * the page, because "Frankfurt (Main) Hbf" reads perfectly well as a label. This is the same class
 * of failure as the untrimmed {@code "Hamburg "} of event 92, arriving through a different door:
 * one paste into the wrong box rather than a stray space.
 *
 * <p><strong>Why the checks are a method and not the compact constructor.</strong> {@link Address}
 * and {@link TrainStationAddress} normalize in theirs precisely because Jackson binds
 * <em>stored</em> payloads through it, so a normalization repairs history on every replay. That
 * property makes it exactly the wrong place to <em>reject</em> anything: a rule there would apply
 * retroactively to every event already in the log, and a single old booking that broke it would
 * stop a replay — and a restore — dead. So this type is constructed on the write path only, from a
 * command that is about to execute, and the log is left alone. An existing booking that trips a
 * rule is only ever met when Ted next edits it, which is the moment he can fix it.
 *
 * <p><strong>The rules</strong> (in the order they are reported, so the earliest mistake is the one
 * shown):
 * <ol>
 *   <li>the building has a name — the half of the paste that gets left blank;</li>
 *   <li>the city is filled in;</li>
 *   <li>the city does not <em>look</em> like a building: it carries no brackets, no digit, and no
 *       word from {@link #VENUE_WORDS}.</li>
 * </ol>
 *
 * <p><strong>A fourth rule was tried and removed</strong> (2026-08-30): "the city is not the
 * building's name again". It reads as the obvious way to catch the whole line pasted into both
 * boxes, and it is wrong here — a station is routinely named for the town it stands in. Run against
 * every venue/city pair in the production log it scored one rejection, Gembloux/Gembloux, and no
 * true positives; the paste it was meant to catch is caught by rule 3 anyway, because a line copied
 * off a booking site carries "Hbf" or brackets. Do not re-add it without new evidence.
 *
 * <p><strong>On the word list.</strong> It is deliberately short and made only of words that are
 * never a whole word of a city name Ted travels to. "Inn" is the instructive omission — it belongs
 * to a river, and Wasserburg am Inn is a real town, so it would reject a real city to catch a
 * mistake nobody makes. "Park" is out for the same reason: Estes Park is in the log, "Parkway" is
 * what a station is called. A false positive here is recoverable in a second (edit the field,
 * submit again); a false negative is silent bad data, which is what this is for. When one does
 * misfire, shortening the list is the fix, not weakening the rule.
 */
public record EnteredLocation(String venueName, String city) {

    /**
     * Whole words that name a building, a platform or a desk — never a city. Matched
     * case-insensitively against the city's words, so "Hbf" inside "Frankfurter" cannot trip it.
     */
    private static final Set<String> VENUE_WORDS = Set.of(
            "hbf", "hauptbahnhof", "bahnhof", "bf", "bhf", "fernbf",
            "gare", "station", "stations", "stazione", "estacion", "estación",
            "centraal", "centrale", "termini", "parkway",
            "flughafen", "airport", "terminal",
            "hotel", "hostel", "resort", "suites");

    public EnteredLocation {
        venueName = normalized(venueName);
        city = normalized(city);
    }

    /** Null and surrounding whitespace both mean "nothing was typed here". */
    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    /** A station is a building with a name, standing in a city. */
    public static EnteredLocation of(TrainStationAddress station) {
        return new EnteredLocation(station.name(), station.city());
    }

    /**
     * A hotel's name is its own field, beside the address it stands at. The city checked is the
     * address's {@code city}, which is also what {@code locationForMatching} falls back to when it
     * is left blank — the ordinary case.
     */
    public static EnteredLocation of(String hotelName, Address address) {
        return new EnteredLocation(hotelName, address == null ? "" : address.city());
    }

    /**
     * @throws InvalidLocationEntry when this names something that cannot be a place, tagged with
     *         {@code role} and the field at fault so the form can point at it.
     */
    public void check(LocationRole role) {
        if (venueName.isBlank()) {
            throw new InvalidLocationEntry(role, LocationField.VENUE_NAME,
                    "Name is required — enter the name of the station or hotel being booked.");
        }
        if (city.isBlank()) {
            throw new InvalidLocationEntry(role, LocationField.CITY,
                    "City is required — enter the city this station or hotel is in.");
        }
        if (looksLikeAVenue()) {
            throw new InvalidLocationEntry(role, LocationField.CITY,
                    "This looks like a station or venue name, not a city — "
                            + "e.g. \"Frankfurt\", not \"Frankfurt (Main) Hbf\".");
        }
    }

    /**
     * Brackets and digits are the shape of a pasted station line ("Frankfurt (Main) Hbf",
     * "Terminal 2"); the word list catches the ones written plainly ("Amsterdam Centraal").
     */
    private boolean looksLikeAVenue() {
        for (int i = 0; i < city.length(); i++) {
            char character = city.charAt(i);
            if (character == '(' || character == ')' || Character.isDigit(character)) {
                return true;
            }
        }
        return Arrays.stream(city.toLowerCase(Locale.ROOT).split("[^\\p{L}]+"))
                     .anyMatch(VENUE_WORDS::contains);
    }
}
