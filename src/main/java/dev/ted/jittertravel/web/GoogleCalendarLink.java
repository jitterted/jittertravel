package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.ZonedTimestamp;
import j2html.tags.DomContent;
import j2html.tags.specialized.ATag;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static j2html.TagCreator.a;
import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.span;

/**
 * The "push this entry into Google Calendar" control, shared by the conference, gathering and
 * private-event list views.
 * <p>
 * <strong>It is a plain link, and that is the whole design</strong> (Ted, 2026-09-08). Google's
 * {@code render?action=TEMPLATE} URL opens its event editor pre-filled; Ted presses Save. No OAuth,
 * no stored refresh token, no Google SDK, no scheduler, and above all <em>nothing the server
 * sends</em> — which keeps the promise the subscription feed was chosen for in the first place
 * ({@code docs/archived/CalendarSubscriptionFeedPlan.md}): no exactly-once hazard, and a replay or a
 * restore cannot re-push anything, because pushing is something Ted does with his thumb.
 * <p>
 * <strong>Why a push at all, when the {@code .ics} feed exists.</strong> Ted schedules real work
 * against Google Calendar, so a conference has to <em>be there</em> to be scheduled around — a
 * subscribed feed refreshes on Google's own clock (hours, uncontrollable) and could not answer
 * "book dinner after the last session ends".
 * <p>
 * <strong>Wall-clock precision is the point, so the link carries a zone.</strong> {@code dates} is
 * emitted as local basic-format times with no {@code Z}, and {@code ctz} names the IANA zone they
 * are read in — which is exactly what {@link ZonedTimestamp} already holds, so the venue's own
 * 09:00 arrives in Google as 09:00 there rather than as whatever that instant is where Ted is
 * standing. Both ends are formatted in the <em>start's</em> zone, since {@code ctz} is one
 * parameter for the pair.
 * <p>
 * <strong>Known and accepted (Ted, 2026-09-08): it is fire-and-forget.</strong> Press twice and
 * Google has two events. Nothing here records that a push happened, so a later edit or cancellation
 * in JitterTravel does not reach Google — that is fixed by hand, and buying the alternative means
 * OAuth plus a persisted entry-to-Google-event mapping. Also accepted: a multi-day conference
 * arrives as one timed event, which Google files in its all-day banner rather than the time grid;
 * the exact start and end are in the event's detail.
 * <p>
 * <strong>OWNER surfaces only.</strong> The URL carries the title and venue in the page's markup, so
 * this control belongs on pages the redaction rules already gate — the three list views it is used
 * from are all OWNER-only. It has no place on any calendar entry: {@code EntryDetails.Publishable}
 * has no slot for it, which is the allow-list doing its job.
 */
public final class GoogleCalendarLink {

    private GoogleCalendarLink() {
    }

    private static final String TEMPLATE_BASE =
            "https://calendar.google.com/calendar/render?action=TEMPLATE";

    /**
     * Local basic format, deliberately without the trailing {@code Z} the feed's {@link ICalWriter}
     * emits: a UTC instant would be re-displayed in whatever zone Google thinks Ted is in, and this
     * link exists to preserve the venue's wall-clock. The {@code ctz} parameter carries the zone.
     */
    private static final DateTimeFormatter LOCAL_BASIC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    /**
     * Font Awesome Pro {@code calendar-arrow-up} (regular). <strong>Its own icon, not a borrowed
     * one</strong> — a pencil means edit and a bin means cancel app-wide, and "send this somewhere
     * else" is a third meaning that has to arrive with a third glyph (CLAUDE.md, "an icon means one
     * thing, app-wide"). It is always visible, so the icon is the affordance and no {@code :hover}
     * is load-bearing.
     */
    static final String ICON_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 640 640\" fill=\"currentColor\" aria-hidden=\"true\">"
                    + "<!--!Font Awesome Pro v7.3.1 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license (Commercial License) Copyright 2026 Fonticons, Inc.-->"
                    + "<path d=\"M216 64C229.3 64 240 74.7 240 88L240 128L400 128L400 88C400 74.7 410.7 64 424 64C437.3 64 448 74.7 448 88L448 128L480 128C515.3 128 544 156.7 544 192L544 480C544 515.3 515.3 544 480 544L160 544C124.7 544 96 515.3 96 480L96 192C96 156.7 124.7 128 160 128L192 128L192 88C192 74.7 202.7 64 216 64zM216 176L160 176C151.2 176 144 183.2 144 192L144 480C144 488.8 151.2 496 160 496L480 496C488.8 496 496 488.8 496 480L496 192C496 183.2 488.8 176 480 176L216 176zM337 239L409 311C418.4 320.4 418.4 335.6 409 344.9C399.6 354.2 384.4 354.3 375.1 344.9L344.1 313.9L344.1 416C344.1 429.3 333.4 440 320.1 440C306.8 440 296.1 429.3 296.1 416L296.1 313.9L265.1 344.9C255.7 354.3 240.5 354.3 231.2 344.9C221.9 335.5 221.8 320.3 231.2 311L303.2 239C312.6 229.6 327.8 229.6 337.1 239z\"/>"
                    + "</svg>";

    /**
     * The pre-filled Google Calendar template URL. {@code location} and {@code details} are omitted
     * entirely when blank rather than sent empty, so the editor opens with those fields untouched.
     */
    public static String href(String title,
                              ZonedTimestamp start,
                              ZonedTimestamp end,
                              String location,
                              String details) {
        ZoneId zone = start.zone();
        StringBuilder url = new StringBuilder(TEMPLATE_BASE);
        url.append("&text=").append(encode(title));
        url.append("&dates=")
           .append(LOCAL_BASIC.format(start.atEntryZone()))
           .append("/")
           .append(LOCAL_BASIC.format(end.at(zone)));
        url.append("&ctz=").append(encode(zone.getId()));
        if (!location.isBlank()) {
            url.append("&location=").append(encode(location));
        }
        if (!details.isBlank()) {
            url.append("&details=").append(encode(details));
        }
        return url.toString();
    }

    /**
     * Icon alone, for a cell whose neighbouring value already says what is being added — the When
     * column on the gathering and private-event lists.
     */
    public static DomContent icon(String href, String tooltip) {
        return anchor(href, tooltip).with(rawHtml(ICON_SVG));
    }

    /**
     * Icon and words, for the line under a conference's dates. The label carries a permanent
     * underline (see {@code site.css}); the iPad has no pointer, so nothing here may wait for a
     * hover to become visible.
     */
    public static DomContent labelled(String href, String label, String tooltip) {
        return anchor(href, tooltip).with(rawHtml(ICON_SVG), span(label).withClass("gcal-label"));
    }

    private static ATag anchor(String href, String tooltip) {
        return a().withClass("gcal-add")
                  .withHref(href)
                  .withTitle(tooltip)
                  .withTarget("_blank")
                  .withRel("noopener");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
