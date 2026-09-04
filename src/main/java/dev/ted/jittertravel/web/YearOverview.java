package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import j2html.tags.DomContent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static j2html.TagCreator.a;
import static j2html.TagCreator.button;
import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.span;

/**
 * The zoomed-out map of {@code /calendar}: one mini month calendar per month the page has drawn,
 * in a panel that opens from a "Jump to month" trigger in the sticky nav. Clicking a month scrolls
 * the linear calendar to that month — never a page load, because every month in the panel is on the
 * page by construction (see {@code docs/archived/YearOverviewPlan.md} D2).
 * <p>
 * <strong>Not rendered at all for an anonymous viewer.</strong> Every fact here is individually
 * public, but a year of them drawn as one 13px-per-day picture is a legible pattern — how often Ted
 * travels, how long he is gone, how far ahead he plans — and that aggregate is a different artefact
 * from the same facts spread over 150 scrolled weeks. Deny-by-default costs nothing here, so the
 * markup is absent rather than hidden. Gate on {@code isPublicUser}, never on {@code isOwner}:
 * FAMILY is already served the owner's entries by {@code CalendarController}, so the panel shows
 * them nothing their calendar is not already showing.
 * <p>
 * A class of its own, with its own test, rather than another static on {@code CalendarViewBuilder}:
 * a self-contained static renderer reached only through the page that embeds it is what let a
 * renamed day-menu item and an added one both ship green (CLAUDE.md).
 */
public final class YearOverview {

    private YearOverview() {
    }

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    /**
     * Which kind tints a day that carries several: <strong>conference, then gathering, then the
     * hotel underneath both</strong> (Ted, 2026-09-01).
     * <p>
     * <strong>Colour and glyph are two independent channels here, and this list is only the
     * colour one.</strong> The colour says what the trip <em>is</em>; the plane says Ted
     * <em>moved</em>. So a flight never costs a day its colour, and a four-day conference flown home
     * from on its last day still shows four conference days — with planes on the two ends.
     * <p>
     * This replaced an earlier seven-kind ordering the same day it shipped. That one gave every
     * kind a colour and every run a glyph, and in a busy month the result was unreadable: each
     * travel day punched a hole in the trip it served, so a trip stopped reading as one block.
     * Fewer channels carrying less is what makes the map answerable at 15px.
     * <p>
     * The order still differs from {@link EntryKind}'s declaration order, which is the <em>lane</em>
     * order and is right for the week grid; it is a subset, so it cannot be derived from it.
     * {@link #coloursTheDay} is the exhaustive switch that forces a decision for a new kind, and
     * {@code YearOverviewTest} pins the two against each other.
     */
    static final List<EntryKind> COLOUR_PRIORITY = List.of(
            EntryKind.CONFERENCE,
            EntryKind.GATHERING,
            EntryKind.LODGING);

    /**
     * Whether a kind tints the day at all. Exhaustive, so a new {@link EntryKind} stops this class
     * compiling until someone decides — which is the only forcing function left now that the glyph
     * is flight-only.
     * <p>
     * <strong>Travel tints nothing</strong> (Ted, 2026-09-01). A flight speaks through its glyph, and
     * a train or a taxi says nothing a reader of this map needs: in a busy month every travel day
     * used to take a colour off the trip it served, so the trip stopped reading as one block.
     * <strong>A private event tints nothing either</strong> — the map answers "where am I going and
     * when", and a Tuesday dinner at home is not that.
     */
    static boolean coloursTheDay(EntryKind kind) {
        return switch (kind) {
            case CONFERENCE, GATHERING, LODGING -> true;
            case PRIVATE_EVENT, FLIGHT, TRAIN, GROUND_TRANSFER -> false;
        };
    }

    /**
     * <strong>The only glyph, and it marks flights alone</strong> (Ted, 2026-09-01). Flights
     * book-end trips, so a pair of them frames a trip at a glance — and their <em>absence</em> is
     * the useful part: a future trip with no planes on either end is one whose flights are not
     * booked yet.
     * <p>
     * Every kind wearing a glyph was tried first and thrown away the same day: a typical busy month
     * became a wall of little pictures, each one covering the colour of the cell it sat on.
     * <p>
     * <strong>U+2708 with no U+FE0F, deliberately.</strong> The variation selector forces emoji
     * presentation — the blue-and-white picture — which ignores {@code color} and fights the pale
     * tint underneath it. Bare, it is a text glyph that takes the cell's colour. An earlier version
     * of this file <em>added</em> the selector and pinned it with a test; that was backwards.
     */
    static final String PLANE = "✈";

