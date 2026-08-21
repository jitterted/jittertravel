package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleContext;
import dev.ted.jittertravel.application.ScheduleProblem;
import j2html.tags.DomContent;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
            /* Lane colours match the card columns in the list view: the two views of one report
               must not disagree about what a colour means. */
            :root {
                --pc-border: #dee2e6;
                --pc-border-strong: darkgray;
                --pc-surface: #ffffff;
                --pc-header-bg: #f8f9fa;
                --pc-text-secondary: #495057;
                --pc-past-hatch: rgba(0, 0, 0, 0.1);
                --pc-today-tint: #eef2ff;
                /* Problem bands are translucent so the context they sit inside stays visible
                   through them — the cause is the point of the backdrop. */
                --pc-bed-bg: rgba(219, 234, 254, 0.8); --pc-bed-border: #1d4ed8; --pc-bed-fg: #1e3a8a;
                --pc-travel-bg: rgba(254, 243, 199, 0.8); --pc-travel-border: #b45309; --pc-travel-fg: #78350f;
                /* Amber, not red: a second booking costs money but Ted can cancel it. */
                --pc-duplicate-bg: rgba(255, 237, 213, 0.8); --pc-duplicate-border: #c2410c; --pc-duplicate-fg: #7c2d12;
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
                margin: 2px 3px; padding: 3px 6px; border-radius: 5px;
                border-left: 4px solid transparent; overflow-wrap: anywhere;
            }
            .pc-band--from-left { margin-left: 0; border-radius: 0 5px 5px 0; }
            .pc-band--to-right  { margin-right: 0; border-radius: 5px 0 0 5px; }
            .pc-band--from-left.pc-band--to-right { border-radius: 0; }
            .pc-band--bed {
                background: var(--pc-bed-bg); border-left-color: var(--pc-bed-border); color: var(--pc-bed-fg);
            }
            .pc-band--travel {
                background: var(--pc-travel-bg); border-left-color: var(--pc-travel-border); color: var(--pc-travel-fg);
            }
            .pc-band--duplicate {
                background: var(--pc-duplicate-bg); border-left-color: var(--pc-duplicate-border); color: var(--pc-duplicate-fg);
            }
            .pc-band-title  { font-weight: 600; font-size: 0.82rem; }
            .pc-band-detail { font-size: 0.75rem; margin-top: 0.1rem; }
            /* The band is its own fix menu's summary, so an outer positioned wrapper takes the
               grid placement and the band keeps filling it — the band gains no chrome and no
               height, and week rows keep their shape. */
            .pc-band-anchor { position: relative; display: flex; }
            .pc-band-anchor > .disclosure-menu { display: flex; flex: 1; min-width: 0; }
            .pc-band-summary { display: flex; flex: 1; min-width: 0; cursor: pointer; }
            .pc-band-summary > .pc-band { flex: 1; min-width: 0; }
            """;

    public static String render(List<ScheduleProblem> problems, List<ScheduleContext> context, LocalDate today) {
        List<ProblemBand> bands = problems.stream()
                .map(ProblemBand::from)
                .flatMap(Optional::stream)
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
