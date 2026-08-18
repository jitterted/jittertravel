package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelView;
import dev.ted.jittertravel.application.BookedHotelsProjector;
import dev.ted.jittertravel.application.TimeView;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the {@code List<ICalEvent>} the calendar feed serves. Phase 1 wires exactly one
 * contributor of booking-derived events — hotel free-cancellation deadlines — plus a recurring
 * liveness heartbeat. Per "no abstraction before the second user" there is no {@code ICalEventSource}
 * interface yet; that arrives with the second contributor (flights, trains, …).
 * <p>
 * The feed is a pure projection of current bookings evaluated against {@code now} (captured at the
 * controller boundary). Nothing is emitted or scheduled server-side; the device fires the alarms.
 */
@Component
public class CalendarFeedAssembler {

    /**
     * Three alarms per deadline. 48h and 24h give early warning; 4h is the backstop that also covers
     * a booking made less than 24h before its deadline (iOS silently skips the already-past 48h/24h
     * alarms and still fires the 4h one).
     */
    static final List<String> DEADLINE_ALARMS = List.of("-PT48H", "-PT24H", "-PT4H");

    /** The heartbeat and probe both fire ~5 minutes before their (near-future) start. */
    private static final List<String> SHORT_ALARM = List.of("-PT5M");

    private static final Duration EVENT_DURATION = Duration.ofMinutes(15);
    private static final Duration PROBE_LEAD = Duration.ofMinutes(10);

    /**
     * Weekly heartbeat, anchored to a fixed Monday 17:00 UTC so its instant is stable across fetches
     * within a week (the device does not thrash) and its UID changes only when it rolls to the next
     * week. A heartbeat that stops firing is early warning the whole pipeline has gone silent —
     * before a real deadline is missed.
     */
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofDays(7);
    private static final Instant HEARTBEAT_ANCHOR = Instant.parse("2024-01-01T17:00:00Z");

    private static final DateTimeFormatter UID_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DESCRIPTION_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BookedHotelsProjector projector;

    public CalendarFeedAssembler(BookedHotelsProjector projector) {
        this.projector = projector;
    }

    /** The real feed: every live cancel-deadline, plus the always-present liveness heartbeat. */
    public List<ICalEvent> feed(Instant now) {
        List<ICalEvent> events = new ArrayList<>(cancelDeadlineEvents(now));
        events.add(heartbeatEvent(now));
        return events;
    }

    private List<ICalEvent> cancelDeadlineEvents(Instant now) {
        return projector.views(TimeView.ALL, now).stream()
                .filter(view -> !view.cancelled())
                .filter(view -> view.cancelBy() != null)
                .filter(view -> view.cancelBy().utc().isAfter(now))
                .map(this::deadlineEvent)
                .toList();
    }

    private ICalEvent deadlineEvent(BookedHotelView view) {
        Instant deadline = view.cancelBy().utc();
        return new ICalEvent(
                view.hotelBookingId().id() + "-cancelby@jittertravel",
                deadline,
                deadline.plus(EVENT_DURATION),
                "Free-cancel deadline: " + view.hotelName(),
                deadlineDescription(view),
                DEADLINE_ALARMS);
    }

    private String deadlineDescription(BookedHotelView view) {
        String checkIn = DESCRIPTION_DATE.format(view.checkIn().atEntryZone());
        String checkOut = DESCRIPTION_DATE.format(view.checkOut().atEntryZone());
        return view.city() + " — check-in " + checkIn + ", check-out " + checkOut;
    }

    ICalEvent heartbeatEvent(Instant now) {
        Instant start = nextHeartbeat(now);
        return new ICalEvent(
                "heartbeat-" + UID_DATE.format(start) + "@jittertravel",
                start,
                start.plus(EVENT_DURATION),
                "✅ JitterTravel reminder feed is alive",
                "Weekly check that your subscribed feed and its alarms still work. If these stop "
                        + "arriving, the deadline reminders have gone silent — re-check the subscription.",
                SHORT_ALARM);
    }

    private Instant nextHeartbeat(Instant now) {
        long interval = HEARTBEAT_INTERVAL.getSeconds();
        long elapsed = now.getEpochSecond() - HEARTBEAT_ANCHOR.getEpochSecond();
        long steps = Math.floorDiv(elapsed, interval) + 1;
        return HEARTBEAT_ANCHOR.plusSeconds(steps * interval);
    }

    /**
     * The synthetic probe event, recomputed as {@code now + 10 min} on every fetch with a stable
     * UID and a {@code -PT5M} alarm. Consumed two ways from the same endpoint: imported one-off via
     * "Add All" (tests the owned-event alarm path, fires ~5 min later) or subscribed via
     * {@code webcal://} (tests the real subscription path — each pull-to-refresh reschedules the
     * alarm ~5 min out). No hotel booking required.
     */
    public ICalEvent probeEvent(Instant now) {
        Instant start = now.plus(PROBE_LEAD);
        return new ICalEvent(
                "probe@jittertravel",
                start,
                start.plus(EVENT_DURATION),
                "JitterTravel test reminder — safe to delete",
                "JitterTravel feed self-test. If this alert pops, your subscription alarms work. "
                        + "This is a fixed synthetic event with no booking data — safe to delete.",
                SHORT_ALARM);
    }
}
