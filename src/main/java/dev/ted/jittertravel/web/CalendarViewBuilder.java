package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import j2html.tags.DomContent;
import j2html.tags.specialized.DivTag;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static j2html.TagCreator.*;

/**
 * Renders the calendar as Sunday→Saturday weeks. Each week is a CSS grid with
 * a day-label row on top and 0..N "swimlane" sub-rows below, one set of sub-rows
 * per {@link EntryKind} (in fixed {@code EnumKind.values()} order). Entries that
 * overlap within the same lane stack vertically into additional sub-rows.
 * <p>
 * Weeks containing no entries collapse to just the day-label row.
 */
public class CalendarViewBuilder {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy");

    public static String render(List<CalendarEntry> entries, LocalDate rangeStart, LocalDate rangeEnd, LocalDate today, boolean isPublicUser) {
        LocalDate gridStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate gridEnd = rangeEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        List<DomContent> weekRows = new ArrayList<>();
        LocalDate sunday = gridStart;
        while (!sunday.isAfter(gridEnd)) {
            weekRows.add(renderWeek(sunday, sunday.plusDays(6), gridStart, today, entries, isPublicUser));
            sunday = sunday.plusDays(7);
        }

        return div().withClass("calendar-container").with(
                div().withClass("calendar-header").with(
                        div("Sunday"), div("Monday"), div("Tuesday"), div("Wednesday"),
                        div("Thursday"), div("Friday"), div("Saturday")
                ),
                each(weekRows.stream())
        ).render();
    }

    private static DivTag renderWeek(LocalDate sunday,
                                     LocalDate saturday,
                                     LocalDate gridStart,
                                     LocalDate today,
                                     List<CalendarEntry> allEntries,
                                     boolean isPublicUser) {
        List<CalendarEntry> intersecting = allEntries.stream()
                .filter(e -> intersectsWeek(e, sunday, saturday))
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();

        // Group by kind into fixed enum order; allocate sub-rows per lane.
        Map<EntryKind, List<CalendarEntry>> byKind = new EnumMap<>(EntryKind.class);
        for (EntryKind kind : EntryKind.values()) {
            byKind.put(kind, new ArrayList<>());
        }
        for (CalendarEntry entry : intersecting) {
            byKind.get(entry.kind()).add(entry);
        }

        Map<CalendarEntry, Integer> subRowOf = new HashMap<>();
        Map<EntryKind, Integer> subRowCount = new EnumMap<>(EntryKind.class);
        for (EntryKind kind : EntryKind.values()) {
            List<int[]> ranges = new ArrayList<>();  // index = sub-row; value = list of occupied [startCol,endCol]
            List<List<int[]>> perRow = new ArrayList<>();
            for (CalendarEntry entry : byKind.get(kind)) {
                int[] segment = segmentColumns(entry, sunday);
                int chosen = -1;
                for (int i = 0; i < perRow.size(); i++) {
                    boolean clash = false;
                    for (int[] occupied : perRow.get(i)) {
                        if (overlaps(occupied, segment)) {
                            clash = true;
                            break;
                        }
                    }
                    if (!clash) {
                        chosen = i;
                        break;
                    }
                }
                if (chosen == -1) {
                    chosen = perRow.size();
                    perRow.add(new ArrayList<>());
                }
                perRow.get(chosen).add(segment);
                subRowOf.put(entry, chosen);
            }
            subRowCount.put(kind, perRow.size());
        }

        // kindOffset[k] = total sub-rows occupied by lanes appearing before k
        Map<EntryKind, Integer> kindOffset = new EnumMap<>(EntryKind.class);
        int offset = 0;
        for (EntryKind kind : EntryKind.values()) {
            kindOffset.put(kind, offset);
            offset += subRowCount.get(kind);
        }
        int totalSubRows = offset;

        List<DomContent> cells = new ArrayList<>();

        // Day-label row (grid-row: 1, columns 1..7)
        for (int i = 0; i < 7; i++) {
            cells.add(renderDayLabelCell(sunday.plusDays(i), gridStart, today, isPublicUser));
        }

        // Per-day lane filler cells, one per (column × lane sub-row), so that the
        // calendar day-borders and month-tint background extend down through the
        // entire week. Entries are rendered after, so they stack on top and cover
        // any cells they occupy.
        for (int subRow = 0; subRow < totalSubRows; subRow++) {
            int gridRow = 2 + subRow;
            for (int col = 1; col <= 7; col++) {
                LocalDate d = sunday.plusDays(col - 1);
                String tint = (d.getMonthValue() % 2 == 0) ? "month-tint-even" : "month-tint-odd";
                cells.add(div().withClass("lane-cell " + tint + dayStateClass(d, today))
                        .withStyle("grid-column: " + col + "; grid-row: " + gridRow + ";"));
            }
        }

        // Entry segments
        for (CalendarEntry entry : intersecting) {
            int[] seg = segmentColumns(entry, sunday);
            int startCol = seg[0];
            int span = seg[1] - seg[0] + 1;
            int gridRow = 2 + kindOffset.get(entry.kind()) + subRowOf.get(entry);
            boolean isContinuation = entry.start().toLocalDate().isBefore(sunday);
            boolean isFinalSegment = !entry.end().toLocalDate().isAfter(sunday.plusDays(6));
            cells.add(renderEntrySegment(entry, startCol, span, gridRow, isContinuation, isFinalSegment));
        }

        String rowsStyle = totalSubRows == 0
                ? "grid-template-rows: auto;"
                : "grid-template-rows: auto repeat(" + totalSubRows + ", auto);";

        return div().withClass("calendar-week").withStyle(rowsStyle).with(cells);
    }

