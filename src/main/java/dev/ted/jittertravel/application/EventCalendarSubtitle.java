package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the owner (unredacted) subtitle lines shared by the single-day venue-event calendar
 * projectors — a gathering, and now a private event: an optional venue name, then the
 * "City, Country", then the start–end time {@link SubtitleLine.Range}. The range keeps its
 * {@link ZonedTimestamp}s so the renderer can emit a {@code <time>} element.
 * <p>
 * This is NOT a redaction path: it is the full owner view. What an anonymous viewer sees is built
 * separately by {@link PublicCalendarProjector}, which never routes through this helper.
 */
public class EventCalendarSubtitle {

    public List<SubtitleLine> venueLocationAndTime(String venueName, Address location,
                                                   ZonedTimestamp startsAt, ZonedTimestamp endsAt) {
        List<SubtitleLine> lines = new ArrayList<>();
        if (!venueName.isBlank()) {
            lines.add(new SubtitleLine.Text(venueName));
        }
        lines.add(new SubtitleLine.Text(cityCountry(location)));
        lines.add(new SubtitleLine.Range(startsAt, endsAt));
        return List.copyOf(lines);
    }

    /** "City, Country" — or just the city when no country was recorded. */
    private String cityCountry(Address location) {
        return location.country().isBlank()
                ? location.city()
                : location.city() + ", " + location.country();
    }
}
