package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import j2html.tags.DomContent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static j2html.TagCreator.*;

public class ScheduleProblemsRenderer {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

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
            .problem-list { display: flex; flex-direction: column; gap: 0.6rem; }
            .problem-card { border-left: 4px solid transparent; border-radius: 0 6px 6px 0; padding: 0.6rem 0.85rem; }
            .problem-card--missing-travel   { border-left-color: #b45309; background: #fef3c7; }
            .problem-card--missing-hotel    { border-left-color: #1d4ed8; background: #dbeafe; }
            .problem-card--scheduling-conflict { border-left-color: #dc2626; background: #fee2e2; }
            .problem-card--city-conflict { border-left-color: #7c3aed; background: #ede9fe; }
            .problem-title  { font-weight: 600; font-size: 0.9rem; color: #1f2937; }
            .problem-detail { font-size: 0.82rem; color: #374151; margin-top: 0.15rem; }
            .empty-column   { color: var(--muted-text); font-style: italic; font-size: 0.85rem; }
            .clear-link { font-size: 0.78rem; color: #7c3aed; text-decoration: none; margin-top: 0.3rem; display: inline-block; }
            .clear-link:hover { text-decoration: underline; }
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
        List<ScheduleProblem.SchedulingConflict> scheduling = problems.stream()
                .filter(p -> p instanceof ScheduleProblem.SchedulingConflict)
                .map(p -> (ScheduleProblem.SchedulingConflict) p)
                .toList();
        List<ScheduleProblem.DifferentCityConflict> cityConflicts = problems.stream()
                .filter(p -> p instanceof ScheduleProblem.DifferentCityConflict)
                .map(p -> (ScheduleProblem.DifferentCityConflict) p)
                .toList();

        return "<!DOCTYPE html>\n" + html(
                Page.head("Schedule Problems", CSS),
                body(
                        div().withClass("page").with(
                                Page.navHomeAndCalendar(),
                                h1("Schedule Problems"),
                                travel.isEmpty() && hotel.isEmpty() && scheduling.isEmpty() && cityConflicts.isEmpty()
                                        ? renderNoProblems()
                                        : renderProblems(travel, hotel, scheduling, cityConflicts)
                        )
                )
        ).withLang("en").render();
    }

    private static DomContent renderNoProblems() {
        return p("No problems found — your schedule looks complete.").withClass("no-problems");
    }

    private static DomContent renderProblems(
            List<ScheduleProblem.MissingTravel> travel,
            List<ScheduleProblem.MissingHotel> hotel,
            List<ScheduleProblem.SchedulingConflict> scheduling,
            List<ScheduleProblem.DifferentCityConflict> cityConflicts) {
        return div().with(
                div().withClass("problem-columns").with(
                        renderTravelColumn(travel),
                        renderHotelColumn(hotel)
                ),
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
                                        div("Arrive " + p.arrivedAt().format(DATE_TIME)
                                            + " — next leg departs " + p.nextDepartureAt().format(DATE_TIME))
                                                .withClass("problem-detail")
                                ))
                        )
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
                                                .withClass("problem-detail")
                                ))
                        )
        );
    }

    private static DomContent renderSchedulingSection(List<ScheduleProblem.SchedulingConflict> scheduling) {
        return div().withStyle("margin-top: 2rem;").with(
                p("Scheduling Conflicts").withClass("column-heading column-heading--scheduling"),
                div().withClass("problem-list").with(
                        each(scheduling, p -> div().withClass("problem-card problem-card--scheduling-conflict").with(
                                div(p.gathering1Name() + " conflicts with " + p.gathering2Name())
                                        .withClass("problem-title"),
                                div(p.date().format(DATE)
                                    + " — "
                                    + p.gathering1Start().format(TIME)
                                    + "–"
                                    + p.gathering1End().format(TIME)
                                    + " overlaps "
                                    + p.gathering2Start().format(TIME)
                                    + "–"
                                    + p.gathering2End().format(TIME))
                                        .withClass("problem-detail")
                        ))
                )
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
                                a("Clear this conflict")
                                        .withHref(clearConflictUrl(p))
                                        .withClass("clear-link")
                        ))
                )
        );
    }

    private static String clearConflictUrl(ScheduleProblem.DifferentCityConflict p) {
        return "/clear-conflict"
               + "?gatheringId=" + p.gatheringId().id()
               + "&conferenceId=" + p.conferenceId().id()
               + "&gatheringName=" + encode(p.gatheringName())
               + "&gatheringCity=" + encode(p.gatheringCity())
               + "&conferenceName=" + encode(p.conferenceName())
               + "&conferenceCity=" + encode(p.conferenceCity())
               + "&date=" + p.date();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
