package dev.ted.jittertravel.domain;

/**
 * A postal address as Ted typed it, normalized on the way in: every field is non-null and carries
 * no surrounding whitespace.
 *
 * <p><strong>Why the trim is here and not at the form.</strong> A city is compared, not just
 * displayed — {@code Place} derives a place from {@code locationForMatching}, and the schedule
 * asks whether two of them name the same city. That comparison is case-insensitive but exact
 * otherwise, so {@code "Hamburg "} and {@code "Hamburg"} are two different cities to it, while HTML
 * collapses the space and shows them as one. In production (event 92, 2026-08-30) that read as
 * "No travel — Hamburg → Hamburg" and a missing hotel for nights the Hamburg booking covered.
 * The space came from an iPhone keyboard: committing an autocorrect suggestion with the space bar
 * leaves the space behind, and nothing between the keyboard and here removes it — {@code
 * type="text"} submits its value verbatim.
 *
 * <p>Normalizing in the compact constructor rather than at the boundary means it also applies when
 * Jackson binds a <em>stored</em> payload, so events already written with a stray space read clean
 * on every replay without rewriting a single row. The stored JSON is untouched (backup and restore
 * copy it verbatim); this is a read-time normalization, in the same spirit as the upcaster, and it
 * changes no event's shape.
 */
public record Address(
        String street,
        String city,
        String region,
        String postalCode,
        String country,
        String locationForMatching
) {
    public Address {
        street = normalized(street);
        city = normalized(city);
        region = normalized(region);
        postalCode = normalized(postalCode);
        country = normalized(country);
        locationForMatching = normalized(locationForMatching);
        // Checked after normalizing, so a field holding only spaces falls back to the city rather
        // than becoming a place name made of whitespace.
        if (locationForMatching.isEmpty()) {
            locationForMatching = city;
        }
    }

    /** Null and surrounding whitespace both mean "nothing was typed here". */
    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
