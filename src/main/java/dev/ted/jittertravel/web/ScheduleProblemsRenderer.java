package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import j2html.tags.DomContent;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static j2html.TagCreator.*;

public class ScheduleProblemsRenderer {

    private static final String DATE_TIME_FORMAT = "MMM d, h:mm a";
    private static final String DAY_DATE_TIME_FORMAT = "EEE, MMM d, h:mm a";
    private static final String TIME_FORMAT = "h:mm a";
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

    /** Above this many choices a list of links becomes a menu — the standing dropdown rule. */
    private static final int MENU_THRESHOLD = 3;

    /** Why a scheduling clash has no link: neither side carries an id to edit (F6 in the plan). */
    private static final String NO_FIX_REASON =
            "Editing a gathering from here arrives with cause-linking";

    private static final String CSS = """
            .page { max-width: 1100px; }
            .no-problems { color: var(--muted-text); font-style: italic; font-size: 0.95rem; padding: 2rem 0; }
            .problem-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 2rem; align-items: start; }
            .column-heading {
                font-size: 0.75rem; font-weight: 700; text-transform: uppercase;
                letter-spacing: 0.1em; margin: 0 0 0.6rem;
            }
            .column-heading--travel    { color: #92400e; }
            .column-heading--hotel     { color: #1e40af; }
            .column-heading--scheduling { color: #991b1b; }
            .column-heading--duplicate { color: #9a3412; }
            .problem-list { display: flex; flex-direction: column; gap: 0.6rem; }
            .problem-card { border-left: 4px solid transparent; border-radius: 0 6px 6px 0; padding: 0.6rem 0.85rem; }
            .problem-card--missing-travel   { border-left-color: #b45309; background: #fef3c7; }
            .problem-card--missing-hotel    { border-left-color: #1d4ed8; background: #dbeafe; }
            .problem-card--scheduling-conflict { border-left-color: #dc2626; background: #fee2e2; }
            .problem-card--city-conflict { border-left-color: #7c3aed; background: #ede9fe; }
            /* Amber, not red: a second booking costs money, but Ted can cancel it. */
            .problem-card--duplicate-hotel { border-left-color: #c2410c; background: #ffedd5; }
            .problem-title  { font-weight: 600; font-size: 0.9rem; color: #1f2937; }
            .problem-detail { font-size: 0.82rem; color: #374151; margin-top: 0.15rem; }
            .empty-column   { color: var(--muted-text); font-style: italic; font-size: 0.85rem; }
            /* Every card keeps its actions in this one place. What goes in it follows the
               dropdown rule (Ted, 2026-08-21): up to three choices are links, above three a menu.
               Wrapping, never scrolling — a card narrowed to a phone stacks its links instead of
               pushing the page sideways. */
            .fix-slot { margin-top: 0.4rem; display: flex; flex-wrap: wrap; gap: 0.35rem; }
            .fix-summary {
                display: inline-block; font-size: 0.78rem; font-weight: 600;
                color: #1f2937; background: rgba(255, 255, 255, 0.75);
                border: 1px solid rgba(0, 0, 0, 0.15); border-radius: 5px;
                padding: 0.15rem 0.5rem;
            }
            .fix-summary:hover { background: #ffffff; }
            /* The one-answer control is a link in the chip, so it looks the same as the menu's
               summary and lands a click sooner. */
            a.fix-summary { color: #1f2937; text-decoration: none; }
            a.fix-summary:hover { text-decoration: none; }
            /* A problem with nothing to link to keeps the slot and says why, rather than leaving
               a card with no vocabulary at all. */
            .fix-summary--disabled { color: var(--muted-text); cursor: default; opacity: 0.75; }
            .fix-summary--disabled:hover { background: rgba(255, 255, 255, 0.75); }
            """;

    public static String render(List<ScheduleProblem> problems) {
        List<ScheduleProblem.MissingTravel> travel = problems.stream()
                .filter(p -> p instanceof ScheduleProblem.MissingTravel)
                .map(p -> (ScheduleProblem.MissingTravel) p)
                .toList();
        List<ScheduleProblem.MissingHotel> hotel = problems.stream()
                .filter(p -> p instanceof ScheduleProblem.MissingHotel)
                .map(p -> (ScheduleProblem.MissingHotel) p)
                .toList();
        List<ScheduleProblem.DuplicateHotel> duplicates = problems.stream()
                .filter(p -> p instanceof ScheduleProblem.DuplicateHotel)
                .map(p -> (ScheduleProblem.DuplicateHotel) p)
                .toList();
        List<ScheduleProblem.SchedulingConflict> scheduling = problems.stream()
                .filter(p -> p instanceof ScheduleProblem.SchedulingConflict)
                .map(p -> (ScheduleProblem.SchedulingConflict) p)
                .toList();
        List<ScheduleProblem.DifferentCityConflict> cityConflicts = problems.stream()
                .filter(p -> p instanceof ScheduleProblem.DifferentCityConflict)
                .map(p -> (ScheduleProblem.DifferentCityConflict) p)
                .toList();

        return "<!DOCTYPE html>\n" + html(
                Page.head("Schedule Problems", CSS + DisclosureMenu.CSS),
                body(
                        div().withClass("page").with(
                                Page.viewNav(Page.NavAudience.OWNER, "/schedule-problems"),
                                h1("Schedule Problems"),
                                ProblemViewToggle.render(ProblemView.LIST),
                                problems.isEmpty()
                                        ? renderNoProblems()
                                        : renderProblems(travel, hotel, duplicates, scheduling, cityConflicts)
                        ),
                        rawHtml("<script>" + DisclosureMenu.SCRIPT + "</script>")
                )
        ).withLang("en").render();
    }

