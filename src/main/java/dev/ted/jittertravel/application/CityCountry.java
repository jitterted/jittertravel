package dev.ted.jittertravel.application;

/**
 * A location as it appears in event data (hotels, train stations, gatherings, conferences) — the
 * unit the zone audit resolves and reports on. The {@code region} (state/province) is carried
 * because {@code LocationZoneResolver} resolves on it for multi-zone countries; auditing without it
 * would report a location as unresolvable that live entry resolves fine. Train stations have no
 * region and pass {@code ""}.
 */
public record CityCountry(String city, String region, String country) {

    public CityCountry {
        city = city != null ? city : "";
        region = region != null ? region : "";
        country = country != null ? country : "";
    }

    public CityCountry(String city, String country) {
        this(city, "", country);
    }

    /** A human-readable label for the audit, e.g. {@code "Lone Tree, CO, USA"}. */
    public String label() {
        StringBuilder label = new StringBuilder();
        appendPart(label, city);
        appendPart(label, region);
        appendPart(label, country);
        return label.isEmpty() ? "(no location)" : label.toString();
    }

    private static void appendPart(StringBuilder label, String part) {
        if (part.isBlank()) {
            return;
        }
        if (!label.isEmpty()) {
            label.append(", ");
        }
        label.append(part);
    }
}
