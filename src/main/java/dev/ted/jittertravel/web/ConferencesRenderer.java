package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.DashboardGroup;
import dev.ted.jittertravel.application.DashboardSection;
import dev.ted.jittertravel.application.DroppedView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import j2html.tags.DomContent;
import j2html.tags.specialized.ATag;
import j2html.tags.specialized.DivTag;
import j2html.tags.specialized.TrTag;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static j2html.TagCreator.*;

public class ConferencesRenderer {

    /**
     * One date format for the whole page — the Dates column and the CFP deadline under the name
     * (Ted, 2026-08-22). They were different, and the row read as two vocabularies: {@code Wed
     * 09/02} in the column with {@code Sat, Sep 12} directly beneath it.
     * <p>
     * Numeric, comma-free and unpadded: the Dates column fits two of these and a separator in one
     * elastic cell, and every character saved is width the fixed Actions column does not have to
     * give up. The design handoff specified {@code MM/dd}; the padding went on Ted's reading of it
     * ({@code Wed 9/2}, not {@code Wed 09/02}).
     */
    private static final String DATE_PATTERN = "EEE M/d";
    private static final String TIME_PATTERN = "h:mm a";

    /**
     * {@link #DATE_PATTERN} as a formatter, for the Dates column — which renders plain text rather
     * than a {@code <time>} element, so it has no use for the pattern string itself.
     */
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.ENGLISH);

    // No container max-width and no overflow-x scroller: the table fills the available space and
    // is never wider than it. Going? and Actions are fixed columns holding content that cannot
    // wrap; Name, Dates and City are elastic and absorb a narrow viewport by wrapping their own
    // content, so an iPad in portrait stacks onto more lines rather than scrolling sideways.
    //
    // The container gives up its horizontal margin and padding (measured 2026-08-19: the columns
    // started scrolling at ~860px with them, and fit at ~820px without) — 96px of gutter is worth
    // more spent on the table than on whitespace. Vertical margin stays, so the page keeps its
    // rhythm. The sibling list pages still have their gutters; this one carries more columns.
    private static final String CSS = """
            /* Smooth in-page jumps for the toolbar's count links. Scoped to this page's own
               <style>, so it is this page that scrolls smoothly and not every other one. */
            html { scroll-behavior: smooth; }
            @media (prefers-reduced-motion: reduce) { html { scroll-behavior: auto; } }
            /* No top margin: the filter row below sets the gap under the heading itself, so the
               two cannot stack. They did — the flex row stopped .time-toggle's own 1rem top margin
               from collapsing through the container the way it does on the sibling list pages,
               leaving 3rem of nothing under the title. */
            .conference-container { margin: 0 0 2rem; padding: 0; }
            /* table-layout: fixed is what makes the column model below hold: the browser sizes the
               columns from the header row rather than from the widest cell, so one long conference
               name cannot squeeze the Actions column until its links wrap. */
            .conference-table {
                width: 100%; table-layout: fixed; border-collapse: collapse; text-align: left;
                margin-top: 0.625rem; font-size: 0.875rem;
                box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                border-radius: 8px; overflow: hidden;
            }
            .conference-table th, .conference-table td {
                padding: 9px 12px; border-bottom: 1px solid var(--border-color);
                vertical-align: top;
            }
            .conference-table th {
                background-color: var(--header-bg); color: var(--muted-text);
                font-weight: 600; text-transform: uppercase;
                font-size: 0.6875rem; letter-spacing: 0.5px; box-sizing: border-box;
            }
            /* The column model, and the reason the page still fits without scrolling sideways:
               Going? and Actions are fixed, because neither wraps — a chip, and a row of nowrap
               links. Name, Dates and City are elastic and take the squeeze instead, each wrapping
               its own content. Measured against the design at 820px, where Dates breaks onto two
               lines and the actions stay on one. */
            .conference-table th.conf-col-name { min-width: 145px; }
            .conference-table th.conf-col-going { width: 108px; }
            .conference-table th.conf-col-city { min-width: 120px; }
            .conference-table th.conf-col-actions { width: 240px; }
            .conference-table tbody tr:last-child td { border-bottom: none; }
            .conference-table tbody tr:hover { background-color: var(--hover-bg); }
            .conf-name { font-weight: 500; color: var(--accent-color); }
            /* One column, not two: the range reads as one fact, and merging them is what pays for
               the fixed Actions column. Each date is its own nowrap unit in a wrapping row, so a
               squeezed column breaks between the two dates and never inside one. */
            .conf-dates { display: flex; flex-wrap: wrap; gap: 0 4px; color: var(--muted-text); }
            /* City and country in one cell, for the same reason. */
            .conf-city { color: var(--muted-text); overflow-wrap: break-word; }
            /* The actions a row carries are decided by the state machine, so their number and
               their words change between rows — the fixed Confirm/Decline slots this replaced no
               longer make sense, because most of these moves are meaningless in most states rather
               than unavailable for now.
               One row, always: the column is sized for the widest set the state machine can offer
               ("Submitted / Ticket Bought / Decline"), so a set that wrapped would change its own
               row's height and shift every row under it. */
            .conf-actions { display: flex; flex-wrap: nowrap; gap: 10px; white-space: nowrap; }
            .conf-decline { color: #b00; text-decoration: none; white-space: nowrap; font-size: 0.8125rem; }
            .conf-decline:hover { text-decoration: underline; }
            /* The CFP line under the conference name is itself the link that records or changes
               the deadline — it is a property of the conference, not a move in the submission
               state machine, so it is not in the actions cell. It inherits the muted deadline
               styling rather than the accent colour: it sits under the name, and a second coloured
               link there would compete with the name for the eye. */
            /* UNDERLINED ALWAYS, not on hover (Ted, 2026-09-04: never have an affordance that
               relies on hover). This is the link that records a conference's call for papers, and
               it was muted text with a hover underline — which on the iPad is no affordance at
               all. The underline is permanent and the colour stays muted, so it keeps the
               hierarchy the note above wanted: the fix was never to make it louder, only to make
               it visible. Do not quote the link's own words here — CSS comments ship inside
               <style>, and ConferencesRendererTest asserts on their absence from the page. */
            .conf-cfp { color: inherit; text-decoration: underline; }
            .conf-cfp:hover { color: var(--accent-color); }
            /* The way out to the submission page, on the same muted line and in the same muted
               colour: it is a second link on that line, and colouring it would make the CFP line
               louder than the conference name it sits under. Underlined always, same reason. */
            .conf-cfp-submit { color: inherit; text-decoration: underline; white-space: nowrap; }
            .conf-cfp-submit:hover { color: var(--accent-color); }
            .conf-cfp-sep { opacity: 0.6; }
            /* The conference name links to its own page when it has one. Declares the accent
               colour it was already inheriting from the .conf-name cell: same pixels, but the
               affordance is now local rather than depending on an ancestor that could change and
               silently take the link's visibility with it. */
            .conf-info-link { color: var(--accent-color); text-decoration: none; }
            .conf-info-link:hover { text-decoration: underline; }
            /* Action labels are nowrap units, and the 240px column is budgeted for three of them —
               the reason they are one or two short words. */
            .conf-action { color: var(--accent-color); text-decoration: none; white-space: nowrap; font-size: 0.8125rem; }
            .conf-action:hover { text-decoration: underline; }
            /* Same two words the public calendar uses, so the list and the calendar agree. */
            .conf-commitment {
                font-size: 0.6875rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em;
                padding: 2px 6px; border-radius: 4px; white-space: nowrap;
            }
            .conf-commitment--watching { background: #b45309; color: #ffffff; }
            .conf-commitment--going { background: #166534; color: #ffffff; }
            /* The one chip whose fill is nearly the row's own colour, so without an edge it had no
               visible left boundary and read as sitting a pixel or two off from the solid chips
               above it in the column (Ted, 2026-08-22).
               The padding is reduced by exactly the border's width, so the outer box still matches
               the two filled chips — a 1px border added on top of the shared 2px 6px would have
               made it a pixel bigger all round and moved the misalignment rather than fixing it.
               Same compensation .conf-speaker uses, for the same reason. */
            .conf-commitment--dropped {
                background: var(--header-bg); color: var(--muted-text);
                border: 1px solid var(--border-color); padding: 1px 5px;
            }
            /* SPEAKER sits beside the commitment chip in the same column, not in one of its own:
               it is a second fact about the same question, and a "Maybe" conference Ted has been
               invited to reads "Maybe SPEAKER". One word, and nowrap, for the reason the Actions
               labels are: this table only just fits at ~820px and every extra character in a
               nowrap unit widens its minimum.
               Outlined rather than filled, so it reads as an annotation on the chip rather than
               competing with it: two solid blocks in one cell would look like two chips of equal
               weight, and the commitment is the answer to the column's question. */
            .conf-speaker {
                font-size: 0.6875rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em;
                padding: 1px 5px; border-radius: 4px; white-space: nowrap;
                border: 1px solid var(--accent-color); color: var(--accent-color);
            }
            /* The pair wraps together when the column is narrow rather than overflowing it. */
            .conf-going-cell { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
            /* Each group gets a heading and a line of guidance over its own table. No colour on the
               headings: this page is an action list, not a problem report, and the rule that every
               problem wears the same amber is about problems sitting among non-problems. The
               commitment chips already carry what colour this page needs. */
            .dashboard-section { margin-top: 1.625rem; scroll-margin-top: 1rem; }
            .dashboard-section:first-child { margin-top: 1.125rem; }
            .dashboard-heading {
                font-size: 0.8125rem; font-weight: 700; text-transform: uppercase;
                letter-spacing: 0.06em; color: var(--muted-text); margin: 0;
            }
            .dashboard-guidance { font-size: 0.875rem; color: var(--muted-text); margin: 0.125rem 0 0; }
            /* The two filters sit on one line and wrap together on a narrow viewport. The row owns
               the gap under the heading; .time-toggle's own top margin is cancelled here, because
               a flex item's margin does not collapse and the two would add up. */
            .conference-filters {
                display: flex; flex-wrap: wrap; gap: 1rem; align-items: center; margin-top: 1rem;
            }
            .conference-filters .time-toggle { margin-top: 0; }
            /* margin-left: auto is what holds it at the right edge, with no spacer element and no
               change to the two controls before it. When the row wraps on a narrow viewport it
               keeps the right edge of whatever line it lands on, which is where the eye looks for
               it either way. Padding matches .time-toggle's so the three line up at one height.
               Filled green, alone in this row (Ted, 2026-08-22): everything else on the toolbar
               answers "which of these am I looking at?", and this one leaves the page entirely.
               Outlined in the accent colour it read as a third filter, because the active time
               segment is accent-filled and the jump links are accent text. A different hue, and
               solid rather than outlined, is what separates a create action from a filter — and
               green is not a colour this app's palette uses for anything else, so it carries no
               meaning it would have to fight. The border matches the fill so the box stays exactly
               .time-toggle's size.
               #1e7a1e rather than CSS `forestgreen` (#228B22), which this started as: white on
               forestgreen is 4.4:1, just under WCAG AA's 4.5:1 for text this size. Two shades
               darker is visually the same green and clears it at 5.4:1. Bold for the same reason —
               weight is the other half of legibility on a filled control. */
            .conf-plan-link {
                margin-left: auto;
                display: inline-flex; align-items: center;
                padding: 6px 16px; font-size: 0.875rem; font-weight: 700;
                border: 1px solid #1e7a1e; border-radius: 6px;
                color: #fff; background: #1e7a1e;
                text-decoration: none; white-space: nowrap;
            }
            /* Darker on hover rather than lighter: a lighter fill would walk the white text back
               under the contrast floor the colour was chosen to clear. */
            .conf-plan-link:hover { background: #1b6f1b; border-color: #1b6f1b; }
            /* One bordered group carrying both of the page's remaining jobs: jumping to a state
               section, and saying whether the dropped conferences are in. They belong in one bar
               because they answer the same question — "which of these lists am I looking at?" */
            .conf-jump-bar {
                display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px;
                padding: 4px 10px; border: 1px solid var(--border-color);
                border-radius: 6px; background: var(--header-bg);
            }
            /* Dotted underline rather than solid: these jump within the page rather than leading
               off it, and the dotted line plus the hover fill are what mark them as click targets
               without making them look like the conference names in the table below. */
            .conf-jump {
                display: inline-flex; align-items: baseline; gap: 5px;
                padding: 3px 8px; border-radius: 5px; font-size: 0.875rem;
                color: var(--accent-color); cursor: pointer;
                text-decoration: underline; text-decoration-style: dotted; text-underline-offset: 3px;
            }
            /* --event-bg is the tint the design names for this fill (#e0e7ff); the token is used
               rather than the literal so the accent ramp stays defined in one place. */
            .conf-jump:hover { background: var(--event-bg); text-decoration-style: solid; }
            .conf-jump--small { font-size: 0.8125rem; }
            .conf-jump-sep { color: #ced4da; }
            /* The key control, and the reason the bar exists. The label never changes — always
               "Show dropped <n>" — so the box is the only thing carrying state, and the control
               reports what is on the page rather than what a click would do. The "Hide dropped"
               link this replaced named the action instead, which left no way to tell from the
               words whether the dropped conferences were currently in or out. */
            .conf-dropped-toggle {
                display: inline-flex; align-items: center; gap: 7px;
                padding: 3px 9px; border-radius: 5px; font-size: 0.875rem;
                color: var(--text-color); text-decoration: none;
                border: 1px solid var(--border-color); background: var(--surface, #fff);
            }
            .conf-dropped-toggle:hover { border-color: var(--accent-color); }
            /* Driven off aria-pressed, so the state a screen reader announces and the state the
               box draws cannot drift apart — there is only one place either is set. */
            .conf-dropped-toggle[aria-pressed="true"] {
                border-color: var(--accent-color); background: #eef2ff;
            }
            .conf-dropped-box {
                width: 14px; height: 14px; border-radius: 3px;
                display: inline-flex; align-items: center; justify-content: center;
                font-size: 10px; line-height: 1; color: #fff;
                border: 1px solid #adb5bd; background: var(--surface, #fff);
            }
            .conf-dropped-toggle[aria-pressed="true"] .conf-dropped-box {
                border-color: var(--accent-color); background: var(--accent-color);
            }
            /* The deadline under the name, not in a column of its own — see nameCell. */
            .conf-cfp-deadline {
                font-size: 0.75rem; font-weight: 400; color: var(--muted-text); margin-top: 0.15rem;
            }
            """;

    public static String render(List<DashboardSection> sections, TimeView activeFilter) {
        return render(sections, activeFilter, DroppedView.HIDE, 0);
    }

    /**
     * The page under both of its filters. They are independent parameters and each control carries
     * the other's value through, so changing one never silently resets the other — see
     * {@link DroppedView} for why they are not one parameter.
     * <p>
     * The two-argument overload above is what {@code TimeFilterToggleConventionTest} discovers and
     * exercises; it is the default view, dropped conferences hidden and none to count.
     *
     * @param droppedCount how many conferences the dropped filter is currently leaving out, which
     *                     the toolbar's switch needs <em>even while they are hidden</em>: its label
     *                     reports what the page is showing rather than what a click would do, so
     *                     the number cannot come from the sections it was handed. Supplied by the
     *                     controller from {@code ConferenceProjector.droppedCount}.
     */
    public static String render(List<DashboardSection> sections, TimeView activeFilter,
                                DroppedView activeDropped, int droppedCount) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Conferences", CSS),
                body(
                        Page.viewNav(Page.NavAudience.OWNER, "/conferences"),
                        h1("Conferences"),
                        div().withClass("conference-container").with(
                                div().withClass("conference-filters").with(
                                        TimeFilterToggle.render("/conferences", activeFilter,
                                                activeDropped == DroppedView.SHOW ? "&dropped=show" : ""),
                                        jumpBar(sections, activeFilter, activeDropped, droppedCount),
                                        planLink()),
                                sections.isEmpty()
                                        ? renderEmptyState(activeFilter)
                                        : div().with(sections.stream()
                                                             .map(ConferencesRenderer::renderSection)
                                                             .toList())
                        )
                )
        ).withLang("en").render();
    }

    /**
     * The page's one create action, at the right edge of the toolbar rather than under the last
     * table (Ted, 2026-08-22). At the bottom it was reachable only by scrolling past every section,
     * and it grew further away the more conferences there were — the one control on the page whose
     * distance depended on the data.
     * <p>
     * Bordered and accent-coloured, at the toolbar's own height: it stands beside two filters, and
     * a bare link there would read as a sentence trailing them — the mistake the old "Show dropped"
     * link made. Outlined rather than filled, because the page is a list of things needing
     * decisions and planning a new one is not the urgent move on it.
     */
    private static DomContent planLink() {
        return a("Plan another conference").withClass("conf-plan-link")
                                           .withHref("/plan-conference");
    }

    /**
     * One bar doing both of the jobs the old filter row could not: saying how many conferences are
     * in each state and jumping to it, and saying whether the dropped ones are included.
     * <p>
     * <strong>A count is only rendered for a section that is on the page.</strong>
     * {@link dev.ted.jittertravel.application.ConferenceDashboard} already leaves empty groups out,
     * so the bar mirrors the sections it is handed — which also means every jump lands somewhere:
     * a link to a heading that is not in the document would do nothing at all, silently.
     * <p>
     * Plain {@code #anchor} links, not script: the page scrolls the document rather than an inner
     * pane, so the browser's own behaviour is the whole feature, and each jump stays shareable.
     */
    private static DomContent jumpBar(List<DashboardSection> sections, TimeView activeFilter,
                                      DroppedView activeDropped, int droppedCount) {
        DivTag bar = div().withClass("conf-jump-bar");
        sections.stream()
                // Dropped is not a count here: it is the switch's own number, at the end of the bar.
                .filter(section -> section.group() != DashboardGroup.DROPPED)
                .forEach(section -> bar.with(
                        jumpLink(section.group(), section.conferences().size()),
                        span("/").withClass("conf-jump-sep")));
        bar.with(droppedSwitch(activeFilter, activeDropped, droppedCount));
        if (sections.stream().anyMatch(section -> section.group() == DashboardGroup.DROPPED)) {
            bar.with(a("jump").withClass("conf-jump conf-jump--small")
                              .withTitle("Jump to " + heading(DashboardGroup.DROPPED))
                              .withHref("#" + sectionId(DashboardGroup.DROPPED)));
        }
        return bar;
    }

    /** The number is bold and the noun is not: the count is what is being scanned for. */
    private static DomContent jumpLink(DashboardGroup group, int count) {
        return a().withClass("conf-jump")
                  .withTitle("Jump to " + heading(group))
                  .withHref("#" + sectionId(group))
                  .with(b(String.valueOf(count)), text(jumpLabel(group)));
    }

    /**
     * <strong>The label never changes, only the box does.</strong> It always reads
     * {@code Show dropped <n>}, so the control describes the state of the page rather than the
     * effect of clicking it — which is what the "Show dropped"/"Hide dropped" link it replaced
     * could not do: whichever word was showing named the action, and nothing said whether the
     * dropped conferences were currently in or out.
     * <p>
     * A link, not a button: this writes {@code ?dropped=show} into the URL exactly as the old one
     * did, so the view stays server-rendered and shareable and no form (or CSRF token) is needed.
     * {@code role="button"} with {@code aria-pressed} is what makes the checkbox semantics real
     * rather than only drawn, and the CSS reads that same attribute so the two cannot disagree.
     */
    private static DomContent droppedSwitch(TimeView activeFilter, DroppedView activeDropped,
                                            int droppedCount) {
        boolean shown = activeDropped == DroppedView.SHOW;
        String filterQuery = "?filter=" + activeFilter.name().toLowerCase(Locale.ENGLISH);
        ATag control = a().withClass("conf-dropped-toggle")
                          .attr("role", "button")
                          .attr("aria-pressed", String.valueOf(shown))
                          .withTitle(shown
                                  ? "Dropped conferences are shown. Click to hide them."
                                  : "Dropped conferences are hidden. Click to show them.")
                          .withHref("/conferences" + filterQuery + (shown ? "" : "&dropped=show"));
        // The tick as a numeric entity rather than the character itself: the page is UTF-8, but an
        // ASCII source file cannot be mis-encoded on the way out (CLAUDE.md, j2html encoding).
        return control.with(
                shown ? span(rawHtml("&#10003;")).withClass("conf-dropped-box")
                      : span().withClass("conf-dropped-box"),
                text("Show dropped"),
                b(String.valueOf(droppedCount)));
    }

    /**
     * A heading, one line saying what to do about the group, and the group's own table. The wording
     * lives here rather than on {@link DashboardGroup}: the enum is the derived fact, and how it is
     * worded is presentation (CLAUDE.md).
     * <p>
     * Exhaustive, so a new group cannot be added without deciding what it tells Ted to do.
     */
    private static DomContent renderSection(DashboardSection section) {
        return div().withClass("dashboard-section")
                    .withId(sectionId(section.group()))
                    .with(
                            h2(heading(section.group())).withClass("dashboard-heading"),
                            p(guidance(section.group())).withClass("dashboard-guidance"),
                            renderTable(section.conferences())
                    );
    }

    /** Derived from the group rather than written out, so a jump link and its section cannot drift. */
    private static String sectionId(DashboardGroup group) {
        return "section-" + group.name().toLowerCase(Locale.ENGLISH).replace('_', '-');
    }

    private static String heading(DashboardGroup group) {
        return switch (group) {
            case CFP_CLOSES_SOON -> "CFP closes soon";
            case INVITED -> "Invited";
            case CFP_DATE_UNKNOWN -> "CFP date unknown";
            case DECIDE -> "Decide";
            case NOTHING_TO_SUBMIT -> "Nothing to submit";
            case WAITING_TO_HEAR -> "Waiting to hear";
            case GOING -> "Going";
            case DROPPED -> "Dropped";
        };
    }

    /**
     * The jump bar's shorter wording for the same group, lower case: it follows a bold number and
     * reads as a quantity — "<b>1</b> CFP closing" — where {@link #heading} titles a section. Short
     * because seven of these can share one bar, and the bar wraps before the page does.
     * <p>
     * Exhaustive for the same reason as {@link #heading}: a new group has to be given its word here
     * too, or it is on the page with no way to reach it.
     */
    private static String jumpLabel(DashboardGroup group) {
        return switch (group) {
            case CFP_CLOSES_SOON -> "CFP closing";
            case INVITED -> "invited";
            case CFP_DATE_UNKNOWN -> "CFP unknown";
            case DECIDE -> "to decide";
            case NOTHING_TO_SUBMIT -> "open space";
            case WAITING_TO_HEAR -> "waiting";
            case GOING -> "going";
            case DROPPED -> "dropped";
        };
    }

    private static String guidance(DashboardGroup group) {
        return switch (group) {
            case CFP_CLOSES_SOON -> "Submit, or decide not to.";
            case INVITED -> "They asked you to speak. Say yes, or decline.";
            case CFP_DATE_UNKNOWN -> "Find the deadline and record it, so a reminder can be set.";
            case DECIDE -> "No talk this time. Go as an attendee, or decline.";
            case NOTHING_TO_SUBMIT -> "Sessions are chosen on the day — just decide whether to go.";
            case WAITING_TO_HEAR -> "Submitted. Record what they say when they say it.";
            case GOING -> "Committed — nothing to do.";
            case DROPPED -> "Said no to these. Kept as a record for next year.";
        };
    }

    private static DomContent renderEmptyState(TimeView activeFilter) {
        String message = activeFilter == TimeView.FUTURE
                ? "No upcoming conferences."
                : "No conferences yet.";
        return p(message).withClass("empty-state");
    }

    /**
     * Five columns, each section carrying its own {@code thead} — the header row is what
     * {@code table-layout: fixed} sizes the columns from, so every section has to have one.
     */
    private static DomContent renderTable(List<ConferenceView> conferences) {
        return table().withClass("conference-table").with(
                thead(tr(
                        th("Name").withClass("conf-col-name"),
                        th("Going?").withClass("conf-col-going"),
                        // Dates is the one elastic column with no floor: it absorbs whatever width
                        // the other four leave, and wraps to two lines when that is not much.
                        th("Dates"),
                        th("City").withClass("conf-col-city"),
                        th("Actions").withClass("conf-col-actions")
                )),
                tbody().with(
                        conferences.stream()
                                   .map(ConferencesRenderer::renderRow)
                                   .toList()
                )
        );
    }

    private static TrTag renderRow(ConferenceView conf) {
        return tr(
                td(nameCell(conf)).withClass("conf-name"),
                td(goingCell(conf)),
                td(datesCell(conf)),
                td(cityCell(conf)).withClass("conf-city"),
                td(actions(conf))
        );
    }

    /**
     * The name, and under it one line saying where this conference's talk stands.
     * <p>
     * A second line rather than a column of its own: this table only just fits at ~820px, and a new
     * column would push it into the horizontal scroll that is ruled out everywhere.
     * <p>
     * The name is a link when the conference recorded its own web page, and plain text when it did
     * not — the same treatment a gathering's title gets on the itinerary and the calendar. This is
     * the conference's public page, never its CFP; that link lives on the line below, where it
     * belongs to the deadline.
     */
    private static DomContent nameCell(ConferenceView conf) {
        return div().with(
                div().with(nameContent(conf)),
                subLine(conf)
        );
    }

    private static DomContent nameContent(ConferenceView conf) {
        if (conf.infoUrl().isBlank()) {
            return text(conf.name());
        }
        return a(conf.name()).withClass("conf-info-link")
                             .withTitle("Open the conference's own page")
                             .withTarget("_blank")
                             .withRel("noopener")
                             .withHref(conf.infoUrl());
    }

    /**
     * The two dates as one range, day-only (Ted, 2026-08-22). Two columns became one so the Actions
     * column could be fixed at the width its widest set needs, and the clock times went with them:
     * what this page is scanned for is which days a conference occupies, and the row it belongs to
     * has room for exactly one line.
     * <p>
     * Each date is its own {@code .nowrap} unit inside a wrapping row, so a squeezed column breaks
     * between the two and never inside one. Plain text rather than {@link ZonedTimeTag}: with no
     * time to show there is no instant to carry, and a {@code <time>} element whose text is a bare
     * day would put a UTC timestamp in the markup that nothing renders.
     */
    private static DomContent datesCell(ConferenceView conf) {
        return div().withClass("conf-dates").with(
                span(day(conf.startDate()) + " -").withClass("nowrap"),
                span(day(conf.endDate())).withClass("nowrap")
        );
    }

    private static String day(ZonedTimestamp when) {
        return DAY.format(when.atEntryZone());
    }

    /**
     * City and country in one cell, joined here rather than on {@link ConferenceView}: which
     * separator goes between them is a decision about how this table reads, and the domain has no
     * business holding it (CLAUDE.md, "Presentation formatting stays out of the domain").
     * <p>
     * A country is {@code ""} when absent — the domain's sentinel for a missing string — and the
     * city then stands alone rather than trailing a comma.
     */
    private static String cityCell(ConferenceView conf) {
        return conf.country().isEmpty()
                ? conf.city()
                : conf.city() + ", " + conf.country();
    }

    /**
     * <strong>The line under the name says the last thing that happened to this conference's
     * talk</strong>, and the CFP deadline is what it says before anything has (Ted, 2026-08-22).
     * <p>
     * The rule it replaced showed the deadline in every state, which made the {@code Decide} group
     * unreadable: that group holds two quite different situations — a talk was turned down, or the
     * CFP closed with nothing submitted — and both rows read {@code CFP <date>}, so nothing on the
     * page said which. A deadline is also simply the wrong thing to show once it can no longer be
     * acted on: it is irrelevant to a rejected talk, and it is what the group is named after.
     * <p>
     * <strong>Where submitting is still on the table, the deadline stays</strong>, link and all —
     * nothing submitted yet, and a talk since withdrawn, which is the state that puts submitting
     * back on the table. Everywhere else the stream has spoken and its word is what shows.
     * <p>
     * It is deliberately not gated on the group: a row says where it stands whichever heading it is
     * under, which is what makes {@code Going} distinguish an accepted talk from an invitation taken
     * up from a ticket bought.
     */
    private static DomContent subLine(ConferenceView conf) {
        String talkState = talkState(conf.speakingStatus());
        if (talkState != null) {
            return div(talkState).withClass("conf-cfp-deadline");
        }
        return cfpLine(conf);
    }

    /**
     * Where the talk stands, in the words the actions cell records it with — "Rejected" the action,
     * "Talk rejected" the state, so the two read as the same vocabulary.
     * <p>
     * Null means the submission stream has said nothing that outranks the CFP deadline: either
     * nothing has been submitted, or a talk was withdrawn and submitting is open again. Exhaustive,
     * so a new {@link SpeakingStatus} cannot be added without deciding what this row says about it.
     */
    private static String talkState(SpeakingStatus status) {
        return switch (status) {
            case SUBMITTED -> "Talk submitted";
            case ACCEPTED -> "Talk accepted";
            case REJECTED -> "Talk rejected";
            case INVITED -> "Invited to speak";
            // Both mean "submitting is the open question", which is the deadline's business.
            case NOT_SPEAKING, WITHDRAWN -> null;
        };
    }

    /**
     * The CFP deadline under the name, and the deadline <em>is</em> the affordance for recording or
     * changing it — which is why this is not an action in the actions cell (Ted, 2026-08-22). A
     * closing date is a property of the conference, like its venue, not a move in the submission
     * state machine, and keeping it out is what lets every state fit in three actions.
     * <p>
     * A conference with no deadline recorded still shows the line, because "not recorded" is the
     * state the CFP-date-unknown group exists to prompt about — an absent line would hide the very
     * job that group is asking for. An open-space conference shows nothing at all: it has no call
     * for papers, and the command refuses to record one.
     */
    private static DomContent cfpLine(ConferenceView conf) {
        if (conf.format() == ConferenceFormat.OPEN_SPACE) {
            return span();
        }
        // A dropped conference keeps its deadline as a record but not as a link: recording a CFP
        // for a conference Ted declined is refused by the domain, so a link there would lead
        // nowhere. Nothing to show at all if no deadline was ever recorded.
        if (conf.commitment() == AttendanceCommitment.NOT_GOING) {
            return conf.cfpClosesOn() == null
                    ? span()
                    : div().withClass("conf-cfp-deadline").with(
                            span("CFP "),
                            ZonedTimeTag.renderDateTimeStacking(
                                    conf.cfpClosesOn(), DATE_PATTERN, TIME_PATTERN));
        }
        String href = "/conferences/" + conf.conferenceId().id() + "/cfp";
        if (conf.cfpClosesOn() == null) {
            return div().withClass("conf-cfp-deadline").with(
                    a("CFP date unknown").withClass("conf-cfp")
                                         .withTitle("Record when this conference's CFP closes")
                                         .withHref(href));
        }
        DivTag line = div().withClass("conf-cfp-deadline").with(
                a().withClass("conf-cfp")
                   .withTitle("Change the recorded CFP deadline")
                   .withHref(href)
                   .with(span("CFP "),
                         ZonedTimeTag.renderDateTimeStacking(
                                 conf.cfpClosesOn(), DATE_PATTERN, TIME_PATTERN)));
        return line.with(submitLink(conf));
    }

    /**
     * The way out to wherever the talk is submitted — Sessionize, usually. It hangs off the deadline
     * line because it is the same fact: this CFP is open, it closes then, you submit there.
     * <p>
     * It shows only while the deadline line does, which is exactly while submitting is still on the
     * table. Once the stream has spoken the line above is a talk state and this goes with it —
     * a link inviting Ted to submit to a conference that already turned him down would be the
     * dashboard arguing with itself.
     * <p>
     * External, so it opens in a new tab: every other outbound link in the app does (a hotel's map,
     * a gathering's page), and the row it leaves is a working surface to come back to.
     */
    private static DomContent submitLink(ConferenceView conf) {
        if (conf.cfpSubmissionUrl().isBlank()) {
            return span();
        }
        return span().with(
                span(" · ").withClass("conf-cfp-sep"),
                a("Submit").withClass("conf-cfp-submit")
                           .withTitle("Open the CFP's submission page")
                           .withTarget("_blank")
                           .withRel("noopener")
                           .withHref(conf.cfpSubmissionUrl()));
    }

    /**
     * The {@code Going?} column answers one question with up to two marks: the commitment chip,
     * always, and {@code SPEAKER} beside it when Ted is speaking. They are not alternatives — a
     * speculative conference he has been invited to reads {@code Maybe SPEAKER}.
     * <p>
     * <strong>{@code SPEAKER}, not the calendar's "A Ted Talk".</strong> Same fact, two surfaces,
     * two lengths: a calendar entry owns a whole row of a day column and can afford the playful
     * wording, while this cell is a nowrap unit in a fixed 108px column.
     */
    private static DomContent goingCell(ConferenceView conf) {
        DivTag cell = div().withClass("conf-going-cell").with(commitmentChip(conf.commitment()));
        if (conf.speaking()) {
            cell.with(span("Speaker").withClass("conf-speaker")
                                     .withTitle("Ted is speaking at this one"));
        }
        return cell;
    }

    /**
     * "Maybe" reads the same here as on the public calendar, deliberately: one vocabulary for one
     * fact. "Going" is spelled out on this list even though the calendar marks it only by the
     * absence of a chip — a table column has to say something in every row.
     */
    private static DomContent commitmentChip(AttendanceCommitment commitment) {
        return switch (commitment) {
            case WATCHING -> span("Maybe").withClass("conf-commitment conf-commitment--watching");
            case GOING -> span("Going").withClass("conf-commitment conf-commitment--going");
            // Muted rather than red: dropping a conference is a decision, not a problem, and the
            // rule that every problem wears the same amber is about problems among non-problems.
            case NOT_GOING -> span("Not going").withClass("conf-commitment conf-commitment--dropped");
        };
    }

    /**
     * <strong>The state machine decides what a row offers.</strong> Each state has at most three
     * moves, so they are links and never a menu — and, unlike the fixed Confirm/CFP/Decline slots
     * this replaced, an action that does not apply is <em>absent</em> rather than greyed.
     * <p>
     * That is a deliberate exception to "an unavailable action is shown disabled, with the reason"
     * (CLAUDE.md), taken with Ted on 2026-08-22: that rule is about an action that has been or
     * will be available to this viewer, and most moves here are not merely unavailable-for-now but
     * meaningless — "Accepted" on a conference nothing was submitted to names an event that could
     * never be true. Carrying nine greyed labels on every row to keep positions fixed would say
     * less, not more. Recording the CFP deadline is <em>not</em> in this cell at all: it is a
     * property of the conference rather than a move, and it lives under the name.
     * <p>
     * <strong>Three of these fit the 240px column on one line, and that is the budget.</strong>
     * The column is fixed so a long conference name cannot squeeze the links into wrapping, which
     * would change a row's height and shift every row below it — but the arithmetic only works
     * while no state offers a fourth action or a longer label. {@code ConferencesRendererTest}
     * pins the count.
     * <p>
     * Every action is a link to a page that hosts the actual POST form, never a POST from here —
     * this renderer is j2html, and the project keeps POST forms in Thymeleaf so renderers stay
     * clear of Spring's CSRF plumbing. The link carries the choice, so the page opens with it
     * already selected and the second click is a confirmation rather than a decision.
     * <p>
     * The labels are past tense, because that is what this app does: it records what has already
     * happened in the world. "Ticket Bought", not "Buy ticket" (Ted, 2026-08-22).
     */
    private static DomContent actions(ConferenceView conf) {
        String base = "/conferences/" + conf.conferenceId().id();
        // A dropped conference has no live action: the domain refuses every command against a
        // declined conference, so there is nothing here that could be triggered — not even in a
        // disabled form, which would promise a capability that does not exist. Going after all
        // means planning it again.
        if (conf.commitment() == AttendanceCommitment.NOT_GOING) {
            return div().withClass("conf-actions");
        }
        DivTag cell = div().withClass("conf-actions");
        if (conf.commitment() == AttendanceCommitment.GOING) {
            // Committed. The only talk-side move left is pulling a talk that is in the program —
            // which changes nothing about attending.
            if (conf.speakingStatus() == SpeakingStatus.ACCEPTED) {
                cell.with(talkLink(base, TalkOutcome.WITHDRAWN, "Withdrawn",
                        "Record that you pulled your talk. You are still going."));
            }
            return cell.with(declineLink(base));
        }
        return switch (conf.speakingStatus()) {
            // Submitted and waiting: the only moves are what the organizers say, and pulling it.
            case SUBMITTED -> cell
                    .with(talkLink(base, TalkOutcome.ACCEPTED, "Accepted",
                            "They said yes. This also records that you are going."))
                    .with(talkLink(base, TalkOutcome.REJECTED, "Rejected", "They said no."))
                    .with(talkLink(base, TalkOutcome.WITHDRAWN, "Withdrawn", "You pulled it."));
            // An open offer. Saying yes is a confirmation naming the invitation as the reason,
            // which is what separates speaking there from merely attending.
            case INVITED -> cell
                    .with(confirmLink(base, AttendanceBasis.SPEAKING_INVITED, "Invitation Accepted",
                            "Say yes: you are going, and you are speaking."))
                    .with(declineLink(base));
            // Turned down. On an ACCEPTANCE_REQUIRED conference this row is not here at all — it
            // was dropped — so this is the go-anyway case.
            case REJECTED -> cell
                    .with(confirmLink(base, AttendanceBasis.TICKET_PURCHASED, "Ticket Bought",
                            "Going as an attendee after all."))
                    .with(declineLink(base));
            // Nothing outstanding: submitting is on the table again wherever there is a CFP.
            case NOT_SPEAKING, WITHDRAWN -> {
                if (conf.format() != ConferenceFormat.OPEN_SPACE) {
                    cell.with(talkLink(base, TalkOutcome.SUBMITTED, "Submitted",
                            "Record that you submitted a talk."));
                }
                yield cell
                        .with(confirmLink(base, AttendanceBasis.TICKET_PURCHASED, "Ticket Bought",
                                "Going as an attendee."))
                        .with(declineLink(base));
            }
            // Accepted while still merely watching cannot happen: accepting commits attendance,
            // so the GOING branch above has already returned.
            case ACCEPTED -> cell.with(declineLink(base));
        };
    }

    private static DomContent talkLink(String base, TalkOutcome outcome, String label, String title) {
        return a(label).withClass("conf-action")
                       .withTitle(title)
                       .withHref(base + "/talk?outcome=" + outcome.name());
    }

    private static DomContent confirmLink(String base, AttendanceBasis basis, String label, String title) {
        return a(label).withClass("conf-action")
                       .withTitle(title)
                       .withHref(base + "/confirm?basis=" + basis.name());
    }

    private static DomContent declineLink(String base) {
        return a("Decline").withClass("conf-decline")
                           .withTitle("Record that you are not going.")
                           .withHref(base + "/decline");
    }
}