    // Both from the approved mockup, sized to 15px so they sit level with the label text. The
    // chevron is an SVG rather than a "▾" character: the glyph sits high in its em box and pulled
    // the whole control's alignment up and right.
    private static final String CALENDAR_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.9\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><rect x=\"3\" y=\"4.5\" width=\"18\" height=\"16\" rx=\"2\"/><path d=\"M3 9.5h18M8 2.5v4M16 2.5v4\"/></svg>";

    private static final String CHEVRON_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.4\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M6 9l6 6 6-6\"/></svg>";


    /**
     * The trigger and its panel, as one {@code <details>} for {@code Page.viewNav}'s trailing slot.
     * Returns nothing at all when {@code isPublicUser}.
     *
     * @param gridStart the first day the calendar drew, and {@code gridEnd} the last — the panel
     *                  spans exactly the page's own range, which is what makes every month in it a
     *                  scroll rather than a page load.
     */
    static DomContent render(List<CalendarEntry> entries, LocalDate gridStart, LocalDate gridEnd,
                             LocalDate today, Set<LocalDate> awayDays, boolean isPublicUser) {
        if (isPublicUser) {
            return each();
        }

        Map<LocalDate, EntryKind> dayColour = collapseToOneColourPerDay(entries, gridStart, gridEnd);
        Set<LocalDate> flightDays = daysWithAFlight(entries, gridStart, gridEnd);

        List<DomContent> months = new ArrayList<>();
        YearMonth first = YearMonth.from(gridStart);
        YearMonth last = YearMonth.from(gridEnd);
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(mini(month, gridStart, gridEnd, dayColour, flightDays, awayDays, today));
        }

        // No title row: it named the range ("Aug 2026 – Feb 2028"), which the first and last minis
        // already say, and it cost a line of the panel's scarcest dimension (Ted, 2026-09-01). The
        // close button stays — it is a control, not information — in a slim row of its own rather
        // than floating over the top-right mini.
        DomContent body = div(
                div(
                        button(rawHtml("&#10005;"))
                                .withType("button")
                                .withClass("yo-close")
                                .withTitle("Close")
                                .attr("aria-label", "Close")
                ).withClass("yo-panel-head"),
                div().withClass("yo-months").with(months)
        ).withClass("yo-panel-body");

