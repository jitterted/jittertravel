package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarViewBuilderTest {

    @Test
    void emptyRangeRendersDayLabelsAndNoLaneRows() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 6, 5)
        );

        // No entries means every week collapses to its day-label row only.
        assertThat(html).contains("day-label-cell");
        assertThat(html).doesNotContain("class=\"entry");
        // Collapsed weeks use only the auto header row.
        assertThat(html).contains("grid-template-rows: auto;");
    }

    @Test
    void monthStartCellsGetIsMonthStartClassOnDayLabelCell() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 6, 5)
        );

        // First visible cell (Sunday May 24, 2026) and June 1 are month-starts.
        assertThat(html).contains(">May 24, 2026<");
        assertThat(html).contains(">Jun 1<");
        // The is-month-start L-border applies to day-label cells now.
        assertThat(html).contains("day-label-cell month-tint-odd is-month-start");
    }

    @Test
    void januaryMonthStartIncludesYearOnDayLabelCell() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 12, 28),
                LocalDate.of(2027, 1, 5)
        );

        assertThat(html).contains(">Jan 1, 2027<");
    }

    @Test
    void conferenceEntryRendersWithTitleAndLocation() {
        CalendarEntry conf = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 4, 17, 0),
                "DevConf",
                "(Portland, USA)",
                "DevConf cont'd",
                "(Portland, USA)"
        );

        String html = CalendarViewBuilder.render(
                List.of(conf),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 6, 5)
        );

        assertThat(html).contains("entry entry--conference");
        assertThat(html).contains(">DevConf<");
        assertThat(html).contains(">(Portland, USA)<");
        // The conference week now has 1 lane sub-row.
        assertThat(html).contains("grid-template-rows: auto repeat(1, auto);");
    }

    @Test
    void flightAndConferenceAcrossWeekBoundaryRenderInSeparateLanes() {
        // Flight UA59: SFO->FRA, departs Sat 2026-06-06 13:55, arrives Sun 2026-06-07 09:45.
        CalendarEntry flight = new CalendarEntry(
                EntryKind.FLIGHT,
                LocalDateTime.of(2026, 6, 6, 13, 55),
                LocalDateTime.of(2026, 6, 7, 9, 45),
                "✈️ SFO\u2192FRA",
                "Departs 1:55 PM",
                null,
                "Arr 9:45 AM"
        );
        // Conference DDD Europe 2026: Sun 2026-06-07 11:00 -> Wed 2026-06-10 17:00.
        CalendarEntry conf = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 7, 11, 0),
                LocalDateTime.of(2026, 6, 10, 17, 0),
                "DDD Europe 2026",
                "(Frankfurt, Germany)",
                "DDD Europe 2026 cont'd",
                "(Frankfurt, Germany)"
        );

        String html = CalendarViewBuilder.render(
                List.of(conf, flight),
                LocalDate.of(2026, 6, 6),
                LocalDate.of(2026, 6, 10)
        );

        // Both entry titles appear (flight title only on departure segment).
        assertThat(html).contains(">✈\uFE0F SFO\u2192FRA<");
        assertThat(html).contains(">Departs 1:55 PM<");
        assertThat(html).contains(">DDD Europe 2026<");
        assertThat(html).contains(">(Frankfurt, Germany)<");
        // The continuation flight segment shows ONLY the arrival subtitle, no title.
        assertThat(html).contains(">Arr 9:45 AM<");
        long titleCount = html.split(">✈\uFE0F SFO\u2192FRA<", -1).length - 1;
        assertThat(titleCount).isEqualTo(1);

        // Both kinds of entry cells are present.
        assertThat(html).contains("entry entry--flight");
        assertThat(html).contains("entry entry--conference");
        // The continuation segment is marked as such.
        assertThat(html).contains("entry--continuation");
    }

    @Test
    void overlappingEntriesInSameLaneStackIntoSubRows() {
        CalendarEntry a = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 4, 17, 0),
                "ConfA", "(City, Country)",
                "ConfA cont'd", "(City, Country)"
        );
        CalendarEntry b = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 5, 17, 0),
                "ConfB", "(City, Country)",
                "ConfB cont'd", "(City, Country)"
        );

        String html = CalendarViewBuilder.render(
                List.of(a, b),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 6)
        );

        // Two overlapping conferences -> 2 sub-rows in the conference lane.
        assertThat(html).contains("grid-template-rows: auto repeat(2, auto);");
        assertThat(html).contains(">ConfA<");
        assertThat(html).contains(">ConfB<");
    }

    @Test
    void fixedLaneOrderingPlacesConferencesAboveFlights() {
        // Both occupy the same week so we have two lanes stacked.
        CalendarEntry conf = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 8, 9, 0),
                LocalDateTime.of(2026, 6, 8, 17, 0),
                "Conf", "(City, Country)",
                "Conf cont'd", "(City, Country)"
        );
        CalendarEntry flight = new CalendarEntry(
                EntryKind.FLIGHT,
                LocalDateTime.of(2026, 6, 9, 9, 0),
                LocalDateTime.of(2026, 6, 9, 13, 0),
                "✈️ A\u2192B", "Departs 9:00 AM",
                null, "Arr 1:00 PM"
        );

        String html = CalendarViewBuilder.render(
                List.of(flight, conf),  // intentionally out of lane order
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 13)
        );

        // Two lane sub-rows.
        assertThat(html).contains("grid-template-rows: auto repeat(2, auto);");
        // Conference must be on grid-row 2 (first lane), flight on grid-row 3 (second lane).
        int confIndex = html.indexOf("grid-row: 2;");
        int flightIndex = html.indexOf("grid-row: 3;");
        assertThat(confIndex).isPositive();
        assertThat(flightIndex).isPositive();
        // And the conference entry actually sits on row 2.
        int confTitle = html.indexOf(">Conf<");
        int row2 = html.lastIndexOf("grid-row: 2;", confTitle);
        assertThat(row2).isPositive();
    }
}
