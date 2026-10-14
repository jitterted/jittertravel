package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.CancelledView;
import dev.ted.jittertravel.application.FlightTrip;
import dev.ted.jittertravel.application.ItinerarySummary;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.Journey;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import j2html.tags.DomContent;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static j2html.TagCreator.*;

public class BookedFlightsRenderer {

    private static final String DATE_PATTERN = "EEE, MMM d";
    private static final String TIME_PATTERN = "h:mm a";
    private static final DateTimeFormatter CANCELLED_ON =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter BAND_DATE =
            DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.ENGLISH);

    // No container max-width, and the grid collapses instead of scrolling. Wide, the six columns
    // sit side by side, each only as wide as its content; on a narrow list they stack into one
    // column, the column header hides, and each info cell shows its own leg label so
    // Departure/Arrival/Route/Airline/Flight Number stay unambiguous. The time always sits on its
    // own line under the date (see the time > .nowrap rule). No page ever scrolls sideways.
    //
    // ONE grid owns the columns. .flight-cards defines the tracks; the header and every row (and a
    // history flight's <details> and its <summary>) inherit them with
    // grid-template-columns: subgrid, so all columns are sized once from every row's content
    // together and line up across rows. (Separate per-row grids drift out of alignment.) A
    // history flight's change list and a cancelled flight's reason both live INSIDE the row (the
    // grid row that already spans every column), so with grid-column: 1 / -1 they span every
    // column too. As siblings of the summary they stayed in the first column instead.
    private static final String CSS = """
            /* No side margin or padding: the list lines up with the heading and nav above it rather
               than sitting 48px in from them on each side, which was width taken from the table.
               It is also the container the stacking query below asks about: the grid cannot be its
               own query container, because the rules there change the grid itself. */
            .conference-container { margin: 1rem 0 0; container: flights / inline-size; }
            .flight-cards {
                display: grid;
                /* Every data column is auto, so each is exactly as wide as its widest cell, and the
                   one fr track at the end takes whatever is left. The card still spans the page
                   (its rows span all seven tracks), but the columns pack to the left instead of
                   one of them stretching to fill it (Ted, 2026-10-08). The last track holds no
                   cell, which is why the spacing below is padding rather than column-gap: a gap
                   would also sit in front of the empty track and spend width on nothing, which is
                   what made 30px gaps wrap Route at 820px.
                   Before this, Route was the 1fr absorber, and a cancelled flight's reason sat in
                   its cell and set its floor. The reason now has a line of its own under the row,
                   with zero intrinsic width (see .flight-cancelled-line), so it never widens a
                   column at all. */
                grid-template-columns: auto auto auto auto auto auto 1fr;
                margin-top: 1rem;
                background-color: var(--surface, #fff);
                /* clip, not hidden: the corners need it, but hidden would also make this a scroll
                   container, and an overflow here is a layout bug to fix rather than to mask. */
                border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: clip;
            }
            .flight-card-header, .flight-card-row {
                grid-column: 1 / -1;
                display: grid;
                grid-template-columns: subgrid;
                align-items: center; padding: 12px 20px;
            }
            /* The space between columns, with 12px more between Flight # and the actions, which
               read as one block without it (Ted, 2026-10-08). Padding on the cell instead of
               column-gap; see .flight-cards for why.
               24px on the iPad, 64px to 96px on a laptop (Ted, 2026-10-08). CSS cannot tell the
               two apart, only how wide the list is, so the space follows the list's width: 24px
               up to a 1148px list, which is the iPad in landscape at 1180 (px rather than rem
               because it is that exact width); a step to 64px just above it; then rising evenly
               to 96px at a 1408px list, a 1440 window. The step is deliberate, chosen over one
               smooth ramp from 24px: a laptop window gets the wide spacing straight away.
               cqi resolves against the flights container. Slope 32px over 260px = 12.3077cqi;
               intercept 64 - 0.123077 * 1148 = -77.29px.
               Below 1148px it is 24px, so the stacking breakpoint measured below is unaffected. */
            .flight-cards { --column-space: 24px; }
            @container flights (width > 1148px) {
                .flight-cards { --column-space: clamp(64px, calc(12.3077cqi - 77.29px), 96px); }
            }
            .flight-card-cell { padding-right: var(--column-space); }
            .flight-card-cell.flight-number { padding-right: calc(var(--column-space) + 12px); }
            /* The Add-to-Google icon beside the route. Larger than site.css's 1.15rem, with a visible
               gap of 0.5rem (the 0.15rem margin plus the 0.35rem of hit-area padding that
               .gcal-add cancels with a negative margin). The icon is an inline-flex box whose
               baseline is its SVG's bottom edge, so left alone it sits on the text baseline and
               reads a few pixels high; lifting that edge by half a cap height minus half the glyph
               centres it on the capitals, the same arithmetic as .edit-pencil. */
            .flight-route .gcal-add { margin-left: 0.15rem; vertical-align: calc(0.35em - 0.7rem); }
            .flight-route .gcal-add svg { width: 1.4rem; height: 1.4rem; }
            .flight-edit-link { font-size: 0.85rem; color: var(--accent-color); text-decoration: none; }
            .flight-edit-link:hover { text-decoration: underline; }
            /* Never wraps: letting Cancel drop under Edit made the pair change shape between rows. */
            .flight-actions { display: flex; gap: 0.75rem; align-items: baseline; }
            /* Red, matching the confirmation page it leads to: there is no undo from inside the app. */
            .flight-cancel-link { font-size: 0.85rem; color: #b00; text-decoration: none; }
            .flight-cancel-link:hover { text-decoration: underline; }
            .flight-action-disabled { font-size: 0.85rem; color: var(--muted-text); opacity: 0.6; cursor: default; }
            .flight-actions-cell { display: grid; gap: 3px; justify-items: start; }
            /* Each itinerary wears one of two hues: a left edge on its band and its rows, a light
               tint on its band, and the fill of its code badge. They are indigo and teal on
               purpose, never amber, which belongs to problems. The inset shadow keeps a tinted row
               the same width as an untinted one. Colour is a hint; the code is what names the
               itinerary. */
            .flight-card--trip-0 { --trip-edge: #4f46e5; --trip-soft: #e0e7ff; }
            .flight-card--trip-1 { --trip-edge: #0f8b8d; --trip-soft: #d5f1f1; }
            [class*="flight-card--trip-"].flight-card-row,
            [class*="flight-card--trip-"] > summary,
            [class*="flight-card--trip-"].flight-band { box-shadow: inset 4px 0 0 var(--trip-edge); }
            .flight-trip-leg { display: block; margin-top: 1px; font-size: 0.7rem; color: var(--muted-text); }
            /* A run of consecutive rows from one itinerary, or of flights booked on their own, under
               the band that names it (docs/FlightItineraryRebookingPlan.md D10: Ted could not see
               where one trip ended and the next began). A subgrid like the rows, so the band's
               actions sit in the rows' actions column. An itinerary another booking interleaves
               with is two runs, each with its own band (Ted, 2026-10-13): the list stays in
               departure order. */
            .flight-group { grid-column: 1 / -1; display: grid; grid-template-columns: subgrid; }
            .flight-band {
                grid-column: 1 / -1; display: grid; grid-template-columns: subgrid; align-items: center;
                padding: 9px 20px; border-bottom: 1px solid var(--border-color);
                border-top: 2px solid var(--trip-edge, #adb5bd);
                /* Light: the full --trip-soft read as heavy (Ted, 2026-10-13). */
                background: color-mix(in srgb, var(--trip-soft, var(--header-bg)) 40%, #fff);
            }
            .flight-group:first-of-type > .flight-band { border-top: none; }
            .flight-band--alone { background: var(--header-bg); color: var(--muted-text); font-size: 0.85rem; }
            .flight-band-text {
                /* The five data columns; the actions go in the sixth, under the rows' own. */
                grid-column: 1 / 6; display: flex; flex-wrap: wrap; align-items: baseline;
                gap: 4px 14px; font-size: 0.9rem;
            }
            .flight-band-code {
                display: inline-block; padding: 2px 9px; border-radius: 999px;
                background: var(--trip-edge); color: #fff; box-shadow: 0 1px 2px rgba(0,0,0,0.25);
                font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
                font-weight: 700; font-size: 0.8rem; letter-spacing: 0.06em;
            }
            .flight-band-route { font-weight: 600; }
            .flight-band-dates, .flight-band-count { color: var(--muted-text); }
            .flight-band-actions { grid-column: 6; display: flex; gap: 0.75rem; align-items: baseline; }
            /* Red like Cancel Flight, and for the same reason: nothing puts the itinerary back. */
            .flight-itinerary-cancel-link { font-size: 0.85rem; color: #b00; text-decoration: none; }
            .flight-itinerary-cancel-link:hover { text-decoration: underline; }
            .flight-itinerary-disabled { display: grid; gap: 1px; justify-items: start; }
            /* The reason is text on the page, never a title: the iPad has no hover. */
            .flight-itinerary-why { font-size: 0.7rem; line-height: 1.3; color: var(--muted-text); max-width: 15ch; }
            /* A cancelled flight is a record, muted so it reads as inactive. */
            .flight-card--cancelled .flight-card-cell { color: var(--muted-text); }
            /* When and why, on a line of its own under the row and spanning all of it. contain:
               inline-size gives the line no intrinsic width, so however long the reason is it
               wraps inside the width the columns already made and never widens one of them. */
            .flight-cancelled-line {
                grid-column: 1 / -1; contain: inline-size;
                margin-top: 4px; font-size: 0.85rem; line-height: 1.4; color: var(--text-color);
            }
            .flight-cancelled-badge {
                display: inline-block; margin-right: 0.5rem; padding: 0 7px; border-radius: 4px;
                font-size: 0.75rem; background: #f1f1f1; color: #6b7280; white-space: nowrap;
            }
            /* Its own row under the toolbar, as the conferences' jump bar, so the toolbar keeps the
               time toggle and the create link on one row. The switch copies the conferences' "Show
               dropped": the label never changes, only the box, so it says what the page shows. */
            .flight-cancelled-bar { margin-top: 0.75rem; }
            .flight-cancelled-toggle {
                display: inline-flex; align-items: center; gap: 7px;
                padding: 3px 9px; border-radius: 5px; font-size: 0.875rem;
                color: var(--text-color); text-decoration: none;
                border: 1px solid var(--border-color); background: var(--surface, #fff);
            }
            .flight-cancelled-toggle:hover { border-color: var(--accent-color); }
            .flight-cancelled-toggle[aria-pressed="true"] {
                border-color: var(--accent-color); background: #eef2ff;
            }
            .flight-cancelled-box {
                width: 14px; height: 14px; border-radius: 3px;
                display: inline-flex; align-items: center; justify-content: center;
                font-size: 10px; line-height: 1; color: #fff;
                border: 1px solid #adb5bd; background: var(--surface, #fff);
            }
            .flight-cancelled-toggle[aria-pressed="true"] .flight-cancelled-box {
                border-color: var(--accent-color); background: var(--accent-color);
            }
            .flight-card-header {
                padding-block: 10px;
                background-color: var(--header-bg); color: var(--muted-text);
                font-weight: 600; text-transform: uppercase;
                font-size: 0.75rem; letter-spacing: 0.5px;
                border-bottom: 1px solid var(--border-color);
            }
            .flight-card { grid-column: 1 / -1; border-bottom: 1px solid var(--border-color); }
            .flight-cards > .flight-card:last-child,
            .flight-group:last-child > .flight-card:last-child { border-bottom: none; }
            .flight-card-has-history {
                grid-column: 1 / -1;
                display: grid; grid-template-columns: subgrid;
                background-color: rgb(255 200 200 / 0.05);
            }
            div.flight-card-row:hover { background-color: var(--hover-bg); }
            .flight-card-has-history > summary { cursor: pointer; list-style: none; }
            .flight-card-has-history > summary::-webkit-details-marker { display: none; }
            .flight-card-has-history > summary:hover { background-color: var(--hover-bg); }
            /* What says a row opens, under its route. It replaced a lightning bolt in a column of
               its own (Ted, 2026-10-08): the bolt said nothing about what it was for, and its
               column cost the width the wider spacing needed. Accent and a permanent underline, so
               it reads as tappable with nothing pointing at it — the iPad has no hover. A span,
               not a link: the whole summary is the toggle, and this is its visible sign. */
            .flight-changed {
                display: block; width: fit-content;
                font-size: 0.8rem; color: var(--accent-color); text-decoration: underline;
            }
            .flight-changed::after { content: " \\25B8"; }
            .flight-card-has-history[open] .flight-changed::after { content: " \\25BE"; }
            .flight-departure { font-weight: 500; }
            /* The time always on its own line under the date, at every width (Ted, 2026-09-30):
               run into the date on one line, the time is hard to pick out. The two are already
               separate .nowrap spans inside the <time>, so making each a block is the whole change;
               the vertical space is the price, paid knowingly. */
            .flight-card-row time > .nowrap { display: block; }
            /* Inside the summary's subgrid row; grid-column: 1 / -1 spans every column, with the
               entries stacking in its single implicit column. It lives in the summary (not as a
               sibling) so it spans, but is hidden while the <details> is closed so tapping the row
               still shows/hides it — the summary click toggles [open] as usual. */
            .flight-history-list {
                grid-column: 1 / -1;
                display: grid;
                margin: 0; padding: 4px 16px 0 2rem; color: var(--muted-text); font-size: 0.9rem;
            }
            .flight-card-has-history:not([open]) .flight-history-list { display: none; }
            .flight-history-list li { list-style: none; margin: 0.15rem 0; }
            .flight-history-list li::before { content: "\\2022"; margin-right: 0.6rem; color: var(--muted-text); }
            .empty-state p { margin: 0.5rem 0; }
            /* Per-leg labels: hidden while the header row is visible, shown once the grid stacks. */
            .leg-label {
                display: none;
                font-size: 0.7rem; font-weight: 600; text-transform: uppercase;
                letter-spacing: 0.5px; color: var(--muted-text);
            }
            /* Measured (2026-10-08, headless Chrome, a cancelled "United Airlines" row and two
               trips on the page): the columns' floors need 621px of list, and overflow by 13px at
               608. 40rem leaves a little over that. A container query on the list, not the
               viewport, so the breakpoint is the list's own width whatever margins the page has
               around it. Watch this when widening a column's padding: every 8px added across the
               six columns is 48px more floor. */
            @container flights (width < 40rem) {
                .flight-cards { grid-template-columns: 1fr; }
                .flight-card-header { display: none; }
                .flight-card-row {
                    grid-template-columns: 1fr;
                    align-items: start; gap: 0.15rem;
                }
                .leg-label { display: block; margin-top: 0.5rem; }
                .flight-band { grid-template-columns: 1fr; gap: 0.4rem; }
                .flight-band-text, .flight-band-actions { grid-column: 1; }
                .flight-card-row > .flight-actions-cell { justify-self: start; margin-top: 0.6rem; }
            }
            """;

    /** The default view, cancelled flights hidden and none to count. */
    public static String render(List<BookedFlightView> flights, TimeView activeFilter) {
        return render(flights, activeFilter, CancelledView.HIDE, 0);
    }

    /**
     * The page under both of its filters, each control carrying the other's value through so
     * changing one never resets the other (see {@link CancelledView}).
     *
     * @param cancelledCount how many cancelled flights the time filter admits, which the switch
     *                       reports even while they are hidden.
     */
    public static String render(List<BookedFlightView> flights, TimeView activeFilter,
                                CancelledView activeCancelled, int cancelledCount) {
        return render(flights, activeFilter, activeCancelled, cancelledCount, Map.of());
    }

    /**
     * @param trips the itinerary each listed flight belongs to; a flight entered by hand has no
     *              entry. When any row has one, the list is drawn in runs, each under a band naming
     *              its itinerary (or saying its flights were booked on their own).
     */
    public static String render(List<BookedFlightView> flights, TimeView activeFilter,
                                CancelledView activeCancelled, int cancelledCount,
                                Map<FlightId, FlightTrip> trips) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Booked Flights", CSS + ListToolbar.CSS),
                body(
                        Page.viewNav(Page.NavAudience.OWNER, "/booked-flights"),
                        h1("Booked Flights"),
                        div().withClass("conference-container").with(
                                ListToolbar.render("Book another flight", "/book-flight",
                                        TimeFilterToggle.render("/booked-flights", activeFilter,
                                                activeCancelled == CancelledView.SHOW ? "&cancelled=show" : "")),
                                cancelledSwitch(activeFilter, activeCancelled, cancelledCount),
                                flights.isEmpty()
                                        ? renderEmptyState(activeFilter)
                                        : renderFlightList(flights, trips)
                        )
                )
        ).withLang("en").render();
    }

    /**
     * The same control as the conferences' "Show dropped": the label never changes, only the box
     * does, so it reports what the page is showing rather than what a click would do. A link, so
     * the view stays server-rendered and shareable; {@code aria-pressed} carries the state for both
     * the screen reader and the CSS.
     */
    private static DomContent cancelledSwitch(TimeView activeFilter, CancelledView activeCancelled,
                                              int cancelledCount) {
        boolean shown = activeCancelled == CancelledView.SHOW;
        String filterQuery = "?filter=" + activeFilter.name().toLowerCase(Locale.ENGLISH);
        return div().withClass("flight-cancelled-bar").with(
                a().withClass("flight-cancelled-toggle")
                   .attr("role", "button")
                   .attr("aria-pressed", String.valueOf(shown))
                   .withTitle(shown
                           ? "Cancelled flights are shown. Click to hide them."
                           : "Cancelled flights are hidden. Click to show them.")
                   .withHref("/booked-flights" + filterQuery + (shown ? "" : "&cancelled=show"))
                   .with(shown ? span(rawHtml("&#10003;")).withClass("flight-cancelled-box")
                               : span().withClass("flight-cancelled-box"),
                         text("Show cancelled"),
                         b(String.valueOf(cancelledCount))));
    }

    private static DomContent renderEmptyState(TimeView activeFilter) {
        if (activeFilter == TimeView.FUTURE) {
            return div().withClass("empty-state").with(
                    p("No upcoming flights.")
            );
        }
        return div().withClass("empty-state").with(
                p("No flights booked yet."),
                p(a("Book a flight").withHref("/book-flight"))
        );
    }

    private static DomContent renderFlightList(List<BookedFlightView> flights, Map<FlightId, FlightTrip> trips) {
        return div().withClass("flight-cards").with(
                // The header cells carry the data cells' classes, so a column's spacing is stated
                // once and the header cannot sit a few pixels off the column it names.
                header().withClass("flight-card-header").with(
                        div("Departure").withClass("flight-card-cell"),
                        div("Arrival").withClass("flight-card-cell"),
                        div("Route").withClass("flight-card-cell"),
                        div("Airline").withClass("flight-card-cell"),
                        // Short, so the header is not the column's widest line; stacked, each cell's
                        // own leg label has the room and keeps the full "Flight Number".
                        div("Flight #").withClass("flight-card-cell flight-number"),
                        div()
                ),
                trips.isEmpty()
                        ? each(flights, flight -> renderFlightCard(flight, null))
                        : each(runsOf(flights, trips), run -> renderRun(run, trips))
        );
    }

    /**
     * Consecutive flights of one itinerary, or consecutive flights booked on their own, in list
     * order. An itinerary whose legs another booking falls between is more than one run.
     */
    private static List<List<BookedFlightView>> runsOf(List<BookedFlightView> flights,
                                                       Map<FlightId, FlightTrip> trips) {
        List<List<BookedFlightView>> runs = new ArrayList<>();
        Optional<FlightItineraryId> previous = Optional.empty();
        for (BookedFlightView flight : flights) {
            Optional<FlightItineraryId> current = itineraryOf(flight, trips);
            if (runs.isEmpty() || !current.equals(previous)) {
                runs.add(new ArrayList<>());
            }
            runs.getLast().add(flight);
            previous = current;
        }
        return runs;
    }

    private static Optional<FlightItineraryId> itineraryOf(BookedFlightView flight, Map<FlightId, FlightTrip> trips) {
        return Optional.ofNullable(trips.get(flight.flightId()))
                .map(trip -> trip.itinerary().itineraryId());
    }

    private static DomContent renderRun(List<BookedFlightView> run, Map<FlightId, FlightTrip> trips) {
        FlightTrip first = trips.get(run.getFirst().flightId());
        return div().withClass("flight-group").with(
                first == null ? aloneBand() : itineraryBand(first.itinerary()),
                each(run, flight -> renderFlightCard(flight, trips.get(flight.flightId()))));
    }

    private static DomContent aloneBand() {
        return div().withClass("flight-band flight-band--alone").with(
                div("Booked on its own").withClass("flight-band-text"));
    }

    /**
     * The code as a badge, where the itinerary goes, when, and how many legs, with the actions that
     * belong to the itinerary as a whole in the rows' actions column (D10).
     */
    private static DomContent itineraryBand(ItinerarySummary itinerary) {
        return div().withClass("flight-band flight-card--trip-" + itinerary.hue()).with(
                div().withClass("flight-band-text").with(
                        span(itinerary.confirmationCode()).withClass("flight-band-code"),
                        span(routeOf(itinerary.journeys())).withClass("flight-band-route"),
                        span(datesOf(itinerary)).withClass("flight-band-dates"),
                        span(itinerary.legCount() + (itinerary.legCount() == 1 ? " leg" : " legs"))
                                .withClass("flight-band-count")),
                div().withClass("flight-band-actions").with(cancelItinerary(itinerary)));
    }

    /**
     * Each journey as its two ends, run together where one starts where the last ended:
     * {@code SFO → YOW → SFO} for a round trip, {@code SFO → YOW, YYZ → SFO} for an open jaw.
     */
    private static String routeOf(List<Journey> journeys) {
        StringBuilder route = new StringBuilder();
        AirportCode previousEnd = null;
        for (Journey journey : journeys) {
            if (previousEnd == null) {
                route.append(journey.from().code());
            } else if (!journey.from().equals(previousEnd)) {
                route.append(", ").append(journey.from().code());
            }
            route.append(" → ").append(journey.to().code());
            previousEnd = journey.to();
        }
        return route.toString();
    }

    /** First departure to last arrival, each on its own airport's calendar; one date for a day trip. */
    private static String datesOf(ItinerarySummary itinerary) {
        String departs = BAND_DATE.format(itinerary.departs().atEntryZone());
        String arrives = BAND_DATE.format(itinerary.arrives().atEntryZone());
        return departs.equals(arrives) ? departs : departs + " – " + arrives;
    }

    /**
     * Shown disabled with its reason written out where the itinerary cannot be cancelled whole: the
     * iPad has no hover, so a tooltip would explain nothing. The reason has to be there even when
     * the date filter hides the legs that already left.
     */
    private static DomContent cancelItinerary(ItinerarySummary itinerary) {
        if (itinerary.cancelled()) {
            return disabledItineraryAction("Itinerary cancelled");
        }
        if (!itinerary.flying()) {
            return disabledItineraryAction("Every leg cancelled");
        }
        if (!itinerary.canBeCancelledWhole()) {
            return disabledItineraryAction(itinerary.departedLegs()
                                           + (itinerary.departedLegs() == 1 ? " leg" : " legs")
                                           + " already left");
        }
        return a("Cancel itinerary").withClass("flight-itinerary-cancel-link")
                .withHref("/booked-itineraries/" + itinerary.itineraryId().id() + "/cancel");
    }

    private static DomContent disabledItineraryAction(String why) {
        return div().withClass("flight-itinerary-disabled").with(
                span("Cancel itinerary").withClass("flight-action-disabled"),
                span(why).withClass("flight-itinerary-why"));
    }

    private static DomContent renderFlightCard(BookedFlightView flight, FlightTrip trip) {
        String changeUrl = "/booked-flights/" + flight.flightId().id();
        String cancelledClass = (flight.cancelled() ? " flight-card--cancelled" : "")
                                + (trip == null ? "" : " flight-card--trip-" + trip.itinerary().hue());
        if (flight.hasChanges()) {
            // The change list lives inside the <summary> — the grid row that already spans and
            // aligns the columns — so the list (grid-column: 1 / -1 within that same subgrid row)
            // spans every column too. As a sibling of the summary it stayed stuck in the first
            // column.
            return details().withClass("flight-card flight-card-has-history" + cancelledClass).with(
                    summary().withClass("flight-card-row")
                            .with(rowCells(flight, changeUrl, trip))
                            .with(ul().withClass("flight-history-list").with(
                                    each(flight.history(), entry -> li(entry.displayText()))
                            ))
            );
        }
        return div().withClass("flight-card flight-card-row" + cancelledClass)
                    .with(rowCells(flight, changeUrl, trip));
    }

    // The plain row and the history summary row share the same cells, so both pick up the
    // stacking times, the leg labels, the cancellation line and the collapse behaviour from one
    // place.
    private static DomContent[] rowCells(BookedFlightView flight, String changeUrl, FlightTrip trip) {
        return new DomContent[]{
                div().withClass("flight-card-cell flight-departure").with(
                        legLabel("Departure"), dateTime(flight.departureDateTime())),
                div().withClass("flight-card-cell").with(
                        legLabel("Arrival"), dateTime(flight.arrivalDateTime())),
                div().withClass("flight-card-cell flight-route").with(
                        legLabel("Route"), text(flight.route()),
                        flight.cancelled() ? text("") : googleCalendarIcon(flight),
                        flight.hasChanges() ? span("Changed").withClass("flight-changed") : text("")),
                div().withClass("flight-card-cell").with(
                        legLabel("Airline"), text(flight.airline())),
                div().withClass("flight-card-cell flight-number").with(
                        legLabel("Flight Number"), text(flight.flightNumber()),
                        trip == null || !trip.flying() ? text("") : legPlace(trip)),
                div().withClass("flight-actions-cell").with(
                        flight.cancelled() ? disabledActions() : actions(changeUrl)),
                // A ternary, not iff(): iff evaluates its argument eagerly, and a live flight has
                // no cancelledOn to format.
                flight.cancelled() ? cancelledLine(flight) : text("")
        };
    }

    // Cancel renders *after* Edit so Edit keeps its position (affordances never move).
    private static DomContent actions(String changeUrl) {
        return div().withClass("flight-actions").with(
                a("Edit").withClass("flight-edit-link").withHref(changeUrl),
                a("Cancel").withClass("flight-cancel-link").withHref(changeUrl + "/cancel"));
    }

    /**
     * Per leg, title only, no location: a flight has no venue, and the flight number and route are
     * what Ted looks for in his calendar. It sits after the route in the Route cell, not with Edit
     * and Cancel: it adds this route to Google, and the cell has the room (Ted, 2026-10-04). A
     * cancelled flight does not offer it, since pushing one to Google is only noise.
     */
    private static DomContent googleCalendarIcon(BookedFlightView flight) {
        String title = flight.flightNumber() + " · " + flight.route();
        String href = GoogleCalendarLink.href(
                title, flight.departureDateTime(), flight.arrivalDateTime(), "", "");
        return GoogleCalendarLink.icon(href, "Add " + title + " to Google Calendar");
    }

    /**
     * This leg's place among the itinerary's legs still flying, under the flight number. The code
     * is on the band above, so the row carries only the place. A leg no longer flying has none.
     */
    private static DomContent legPlace(FlightTrip trip) {
        int legs = trip.itinerary().legCount();
        String place = legs == 1 ? "one-way" : "leg " + trip.legNumber() + " of " + legs;
        return span(place).withClass("flight-trip-leg");
    }

    /**
     * Greyed, not removed, and in the same slots: "already cancelled" is state, not permission, so
     * the actions stay visible with the reason (CLAUDE.md, action affordances). Spans, never
     * disabled links — there is nothing to follow. Unlike {@code /booked-hotels}, whose cancelled
     * rows leave the cell empty; that predates the rule.
     */
    private static DomContent disabledActions() {
        String why = "This flight was cancelled";
        return div().withClass("flight-actions").with(
                span("Edit").withClass("flight-action-disabled").withTitle(why),
                span("Cancel").withClass("flight-action-disabled").withTitle(why));
    }

    /**
     * When and why the flight was cancelled (Ted, 2026-09-29), on a line of its own under the row
     * (Ted, 2026-10-08) so a long reason wraps across the whole row instead of widening the Route
     * column. The date is shown in the departure airport's zone: {@code cancelledOn} is an instant,
     * and the flight's own zone is the one on the rest of the row. The reason is written out rather
     * than hidden in a tooltip: the iPad has no hover, and the extra height is fine on a list you
     * opted into.
     */
    private static DomContent cancelledLine(BookedFlightView flight) {
        String on = CANCELLED_ON.format(flight.cancelledOn().atZone(flight.departureDateTime().zone()));
        var line = div(span("Cancelled " + on).withClass("flight-cancelled-badge"))
                .withClass("flight-cancelled-line");
        return flight.cancellationReason().isBlank()
                ? line
                : line.with(span(flight.cancellationReason()).withClass("flight-cancelled-reason"));
    }

    // Shown only once the grid stacks (see the container query); on a wide list the column header
    // carries these labels instead.
    private static DomContent legLabel(String text) {
        return span(text).withClass("leg-label");
    }

    private static DomContent dateTime(ZonedTimestamp when) {
        return ZonedTimeTag.renderDateTimeStacking(when, DATE_PATTERN, TIME_PATTERN);
    }
}
