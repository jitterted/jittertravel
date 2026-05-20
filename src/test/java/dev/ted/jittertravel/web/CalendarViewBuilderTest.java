package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TentativeConferenceView;
import dev.ted.jittertravel.domain.ConferenceId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarViewBuilderTest {

    @Test
    void monthStartCellsGetIsMonthStartClassAndMonthDayLabel() {
        // Range that crosses a month boundary: May 28 - June 5, 2026
        List<TentativeConferenceView> conferences = List.of();
        String html = CalendarViewBuilder.render(
                conferences,
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 6, 5)
        );

        // The first visible cell (Sunday May 24, 2026) should be a month-start with full label
        assertThat(html).contains(">May 24, 2026<");
        // June 1 (Monday) must be flagged as a new-month start with "Jun 1" label
        assertThat(html).contains(">Jun 1<");
        // Regular days in May render as bare day numbers (sanity)
        assertThat(html).contains(">29<");
        assertThat(html).contains(">30<");
        // is-month-start class appears on the relevant cells
        long monthStartCount = html.split("is-month-start", -1).length - 1;
        assertThat(monthStartCount).isGreaterThanOrEqualTo(2); // first visible cell + June 1
    }

    @Test
    void cellsAreTaggedWithAlternatingMonthTintClasses() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 6, 5)
        );

        // May (month 5, odd) -> month-tint-odd; June (month 6, even) -> month-tint-even
        assertThat(html).contains("month-tint-odd");
        assertThat(html).contains("month-tint-even");
    }

    @Test
    void januaryMonthStartIncludesYear() {
        String html = CalendarViewBuilder.render(
                List.of(),
                LocalDate.of(2026, 12, 28),
                LocalDate.of(2027, 1, 5)
        );

        // January 1, 2027 should render with year
        assertThat(html).contains(">Jan 1, 2027<");
    }

    @Test
    void eventSpanningMonthBoundaryIsOneCellWithInnerMonthEdgeBorder() {
        // Event runs Jun 29 - Jul 2, 2026 (the example from the user's request).
        // Both weeks involved (Jun 28-Jul 4) hold the full event in a single week,
        // so we expect ONE has-event cell containing the full event title once,
        // with an inner .event-day for Jul 1 carrying the .is-month-start class.
        TentativeConferenceView conf = new TentativeConferenceView(
                ConferenceId.of(UUID.randomUUID()),
                "MonthCrosser",
                LocalDateTime.of(2026, 6, 29, 9, 0),
                LocalDateTime.of(2026, 7, 2, 17, 0),
                "Portland",
                "USA"
        );

        String html = CalendarViewBuilder.render(
                List.of(conf),
                LocalDate.of(2026, 6, 27),
                LocalDate.of(2026, 7, 3)
        );

        // Title appears exactly once (no duplication across the month split)
        long titleOccurrences = html.split(">MonthCrosser<", -1).length - 1;
        assertThat(titleOccurrences).isEqualTo(1);

        // Exactly one has-event cell for the spanning event
        long eventCellCount = html.split("\"calendar-cell has-event", -1).length - 1;
        assertThat(eventCellCount).isEqualTo(1);

        // Every event day shows its own date label
        assertThat(html).contains(">29<");
        assertThat(html).contains(">30<");
        assertThat(html).contains(">Jul 1<");
        assertThat(html).contains(">2<");

        // The Jul 1 sub-cell gets the L-shaped amber border via .event-day.is-month-start
        assertThat(html).contains("class=\"event-day is-month-start\"");

        // Old split/inline-marker artifacts are gone
        assertThat(html).doesNotContain("month-break-inline");
        assertThat(html).doesNotContain("crosses-month");
    }

    @Test
    void existingEventRenderingStillWorks() {
        TentativeConferenceView conf = new TentativeConferenceView(
                ConferenceId.of(UUID.randomUUID()),
                "DevConf",
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 4, 17, 0),
                "Portland",
                "USA"
        );

        String html = CalendarViewBuilder.render(
                List.of(conf),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 6, 5)
        );

        assertThat(html).contains("has-event");
        assertThat(html).contains("DevConf");
        assertThat(html).contains("Portland");
    }
}