    private static DomContent renderDayLabelCell(LocalDate date, LocalDate gridStart, LocalDate today, boolean isPublicUser) {
        boolean isFirstCellOfGrid = date.equals(gridStart);
        boolean isMonthStart = date.getDayOfMonth() == 1 || isFirstCellOfGrid;
        String monthTint = (date.getMonthValue() % 2 == 0) ? "month-tint-even" : "month-tint-odd";
        String labelClass = "day-label-cell " + monthTint + (isMonthStart ? " is-month-start" : "") + dayStateClass(date, today);
        String dayNumberClass = "day-number" + (isMonthStart ? " is-month-start" : "");
        String label = formatDayLabel(date, isMonthStart, isFirstCellOfGrid);
        DomContent dayNumber = isPublicUser
                ? span(label).withClass(dayNumberClass)
                : a(label).withHref("/itinerary?date=" + date).withClass(dayNumberClass);
        return div().withClass(labelClass).with(dayNumber);
    }

    private static DomContent renderEntrySegment(CalendarEntry entry,
                                                 int startCol,
                                                 int span,
                                                 int gridRow,
                                                 boolean isContinuation,
                                                 boolean isFinalSegment) {
        String kindClass = "entry--" + entry.kind().name().toLowerCase();
        String classes = "entry " + kindClass + (isContinuation ? " entry--continuation" : "");
        String style = "grid-column: " + startCol + " / span " + span
                + "; grid-row: " + gridRow + ";";
        if (entry.kind() == EntryKind.LODGING && isFinalSegment) {
            double pct = (span - 1.0) / span * 100.0;
            style += String.format(
                    " background: linear-gradient(to right, var(--entry-lodging-bg) %.4f%%, #bbf7d0 %.4f%%);",
                    pct, pct);
        }

        String title = isContinuation ? entry.continuationTitle() : entry.mainTitle();
        List<String> subtitle = isContinuation ? entry.continuationSubTitle() : entry.subTitle();

        DivTag div = div().withClass(classes).withStyle(style);
        if (title != null) {
            DomContent titleContent = (entry.mapsUrl() != null && !isContinuation)
                    ? a(title).withHref(entry.mapsUrl()).withTarget("_blank").withRel("noopener").withClass("entry-title")
                    : div(title).withClass("entry-title");
            div.with(titleContent);
        }
        if (subtitle != null) {
            for (String line : subtitle) {
                div.with(div(line).withClass("entry-subtitle"));
            }
        }
        return div;
    }

    /** Past days are hatched; today gets the accent-column treatment. */
    private static String dayStateClass(LocalDate date, LocalDate today) {
        if (date.isBefore(today)) {
            return " is-past";
        }
        if (date.equals(today)) {
            return " is-today";
        }
        return "";
    }

    private static int[] segmentColumns(CalendarEntry entry, LocalDate sunday) {
        LocalDate segStart = entry.start().toLocalDate().isBefore(sunday) ? sunday : entry.start().toLocalDate();
        LocalDate weekEnd = sunday.plusDays(6);
        LocalDate segEnd = entry.end().toLocalDate().isAfter(weekEnd) ? weekEnd : entry.end().toLocalDate();
        int startCol = (int) ChronoUnit.DAYS.between(sunday, segStart) + 1;
        int endCol = (int) ChronoUnit.DAYS.between(sunday, segEnd) + 1;
        return new int[]{startCol, endCol};
    }

    private static boolean intersectsWeek(CalendarEntry entry, LocalDate sunday, LocalDate saturday) {
        LocalDate entryStart = entry.start().toLocalDate();
        LocalDate entryEnd = entry.end().toLocalDate();
        return !entryEnd.isBefore(sunday) && !entryStart.isAfter(saturday);
    }

    private static boolean overlaps(int[] a, int[] b) {
        return a[0] <= b[1] && b[0] <= a[1];
    }

    private static String formatDayLabel(LocalDate date, boolean isMonthStart, boolean isFirstCellOfGrid) {
        if (!isMonthStart) {
            return String.valueOf(date.getDayOfMonth());
        }
        DateTimeFormatter formatter = (date.getMonth() == Month.JANUARY || isFirstCellOfGrid)
                ? MONTH_DAY_YEAR
                : MONTH_DAY;
        return date.format(formatter);
    }
}
