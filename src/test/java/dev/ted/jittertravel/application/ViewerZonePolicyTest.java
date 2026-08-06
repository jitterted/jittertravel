package dev.ted.jittertravel.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ViewerZonePolicyTest {

    private final ViewerZonePolicy policy = new ViewerZonePolicy();

    @Test
    void ownerIsPinnedToEntryLocalWithNoToggle() {
        ZoneDisplay display = policy.forViewer(true, false, null);

        assertThat(display.active())
                .isEqualTo(DisplayZone.ENTRY);
        assertThat(display.toggleable())
                .as("the traveler's own view has no zone to choose between")
                .isFalse();
        assertThat(display.needsScript())
                .as("OWNER must not even receive the browser-zone script")
                .isFalse();
    }

    @Test
    void ownerIgnoresAnExplicitTzParam() {
        // A stray ?tz=browser on an OWNER page must not move the traveler off entry-local.
        ZoneDisplay display = policy.forViewer(true, false, "browser");

        assertThat(display.active())
                .isEqualTo(DisplayZone.ENTRY);
    }

    @Test
    void familySeesTheirOwnBrowserZoneWithNoToggle() {
        ZoneDisplay display = policy.forViewer(false, true, null);

        assertThat(display.active())
                .isEqualTo(DisplayZone.BROWSER);
        assertThat(display.toggleable())
                .isFalse();
        assertThat(display.needsScript())
                .as("browser zone only happens once the script runs")
                .isTrue();
    }

    @Test
    void anonymousStartsEntryLocalAndCanToggle() {
        ZoneDisplay display = policy.forViewer(false, false, null);

        assertThat(display.active())
                .as("the no-JS baseline is also the anonymous default")
                .isEqualTo(DisplayZone.ENTRY);
        assertThat(display.toggleable())
                .isTrue();
        assertThat(display.needsScript())
                .as("the toggle is inert without the script")
                .isTrue();
    }

    @Test
    void anonymousTzParamSelectsTheStartingZone() {
        ZoneDisplay display = policy.forViewer(false, false, "browser");

        assertThat(display.active())
                .isEqualTo(DisplayZone.BROWSER);
        assertThat(display.toggleable())
                .as("choosing browser zone must not remove the way back")
                .isTrue();
    }

    @Test
    void aViewerHoldingBothRolesIsTreatedAsOwner() {
        ZoneDisplay display = policy.forViewer(true, true, null);

        assertThat(display.active())
                .isEqualTo(DisplayZone.ENTRY);
    }
}
