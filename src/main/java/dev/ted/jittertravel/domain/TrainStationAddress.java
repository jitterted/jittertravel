package dev.ted.jittertravel.domain;

/**
 * A train station: the building's own {@code name} and the {@code city} it stands in. {@code Place}
 * reads the city, so the city is compared and not merely displayed.
 *
 * <p>Normalized on the way in for the same reason as {@link Address} — a stray space makes a city
 * that looks identical compare as a different one. This is not hypothetical for stations: the
 * production log already carries {@code " Frankfurt(M) Flughafen Fernbf"} and
 * {@code "London St Pancras Int'l (STP) East "}. Those landed in {@code name}, which nothing
 * matches on, so they broke nothing; one in {@code city} would.
 */
public record TrainStationAddress(
        String name,
        String city,
        String country,
        String mapsUrl
) {
    public TrainStationAddress {
        name = normalized(name);
        city = normalized(city);
        country = normalized(country);
        mapsUrl = normalized(mapsUrl);
    }

    /** Null and surrounding whitespace both mean "nothing was typed here". */
    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
