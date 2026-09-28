package dev.ted.jittertravel.application;

import java.util.List;

/**
 * One group of the conference dashboard and the rows in it, ready for rendering.
 * <p>
 * Never empty: {@link ConferenceDashboard} leaves a group out entirely rather than emitting a heading
 * over nothing.
 */
public record DashboardSection(
        DashboardGroup group,
        List<DashboardRow> rows
) {
    public DashboardSection {
        rows = List.copyOf(rows);
    }
}