    private static DomContent renderNoProblems() {
        return p("No problems found — your schedule looks complete.").withClass("no-problems");
    }

    private static DomContent renderProblems(
            List<ScheduleProblem.MissingTravel> travel,
            List<ScheduleProblem.MissingHotel> hotel,
            List<ScheduleProblem.DuplicateHotel> duplicates,
            List<ScheduleProblem.SchedulingConflict> scheduling,
            List<ScheduleProblem.DifferentCityConflict> cityConflicts) {
        return div().with(
                div().withClass("problem-columns").with(
                        renderTravelColumn(travel),
                        renderHotelColumn(hotel)
                ),
                duplicates.isEmpty()
                        ? span()
                        : renderDuplicatesSection(duplicates),
                scheduling.isEmpty()
                        ? span()
                        : renderSchedulingSection(scheduling),
                cityConflicts.isEmpty()
                        ? span()
                        : renderCityConflictsSection(cityConflicts)
        );
    }

    private static DomContent renderTravelColumn(List<ScheduleProblem.MissingTravel> travel) {
        return div().with(
                p("Missing Travel").withClass("column-heading column-heading--travel"),
                travel.isEmpty()
                        ? p("None").withClass("empty-column")
                        : div().withClass("problem-list").with(
                                each(travel, p -> div().withClass("problem-card problem-card--missing-travel").with(
                                        div(p.fromCity() + " → " + p.toCity()).withClass("problem-title"),
                                        travelDetail(p),
                                        fixSlot(p)
                                ))
                        )
        );
    }

    /**
     * A gap out of home has no stranded stretch to report — its window is the single moment he has
     * to be somewhere else (see {@code ScheduleTimeline.gapLeaving}). "Arrive Nov 11 — next leg
     * departs Nov 11" would be true and useless; what he needs is the one date.
     */
    private static DomContent travelDetail(ScheduleProblem.MissingTravel gap) {
        if (gap.arrivedAt().equals(gap.nextDepartureAt())) {
            return div().withClass("problem-detail").with(
                    text("Nothing booked — needed by "),
                    ZonedTimeTag.render(gap.nextDepartureAt(), DATE_TIME_FORMAT)
            );
        }
        return div().withClass("problem-detail").with(
                text("Arrive "),
                ZonedTimeTag.render(gap.arrivedAt(), DATE_TIME_FORMAT),
                text(" — next leg departs "),
                ZonedTimeTag.render(gap.nextDepartureAt(), DATE_TIME_FORMAT)
        );
    }

    private static DomContent renderHotelColumn(List<ScheduleProblem.MissingHotel> hotel) {
        return div().with(
                p("Missing Hotel").withClass("column-heading column-heading--hotel"),
                hotel.isEmpty()
                        ? p("None").withClass("empty-column")
                        : div().withClass("problem-list").with(
                                each(hotel, p -> div().withClass("problem-card problem-card--missing-hotel").with(
                                        div().withClass("problem-title").with(
                                                text(p.city()),
                                                p.conferenceName().isEmpty()
                                                        ? span()
                                                        : text(" — for " + p.conferenceName())
                                        ),
                                        div("No hotel covering checking in on "
                                            + p.checkIn().format(DATE)
                                            + " through check out on "
                                            + p.checkOut().format(DATE))
                                                .withClass("problem-detail"),
                                        fixSlot(p)
                                ))
                        )
        );
    }

