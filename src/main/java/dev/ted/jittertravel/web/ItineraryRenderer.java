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

    private static final String TIME_FORMAT = "h:mm a";
    private static final DateTimeFormatter DAY_HEADER_FMT = DateTimeFormatter.ofPattern("EEE, MMM d");

    private static final String FLIGHT_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#075985\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M2 16l20-7-9 13-2-6-9 0z\"/></svg>";
    private static final String TRAIN_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#9a3412\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><rect x=\"4\" y=\"3\" width=\"16\" height=\"14\" rx=\"3\"/><path d=\"M4 11h16M8 3v8M16 3v8M7 17l-2 4M17 17l2 4\"/><circle cx=\"8.5\" cy=\"14.5\" r=\"1\" fill=\"#9a3412\" stroke=\"none\"/><circle cx=\"15.5\" cy=\"14.5\" r=\"1\" fill=\"#9a3412\" stroke=\"none\"/></svg>";
    private static final String HOTEL_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#166534\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M2 4v16M2 8h18a2 2 0 0 1 2 2v10M2 17h20\"/><path d=\"M6 8a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2\"/></svg>";
    // Taxi, from the travel-icons row on the home page — a ground transfer is whatever comes next,
    // so the taxi stands for the whole category.
    private static final String TRANSFER_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#854d0e\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M5 11l1.6-4.2A2 2 0 0 1 8.5 5.5h7a2 2 0 0 1 1.9 1.3L19 11\"/><path d=\"M3 11h18v5H3zM6 16v2M18 16v2M9 5.5V3.5h6v2\"/><circle cx=\"7\" cy=\"13.5\" r=\"1\" fill=\"#854d0e\" stroke=\"none\"/><circle cx=\"17\" cy=\"13.5\" r=\"1\" fill=\"#854d0e\" stroke=\"none\"/></svg>";
    // Home, in the same hand and the same lodging green as the hotel: both rows answer "where do
    // you sleep tonight", and only the answer differs.
    // The same hotel, drawn in amber: the row still means lodging, it just means the missing kind.
    private static final String HOTEL_MISSING_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#b45309\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M2 4v16M2 8h18a2 2 0 0 1 2 2v10M2 17h20\"/><path d=\"M6 8a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2\"/></svg>";
    private static final String HOME_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#166534\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M3 10.5 12 3l9 7.5\"/><path d=\"M5.5 9.5V20h13V9.5\"/><path d=\"M10 20v-5h4v5\"/></svg>";
    private static final String PENCIL_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M12 20h9\"/><path d=\"M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z\"/></svg>";
    // Shared with the calendar: a bin always means "cancel this entry", as the pencil means edit.
    private static final String TRASH_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><path d=\"M3 6h18\"/><path d=\"M8 6V4h8v2\"/><path d=\"M19 6l-1 14H6L5 6\"/><path d=\"M10 11v6\"/><path d=\"M14 11v6\"/></svg>";

    private static final String CSS = """
                .page { max-width: 1200px; }
                .date-nav { display: flex; align-items: center; gap: 1.25rem; margin-bottom: 1.25rem; font-size: 0.95rem; }
                .date-nav a { color: var(--accent-color); text-decoration: none; font-weight: 600; }
                .date-nav a:hover { text-decoration: underline; }
                .today-link--current { font-weight: 400; color: var(--muted-text); cursor: default; }
                .itinerary-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 1rem; align-items: start; }
                .day-header { font-weight: 700; font-size: 1rem; padding-bottom: 0.4rem; border-bottom: 2px solid var(--border-color); margin-bottom: 0.6rem; color: var(--text-color); }
                .empty-day { font-size: 0.85rem; color: var(--muted-text); font-style: italic; }
                /* Where he is on a day nothing happens. Not an entry card — no left border, and
                   a tint far lighter than the lodging card's, so it reads as context rather than
                   as something scheduled. Two lines, because the one-liner wrapped in a
                   third-of-a-column: where first, then which hotel. */
                .whereabouts { background: #f0fdf4; border-radius: 6px; padding: 0.45rem 0.6rem; font-size: 0.85rem; }
                .whereabouts-where { display: flex; align-items: center; gap: 0.35rem; font-weight: 600; color: #166534; }
                .whereabouts-where svg { width: 13px; height: 13px; flex-shrink: 0; }
                .whereabouts-detail { padding-left: 1.2rem; color: var(--muted-text); line-height: 1.3; }
                /* Where he is with no bed under him: amber, because a schedule problem to look at
                   is work waiting and recoverable — never the green of a night that is sorted.
                   (/schedule-problems colours its own cards by problem *kind*, blue for hotel;
                   here the row's whole job is to stand out from the settled days around it.) */
                .whereabouts--unbooked { background: #fffbeb; }
                .whereabouts--unbooked .whereabouts-where { color: #b45309; }
                .whereabouts--unbooked .whereabouts-detail { color: #92400e; }
                .whereabouts-fix { padding-left: 1.2rem; margin-top: 0.2rem; }
                .whereabouts-fix a { color: #b45309; font-weight: 600; text-decoration: none; }
                .whereabouts-fix a:hover { text-decoration: underline; }
                .entry-card { border-left: 4px solid transparent; border-radius: 0 6px 6px 0; padding: 0.55rem 0.75rem; margin-bottom: 0.6rem; }
                .entry-card--conference { border-left-color: #4f46e5; background: #e0e7ff; }
                .entry-card--flight     { border-left-color: #075985; background: #cfeafd; }
                .entry-card--train      { border-left-color: #9a3412; background: #ffedd5; }
                .entry-card--lodging    { border-left-color: #166534; background: #dcfce7; }
                .entry-card--gathering  { border-left-color: #7c3aed; background: #f5f3ff; }
                .entry-card--private-event { border-left-color: #475569; background: #f1f5f9; }
                .entry-card--ground-transfer { border-left-color: #854d0e; background: #fef9c3; }
                .entry-header { display: flex; align-items: center; gap: 0.3rem; margin-bottom: 0.2rem; }
                .entry-header svg { width: 13px; height: 13px; flex-shrink: 0; }
                .entry-kind { font-size: 0.68rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; }
                .entry-kind--conference { color: #4f46e5; }
                .entry-kind--flight     { color: #075985; }
                .entry-kind--train      { color: #9a3412; }
                .entry-kind--lodging    { color: #166534; }
                .entry-kind--gathering  { color: #7c3aed; }
                .entry-kind--private-event { color: #475569; }
                .entry-kind--ground-transfer { color: #854d0e; }
                .entry-title { font-weight: 600; font-size: 0.9rem; margin-bottom: 0.2rem; line-height: 1.3; }
                .entry-detail { font-size: 0.82rem; color: #374151; line-height: 1.4; }
                .entry-detail a { color: inherit; text-decoration: underline; }
                .edit-pencil { margin-left: 0.4rem; color: inherit; opacity: 0.65; text-decoration: none; vertical-align: middle; }
                .edit-pencil:hover { opacity: 1; }
                .edit-pencil svg { width: 12px; height: 12px; }
                /* The cancel bin sits where the pencil sits on the kinds that have an edit page,
                   and looks the same — no red: re-entering a removed transfer puts it back, and
                   red is reserved for what cannot be undone. */
                .cancel-bin { margin-left: 0.4rem; color: inherit; opacity: 0.65; text-decoration: none; vertical-align: middle; }
                .cancel-bin:hover { opacity: 1; }
                .cancel-bin svg { width: 12px; height: 12px; }
                .entry-location { font-weight: 700; }
                .speaking-badge { display: inline-block; font-size: 0.65rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em; background: #7c3aed; color: #fff; border-radius: 4px; padding: 0.1rem 0.4rem; margin-top: 0.25rem; }
            """;

    public static String render(List<ItineraryDay> days, LocalDate prevDate, LocalDate nextDate, LocalDate today, boolean isOwner) {
        return render(days, prevDate, nextDate, today, isOwner, ZoneDisplay.entryOnly());
    }

    public static String render(List<ItineraryDay> days, LocalDate prevDate, LocalDate nextDate,
                                LocalDate today, boolean isOwner, ZoneDisplay zoneDisplay) {
        return "<!DOCTYPE html>\n" + BrowserZoneScript.markRoot(html(
                Page.head("Itinerary", CSS),
                body(
                        div().withClass("page").with(
                                Page.viewNav(isOwner ? Page.NavAudience.OWNER : Page.NavAudience.FAMILY,
                                        "/itinerary"),
                                h1("Itinerary"),
                                ZoneToggle.render(zoneDisplay),
                                renderDateNav(days, prevDate, nextDate, today),
                                div().withClass("itinerary-grid").with(
                                        days.stream().map(day -> renderDay(day, isOwner)).toList()
                                )
                        ),
                        BrowserZoneScript.render(zoneDisplay)
                )
        ), zoneDisplay).withLang("en").render();
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

    private static DivTag renderDay(ItineraryDay day, boolean isOwner) {
        DivTag dayDiv = div(
                div(day.date().format(DAY_HEADER_FMT)).withClass("day-header")
        );
        if (!day.hasEntries()) {
            // A day with nothing on it still has an answer to "where am I" whenever the schedule
            // holds one. The stay wins over home: a hotel in a home city is still the hotel.
            dayDiv.with(renderWhereabouts(day, isOwner));
        } else {
            day.entries().stream()
                    .map(entry -> renderEntry(entry, isOwner))
                    .forEach(dayDiv::with);
        }
        return dayDiv;
    }

    private static DivTag renderWhereabouts(ItineraryDay day, boolean isOwner) {
        if (day.ongoingStay().isPresent()) {
            return renderOngoingStay(day.ongoingStay().get());
        }
        if (day.nightWithoutABed().isPresent()) {
            return renderNightWithoutABed(day.nightWithoutABed().get(), isOwner);
        }
        if (day.atHome()) {
            return div().withClass("whereabouts").with(
                    div().withClass("whereabouts-where").with(
                            rawHtml(HOME_SVG),
                            span(rawHtml("You&rsquo;re Home"))
                    )
            );
        }
        return div("Nothing scheduled").withClass("empty-day");
    }

    /**
     * Where he is on a night with no bed booked. The city comes from the same
     * {@code MissingHotel} the report is built on, and the fix links come from {@link ProblemFix},
     * so this row and {@code /schedule-problems} can never offer different dates or a different
     * destination for the same gap. Rendered as plain links rather than the report's disclosure
     * menu: a missing hotel has exactly one fix, and a menu holding one item is a worse door.
     * <p>
     * The links are OWNER-only. Family sees the row — they can see the whole itinerary — but
     * {@code /book-hotel} is a form they could never submit, and CLAUDE.md's split says a control
     * a viewer can <em>never</em> trigger is rendered not at all, rather than disabled.
     */
    private static DivTag renderNightWithoutABed(ScheduleProblem.MissingHotel gap, boolean isOwner) {
        DivTag row = div().withClass("whereabouts whereabouts--unbooked").with(
                div().withClass("whereabouts-where").with(
                        rawHtml(HOTEL_MISSING_SVG),
                        span("In " + gap.city())
                ),
                div("No hotel booked").withClass("whereabouts-detail")
        );
        if (isOwner) {
            ProblemFix.forProblem(gap, FixOrigin.ITINERARY).forEach(fix -> row.with(
                    div().withClass("whereabouts-fix").with(
                            a().withHref(fix.href()).with(text(fix.label()), rawHtml(" &rarr;")))));
        }
        return row;
    }

    private static DivTag renderOngoingStay(OngoingStay stay) {
        DivTag row = div().withClass("whereabouts").with(
                div().withClass("whereabouts-where").with(
                        rawHtml(HOTEL_SVG),
                        span(stay.locationLabel())
                )
        );
        if (!stay.hotelName().isBlank()) {
            row.with(div(stay.hotelName()).withClass("whereabouts-detail"));
        }
        return row;
    }

    private static DomContent renderEntry(ItineraryEntry entry, boolean isOwner) {
        return switch (entry) {
            case FlightItineraryEntry e -> renderFlight(e, isOwner);
            case TrainItineraryEntry e -> renderTrain(e, isOwner);
            case HotelItineraryEntry e -> renderHotel(e, isOwner);
            case GatheringItineraryEntry e -> renderGathering(e, isOwner);
            case ConferenceItineraryEntry e -> renderConference(e);
            case PrivateEventItineraryEntry e -> renderPrivateEvent(e);
            case GroundTransferItineraryEntry e -> renderGroundTransfer(e, isOwner);
        };
    }

    private static DivTag renderFlight(FlightItineraryEntry e, boolean isOwner) {
        String kindLabel = e.role() == FlightDayRole.ARRIVAL ? "Arriving" : "Flight";
        DivTag title = div().withClass("entry-title").with(span(e.airline() + " " + e.flightNumber()));
        if (isOwner) {
            title.with(editPencil("/booked-flights/" + e.flightId().id(), "Edit flight"));
        }
        return div().withClass("entry-card entry-card--flight").with(
                div().withClass("entry-header").with(
                        rawHtml(FLIGHT_SVG),
                        span(kindLabel).withClass("entry-kind entry-kind--flight")
                ),
                title,
                div().withClass("entry-detail").with(
                        strong(e.departureAirportCode()),
                        text(" "),
                        ZonedTimeTag.render(e.departureDateTime(), TIME_FORMAT),
                        rawHtml("&nbsp;&rarr;&nbsp;"),
                        strong(e.arrivalAirportCode()),
                        text(" "),
                        ZonedTimeTag.render(e.arrivalDateTime(), TIME_FORMAT)
                )
        );
    }

    private static DivTag renderTrain(TrainItineraryEntry e, boolean isOwner) {
        String kindLabel = e.role() == TrainDayRole.ARRIVAL ? "Arriving" : "Train";
        DivTag card = div().withClass("entry-card entry-card--train").with(
                div().withClass("entry-header").with(
                        rawHtml(TRAIN_SVG),
                        span(kindLabel).withClass("entry-kind entry-kind--train")
                ),
                // Station -> station moved to the top of the entry; stations keep their maps links.
                div().withClass("entry-title").with(
                        stationContent(e.departureStationName(), e.departureMapsUrl()),
                        rawHtml("&nbsp;&rarr;&nbsp;"),
                        stationContent(e.arrivalStationName(), e.arrivalMapsUrl())
                )
        );
        // Service-ID line carries the OWNER edit pencil at its end; when there is no service id
        // the pencil still appears on its own (owner only).
        boolean hasService = !e.serviceId().isBlank();
        if (hasService || isOwner) {
            DivTag serviceLine = div().withClass("entry-detail");
            if (hasService) {
                serviceLine.with(span(e.serviceId()));
            }
            if (isOwner) {
                serviceLine.with(editPencil("/booked-trains/" + e.tripId().id(), "Edit train"));
            }
            card.with(serviceLine);
        }
        card.with(
                div().withClass("entry-detail").with(
                        ZonedTimeTag.render(e.departureDateTime(), TIME_FORMAT),
                        rawHtml("&nbsp;&rarr;&nbsp;"),
                        ZonedTimeTag.render(e.arrivalDateTime(), TIME_FORMAT)
                )
        );
        return card;
    }

    private static DomContent editPencil(String href, String label) {
        return a(rawHtml(PENCIL_SVG)).withClass("edit-pencil").withHref(href).withTitle(label);
    }

    private static DomContent cancelBin(String href, String label) {
        return a(rawHtml(TRASH_SVG)).withClass("cancel-bin").withHref(href).withTitle(label);
    }

    private static DomContent stationContent(String name, String mapsUrl) {
        if (mapsUrl.isBlank()) {
            return span(name);
        }
        return a(name).withHref(mapsUrl).withTarget("_blank").withRel("noopener");
    }

    private static DivTag renderHotel(HotelItineraryEntry e, boolean isOwner) {
        String kindLabel = e.dayRole() == HotelDayRole.CHECK_IN ? "Check-In" : "Check-Out";
        Address addr = e.address();
        String cityLine = addr.city()
                + (addr.region().isEmpty() ? "" : ", " + addr.region())
                + " " + addr.postalCode();
        DivTag title = div().withClass("entry-title").with(
                a(e.hotelName()).withHref(e.mapsUrl()).withTarget("_blank").withRel("noopener"));
        if (isOwner) {
            title.with(editPencil("/booked-hotels/" + e.hotelBookingId().id(), "Edit hotel"));
        }
        return div().withClass("entry-card entry-card--lodging").with(
                div().withClass("entry-header").with(
                        rawHtml(HOTEL_SVG),
                        span(kindLabel).withClass("entry-kind entry-kind--lodging")
                ),
                title,
                div(addr.street()).withClass("entry-detail"),
                div(cityLine).withClass("entry-detail entry-location"),
                div(addr.country()).withClass("entry-detail entry-location"),
                div().withClass("entry-detail").with(ZonedTimeTag.render(e.anchorDateTime(), TIME_FORMAT))
        );
    }

    private static DivTag renderGathering(GatheringItineraryEntry e, boolean isOwner) {
        DomContent titleContent = e.infoUrl().isBlank()
                ? new Text(e.title())
                : a(e.title()).withHref(e.infoUrl()).withTarget("_blank").withRel("noopener");
        DivTag title = div().withClass("entry-title").with(titleContent);
        if (isOwner) {
            title.with(editPencil("/planned-gatherings/" + e.gatheringId().id(), "Edit gathering"));
        }
        DivTag card = div().withClass("entry-card entry-card--gathering").with(
                div("Gathering").withClass("entry-kind entry-kind--gathering"),
                title,
                div(e.venueLocation()).withClass("entry-detail"),
                div().withClass("entry-detail").with(
                        ZonedTimeTag.render(e.anchorDateTime(), TIME_FORMAT),
                        rawHtml(" &ndash; "),
                        ZonedTimeTag.render(e.endDateTime(), TIME_FORMAT)
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
        // The conference's own page hangs off the title, exactly as a gathering's does; with none
        // recorded the title stays plain text rather than becoming a link to nowhere.
        DomContent titleContent = e.infoUrl().isBlank()
                ? new Text(e.name())
                : a(e.name()).withHref(e.infoUrl()).withTarget("_blank").withRel("noopener");
        return div().withClass("entry-card entry-card--conference").with(
                div(kindLabel).withClass("entry-kind entry-kind--conference"),
                div().withClass("entry-title").with(titleContent),
                div(e.venueName()).withClass("entry-detail"),
                div(location).withClass("entry-detail entry-location")
        );
    }

    /**
     * A short hop with no booking, so there is no service id to show: both ends and the
     * (approximate) times, and nothing else — the airport code or hotel name already says where
     * each end is, so a cities line only repeated the journey (Ted, 2026-08-20).
     * <p>
     * There is nothing to edit into either, so the owner's action in the pencil's slot is a cancel:
     * a wrong transfer is corrected by removing it and entering it again — and left in place it
     * keeps asserting a hop that never happened, masking a real gap on /schedule-problems.
     */
    private static DivTag renderGroundTransfer(GroundTransferItineraryEntry e, boolean isOwner) {
        DivTag title = div().withClass("entry-title").with(
                span(e.origin()),
                rawHtml("&nbsp;&rarr;&nbsp;"),
                span(e.destination())
        );
        if (isOwner) {
            title.with(cancelBin("/ground-transfers/" + e.groundTransferId().id() + "/cancel",
                    "Cancel ground transfer"));
        }
        return div().withClass("entry-card entry-card--ground-transfer").with(
                div().withClass("entry-header").with(
                        rawHtml(TRANSFER_SVG),
                        span("Ground transfer").withClass("entry-kind entry-kind--ground-transfer")
                ),
                title,
                div().withClass("entry-detail").with(
                        ZonedTimeTag.render(e.departsAt(), TIME_FORMAT),
                        rawHtml("&nbsp;&rarr;&nbsp;"),
                        ZonedTimeTag.render(e.arrivesAt(), TIME_FORMAT)
                )
        );
    }

    private static DivTag renderPrivateEvent(PrivateEventItineraryEntry e) {
        // Itinerary is OWNER/FAMILY only, so full detail is fine here — redaction is an
        // anonymous-calendar concern. No title link: a private event has no public info URL.
        return div().withClass("entry-card entry-card--private-event").with(
                div("Private").withClass("entry-kind entry-kind--private-event"),
                div().withClass("entry-title").with(span(e.title())),
                div(e.venueLocation()).withClass("entry-detail"),
                div().withClass("entry-detail").with(
                        ZonedTimeTag.render(e.anchorDateTime(), TIME_FORMAT),
                        rawHtml(" &ndash; "),
                        ZonedTimeTag.render(e.endDateTime(), TIME_FORMAT)
                )
        );
    }
}
