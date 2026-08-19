package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import dev.ted.jittertravel.application.SubtitleLine;
import dev.ted.jittertravel.domain.ZonedTimestamp;
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
    private static final String TIME_OF_DAY_FORMAT = "h:mm a";

    // Shared with the itinerary view: a pencil always means "edit this booking". stroke uses
    // currentColor so the icon picks up each entry kind's foreground tint.
    private static final String PENCIL_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M12 20h9\"/><path d=\"M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z\"/></svg>";

    public static String render(List<CalendarEntry> entries, LocalDate rangeStart, LocalDate rangeEnd, LocalDate today, boolean isPublicUser) {
        return render(entries, rangeStart, rangeEnd, today, isPublicUser, false);
    }

    public static String render(List<CalendarEntry> entries, LocalDate rangeStart, LocalDate rangeEnd, LocalDate today, boolean isPublicUser, boolean isOwner) {
        LocalDate gridStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate gridEnd = rangeEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        // Weeks entirely before the week containing "today" collapse to their day-label
        // row; if they carry entries, those entries are kept in the markup (hidden) so a
        // click can reveal them without a server round-trip.
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

        List<DomContent> weekRows = new ArrayList<>();
        boolean anyCollapsedWithEntries = false;
        LocalDate sunday = gridStart;
        while (!sunday.isAfter(gridEnd)) {
            LocalDate weekStart = sunday;
            LocalDate saturday = sunday.plusDays(6);
            boolean collapsed = saturday.isBefore(currentWeekStart);
            if (collapsed && entries.stream().anyMatch(e -> intersectsWeek(e, weekStart, saturday))) {
                anyCollapsedWithEntries = true;
            }
            weekRows.add(renderWeek(sunday, saturday, gridStart, today, entries, isPublicUser, isOwner, collapsed));
            sunday = sunday.plusDays(7);
        }

        DivTag container = div().withClass("calendar-container").with(
                div().withClass("calendar-header").with(
                        div("Sunday"), div("Monday"), div("Tuesday"), div("Wednesday"),
                        div("Thursday"), div("Friday"), div("Saturday")
                ),
                each(weekRows.stream())
        );

        // The toggle sits in the outer wrapper, above the container's top-right border.
        List<DomContent> outerChildren = new ArrayList<>();
        if (anyCollapsedWithEntries) {
            outerChildren.add(
                    button("Show past weeks")
                            .withId("toggle-all-weeks")
                            .withType("button")
                            .withClass("toggle-all-weeks"));
        }
        outerChildren.add(container);

        return div().withClass("calendar-outer").with(outerChildren).render();
    }

    private static DivTag renderWeek(LocalDate sunday,
                                     LocalDate saturday,
                                     LocalDate gridStart,
                                     LocalDate today,
                                     List<CalendarEntry> allEntries,
                                     boolean isPublicUser,
                                     boolean isOwner,
                                     boolean collapsed) {
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

        // Per-day entry counts (a multi-day entry counts on every day it spans). Surfaced
        // as a badge that is only visible while the week is collapsed.
        int[] dayCounts = new int[7];
        for (CalendarEntry entry : intersecting) {
            for (int i = 0; i < 7; i++) {
                LocalDate d = sunday.plusDays(i);
                if (!entry.start().toLocalDate().isAfter(d) && !entry.end().toLocalDate().isBefore(d)) {
                    dayCounts[i]++;
                }
            }
        }

        List<DomContent> cells = new ArrayList<>();

        // Day-label row (grid-row: 1, columns 1..7). Count badges are only emitted for
        // collapsed weeks, where they are the sole hint of hidden entries.
        for (int i = 0; i < 7; i++) {
            int badgeCount = collapsed ? dayCounts[i] : 0;
            cells.add(renderDayLabelCell(sunday.plusDays(i), gridStart, today, isPublicUser, isOwner, badgeCount));
        }

        // A non-collapsed week with no entries still gets one lane band so the day
        // borders and month tint extend down and the empty week reads as open space
        // rather than a thin strip of date labels. Collapsed (past) empty weeks stay
        // as their day-label row only.
        boolean emptyBand = !collapsed && totalSubRows == 0;
        int bandRows = emptyBand ? 1 : totalSubRows;

        // Per-day lane filler cells, one per (column × lane sub-row), so that the
        // calendar day-borders and month-tint background extend down through the
        // entire week. Entries are rendered after, so they stack on top and cover
        // any cells they occupy.
        for (int subRow = 0; subRow < bandRows; subRow++) {
            int gridRow = 2 + subRow;
            for (int col = 1; col <= 7; col++) {
                LocalDate d = sunday.plusDays(col - 1);
                String tint = (d.getMonthValue() % 2 == 0) ? "month-tint-even" : "month-tint-odd";
                String emptyClass = emptyBand ? " lane-cell--empty" : "";
                cells.add(div().withClass("lane-cell " + tint + dayStateClass(d, today) + emptyClass)
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
            cells.add(renderEntrySegment(entry, startCol, span, gridRow, isContinuation, isFinalSegment, isOwner));
        }

        String rowsStyle = bandRows == 0
                ? "grid-template-rows: auto;"
                : "grid-template-rows: auto repeat(" + bandRows + ", auto);";

        String weekClass = "calendar-week" + (collapsed ? " calendar-week--collapsed" : "");
        return div().withClass(weekClass).withStyle(rowsStyle).with(cells);
    }

    private static DomContent renderDayLabelCell(LocalDate date, LocalDate gridStart, LocalDate today, boolean isPublicUser, boolean isOwner, int entryCount) {
        boolean isFirstCellOfGrid = date.equals(gridStart);
        boolean isMonthStart = date.getDayOfMonth() == 1 || isFirstCellOfGrid;
        String monthTint = (date.getMonthValue() % 2 == 0) ? "month-tint-even" : "month-tint-odd";
        String labelClass = "day-label-cell " + monthTint + (isMonthStart ? " is-month-start" : "") + dayStateClass(date, today);
        String dayNumberClass = "day-number" + (isMonthStart ? " is-month-start" : "");
        String label = formatDayLabel(date, isMonthStart, isFirstCellOfGrid);
        // OWNER on a strictly-future day gets a tap-to-open disclosure menu (Open day + Add …);
        // everyone else keeps the plain behavior — an itinerary link for signed-in viewers
        // (OWNER on past/today, FAMILY on any day), a plain number for anonymous visitors.
        DomContent dayNumber;
        if (isOwner && date.isAfter(today)) {
            dayNumber = dayMenu(date, label, dayNumberClass);
        } else if (isPublicUser) {
            dayNumber = span(label).withClass(dayNumberClass);
        } else {
            dayNumber = a(label).withHref("/itinerary?date=" + date).withClass(dayNumberClass);
        }
        DivTag cell = div().withClass(labelClass).with(dayNumber);
        // Only emitted when the day has entries; CSS reveals it only in collapsed weeks.
        if (entryCount > 0) {
            cell.with(span(String.valueOf(entryCount))
                    .withClass("day-badge")
                    .withTitle(entryCount + (entryCount == 1 ? " item" : " items")));
        }
        return cell;
    }

    /**
     * The day number rendered as a native {@code <details>} disclosure: tapping the number
     * opens a small menu with the itinerary link plus one "Add …" link per bookable kind,
     * each carrying {@code ?date=} so the create form opens on this day. Native disclosure is
     * used deliberately — it is touch-first (a tap toggles it) with no hover dependency.
     */
    private static DomContent dayMenu(LocalDate date, String label, String dayNumberClass) {
        String iso = date.toString();
        return details().withClass("day-menu").with(
                summary(label).withClass(dayNumberClass),
                div().withClass("day-menu-list").with(
                        dayMenuItem("Open day", "/itinerary?date=" + iso),
                        dayMenuItem("Add flight", "/book-flight?date=" + iso),
                        dayMenuItem("Add train", "/book-train?date=" + iso),
                        dayMenuItem("Add hotel", "/book-hotel?date=" + iso),
                        dayMenuItem("Add gathering", "/plan-gathering?date=" + iso),
                        dayMenuItem("Add conference", "/plan-conference?date=" + iso)
                )
        );
    }

    private static DomContent dayMenuItem(String label, String href) {
        return a(label).withHref(href).withClass("day-menu-item");
    }

    private static DomContent renderEntrySegment(CalendarEntry entry,
                                                 int startCol,
                                                 int span,
                                                 int gridRow,
                                                 boolean isContinuation,
                                                 boolean isFinalSegment,
                                                 boolean isOwner) {
        String kindClass = "entry--" + entry.kind().name().toLowerCase();
        String classes = "entry " + kindClass + (isContinuation ? " entry--continuation" : "");
        // Square the edge (and run flush to the week boundary) on the side where the entry
        // continues into an adjacent week: leftward for a continuation, rightward when this
        // is not the final segment.
        if (isContinuation) {
            classes += " entry--from-left";
        }
        if (!isFinalSegment) {
            classes += " entry--to-right";
        }
        String style = "grid-column: " + startCol + " / span " + span
                + "; grid-row: " + gridRow + ";";
        if (entry.kind() == EntryKind.LODGING && isFinalSegment) {
            double pct = (span - 1.0) / span * 100.0;
            style += String.format(
                    " background: linear-gradient(to right, var(--entry-lodging-bg) %.4f%%, #bbf7d0 %.4f%%);",
                    pct, pct);
        }

        String title = isContinuation ? entry.continuationTitle() : entry.mainTitle();
        List<SubtitleLine> subtitle = isContinuation ? entry.continuationSubTitle() : entry.subTitle();

        DivTag div = div().withClass(classes).withStyle(style);
        if (title != null) {
            // The title is a plain text link only when it navigates *out* (maps); editing is
            // never the title itself but a separate pencil appended after it, so a link on the
            // title always means "go look at this elsewhere" and the pencil always means "edit".
            List<DomContent> titleParts = breakableTitle(title);
            DomContent titleText = entry.mapsUrl() != null && !isContinuation
                    ? a().with(titleParts).withHref(entry.mapsUrl()).withTarget("_blank").withRel("noopener")
                    : span().with(titleParts);
            DivTag titleDiv = div().withClass("entry-title").with(titleText);
            if (entry.editPath() != null && isOwner && !isContinuation) {
                // OWNER-only deep link to the entry's edit page — every editable kind
                // (flights, trains, hotels, gatherings) sets editPath; the redactor drops it.
                titleDiv.with(editPencil(entry.editPath(), "Edit"));
            }
            div.with(titleDiv);
        }
        if (subtitle != null) {
            for (SubtitleLine line : subtitle) {
                div.with(renderSubtitleLine(line));
            }
        }
        // Public "speaking" marker: that Ted speaks at a gathering is public by decision (the
        // venue and time already are), so it renders for every viewer — the redactor keeps
        // `speaking` on the GATHERING branch. Only on the entry's own (non-continuation) segment,
        // like the title and pencil.
        if (entry.speaking() && !isContinuation) {
            div.with(span("A Ted Talk").withClass("entry-speaking-badge"));
        }
        return div;
    }

    /**
     * A subtitle line that names a moment renders it as a {@code <time>} element so the UTC
     * instant travels with the wall-clock; plain lines stay plain text.
     */
    private static DivTag renderSubtitleLine(SubtitleLine line) {
        DivTag lineDiv = div().withClass("entry-subtitle");
        return switch (line) {
            case SubtitleLine.Text(String value) -> lineDiv.withText(value);
            case SubtitleLine.At(String label, ZonedTimestamp moment) -> lineDiv.with(
                    text(label + " "),
                    ZonedTimeTag.render(moment, TIME_OF_DAY_FORMAT));
            case SubtitleLine.Range(ZonedTimestamp from, ZonedTimestamp to) -> lineDiv.with(
                    ZonedTimeTag.render(from, TIME_OF_DAY_FORMAT),
                    text(" → "),
                    ZonedTimeTag.render(to, TIME_OF_DAY_FORMAT));
            // Redacted private-event time: fixed in the event's own zone with a zone label, as
            // plain text so the browser-zone script leaves it alone (it only rewrites
            // <time data-fmt>). Public in that zone by decision — see docs/PrivateSocialEventPlan.md.
            case SubtitleLine.FixedRange(ZonedTimestamp from, ZonedTimestamp to) ->
                    lineDiv.withText(fixedRangeText(from, to));
        };
    }

    private static String fixedRangeText(ZonedTimestamp from, ZonedTimestamp to) {
        DateTimeFormatter time = DateTimeFormatter.ofPattern(TIME_OF_DAY_FORMAT, Locale.ENGLISH);
        DateTimeFormatter zoneAbbrev = DateTimeFormatter.ofPattern("z", Locale.ENGLISH);
        return time.format(from.atEntryZone()) + " → " + time.format(to.atEntryZone())
                + " " + zoneAbbrev.format(from.atEntryZone());
    }

    /**
     * Splits a title so the browser has somewhere to wrap it, without changing a visible character.
     * A flight route ({@code ✈️ SFO→MUC}) is one unbreakable run — U+2192 is line-break class AL, so
     * there is no break opportunity between the codes and the entry's min-content width is the whole
     * route. A {@code <wbr>} after the arrow lets the codes stack onto two lines, but only in a column
     * too narrow to hold them side by side. A *spaced* arrow (a train's {@code 🚄 Frankfurt → Paris})
     * already breaks at its spaces, so it is left alone.
     */
    private static List<DomContent> breakableTitle(String title) {
        List<DomContent> parts = new ArrayList<>();
        int segmentStart = 0;
        for (int i = 0; i < title.length(); i++) {
            boolean unspacedArrow = title.charAt(i) == '→'
                    && i + 1 < title.length()
                    && title.charAt(i + 1) != ' ';
            if (unspacedArrow) {
                parts.add(text(title.substring(segmentStart, i + 1)));
                parts.add(wbr());
                segmentStart = i + 1;
            }
        }
        parts.add(text(title.substring(segmentStart)));
        return parts;
    }

    private static DomContent editPencil(String href, String label) {
        return a(rawHtml(PENCIL_SVG)).withClass("edit-pencil").withHref(href).withTitle(label);
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
