package dev.ted.jittertravel.web;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the {@code List<ICalEvent>} the calendar feed serves: every {@link ICalEventSource}'s
 * contribution, plus a recurring liveness heartbeat that belongs to no source because it is about
 * the feed itself rather than about anything Ted booked.
 * <p>
 * <strong>The sources are named, not collection-injected.</strong> Asking Spring for a
 * {@code List<ICalEventSource>} would wire them by type and make the feed's contents depend on which
 * beans happen to exist; naming them keeps "what is in the feed" a decision written down in one
 * place — which matters, because every source widens what one leaked feed URL exposes. Adding a
 * third means editing this constructor, deliberately.
 * <p>
 * The feed is a pure projection evaluated against {@code now} (captured at the controller boundary).
 * Nothing is emitted or scheduled server-side; the device fires the alarms.
 */
@Component
public class CalendarFeedAssembler {

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

    private final List<ICalEventSource> sources;

    public CalendarFeedAssembler(HotelCancelDeadlineSource hotelCancelDeadlines,
                                 CfpDeadlineSource cfpDeadlines) {
        this.sources = List.of(hotelCancelDeadlines, cfpDeadlines);
    }

    /** The real feed: every source's live deadlines, plus the always-present liveness heartbeat. */
    public List<ICalEvent> feed(Instant now) {
        List<ICalEvent> events = new ArrayList<>();
        sources.forEach(source -> events.addAll(source.events(now)));
        events.add(heartbeatEvent(now));
        return events;
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
