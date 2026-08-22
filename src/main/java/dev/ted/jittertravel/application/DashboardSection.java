package dev.ted.jittertravel.application;

import java.util.List;

/**
 * One group of the conference dashboard and the conferences in it, ready for rendering.
 * <p>
 * Never empty: {@link ConferenceDashboard} leaves a group out entirely rather than emitting a heading
 * over nothing.
 */
public record DashboardSection(
        DashboardGroup group,
        List<ConferenceView> conferences
) {
    public DashboardSection {
        conferences = List.copyOf(conferences);
    }
}
