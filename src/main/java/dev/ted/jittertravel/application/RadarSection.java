package dev.ted.jittertravel.application;

import java.util.List;

/**
 * One group of the conference radar and the conferences in it, ready for rendering.
 * <p>
 * Never empty: {@link ConferenceRadar} leaves a group out entirely rather than emitting a heading
 * over nothing.
 */
public record RadarSection(
        RadarGroup group,
        List<ConferenceView> conferences
) {
    public RadarSection {
        conferences = List.copyOf(conferences);
    }
}
