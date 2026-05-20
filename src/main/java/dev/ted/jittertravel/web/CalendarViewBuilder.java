package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TentativeConferenceView;
import j2html.tags.DomContent;
import j2html.tags.specialized.DivTag;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static j2html.TagCreator.div;
import static j2html.TagCreator.each;

public class CalendarViewBuilder {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy");

    public static String render(List<TentativeConferenceView> conferences, LocalDate rangeStart, LocalDate rangeEnd) {
        // Align boundaries to Sunday and Saturday grid edges
        LocalDate gridStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate gridEnd = rangeEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        List<DomContent> weekRows = new ArrayList<>();
        LocalDate currentSunday = gridStart;

        while (!currentSunday.isAfter(gridEnd)) {
            LocalDate saturday = currentSunday.plusDays(6);
            List<DomContent> weekCells = new ArrayList<>();
            LocalDate dayRunner = currentSunday;

            while (!dayRunner.isAfter(saturday)) {
                final LocalDate currentDate = dayRunner;

                // Find intersecting conference
                TentativeConferenceView activeConf = conferences.stream()
                    .filter(c -> !currentDate.isBefore(c.startDate().toLocalDate()) && !currentDate.isAfter(c.endDate().toLocalDate()))
                    .findFirst()
                    .orElse(null);

                if (activeConf != null) {
                    LocalDate segmentStart = activeConf.startDate().toLocalDate().isBefore(currentSunday) ? currentSunday : activeConf.startDate().toLocalDate();
                    LocalDate segmentEnd = activeConf.endDate().toLocalDate().isAfter(saturday) ? saturday : activeConf.endDate().toLocalDate();

                    int span = (int) ChronoUnit.DAYS.between(segmentStart, segmentEnd) + 1;
                    boolean isContinuation = activeConf.startDate().toLocalDate().isBefore(currentSunday);
                    boolean isNativeEnd = !activeConf.endDate().toLocalDate().isAfter(saturday);
                    String classList = monthTintCssFrom(segmentStart, isContinuation, isNativeEnd);

                    DivTag eventGrid = div().withClass("event-grid")
                            .withStyle("grid-template-columns: repeat(" + span + ", 1fr)");

                    LocalDate d = segmentStart;
                    while (!d.isAfter(segmentEnd)) {
                        boolean dIsFirstCellOfGrid = d.equals(gridStart);
                        boolean dIsMonthStart = d.getDayOfMonth() == 1 || dIsFirstCellOfGrid;
                        String dLabel = formatDayLabel(d, dIsMonthStart, dIsFirstCellOfGrid);
                        String subCellClass = "event-day" + (dIsMonthStart ? " is-month-start" : "");
                        String dnClass = "day-number" + (dIsMonthStart ? " is-month-start" : "");
                        eventGrid.with(
                                div().withClass(subCellClass).with(
                                        div(dLabel).withClass(dnClass)
                                )
                        );
                        d = d.plusDays(1);
                    }

                    eventGrid.with(
                            div().withClass("event-details").with(
                                    div(activeConf.name() + (isContinuation ? " cont'd" : "")).withClass("event-title"),
                                    div("(" + activeConf.city() + ", " + activeConf.country() + ")").withClass("event-location")
                            )
                    );

                    weekCells.add(
                            div().withClass(classList).withStyle("grid-column-end: span " + span).with(eventGrid)
                    );

                    dayRunner = segmentEnd.plusDays(1);
                } else {
                    boolean isFirstCellOfGrid = currentDate.equals(gridStart);
                    boolean isMonthStart = currentDate.getDayOfMonth() == 1 || isFirstCellOfGrid;
                    String monthTintClass = (currentDate.getMonthValue() % 2 == 0) ? "month-tint-even" : "month-tint-odd";
                    String monthStartClass = isMonthStart ? " is-month-start" : "";
                    String dayLabel = formatDayLabel(currentDate, isMonthStart, isFirstCellOfGrid);
                    String dayNumberClass = "day-number" + (isMonthStart ? " is-month-start" : "");

                    // Empty standard cell
                    weekCells.add(
                        div().withClass("calendar-cell " + monthTintClass + monthStartClass).with(
                            div(dayLabel).withClass(dayNumberClass)
                        )
                    );
                    dayRunner = dayRunner.plusDays(1);
                }
            }

            weekRows.add(div().withClass("calendar-week").with(weekCells));
            currentSunday = currentSunday.plusDays(7);
        }

        // Wrap everything into the final page body container
        return div().withClass("calendar-container").with(
            div().withClass("calendar-header").with(
                div("Sunday"), div("Monday"), div("Tuesday"), div("Wednesday"),
                div("Thursday"), div("Friday"), div("Saturday")
            ),
            each(weekRows.stream())
        ).render();
    }

    private static String monthTintCssFrom(LocalDate segmentStart, boolean isContinuation, boolean isNativeEnd) {
        String monthTintClass = (segmentStart.getMonthValue() % 2 == 0) ? "month-tint-even" : "month-tint-odd";

        // The whole event renders as ONE cell that spans all its days. The amber
        // month-break border is applied to an inner per-day sub-cell so the L-shape
        // appears at the column boundary of the new-month day, not at the parent's edge.
        return "calendar-cell has-event " + monthTintClass
            + (isContinuation ? " is-continuation" : "")
            + (!isNativeEnd ? " not-native-end" : "");
    }

    private static String formatDayLabel(LocalDate date, boolean isMonthStart, boolean isFirstCellOfGrid) {
        if (!isMonthStart) {
            return String.valueOf(date.getDayOfMonth());
        }
        // Include year for January (year transition) or for the very first visible cell.
        DateTimeFormatter formatter = (date.getMonth() == Month.JANUARY || isFirstCellOfGrid)
                ? MONTH_DAY_YEAR
                : MONTH_DAY;
        return date.format(formatter);
    }

}