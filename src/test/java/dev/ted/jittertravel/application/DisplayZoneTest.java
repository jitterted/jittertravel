package dev.ted.jittertravel.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayZoneTest {

    @Test
    void absentParamFallsBackToEntryLocal() {
        assertThat(DisplayZone.fromParam(null))
                .isEqualTo(DisplayZone.ENTRY);
    }

    @Test
    void unrecognizedParamFallsBackToEntryLocal() {
        // The server-rendered baseline is the safe default: a junk ?tz= must never leave the
        // page claiming a zone nobody applied.
        assertThat(DisplayZone.fromParam("utc"))
                .isEqualTo(DisplayZone.ENTRY);
    }

    @Test
    void paramIsCaseInsensitive() {
        assertThat(DisplayZone.fromParam("BrOwSeR"))
                .isEqualTo(DisplayZone.BROWSER);
    }

    @Test
    void paramValueRoundTripsThroughFromParam() {
        assertThat(DisplayZone.fromParam(DisplayZone.BROWSER.paramValue()))
                .isEqualTo(DisplayZone.BROWSER);
        assertThat(DisplayZone.fromParam(DisplayZone.ENTRY.paramValue()))
                .isEqualTo(DisplayZone.ENTRY);
    }
}