    /**
     * Every doubly-booked stay is named with its city and its booking intent, because the question
     * this row raises is "which one do I cancel?" — and the tentative one is usually the answer.
     */
    private static DomContent renderDuplicatesSection(List<ScheduleProblem.DuplicateHotel> duplicates) {
        return div().withStyle("margin-top: 2rem;").with(
                p("Duplicate Hotels").withClass("column-heading column-heading--duplicate"),
                div().withClass("problem-list").with(
                        each(duplicates, p -> div().withClass("problem-card problem-card--duplicate-hotel").with(
                                div(p.stays().size() + " hotels booked for the same nights")
                                        .withClass("problem-title"),
                                div("Nights of " + p.firstNight().format(DATE)
                                    + " through " + p.lastNight().format(DATE)
                                    + " — check out " + p.checkOut().format(DATE))
                                        .withClass("problem-detail"),
                                each(p.stays(), stay -> div(stay.hotelName() + ", " + stay.city()
                                                            + " (" + intentLabel(stay) + ")")
                                        .withClass("problem-detail")),
                                fixSlot(p)
                        ))
                )
        );
    }

    private static String intentLabel(ScheduleProblem.DuplicateStay stay) {
        return stay.bookingIntent().name().toLowerCase(Locale.ENGLISH);
    }

    private static DomContent renderSchedulingSection(List<ScheduleProblem.SchedulingConflict> scheduling) {
        return div().withStyle("margin-top: 2rem;").with(
                p("Scheduling Conflicts").withClass("column-heading column-heading--scheduling"),
                div().withClass("problem-list").with(
                        each(scheduling, p -> div().withClass("problem-card problem-card--scheduling-conflict").with(
                                div(p.first().name() + " conflicts with " + p.second().name())
                                        .withClass("problem-title"),
                                // Each side shows its OWN date: overlapping gatherings in different
                                // zones can fall on different local days, so there is no shared date.
                                div().withClass("problem-detail").with(
                                        conflictSide(p.first()),
                                        text(" overlaps "),
                                        conflictSide(p.second())
                                ),
                                fixSlot(p)
                        ))
                )
        );
    }

    private static DomContent conflictSide(ScheduleProblem.ConflictingGathering gathering) {
        return span(
                ZonedTimeTag.render(gathering.startsAt(), DAY_DATE_TIME_FORMAT),
                rawHtml("&ndash;"),
                ZonedTimeTag.render(gathering.endsAt(), TIME_FORMAT),
                gathering.city().isBlank()
                        ? span()
                        : text(" (" + gathering.city() + ")")
        );
    }

    private static DomContent renderCityConflictsSection(List<ScheduleProblem.DifferentCityConflict> cityConflicts) {
        return div().withStyle("margin-top: 2rem;").with(
                p("City Conflicts").withClass("column-heading").withStyle("color: #7c3aed;"),
                div().withClass("problem-list").with(
                        each(cityConflicts, p -> div().withClass("problem-card problem-card--city-conflict").with(
                                div(p.gatheringName() + " (" + p.gatheringCity() + ")"
                                    + " — during "
                                    + p.conferenceName() + " (" + p.conferenceCity() + ")")
                                        .withClass("problem-title"),
                                div(p.date().format(DATE)).withClass("problem-detail"),
                                fixSlot(p)
                        ))
                )
        );
    }

    /**
     * The card's fix control: always present, always in the same place, whatever the problem type.
     * A problem with no fix yet ({@code SchedulingConflict}) shows the same control greyed with the
     * reason, per the affordance rule in CLAUDE.md — removing it would change the card's vocabulary
     * from row to row and hide that fixing is a thing you can do here at all.
     */
    /**
     * The card's actions, in the one place every card keeps for them. What sits in the slot
     * follows the standing dropdown rule (Ted, 2026-08-21): <strong>a dropdown only above three
     * choices, or where space is constrained</strong>. A problem card has a whole column's width
     * and at most three answers, so its fixes are links — the menu is left for a duplicate booked
     * four ways, which is the only case here that can exceed three.
     * <p>
     * None is the greyed control with its reason (F6), which stays: a card with no vocabulary at
     * all teaches nothing. The affordance rule the old menu was justified by is about position
     * <em>within a column</em>, where every card is the same problem type and so carries the same
     * control row to row; across columns the widths now differ, which is the accepted trade.
     */
    private static DomContent fixSlot(ScheduleProblem problem) {
        List<ProblemFix> fixes = ProblemFix.forProblem(problem, FixOrigin.PROBLEM_LIST);
        if (fixes.isEmpty()) {
            return div().withClass("fix-slot").with(
                    span("Fix").withClass("fix-summary fix-summary--disabled")
                            .withTitle(NO_FIX_REASON));
        }
        if (fixes.size() > MENU_THRESHOLD) {
            return div().withClass("fix-slot").with(
                    DisclosureMenu.render(rawHtml("Fix &#9662;"), "fix-summary",
                            fixes.stream()
                                    .map(fix -> DisclosureMenu.item(fix.label(), fix.href()))
                                    .toList()));
        }
        return div().withClass("fix-slot").with(
                fixes.stream()
                        .map(fix -> a().withHref(fix.href()).withClass("fix-summary")
                                .with(text(fix.label()), rawHtml(" &rarr;")))
                        .toList());
    }
}
