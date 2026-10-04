package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyNotifyStatusTest {

    @Test
    void anUnconfiguredNotifierSaysSoWhateverTheSwitchIs() {
        assertThat(new FamilyNotifyStatus(true, false, "").summary())
                .isEqualTo("Family email is not configured.");
        assertThat(new FamilyNotifyStatus(false, false, "").summary())
                .isEqualTo("Family email is not configured.");
    }

    @Test
    void anOnNotifierNamesWhereBookingsGo() {
        FamilyNotifyStatus status = new FamilyNotifyStatus(true, true, "family@example.com");

        assertThat(status.summary())
                .isEqualTo("Family email is on: a new flight or trip is emailed to family@example.com.");
        assertThat(status.explanation())
                .isEqualTo("Switch it off with FAMILY_NOTIFY_ENABLED=false. The test below works either way.");
    }

    @Test
    void anOffNotifierSaysNothingIsSentAndHowToTurnItOn() {
        FamilyNotifyStatus status = new FamilyNotifyStatus(false, true, "family@example.com");

        assertThat(status.summary())
                .isEqualTo("Family email is off: nothing is sent when a flight is booked.");
        assertThat(status.explanation())
                .isEqualTo("Switch it on with FAMILY_NOTIFY_ENABLED=true once a test has reached the right inbox.");
    }
}
