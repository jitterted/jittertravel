package dev.ted.jittertravel.application;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ViewerTodayZoneTest {

    private static final ZoneId FALLBACK = ZoneId.of("America/Los_Angeles");
    private final ViewerTodayZone viewerTodayZone = new ViewerTodayZone(FALLBACK);

    @Test
    void resolvesAValidIanaZoneFromTheCookie() {
        assertThat(viewerTodayZone.resolve("Europe/Berlin"))
                .isEqualTo(ZoneId.of("Europe/Berlin"));
    }

    @Test
    void fallsBackWhenCookieIsAbsent() {
        assertThat(viewerTodayZone.resolve(null))
                .isEqualTo(FALLBACK);
    }

    @Test
    void fallsBackWhenCookieIsBlank() {
        assertThat(viewerTodayZone.resolve("   "))
                .isEqualTo(FALLBACK);
    }

    @Test
    void fallsBackWhenCookieValueIsNotARecognizedZone() {
        assertThat(viewerTodayZone.resolve("Mars/Olympus_Mons"))
                .isEqualTo(FALLBACK);
    }
}
