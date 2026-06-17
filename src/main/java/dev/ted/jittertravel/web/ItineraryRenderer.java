package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.*;
import dev.ted.jittertravel.domain.Address;
import j2html.tags.DomContent;
import j2html.tags.Text;
import j2html.tags.specialized.DivTag;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static j2html.TagCreator.*;

public class ItineraryRenderer {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DAY_HEADER_FMT = DateTimeFormatter.ofPattern("EEE, MMM d");

    private static final String FLIGHT_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#075985\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M2 16l20-7-9 13-2-6-9 0z\"/></svg>";
    private static final String TRAIN_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#9a3412\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><rect x=\"4\" y=\"3\" width=\"16\" height=\"14\" rx=\"3\"/><path d=\"M4 11h16M8 3v8M16 3v8M7 17l-2 4M17 17l2 4\"/><circle cx=\"8.5\" cy=\"14.5\" r=\"1\" fill=\"#9a3412\" stroke=\"none\"/><circle cx=\"15.5\" cy=\"14.5\" r=\"1\" fill=\"#9a3412\" stroke=\"none\"/></svg>";
    private static final String HOTEL_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#166534\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M2 4v16M2 8h18a2 2 0 0 1 2 2v10M2 17h20\"/><path d=\"M6 8a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2\"/></svg>";

    private static final String CSS = """
                .page { max-width: 1200px; }
                .date-nav { display: flex; align-items: center; gap: 1.25rem; margin-bottom: 1.25rem; font-size: 0.95rem; }
                .date-nav a { color: var(--accent-color); text-decoration: none; font-weight: 600; }
                .date-nav a:hover { text-decoration: underline; }
                .today-link--current { font-weight: 400; color: var(--muted-text); cursor: default; }
                .itinerary-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 1rem; align-items: start; }
                .day-header { font-weight: 700; font-size: 1rem; padding-bottom: 0.4rem; border-bottom: 2px solid var(--border-color); margin-bottom: 0.6rem; color: var(--text-color); }
                .empty-day { font-size: 0.85rem; color: var(--muted-text); font-style: italic; }
                .entry-card { border-left: 4px solid transparent; border-radius: 0 6px 6px 0; padding: 0.55rem 0.75rem; margin-bottom: 0.6rem; }
                .entry-card--conference { border-left-color: #4f46e5; background: #e0e7ff; }
                .entry-card--flight     { border-left-color: #075985; background: #cfeafd; }
                .entry-card--train      { border-left-color: #9a3412; background: #ffedd5; }
                .entry-card--lodging    { border-left-color: #166534; background: #dcfce7; }
                .entry-card--gathering  { border-left-color: #7c3aed; background: #f5f3ff; }
                .entry-header { display: flex; align-items: center; gap: 0.3rem; margin-bottom: 0.2rem; }
                .entry-header svg { width: 13px; height: 13px; flex-shrink: 0; }
                .entry-kind { font-size: 0.68rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; }
                .entry-kind--conference { color: #4f46e5; }
                .entry-kind--flight     { color: #075985; }
                .entry-kind--train      { color: #9a3412; }
                .entry-kind--lodging    { color: #166534; }
                .entry-kind--gathering  { color: #7c3aed; }
                .entry-title { font-weight: 600; font-size: 0.9rem; margin-bottom: 0.2rem; line-height: 1.3; }
                .entry-detail { font-size: 0.82rem; color: #374151; line-height: 1.4; }
                .entry-detail a { color: inherit; text-decoration: underline; }
                .entry-location { font-weight: 700; }
                .speaking-badge { display: inline-block; font-size: 0.65rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em; background: #7c3aed; color: #fff; border-radius: 4px; padding: 0.1rem 0.4rem; margin-top: 0.25rem; }
            """;

