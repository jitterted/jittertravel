package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryDetails;
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

    // A bin always means "cancel this entry", as the pencil means edit. Same stroke weight and
    // currentColor, so the two read as one family and sit at the same size in the same slot.
    private static final String TRASH_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M3 6h18\"/><path d=\"M8 6V4h8v2\"/><path d=\"M19 6l-1 14H6L5 6\"/><path d=\"M10 11v6\"/><path d=\"M14 11v6\"/></svg>";

    // The same fork-and-knife that fronts the "Private event" nav card on the home page, so the
    // lane on the calendar and the way in to creating one wear one glyph. The nav card's copy
    // hard-codes its fill; here it is currentColor, to tint with the entry's own foreground.
    private static final String UTENSILS_SVG = "<svg viewBox=\"0 0 448 512\" fill=\"currentColor\" aria-hidden=\"true\"><path d=\"M33.1 0C42 .7 48.6 8.3 48 17.1L36.9 172.6c-2 27.8 20 51.4 47.9 51.4l54.5 0c27.9 0 49.9-23.6 47.9-51.4L176 17.1C175.4 8.3 182 .7 190.9 0S207.3 6 208 14.9l11.1 155.4c3.3 46.3-33.4 85.7-79.8 85.7l-11.3 0 0 240c0 8.8-7.2 16-16 16s-16-7.2-16-16l0-240-11.3 0c-46.4 0-83.1-39.4-79.8-85.7L16 14.9C16.7 6 24.3-.6 33.1 0zM88.8 0c8.8 .4 15.6 8 15.2 16.8l-8 160c-.4 8.8-8 15.6-16.8 15.2S63.6 184 64 175.2l8-160C72.5 6.4 80-.4 88.8 0zm46.4 0C144-.4 151.5 6.4 152 15.2l8 160c.4 8.8-6.4 16.3-15.2 16.8s-16.3-6.4-16.8-15.2l-8-160C119.6 8 126.4 .5 135.2 0zM288 136C288 60.9 348.9 0 424 0l8 0c8.8 0 16 7.2 16 16l0 480c0 8.8-7.2 16-16 16s-16-7.2-16-16l0-144-64 0c-35.3 0-64-28.7-64-64l0-152zM416 320l0-287.7c-53.7 4.1-96 49-96 103.7l0 152c0 17.7 14.3 32 32 32l64 0z\"/></svg>";

    public static String render(List<CalendarEntry> entries, LocalDate rangeStart, LocalDate rangeEnd, LocalDate today, boolean isPublicUser) {
        return render(entries, rangeStart, rangeEnd, today, isPublicUser, false);
    }

    public static String render(List<CalendarEntry> entries, LocalDate rangeStart, LocalDate rangeEnd, LocalDate today, boolean isPublicUser, boolean isOwner) {
        return render(entries, rangeStart, rangeEnd, today, isPublicUser, isOwner, Set.of());
    }

    /**
     * @param awayDays every day Ted is away from home, from {@code ScheduleGapProjector}. The same
     *                 set for every viewer — the band is public by decision, and nothing about it
     *                 goes through the redactor. See {@code docs/archived/CalendarAwayBandPlan.md}.
     */
    public static String render(List<CalendarEntry> entries, LocalDate rangeStart, LocalDate rangeEnd, LocalDate today,
                                boolean isPublicUser, boolean isOwner, Set<LocalDate> awayDays) {
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
            weekRows.add(renderWeek(sunday, saturday, gridStart, today, entries, isPublicUser, isOwner, collapsed, awayDays));
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
                                     boolean collapsed,
                                     Set<LocalDate> awayDays) {
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
            LocalDate day = sunday.plusDays(i);
            cells.add(renderDayLabelCell(day, gridStart, today, isPublicUser, isOwner, badgeCount, awayDays.contains(day)));
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

    private static DomContent renderDayLabelCell(LocalDate date, LocalDate gridStart, LocalDate today, boolean isPublicUser, boolean isOwner, int entryCount, boolean isAway) {
        boolean isFirstCellOfGrid = date.equals(gridStart);
        boolean isMonthStart = date.getDayOfMonth() == 1 || isFirstCellOfGrid;
        String monthTint = (date.getMonthValue() % 2 == 0) ? "month-tint-even" : "month-tint-odd";
        // The away band: a turquoise bottom border, and nothing else — the label row is the one
        // row that survives week-collapse, so past trips keep their stripe. Every viewer gets it.
        String labelClass = "day-label-cell " + monthTint + (isMonthStart ? " is-month-start" : "")
                            + dayStateClass(date, today) + (isAway ? " is-away" : "");
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
     * The day number rendered as a {@link DisclosureMenu}: tapping the number opens a small menu
     * with the itinerary link plus one "Add …" link per bookable kind, each carrying {@code ?date=}
     * so the create form opens on this day. The menu's mechanics — touch-first, outside-click and
     * Escape dismissal, no stacking — are shared with the fix menus on {@code /schedule-problems}.
     */
    private static DomContent dayMenu(LocalDate date, String label, String dayNumberClass) {
        String iso = date.toString();
        return DisclosureMenu.render(text(label), dayNumberClass, List.of(
                DisclosureMenu.item("Open day", "/itinerary?date=" + iso),
                DisclosureMenu.item("Add flight", "/book-flight?date=" + iso),
                DisclosureMenu.item("Add train", "/book-train?date=" + iso),
                DisclosureMenu.item("Add hotel", "/book-hotel?date=" + iso),
                DisclosureMenu.item("Add ground transfer", "/plan-ground-transfer?date=" + iso),
                DisclosureMenu.item("Add gathering", "/plan-gathering?date=" + iso),
                DisclosureMenu.item("Add conference", "/plan-conference?date=" + iso)
        ));
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
            String titleLink = isContinuation ? null : titleLink(entry.details());
            List<DomContent> titleParts = breakableTitle(title);
            DomContent titleText = titleLink != null
                    ? a().with(titleParts).withHref(titleLink).withTarget("_blank").withRel("noopener")
                    : span().with(titleParts);
            DivTag titleDiv = div().withClass("entry-title").with(kindIcon(entry.details())).with(titleText);
            if (isOwner && !isContinuation) {
                titleDiv.with(ownerActions(entry.details()));
            }
            div.with(titleDiv);
        }
        if (subtitle != null) {
            for (SubtitleLine line : subtitle) {
                div.with(renderSubtitleLine(line));
            }
        }
        if (!isContinuation) {
            div.with(badges(entry.details()));
        }
        return div;
    }

    /**
     * Where the entry's title navigates to, or {@code null} when it is plain text. A linked title
     * always means "go look at this elsewhere" — the map for a hotel, the event's own page for a
     * gathering. Editing is never the title itself but a separate pencil appended after it.
     * <p>
     * Exhaustive over {@link EntryDetails} rather than defaulted, so a new kind cannot be added
     * without deciding whether its title links out.
     */
    private static String titleLink(EntryDetails details) {
        return switch (details) {
            case EntryDetails.Lodging d -> d.mapsUrl();
            case EntryDetails.Gathering d -> d.infoUrl();
            // A gathering's info URL is public by decision, so it survives into the public model.
            case EntryDetails.PublicGathering d -> d.infoUrl();
            // And so does a conference's, for the same reason: it is a public event, and its own
            // page is on the published list in CLAUDE.md. Owner and anonymous get the same link —
            // it is the CFP submission URL that is private, and that never reaches an entry.
            case EntryDetails.Conference d -> d.infoUrl();
            case EntryDetails.PublicConference d -> d.infoUrl();
            // The remaining kinds have nowhere to point: a flight, train or transfer is a leg, and
            // a private event's venue is not published. Nor does any travel kind publicly — a
            // PublishableTravel holds nothing at all.
            case EntryDetails.Flight _,
                 EntryDetails.Train _,
                 EntryDetails.GroundTransfer _,
                 EntryDetails.PrivateEvent _,
                 EntryDetails.PublishableTravel _,
                 EntryDetails.Busy _ -> null;
        };
    }

    /**
     * The OWNER-only action icons for an entry, in one fixed slot after the title.
     * <p>
     * Most kinds offer an edit pencil. A ground transfer has nothing to edit — the way to correct
     * one is to remove it and enter it again — so its action is a cancel bin in that same slot,
     * with a different verb. No kind offers both, so the icon never moves between rows. Anonymous
     * and family viewers get nothing at all here rather than a greyed control: the link itself
     * would disclose that the surface exists (CLAUDE.md, affordances vs authorization).
     */
    private static List<DomContent> ownerActions(EntryDetails details) {
        return switch (details) {
            case EntryDetails.Lodging d -> pencil(d.editPath());
            case EntryDetails.Gathering d -> pencil(d.editPath());
            case EntryDetails.Flight d -> pencil(d.editPath());
            case EntryDetails.Train d -> pencil(d.editPath());
            case EntryDetails.GroundTransfer d -> d.cancelPath() == null
                    ? List.of()
                    : List.of(cancelBin(d.cancelPath(), "Cancel"));
            // A conference is declined or cancelled from its own pages, and a private event has no
            // edit flow yet (docs/Cleanup_Tasks.md, "Change Private Event").
            case EntryDetails.Conference _, EntryDetails.PrivateEvent _ -> List.of();
            // No publishable details type can carry an owner action, so these arms are not a
            // policy decision the renderer makes — there is nothing there to render. An anonymous
            // viewer never reaches this method anyway (it is gated on isOwner), and a viewer who
            // is somehow both would still get nothing.
            case EntryDetails.Publishable _ -> List.of();
        };
    }

    private static List<DomContent> pencil(String editPath) {
        return editPath == null ? List.of() : List.of(editPencil(editPath, "Edit"));
    }

    /**
     * The public chips an entry wears, rendered on its own (non-continuation) segment only, like
     * the title and the pencil.
     * <p>
     * Both are public by decision. That Ted <em>speaks</em> at a gathering reveals nothing the
     * already-public venue and time do not. The conference "Maybe" chip is publishable only
     * because {@code ConferenceCalendarProjector} has already collapsed every speculative state
     * (CFP pending, submitted, rejected-but-undecided) into {@code WATCHING} before it got here.
     * Only the speculative case is marked: "Ted is going" is the default reading of a calendar
     * entry, so a "Going" chip would be noise on every committed conference.
     */
    /**
     * The glyph that fronts an entry's title, or nothing. Today only a private event has one: its
     * lane sits between the conference's indigo and the gathering's violet, and a fill alone was
     * not enough to tell three neighbouring purples apart at week-grid scale.
     * <p>
     * It is deliberately keyed on the <em>details</em> type rather than on {@link EntryKind}:
     * {@link EntryDetails.Busy} is the public face of the same kind, and a fork and knife on it
     * would tell an anonymous viewer that the block is a meal — which is the one thing a Busy bar
     * exists not to say. Per CLAUDE.md the anonymous view of a private event is "Busy, a
     * zone-labelled time range, and city/country, and nothing else"; the icon would be a fifth
     * thing. So the audience stays chosen at the boundary and applied inward — this method never
     * asks who is looking, it just draws the two details types differently.
     * <p>
     * Exhaustive rather than defaulted, so a new kind cannot be added without deciding whether it
     * carries a glyph, and a new <em>public</em> details type cannot be added without deciding
     * that separately from its owner twin.
     */
    private static List<DomContent> kindIcon(EntryDetails details) {
        return switch (details) {
            case EntryDetails.PrivateEvent _ ->
                    List.of(span(rawHtml(UTENSILS_SVG)).withClass("entry-kind-icon"));
            case EntryDetails.Busy _,
                 EntryDetails.Conference _,
                 EntryDetails.PublicConference _,
                 EntryDetails.Gathering _,
                 EntryDetails.PublicGathering _,
                 EntryDetails.Flight _,
                 EntryDetails.Train _,
                 EntryDetails.GroundTransfer _,
                 EntryDetails.Lodging _,
                 EntryDetails.PublishableTravel _ -> List.of();
        };
    }

    private static List<DomContent> badges(EntryDetails details) {
        return switch (details) {
            case EntryDetails.Gathering d -> d.speaking()
                    ? List.of(span("A Ted Talk").withClass("entry-speaking-badge"))
                    : List.of();
            case EntryDetails.Conference d -> conferenceBadges(d.commitment(), d.speaking());
            // Both chips are public by decision, so the public model carries them too.
            case EntryDetails.PublicGathering d -> d.speaking()
                    ? List.of(span("A Ted Talk").withClass("entry-speaking-badge"))
                    : List.of();
            case EntryDetails.PublicConference d -> conferenceBadges(d.commitment(), d.speaking());
            case EntryDetails.Flight _,
                 EntryDetails.Train _,
                 EntryDetails.GroundTransfer _,
                 EntryDetails.Lodging _,
                 EntryDetails.PrivateEvent _,
                 EntryDetails.PublishableTravel _,
                 EntryDetails.Busy _ -> List.of();
        };
    }

    /**
     * A conference wears at most one chip, and never both: "Maybe" while it is still speculative,
     * "A Ted Talk" once Ted is committed and speaking. They cannot co-occur, because the projectors
     * set the speaking flag only on a committed conference — so a badge pair that would say
     * "he was asked to speak somewhere he has not decided about" is not constructible here.
     * <p>
     * A committed conference Ted merely attends wears nothing, which is the right default reading
     * of a calendar entry.
     */
    private static List<DomContent> conferenceBadges(AttendanceCommitment commitment, boolean speaking) {
        if (commitment == AttendanceCommitment.WATCHING) {
            return List.of(span("Maybe").withClass("entry-maybe-badge"));
        }
        return speaking
                ? List.of(span("A Ted Talk").withClass("entry-speaking-badge"))
                : List.of();
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
            // <time data-fmt>). Public in that zone by decision — see docs/archived/PrivateSocialEventPlan.md.
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

    private static DomContent cancelBin(String href, String label) {
        return a(rawHtml(TRASH_SVG)).withClass("cancel-bin").withHref(href).withTitle(label);
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
