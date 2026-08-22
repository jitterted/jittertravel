package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * The assembler's own job, now that the deadline logic lives in {@link ICalEventSource}s: compose
 * every source's contribution and add the liveness heartbeat, which belongs to no source because it
 * is about the feed itself rather than anything Ted booked.
 * <p>
 * What each source contributes is its own test — {@link HotelCancelDeadlineSourceTest},
 * {@link CfpDeadlineSourceTest}. What matters here is that <em>none of them is dropped</em>, which
 * is the failure this composition can have and they cannot.
 */
class CalendarFeedAssemblerTest {

    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    private final HotelCancelDeadlineSource hotelDeadlines = mock(HotelCancelDeadlineSource.class);
    private final CfpDeadlineSource cfpDeadlines = mock(CfpDeadlineSource.class);
    private final CalendarFeedAssembler assembler =
            new CalendarFeedAssembler(hotelDeadlines, cfpDeadlines);

    /**
     * The regression this guards is a source silently missing from the feed — which looks exactly
     * like "no deadlines right now" and so never announces itself.
     */
    @Test
    void everySourcesEventsReachTheFeed() {
        given(hotelDeadlines.events(NOW)).willReturn(List.of(event("hotel-1@jittertravel")));
        given(cfpDeadlines.events(NOW)).willReturn(List.of(event("cfp-1@jittertravel")));

        assertThat(assembler.feed(NOW))
                .extracting(ICalEvent::uid)
                .contains("hotel-1@jittertravel", "cfp-1@jittertravel");
    }

    @Test
    void feedAlwaysContainsTheLivenessHeartbeatEvenWithNothingToRemind() {
        given(hotelDeadlines.events(NOW)).willReturn(List.of());
        given(cfpDeadlines.events(NOW)).willReturn(List.of());

        List<ICalEvent> feed = assembler.feed(NOW);

        assertThat(feed).hasSize(1);
        ICalEvent heartbeat = feed.getFirst();
        assertThat(heartbeat.uid()).startsWith("heartbeat-").endsWith("@jittertravel");
        assertThat(heartbeat.alarmTriggers()).containsExactly("-PT5M");
    }

    @Test
    void heartbeatIsStrictlyInTheFutureAndAlignedToTheWeeklyCadence() {
        ICalEvent heartbeat = assembler.heartbeatEvent(NOW);

        assertThat(heartbeat.start()).isAfter(NOW);
        // Anchored to a fixed Monday 17:00 UTC, so its offset from the anchor is a whole number of weeks.
        Instant anchor = Instant.parse("2024-01-01T17:00:00Z");
        long secondsFromAnchor = heartbeat.start().getEpochSecond() - anchor.getEpochSecond();
        assertThat(secondsFromAnchor % Duration.ofDays(7).getSeconds()).isZero();
    }

    @Test
    void probeEventIsTenMinutesOutWithAShortAlarmAndAStableUid() {
        ICalEvent probe = assembler.probeEvent(NOW);

        assertThat(probe.uid()).isEqualTo("probe@jittertravel");
        assertThat(probe.start()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(probe.alarmTriggers()).containsExactly("-PT5M");
    }

    private static ICalEvent event(String uid) {
        return new ICalEvent(uid, NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(1)),
                "Something", "", List.of());
    }
}
