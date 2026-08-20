package dev.ted.jittertravel.web;

import j2html.tags.DomContent;
import j2html.tags.specialized.DivTag;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.span;

/**
 * Renders {@link ProblemBand}s as Sunday→Saturday weeks, over a backdrop of {@link ContextBand}s.
 * Each week is a CSS grid with a day-label row on top and 0..N lane sub-rows below, one set of
 * sub-rows per {@link ProblemBand.Lane} in fixed enum order. Bands that overlap within the same
 * lane stack into extra sub-rows, and a band crossing a week boundary renders one segment per week.
 * <p>
 * Context bands are drawn <em>behind</em> the problem bands, spanning the full height of the week's
 * lanes, so a gap sits visibly inside the conference or between the legs that caused it. Each one
 * reserves a line at the bottom of the week for its label, which is why the lane block grows by one
 * row per overlapping context band: labels then stack instead of colliding, and no label ever lands
 * under a problem band.
 * <p>
 * A smaller sibling of {@code CalendarViewBuilder}, deliberately not shared with it: this grid has
 * no entry kinds, no day menus, no edit pencils, no zone toggle and no past-week collapsing, and
 * never will — see {@code docs/ProblemCalendarPlan.md}.
 */
public class ProblemCalendarViewBuilder {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    /** Height of one context label line, in em, used to stack the labels off the week's floor. */
    private static final double LABEL_LINE_EM = 1.15;

    public static String render(List<ProblemBand> bands,
                                List<ContextBand> context,
                                LocalDate rangeStart,
                                LocalDate rangeEnd,
                                LocalDate today) {
        LocalDate gridStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate gridEnd = rangeEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        List<DomContent> weekRows = new ArrayList<>();
        for (LocalDate sunday = gridStart; !sunday.isAfter(gridEnd); sunday = sunday.plusDays(7)) {
            weekRows.add(renderWeek(sunday, gridStart, today, bands, context));
        }

        return div().withClass("pc-container").with(
                div().withClass("pc-header").with(
                        div("Sunday"), div("Monday"), div("Tuesday"), div("Wednesday"),
                        div("Thursday"), div("Friday"), div("Saturday")
                ),
                each(weekRows.stream())
        ).render();
    }

    private static DivTag renderWeek(LocalDate sunday,
                                     LocalDate gridStart,
                                     LocalDate today,
                                     List<ProblemBand> allBands,
                                     List<ContextBand> allContext) {
        LocalDate saturday = sunday.plusDays(6);
        List<ProblemBand> bands = allBands.stream()
                .filter(band -> intersectsWeek(band.firstDay(), band.lastDay(), sunday, saturday))
                .sorted(Comparator.comparing(ProblemBand::firstDay).thenComparing(ProblemBand::title))
                .toList();
        List<ContextBand> context = allContext.stream()
                .filter(band -> intersectsWeek(band.firstDay(), band.lastDay(), sunday, saturday))
                .sorted(Comparator.comparing(ContextBand::firstDay).thenComparing(ContextBand::label))
                .toList();

        Map<ProblemBand, Integer> subRowOf = new HashMap<>();
        Map<ProblemBand.Lane, Integer> laneOffset = new EnumMap<>(ProblemBand.Lane.class);
        int problemRows = allocateLaneSubRows(sunday, bands, subRowOf, laneOffset);

        List<Integer> labelRowOf = packRows(context.stream()
                .map(band -> segmentColumns(band.firstDay(), band.lastDay(), sunday))
                .toList());
        int contextRows = labelRowOf.stream().mapToInt(row -> row + 1).max().orElse(0);

        // A week with nothing in it still gets one lane row, so an untroubled week reads as open
        // space rather than a thin strip of dates.
        int laneRows = Math.max(1, problemRows + contextRows);

        List<DomContent> cells = new ArrayList<>();
        for (int column = 1; column <= 7; column++) {
            cells.add(renderDayCell(sunday.plusDays(column - 1), gridStart, today));
        }
        // Lane fillers first, so the day borders and the today/past tints sit under everything;
        // then the context backdrop; then the problem bands, which paint over both.
        for (int subRow = 0; subRow < laneRows; subRow++) {
            for (int column = 1; column <= 7; column++) {
                LocalDate day = sunday.plusDays(column - 1);
                cells.add(div().withClass("pc-lane-cell" + dayStateClass(day, today))
                        .withStyle("grid-column: " + column + "; grid-row: " + (2 + subRow) + ";"));
            }
        }
        for (int i = 0; i < context.size(); i++) {
            cells.add(renderContextSegment(context.get(i), sunday, laneRows, labelRowOf.get(i)));
        }
        for (ProblemBand band : bands) {
            int gridRow = 2 + laneOffset.get(band.lane()) + subRowOf.get(band);
            cells.add(renderBandSegment(band, sunday, gridRow));
        }

        return div().withClass("pc-week")
                .withStyle("grid-template-rows: auto repeat(" + laneRows + ", auto);")
                .with(cells);
    }

    /**
     * Packs each lane's bands into sub-rows, lanes in fixed enum order. Fills {@code subRowOf} and
     * {@code laneOffset} (the sub-rows consumed by the lanes above), and returns the total.
     */
    private static int allocateLaneSubRows(LocalDate sunday,
                                           List<ProblemBand> bands,
                                           Map<ProblemBand, Integer> subRowOf,
                                           Map<ProblemBand.Lane, Integer> laneOffset) {
        int offset = 0;
        for (ProblemBand.Lane lane : ProblemBand.Lane.values()) {
            laneOffset.put(lane, offset);
            List<ProblemBand> inLane = bands.stream()
                    .filter(band -> band.lane() == lane)
                    .toList();
            List<Integer> rows = packRows(inLane.stream()
                    .map(band -> segmentColumns(band.firstDay(), band.lastDay(), sunday))
                    .toList());
            for (int i = 0; i < inLane.size(); i++) {
                subRowOf.put(inLane.get(i), rows.get(i));
            }
            offset += rows.stream().mapToInt(row -> row + 1).max().orElse(0);
        }
        return offset;
    }

