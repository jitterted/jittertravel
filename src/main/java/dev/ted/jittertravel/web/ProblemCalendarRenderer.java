package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleContext;
import dev.ted.jittertravel.application.ScheduleProblem;
import j2html.tags.DomContent;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.html;
import static j2html.TagCreator.p;
import static j2html.TagCreator.rawHtml;

/**
 * The calendar view of the schedule-problems report: the same problems as
 * {@link ScheduleProblemsRenderer} shows as cards, placed on week rows so a hole in a trip reads
 * as a hole rather than as three separate cards.
 * <p>
 * OWNER-only, like the whole {@code /schedule-problems} route, so no redaction happens here and
 * nothing on this page may ever be linked from an anonymous surface.
 */
public class ProblemCalendarRenderer {

    private static final String CSS = """
            .page { max-width: 1400px; }
            .no-problems { color: var(--muted-text); font-style: italic; font-size: 0.95rem; padding: 2rem 0; }
            /* Lane *edges* match the card columns in the list view, so the two views of one report
               do not disagree about what a hue means. The fill is a different question here and
               is answered below: on a calendar the bands sit among untroubled days. */
            :root {
                --pc-border: #dee2e6;
                --pc-border-strong: darkgray;
                --pc-surface: #ffffff;
                --pc-header-bg: #f8f9fa;
                --pc-text-secondary: #495057;
                --pc-past-hatch: rgba(0, 0, 0, 0.1);
                --pc-today-tint: #eef2ff;
                /* Every band wears the same amber, whatever kind of problem it is (Ted,
                   2026-08-21): he missed a whole run of missing hotels because they were blue,
                   and on a calendar of mostly-fine days the first thing a band has to say is
                   "something here is wrong". Kind is the second question, and it survives on the
                   left edge below — plus the band names itself in words.
                   Translucent so the context the band sits inside stays visible through it —
                   the cause is the point of the backdrop. */
                --pc-problem-bg: rgba(254, 243, 199, 0.85);
                --pc-problem-fg: #78350f;
                /* Kind, kept only as the 4px left edge, in the hue its column uses in the list
                   view. Red on a scheduling clash is the odd one out and stays a border rather
                   than a fill for a second reason: red means irreversible, and a clash is not. */
                --pc-bed-border: #1d4ed8;
                --pc-travel-border: #b45309;
                --pc-duplicate-border: #c2410c;
                --pc-clash-city-border: #7c3aed;
                --pc-clash-scheduling-border: #dc2626;
                /* One grey for every kind of context; the kind is named in the label. */
                --pc-context-bg: rgba(107, 114, 128, 0.14);
                --pc-context-border: rgba(107, 114, 128, 0.35);
                --pc-context-fg: #4b5563;
            }
            .pc-container {
                margin-top: 1rem;
                border-left: 1px solid var(--pc-border-strong);
                border-top: 1px solid var(--pc-border-strong);
                border-bottom: 1px solid var(--pc-border-strong);
            }
            /* minmax(0, 1fr), never a bare 1fr: each week is its own grid, so a track floored at
               its widest band's min-content width would knock that week out of registration with
               the others and with this header. */
            .pc-header, .pc-week {
                display: grid; grid-template-columns: repeat(7, minmax(0, 1fr));
            }
            .pc-header div {
                text-align: center; font-weight: 600; font-size: 0.9rem;
                padding: 10px 0; color: var(--pc-text-secondary);
                background-color: var(--pc-header-bg);
                border-bottom: 1px solid var(--pc-border);
                border-right: 1px solid var(--pc-border);
            }
            .pc-week { background-color: var(--pc-surface); }
            .pc-day-cell {
                grid-row: 1; min-height: 32px; padding: 4px 6px; box-sizing: border-box;
                border-top: 1px solid var(--pc-border-strong);
                border-bottom: 1px solid var(--pc-border);
                border-right: 1px solid var(--pc-border);
            }
            .pc-day-cell.is-month-start { border-top: 3px solid #b45309; border-left: 3px solid #b45309; }
            .pc-day-number { font-size: 0.85rem; font-weight: 700; color: var(--pc-text-secondary); }
            .pc-lane-cell {
                min-height: 26px; box-sizing: border-box;
                border-right: 1px solid var(--pc-border);
            }
            .pc-day-cell.is-today, .pc-lane-cell.is-today { background-color: var(--pc-today-tint); }
            .pc-day-cell.is-past, .pc-lane-cell.is-past {
                background-image: repeating-linear-gradient(45deg,
                    var(--pc-past-hatch) 0 2px, transparent 2px 6px);
            }
            /* The context backdrop: full lane height, so problem bands sit inside it, with the
               label on the week's floor (lifted by an inline padding-bottom when another label is
               already there). overflow:hidden plus the ellipsis below keeps a long venue name from
               widening the day column — nothing on this page may scroll sideways. */
            .pc-context {
                display: flex; align-items: flex-end; overflow: hidden;
                margin: 2px 3px; border-radius: 5px;
                background: var(--pc-context-bg);
                border: 1px solid var(--pc-context-border);
            }
            .pc-context--from-left { margin-left: 0; border-radius: 0 5px 5px 0; border-left: none; }
            .pc-context--to-right  { margin-right: 0; border-radius: 5px 0 0 5px; border-right: none; }
            .pc-context--from-left.pc-context--to-right { border-radius: 0; }
            .pc-context-label {
                min-width: 0; padding: 0 5px 3px;
                font-size: 0.7rem; color: var(--pc-context-fg);
                white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
            }
            .pc-band {
                display: flex; align-items: center; gap: 4px;
                margin: 2px 3px; padding: 3px 6px; border-radius: 5px;
                border-left: 4px solid transparent; overflow-wrap: anywhere;
                background: var(--pc-problem-bg); color: var(--pc-problem-fg);
            }
            .pc-band--from-left { margin-left: 0; border-radius: 0 5px 5px 0; }
            .pc-band--to-right  { margin-right: 0; border-radius: 5px 0 0 5px; }
            .pc-band--from-left.pc-band--to-right { border-radius: 0; }
            .pc-band--bed              { border-left-color: var(--pc-bed-border); }
            .pc-band--travel           { border-left-color: var(--pc-travel-border); }
            .pc-band--duplicate        { border-left-color: var(--pc-duplicate-border); }
            .pc-band--clash-city       { border-left-color: var(--pc-clash-city-border); }
            .pc-band--clash-scheduling { border-left-color: var(--pc-clash-scheduling-border); }
            /* The words take what is left after the chip, and may wrap; the chip never shrinks
               and never wraps, so the narrowest one-day band still shows its whole action. */
            .pc-band-text   { flex: 1; min-width: 0; }
            .pc-band-title  { font-weight: 600; font-size: 0.82rem; }
            .pc-band-detail { font-size: 0.75rem; margin-top: 0.1rem; }
            /* The affordance. Knowing the band is clickable used to require having been told,
               which is a hidden affordance (Ted, 2026-08-21): the chip says out loud that there
               is an action here, and what it is. */
            .pc-band-fix {
                flex-shrink: 0; white-space: nowrap;
                font-size: 0.68rem; font-weight: 700; line-height: 1.5;
                background: rgba(255, 255, 255, 0.85);
                border: 1px solid rgba(0, 0, 0, 0.2); border-radius: 4px;
                padding: 0 4px;
            }
            .pc-band-link:hover .pc-band-fix, .pc-band-summary:hover .pc-band-fix { background: #ffffff; }
            /* The band is its own fix menu's summary, so an outer positioned wrapper takes the
               grid placement and the band keeps filling it — the band gains no chrome and no
               height, and week rows keep their shape. */
            .pc-band-anchor { position: relative; display: flex; }
            .pc-band-anchor > .disclosure-menu { display: flex; flex: 1; min-width: 0; }
            .pc-band-summary { display: flex; flex: 1; min-width: 0; cursor: pointer; }
            .pc-band-summary > .pc-band { flex: 1; min-width: 0; }
            /* A band with one answer navigates rather than opening a menu of one, so the whole
               band is the link — same target, one click less. */
            .pc-band-link { display: flex; flex: 1; min-width: 0; text-decoration: none; color: inherit; }
            .pc-band-link > .pc-band { flex: 1; min-width: 0; }
            """;

