package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.DisplayZone;
import dev.ted.jittertravel.application.ZoneDisplay;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneToggleTest {

    @Test
    void nonToggleableViewerGetsNoToggleAtAll() {
        String html = ZoneToggle.render(ZoneDisplay.entryOnly()).render();

        assertThat(html)
                .doesNotContain("zone-toggle");
    }

    @Test
    void familyGetsNoToggleBecauseTheirRoleDecidesTheZone() {
        String html = ZoneToggle.render(new ZoneDisplay(DisplayZone.BROWSER, false)).render();

        assertThat(html)
                .doesNotContain("zone-toggle");
    }

    @Test
    void anonymousEntryLocalMarksEventTimeAsTheActiveChoice() {
        String html = ZoneToggle.render(new ZoneDisplay(DisplayZone.ENTRY, true)).render();

        assertThat(html)
                .contains("<button type=\"button\" data-zone-choice=\"entry\" "
                          + "aria-pressed=\"true\" class=\"active\">Event time</button>")
                .contains("<button type=\"button\" data-zone-choice=\"browser\" "
                          + "aria-pressed=\"false\" class=\"\">My time</button>");
    }

    @Test
    void anonymousBrowserZoneMarksMyTimeAsTheActiveChoice() {
        String html = ZoneToggle.render(new ZoneDisplay(DisplayZone.BROWSER, true)).render();

        assertThat(html)
                .contains("data-zone-choice=\"browser\" aria-pressed=\"true\" class=\"active\"")
                .contains("data-zone-choice=\"entry\" aria-pressed=\"false\" class=\"\"");
    }
}