    /**
     * Assigns each segment the first row whose occupied columns it does not touch, otherwise a new
     * one — the classic interval-packing that keeps overlapping items visible side by side instead
     * of on top of each other. Returns one row index per segment, in the order given.
     */
    private static List<Integer> packRows(List<int[]> segments) {
        List<List<int[]>> occupiedPerRow = new ArrayList<>();
        List<Integer> rowOf = new ArrayList<>();
        for (int[] segment : segments) {
            int chosen = -1;
            for (int row = 0; row < occupiedPerRow.size() && chosen == -1; row++) {
                boolean free = occupiedPerRow.get(row).stream()
                        .noneMatch(occupied -> overlaps(occupied, segment));
                if (free) {
                    chosen = row;
                }
            }
            if (chosen == -1) {
                chosen = occupiedPerRow.size();
                occupiedPerRow.add(new ArrayList<>());
            }
            occupiedPerRow.get(chosen).add(segment);
            rowOf.add(chosen);
        }
        return rowOf;
    }

    private static DomContent renderDayCell(LocalDate date, LocalDate gridStart, LocalDate today) {
        boolean isFirstCellOfGrid = date.equals(gridStart);
        boolean isMonthStart = date.getDayOfMonth() == 1 || isFirstCellOfGrid;
        String cellClass = "pc-day-cell" + (isMonthStart ? " is-month-start" : "") + dayStateClass(date, today);
        return div().withClass(cellClass).with(
                span(formatDayLabel(date, isMonthStart, isFirstCellOfGrid)).withClass("pc-day-number")
        );
    }

    /**
     * One week's worth of a context band: full lane height, so the problem bands sit inside it, with
     * its label on the week's floor lifted clear of any label below it.
     */
    private static DomContent renderContextSegment(ContextBand band, LocalDate sunday, int laneRows, int labelRow) {
        int[] segment = segmentColumns(band.firstDay(), band.lastDay(), sunday);
        int span = segment[1] - segment[0] + 1;
        String classes = "pc-context" + continuationClasses(band.firstDay(), band.lastDay(), sunday, "pc-context");
        String style = "grid-column: " + segment[0] + " / span " + span
                       + "; grid-row: 2 / span " + laneRows + ";";
        if (labelRow > 0) {
            style += String.format(Locale.ENGLISH, " padding-bottom: %.2fem;", labelRow * LABEL_LINE_EM);
        }
        return div().withClass(classes).withStyle(style).with(
                span(band.label()).withClass("pc-context-label")
        );
    }

    /**
     * One week's worth of a problem band. The title repeats on every segment: a hole in the
     * schedule that spans a week boundary is still "no hotel in London" on the far side, and a
     * nameless continuation would have to be traced back to the previous row to be read.
     */
    private static DomContent renderBandSegment(ProblemBand band, LocalDate sunday, int gridRow) {
        int[] segment = segmentColumns(band.firstDay(), band.lastDay(), sunday);
        int span = segment[1] - segment[0] + 1;
        String classes = "pc-band pc-band--" + band.lane().name().toLowerCase(Locale.ENGLISH)
                         + continuationClasses(band.firstDay(), band.lastDay(), sunday, "pc-band");
        return div().withClass(classes)
                .withStyle("grid-column: " + segment[0] + " / span " + span + "; grid-row: " + gridRow + ";")
                .with(
                        div(band.title()).withClass("pc-band-title"),
                        div(band.detail()).withClass("pc-band-detail")
                );
    }

    /** Squares off the side on which the band runs into an adjacent week. */
    private static String continuationClasses(LocalDate firstDay, LocalDate lastDay, LocalDate sunday, String prefix) {
        String classes = "";
        if (firstDay.isBefore(sunday)) {
            classes += " " + prefix + "--from-left";
        }
        if (lastDay.isAfter(sunday.plusDays(6))) {
            classes += " " + prefix + "--to-right";
        }
        return classes;
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

    private static int[] segmentColumns(LocalDate firstDay, LocalDate lastDay, LocalDate sunday) {
        LocalDate weekEnd = sunday.plusDays(6);
        LocalDate segmentStart = firstDay.isBefore(sunday) ? sunday : firstDay;
        LocalDate segmentEnd = lastDay.isAfter(weekEnd) ? weekEnd : lastDay;
        return new int[]{
                (int) ChronoUnit.DAYS.between(sunday, segmentStart) + 1,
                (int) ChronoUnit.DAYS.between(sunday, segmentEnd) + 1
        };
    }

    private static boolean intersectsWeek(LocalDate firstDay, LocalDate lastDay, LocalDate sunday, LocalDate saturday) {
        return !lastDay.isBefore(sunday) && !firstDay.isAfter(saturday);
    }

    private static boolean overlaps(int[] a, int[] b) {
        return a[0] <= b[1] && b[0] <= a[1];
    }

    private static String formatDayLabel(LocalDate date, boolean isMonthStart, boolean isFirstCellOfGrid) {
        if (!isMonthStart) {
            return String.valueOf(date.getDayOfMonth());
        }
        return date.format(date.getMonth() == Month.JANUARY || isFirstCellOfGrid
                ? MONTH_DAY_YEAR
                : MONTH_DAY);
    }
}