    public static String render(List<ScheduleProblem> problems, List<ScheduleContext> context, LocalDate today) {
        List<ProblemBand> bands = problems.stream()
                .map(ProblemBand::from)
                .sorted(Comparator.comparing(ProblemBand::firstDay))
                .toList();
        // Context is not clipped here: the window below is drawn from the problems, and the builder
        // renders only the weeks inside it, so context outside that window never reaches the page.
        List<ContextBand> contextBands = context.stream()
                .map(ContextBand::from)
                .toList();

        return "<!DOCTYPE html>\n" + html(
                Page.head("Schedule Problems", CSS + DisclosureMenu.CSS),
                body(
                        div().withClass("page").with(
                                Page.viewNav(Page.NavAudience.OWNER, "/schedule-problems"),
                                h1("Schedule Problems"),
                                ProblemViewToggle.render(ProblemView.CALENDAR),
                                bands.isEmpty() ? renderNoProblems() : renderGrid(bands, contextBands, today)
                        ),
                        rawHtml("<script>" + DisclosureMenu.SCRIPT + "</script>")
                )
        ).withLang("en").render();
    }

    private static DomContent renderNoProblems() {
        return p("No problems found — your schedule looks complete.").withClass("no-problems");
    }

    /**
     * The window: wide enough to hold every band, and always showing at least the fortnight ahead
     * so an untroubled schedule still renders a recognizable calendar. It reaches back before
     * today only for a band that started already — a stay under way can still be missing a bed for
     * tonight. Past problems never arrive here at all; the projector has already dropped them.
     */
    private static DomContent renderGrid(List<ProblemBand> bands, List<ContextBand> context, LocalDate today) {
        LocalDate earliestBandDay = bands.stream()
                .map(ProblemBand::firstDay)
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate latestBandDay = bands.stream()
                .map(ProblemBand::lastDay)
                .max(LocalDate::compareTo)
                .orElseThrow();
        LocalDate rangeStart = earliestBandDay.isBefore(today) ? earliestBandDay : today;
        LocalDate twoWeeksOut = today.plusWeeks(2);
        LocalDate rangeEnd = latestBandDay.isAfter(twoWeeksOut) ? latestBandDay : twoWeeksOut;

        return rawHtml(ProblemCalendarViewBuilder.render(bands, context, rangeStart, rangeEnd, today));
    }
}
