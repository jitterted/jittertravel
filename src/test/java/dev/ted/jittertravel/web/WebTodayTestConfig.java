package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ViewerTodayZone;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Supplies the boundary beans view controllers need for time — a fixed {@link Clock} and a
 * {@link ViewerTodayZone} with a known fallback — to the {@code @WebMvcTest} slices that
 * exercise them. The instant is a late-June midday so the fallback zone
 * (America/Los_Angeles) yields today = 2026-06-25.
 * <p>
 * Every view-controller slice needs this: no production class may read the ambient system
 * clock (see {@code NoAmbientClockReadsTest}), so each controller takes a {@code Clock}, and
 * a {@code @WebMvcTest} slice has no {@code Clock} bean unless one is imported here.
 */
@TestConfiguration
public class WebTodayTestConfig {

    /** Midday UTC on 2026-06-25; in America/Los_Angeles that is still 2026-06-25 (05:00). */
    public static final Instant FIXED_INSTANT = Instant.parse("2026-06-25T12:00:00Z");

    @Bean
    Clock clock() {
        return Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));
    }

    @Bean
    ViewerTodayZone viewerTodayZone() {
        return new ViewerTodayZone(ZoneId.of("America/Los_Angeles"));
    }
}