        return DisclosureMenu.render(
                        each(span(rawHtml(CALENDAR_SVG)).withClass("yo-icon"),
                             span("Jump to month"),
                             span(rawHtml(CHEVRON_SVG)).withClass("yo-icon yo-chev")),
                        "year-overview-trigger",
                        List.of(body))
                // Overwrites DisclosureMenu's own class with both, so the panel can be pulled out
                // of the menu's absolute positioning without touching the shared class.
                .withClass(DisclosureMenu.MENU_CLASS + " year-overview");
    }

    /*
     * There was a legend here, and it went with the seven-colour palette it existed to decode
     * (Ted, 2026-09-01). Three tints and one glyph need no key: the tints are the same ones the
     * week grid below already uses for those kinds, so the panel and the calendar teach each other.
     */

    /**
     * One mini month: a weekday-aligned 7-column grid whose cells carry a colour, sometimes a
     * glyph, and nothing else.
     * <p>
     * The grid is {@code aria-hidden} because a cell holds no text — leaving thirty-one unlabelled
     * empty cells per month in the accessibility tree would have a screen reader announce thirty-one
     * nothings. The month link above it carries the accessible name, so what a screen-reader user
     * gets is a working list of months.
     */
    private static DomContent mini(YearMonth month, LocalDate gridStart, LocalDate gridEnd,
                                   Map<LocalDate, EntryKind> dayColour, Set<LocalDate> flightDays,
                                   Set<LocalDate> awayDays, LocalDate today) {
        List<DomContent> cells = new ArrayList<>();
        // The weekday header. The mockup carries it on every mini, and it is what turns seven
        // anonymous columns into "that mark is on a Tuesday" — the whole point of aligning them.
        for (String initial : List.of("S", "M", "T", "W", "T", "F", "S")) {
            cells.add(span(initial).withClass("yo-dow"));
        }
        // Sunday-first, matching the week grid: DayOfWeek is Monday=1..Sunday=7.
        int leadingBlanks = month.atDay(1).getDayOfWeek().getValue() % 7;
        for (int i = 0; i < leadingBlanks; i++) {
            cells.add(span().withClass("yo-day yo-blank"));
        }
        for (int dayOfMonth = 1; dayOfMonth <= month.lengthOfMonth(); dayOfMonth++) {
            cells.add(dayCell(month.atDay(dayOfMonth), gridStart, gridEnd, dayColour, flightDays, awayDays, today));
        }

        // The WHOLE mini is the click target, not just its label (Ted, 2026-09-01). A 15px day cell
        // is far under a comfortable touch target and days are not individually clickable anyway, so
        // there is nothing inside competing for the tap — which is what makes the whole block safe
        // to hand over.
        return a().withHref("#" + CalendarViewBuilder.monthAnchorId(month))
                .withClass("yo-month")
                .attr("data-month", month.toString())
                .with(
                        span(MONTH_LABEL.format(month)).withClass("yo-month-label"),
                        // Decorative: a cell holds no text, so thirty-one unlabelled empties would
                        // have a screen reader announce thirty-one nothings. The link's own label
                        // carries the accessible name.
                        span().withClass("yo-grid").attr("aria-hidden", "true").with(cells)
                );
    }

    private static DomContent dayCell(LocalDate date, LocalDate gridStart, LocalDate gridEnd,
                                      Map<LocalDate, EntryKind> dayColour, Set<LocalDate> flightDays,
                                      Set<LocalDate> awayDays, LocalDate today) {
        boolean onThePage = !date.isBefore(gridStart) && !date.isAfter(gridEnd);
        StringBuilder classes = new StringBuilder("yo-day");
        if (!onThePage) {
            // A day of this month the calendar did not draw. Shown so the weekday alignment stays
            // honest, muted so it cannot be read as "nothing happening".
            classes.append(" yo-off");
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            classes.append(" yo-weekend");
        }
        EntryKind kind = onThePage ? dayColour.get(date) : null;
        if (kind != null) {
            classes.append(" yo-filled yo-").append(kind.name().toLowerCase());
        }
        boolean flying = onThePage && flightDays.contains(date);
        if (flying) {
            classes.append(" yo-flying");
        }
        // Read independently of whether the day has an entry: ScheduleTimeline.walk() fills the
        // nights *between* points, so a trip flown out and back with no hotel booked yet marks days
        // carrying no entries at all — exactly the days the band exists to warn about.
        if (onThePage && awayDays.contains(date)) {
            classes.append(" yo-away");
        }
        if (date.equals(today)) {
            classes.append(" yo-today");
        }

        return flying
                ? span(rawHtml(PLANE)).withClass(classes.toString())
                : span().withClass(classes.toString());
    }

    /**
     * Every day in the range a colouring kind covers, mapped to the one that wins it under
     * {@link #COLOUR_PRIORITY}. Kinds that colour nothing never enter the map, so they cannot win a
     * day by accident.
     */
    private static Map<LocalDate, EntryKind> collapseToOneColourPerDay(List<CalendarEntry> entries,
                                                                       LocalDate gridStart, LocalDate gridEnd) {
        Map<LocalDate, EntryKind> dayColour = new HashMap<>();
        for (CalendarEntry entry : entries) {
            if (!coloursTheDay(entry.kind())) {
                continue;
            }
            eachDayInRange(entry, gridStart, gridEnd, day -> dayColour.merge(day, entry.kind(), YearOverview::stronger));
        }
        return dayColour;
    }

    private static EntryKind stronger(EntryKind a, EntryKind b) {
        return COLOUR_PRIORITY.indexOf(a) <= COLOUR_PRIORITY.indexOf(b) ? a : b;
    }

    /**
     * Every day carrying a flight — <em>every</em> one, not just the first of a run.
     * <p>
     * A flight is a single day, and two on one day (a layover entered as two flights) is still one
     * day Ted moved, so there is no run to collapse. This is why the old run-detection went with the
     * six other glyphs: it existed to stop a five-day conference wearing five microphones, and
     * nothing here spans days any more.
     */
    private static Set<LocalDate> daysWithAFlight(List<CalendarEntry> entries,
                                                  LocalDate gridStart, LocalDate gridEnd) {
        Set<LocalDate> flightDays = new HashSet<>();
        for (CalendarEntry entry : entries) {
            if (entry.kind() == EntryKind.FLIGHT) {
                eachDayInRange(entry, gridStart, gridEnd, flightDays::add);
            }
        }
        return flightDays;
    }

    private static void eachDayInRange(CalendarEntry entry, LocalDate gridStart, LocalDate gridEnd,
                                       Consumer<LocalDate> action) {
        LocalDate lastDay = entry.end().toLocalDate();
        for (LocalDate day = entry.start().toLocalDate(); !day.isAfter(lastDay); day = day.plusDays(1)) {
            if (!day.isBefore(gridStart) && !day.isAfter(gridEnd)) {
                action.accept(day);
            }
        }
    }

    static final String CSS = """
            /* A FIXED cell is what makes the panel fit: at 18px a mini is 7*18 + 6*2 = 138px wide,
               so a 1180px panel wraps seven per row and 18 months land in three rows with nothing to
               scroll. Sizing cells fluidly instead (aspect-ratio inside a 2-column grid) is what
               produced a tall, narrow, scrolling panel two months wide.
               The mockup's 15px went to 18 once there was room to spare (Ted, 2026-09-01) — it costs
               one mini per row and buys a legible plane. Raising it further starts costing rows. */
            .year-overview { --yo-cell: 18px; --yo-gap: 2px; position: relative; align-self: center; margin-left: auto; }

            /* The trigger sits in the shared .view-nav flex row, which is align-items: baseline —
               so the control centres itself (align-self above) and its contents centre inside it.
               Both icons are sized in px and made block-level: an inline SVG sits on the text
               baseline with descender space under it, which pushed the calendar glyph up and right
               of where it belonged. */
            /* Selector must out-specify DisclosureMenu's `.disclosure-menu > summary { display:
               block }`, which is (0,1,1) — a bare `.year-overview-trigger` is (0,1,0) and LOSES,
               whatever the source order. When it lost, the summary stayed a block, the two icons
               (display: block, so they could be sized) stacked under the label, and the control
               rendered three lines tall. */
            .year-overview > .year-overview-trigger {
                display: inline-flex; align-items: center; gap: 7px; white-space: nowrap;
                padding: 5px 11px; border: 1px solid var(--calendar-border-strong, darkgray);
                border-radius: 6px;
                color: #334155; font-size: 0.82rem; font-weight: 600;
                background-color: var(--calendar-surface, #ffffff);
            }
            .year-overview-trigger:hover,
            .year-overview[open] .year-overview-trigger {
                border-color: var(--calendar-month-start-color, #b45309);
                color: var(--calendar-month-start-color, #b45309);
            }
            .yo-icon { display: block; width: 15px; height: 15px; flex: none; }
            .yo-icon svg { display: block; width: 15px; height: 15px; }
            /* ONLY the chevron rotates. A transform on the trigger or on .year-overview would
               become the containing block for the fixed panel below, silently turning it back into
               an absolutely-positioned one that slides away as the page scrolls. */
            .yo-chev { transition: transform 0.15s ease; }
            .year-overview[open] .yo-chev { transform: rotate(180deg); }

            /* Pulls the panel out of DisclosureMenu's absolute positioning: the trigger lives in a
               sticky bar, so an absolute panel would scroll away from its own button. Fixed works
               here because .disclosure-menu is only position: relative, which is not a containing
               block for a fixed descendant — see the chevron note above for what would break it. */
            .year-overview > .disclosure-menu-list {
                position: fixed; z-index: 60;
                top: calc(var(--nav-height, 0px) + 8px); right: 12px; left: auto;
                /* An explicit width, never max-content: on a wrapping flex container max-content is
                   the UNWRAPPED width — all 18 minis in one row — so the panel went full-bleed and
                   clipped off the left edge. 1180px holds 8 minis per row, which puts 18 months in
                   three rows. */
                width: min(1180px, calc(100vw - 24px));
                /* This page has no global border-box reset, so without it the 16px padding and the
                   1px border land OUTSIDE the width and the panel overhangs the viewport's left
                   edge by exactly that much. */
                box-sizing: border-box;
                max-height: calc(100vh - var(--nav-height, 0px) - 24px);
                overflow: auto; overscroll-behavior: contain;
                padding: 14px 16px 16px;
                box-shadow: 0 14px 40px rgba(15, 23, 42, 0.28);
            }
            .yo-panel-head { display: flex; justify-content: flex-end; margin-bottom: 2px; }
            .yo-close {
                background: none; border: none; padding: 0 2px; cursor: pointer;
                font-size: 1.1rem; line-height: 1; color: var(--muted-text, #6b7280);
            }

            /* flex-wrap, NOT a fixed column count: each mini is its natural 117px, so the row holds
               as many as the panel is wide and the panel is as wide as the viewport allows. That is
               what gets 12-18 months on screen at once without scrolling. */
            /* WIDTH is the binding dimension, not height (Ted, 2026-09-01): the narrowest real
               target is an iPad in portrait at 820x1180, which is short on width and generous on
               height. So the row count is set by how many 138px minis fit across, and there is
               vertical room to spare at every viewport — 19 months needs 5 rows on the iPad in
               portrait, 4 in landscape, 3 on a laptop, and none of them scroll. */
            .yo-months { display: flex; flex-wrap: wrap; gap: 14px 20px; }
            .yo-month {
                display: block; text-decoration: none; color: inherit;
                padding: 2px 3px 3px; border-radius: 5px;
                border: 1px solid transparent;
            }
            /* The whole mini is the click target, so the whole mini responds to the pointer. */
            .yo-month:hover { background-color: var(--calendar-header-bg, #f8f9fa); }
            .yo-month:hover .yo-month-label { color: var(--calendar-month-start-color, #b45309); }
            .yo-month.is-current { border-color: var(--calendar-month-start-color, #b45309); }
            .yo-month.is-current .yo-month-label { color: var(--calendar-month-start-color, #b45309); }
            .yo-month-label {
                display: block; margin-bottom: 3px;
                font-size: 0.74rem; font-weight: 700; color: #334155;
            }
            .yo-grid {
                display: grid; grid-template-columns: repeat(7, var(--yo-cell));
                gap: var(--yo-gap);
            }
            .yo-dow {
                font-size: 0.55rem; line-height: 1.15; text-align: center;
                color: var(--muted-text, #6b7280);
            }
            /* An EMPTY day carries NO fill at all — only its border (Ted, 2026-09-01). The fills
               below are the calendar's PALE tints, so any grey in an empty cell competes with them:
               at #fafbfc a #f5f3ff gathering was nearly invisible, and the grey-blue weekend read as
               the pale-blue conference. Drawing the empty grid in outline alone leaves the whole
               tint range free for cells that mean something. */
            .yo-day {
                width: var(--yo-cell); height: var(--yo-cell); border-radius: 2px;
                display: block; position: relative; overflow: hidden;
                font-size: calc(var(--yo-cell) * 0.72); line-height: var(--yo-cell);
                text-align: center;
                background-color: transparent;
                box-shadow: inset 0 0 0 1px #e2e6ea;
            }
            .yo-blank { box-shadow: none; }
            /* WARM grey, deliberately. Every tint below is cool — lavender, violet, mint — so a
               neutral or blue-grey weekend reads as one of them at 15px, which is exactly what
               #eceff2 did against the conference's #e0e7ff. */
            .yo-weekend { background-color: #faf8f5; }
            .yo-off { opacity: 0.35; }
            /* The SAME pale tints the week grid uses for these kinds, so the panel and the calendar
               below it teach each other (Ted, 2026-09-01, replacing the saturated -fg set). Only
               three kinds tint a day: travel says nothing here beyond its plane, and a private
               event is not a trip. See coloursTheDay.
               The darker edge is what says "filled", separately from the hue that says "which".
               That split is what keeps the gathering's near-white #f5f3ff readable as a marked day
               at all — it is detectable by its border before its colour. */
            .yo-filled { box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.26); }
            .yo-conference { background-color: var(--entry-conference-bg); }
            .yo-gathering  { background-color: var(--entry-gathering-bg); }
            .yo-lodging    { background-color: var(--entry-lodging-bg); }
            /* The plane. Monochrome (U+2708 with no variation selector), so it takes this colour
               rather than rendering as the blue-and-white emoji — which would ignore it and fight
               the tint underneath. Dark slate, because white is invisible on every fill above. */
            .yo-flying { color: #1e293b; font-weight: 700; }
            /* The away band and today's outline are both edge treatments on a cell with no
               interior, and a day can be both — so the band is a ::after strip and today is an
               outline, which compose rather than overwriting each other. */
            .yo-away::after {
                content: ""; position: absolute; left: 0; right: 0; bottom: 0;
                height: 2px; background-color: var(--calendar-away-color, turquoise);
            }
            .yo-today { outline: 2px solid var(--calendar-month-start-color, #b45309); z-index: 2; }
            """;

    /**
     * Dismissal on outside-click and Escape, and one-open-at-a-time, all come free from
     * {@code DisclosureMenu.SCRIPT} — this adds only what a panel needs beyond a menu: focus
     * returning to the trigger, closing on a chosen month, and the "you are here" marking.
     * <p>
     * <strong>"You are here" is recomputed on every open and never stored</strong>, so there is no
     * state to go stale when the page is scrolled with the panel closed. It reads the month-start
     * day cells rather than the month bands: those cells are the anchors, so the panel and the jump
     * targets cannot disagree about which months exist, and each anchor's own
     * {@code scroll-margin-top} is the offset it comes to rest at — one number, used by both halves.
     * The fallback matters — at scroll 0 no cell is above that offset yet, which is the ordinary
     * landing state, and marking nothing there is the "map with no you-are-here" failure the whole
     * decision exists to avoid.
     */
    static final String SCRIPT = """
            var yearOverview = document.querySelector('.year-overview');
            if (yearOverview) {
                var yearOverviewPanel = yearOverview.querySelector('.disclosure-menu-list');
                var yearOverviewTrigger = yearOverview.querySelector('summary');
                var yearOverviewCurrentMonth = function () {
                    var anchors = document.querySelectorAll('.day-label-cell.is-month-start[id^="m-"]');
                    var current = null;
                    anchors.forEach(function (anchor) {
                        // The anchor's own scroll-margin-top IS the resting place a jump puts it at,
                        // so read that rather than re-adding up the sticky stack. Measuring the bars
                        // separately was off by the few pixels between the weekday header's real
                        // height and the --calendar-weekday-header-height literal, which was enough
                        // for the panel to name the PREVIOUS month immediately after a jump to one.
                        var restingTop = parseFloat(getComputedStyle(anchor).scrollMarginTop) || 0;
                        if (anchor.getBoundingClientRect().top <= restingTop + 1) {
                            current = anchor.id.substring(2);   // strip the "m-" prefix
                        }
                    });
                    if (!anchors.length) return null;
                    // Scrolled to the bottom: the last month or two can never reach their resting
                    // place, because there is not enough document left below them to scroll. Without
                    // this the panel names an earlier month while Ted looks at December — the "map
                    // that doesn't show where you're standing" failure, at the one end of the range
                    // he scrolled all the way to on purpose.
                    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2) {
                        return anchors[anchors.length - 1].id.substring(2);
                    }
                    if (!current) {
                        current = anchors[0].id.substring(2);   // page not scrolled yet
                    }
                    return current;
                };
                var yearOverviewClose = function () {
                    yearOverview.open = false;
                    yearOverviewTrigger.focus();
                };
                yearOverview.addEventListener('toggle', function () {
                    if (!yearOverview.open) return;
                    var current = yearOverviewCurrentMonth();
                    yearOverviewPanel.querySelectorAll('.yo-month').forEach(function (month) {
                        month.classList.toggle('is-current', month.dataset.month === current);
                    });
                    var marked = yearOverviewPanel.querySelector('.yo-month.is-current');
                    if (marked) {
                        yearOverviewPanel.scrollTop = marked.offsetTop - 8;
                    }
                });
                yearOverviewPanel.addEventListener('click', function (event) {
                    var close = event.target.closest('.yo-close');
                    if (close) {
                        yearOverviewClose();
                        return;
                    }
                    var monthLink = event.target.closest('a[href^="#m-"]');
                    if (!monthLink) return;
                    var target = document.getElementById(monthLink.getAttribute('href').substring(1));
                    if (target) {
                        // Acknowledge the jump on the whole week, not on one 40px square in the
                        // middle of a seven-column grid.
                        var week = target.closest('.calendar-week');
                        if (week) {
                            week.classList.add('is-jump-target');
                            setTimeout(function () { week.classList.remove('is-jump-target'); }, 1200);
                        }
                    }
                    yearOverviewClose();
                });
                document.addEventListener('keydown', function (event) {
                    if (event.key === 'Escape' && yearOverview.open) {
                        yearOverviewClose();
                    }
                });
            }
            """;
}