    public static String render(List<ItineraryDay> days, LocalDate prevDate, LocalDate nextDate, LocalDate today) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Itinerary", CSS),
                body(
                        div().withClass("page").with(
                                Page.navHomeAndCalendar(),
                                h1("Itinerary"),
                                renderDateNav(days, prevDate, nextDate, today),
                                div().withClass("itinerary-grid").with(
                                        days.stream().map(ItineraryRenderer::renderDay).toList()
                                )
                        )
                )
        ).withLang("en").render();
    }

    private static DivTag renderDateNav(List<ItineraryDay> days, LocalDate prevDate, LocalDate nextDate, LocalDate today) {
        DivTag dateNav = div().withClass("date-nav").with(
                a().withHref("/itinerary?date=" + prevDate).with(rawHtml("&larr; Previous"))
        );
        LocalDate firstDay = days.get(0).date();
        LocalDate lastDay = days.get(days.size() - 1).date();
        boolean todayDisplayed = !today.isBefore(firstDay) && !today.isAfter(lastDay);
        if (todayDisplayed) {
            dateNav.with(span("Today").withClass("today-link today-link--current"));
        } else {
            dateNav.with(a("Today").withClass("today-link").withHref("/itinerary?date=" + today));
        }
        dateNav.with(a().withHref("/itinerary?date=" + nextDate).with(rawHtml("Next &rarr;")));
        return dateNav;
    }

    private static DivTag renderDay(ItineraryDay day) {
        DivTag dayDiv = div(
                div(day.date().format(DAY_HEADER_FMT)).withClass("day-header")
        );
        if (!day.hasEntries()) {
            dayDiv.with(div("Nothing scheduled").withClass("empty-day"));
        } else {
            day.entries().stream()
                    .map(ItineraryRenderer::renderEntry)
                    .forEach(dayDiv::with);
        }
        return dayDiv;
    }

    private static DomContent renderEntry(ItineraryEntry entry) {
        return switch (entry) {
            case FlightItineraryEntry e -> renderFlight(e);
            case TrainItineraryEntry e -> renderTrain(e);
            case HotelItineraryEntry e -> renderHotel(e);
            case GatheringItineraryEntry e -> renderGathering(e);
            case ConferenceItineraryEntry e -> renderConference(e);
        };
    }

    private static DivTag renderFlight(FlightItineraryEntry e) {
        String kindLabel = e.role() == FlightDayRole.ARRIVAL ? "Arriving" : "Flight";
        return div().withClass("entry-card entry-card--flight").with(
                div().withClass("entry-header").with(
                        rawHtml(FLIGHT_SVG),
                        span(kindLabel).withClass("entry-kind entry-kind--flight")
                ),
                div(e.airline() + " " + e.flightNumber()).withClass("entry-title"),
                div().withClass("entry-detail").with(
                        strong(e.departureAirportCode()),
                        span(" " + e.departureDateTime().format(TIME_FMT)),
                        rawHtml("&nbsp;&rarr;&nbsp;"),
                        strong(e.arrivalAirportCode()),
                        span(" " + e.arrivalDateTime().format(TIME_FMT))
                )
        );
    }

    private static DivTag renderTrain(TrainItineraryEntry e) {
        String kindLabel = e.role() == TrainDayRole.ARRIVAL ? "Arriving" : "Train";
        DivTag card = div().withClass("entry-card entry-card--train").with(
                div().withClass("entry-header").with(
                        rawHtml(TRAIN_SVG),
                        span(kindLabel).withClass("entry-kind entry-kind--train")
                )
        );
        if (!e.serviceId().isBlank()) {
            card.with(div(e.serviceId()).withClass("entry-detail"));
        }
        card.with(
                div().withClass("entry-detail").with(
                        span(e.departureDateTime().format(TIME_FMT)),
                        rawHtml("&nbsp;&rarr;&nbsp;"),
                        span(e.arrivalDateTime().format(TIME_FMT))
                ),
                div().withClass("entry-title").with(
                        stationContent(e.departureStationName(), e.departureMapsUrl()),
                        rawHtml("&nbsp;&rarr;&nbsp;"),
                        stationContent(e.arrivalStationName(), e.arrivalMapsUrl())
                )
        );
        return card;
    }

    private static DomContent stationContent(String name, String mapsUrl) {
        if (mapsUrl.isBlank()) {
            return span(name);
        }
        return a(name).withHref(mapsUrl).withTarget("_blank").withRel("noopener");
    }

    private static DivTag renderHotel(HotelItineraryEntry e) {
        String kindLabel = e.dayRole() == HotelDayRole.CHECK_IN ? "Check-In" : "Check-Out";
        Address addr = e.address();
        String cityLine = addr.city()
                + (addr.region().isEmpty() ? "" : ", " + addr.region())
                + " " + addr.postalCode();
        return div().withClass("entry-card entry-card--lodging").with(
                div().withClass("entry-header").with(
                        rawHtml(HOTEL_SVG),
                        span(kindLabel).withClass("entry-kind entry-kind--lodging")
                ),
                a(e.hotelName()).withClass("entry-title")
                        .withHref(e.mapsUrl()).withTarget("_blank").withRel("noopener"),
                div(addr.street()).withClass("entry-detail"),
                div(cityLine).withClass("entry-detail entry-location"),
                div(addr.country()).withClass("entry-detail entry-location"),
                div(e.anchorDateTime().format(TIME_FMT)).withClass("entry-detail")
        );
    }

    private static DivTag renderGathering(GatheringItineraryEntry e) {
        DomContent titleContent = e.infoUrl().isBlank()
                ? new Text(e.title())
                : a(e.title()).withHref(e.infoUrl()).withTarget("_blank").withRel("noopener");
        DivTag card = div().withClass("entry-card entry-card--gathering").with(
                div("Gathering").withClass("entry-kind entry-kind--gathering"),
                div().withClass("entry-title").with(titleContent),
                div(e.venueLocation()).withClass("entry-detail"),
                div().withClass("entry-detail").with(
                        span(e.anchorDateTime().format(TIME_FMT)),
                        rawHtml(" &ndash; "),
                        span(e.endDateTime().format(TIME_FMT))
                )
        );
        if (e.speaking()) {
            card.with(div("Speaking").withClass("speaking-badge"));
        }
        return card;
    }

    private static DivTag renderConference(ConferenceItineraryEntry e) {
        String kindLabel = e.totalDays() > 1
                ? "Day " + e.dayNumber() + " of " + e.totalDays()
                : "Conference";
        String location = e.venueAddress().city() + ", " + e.venueAddress().country();
        return div().withClass("entry-card entry-card--conference").with(
                div(kindLabel).withClass("entry-kind entry-kind--conference"),
                div(e.name()).withClass("entry-title"),
                div(e.venueName()).withClass("entry-detail"),
                div(location).withClass("entry-detail entry-location")
        );
    }
}
