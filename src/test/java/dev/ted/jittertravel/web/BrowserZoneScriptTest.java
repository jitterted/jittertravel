package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.DisplayZone;
import dev.ted.jittertravel.application.ZoneDisplay;
import org.junit.jupiter.api.Test;

import static j2html.TagCreator.body;
import static j2html.TagCreator.html;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the <em>server</em> emits. The script's behavior in a browser is
 * {@link BrowserZoneJsTest}'s job; here we only pin what is shipped to whom.
 */
class BrowserZoneScriptTest {

    @Test
    void ownerPageShipsNoScript() {
        String html = BrowserZoneScript.render(ZoneDisplay.entryOnly()).render();

        assertThat(html)
                .as("OWNER is entry-local by role, so there is nothing for a script to do")
                .doesNotContain("<script");
    }

    @Test
    void familyPageShipsTheScript() {
        String html = BrowserZoneScript.render(new ZoneDisplay(DisplayZone.BROWSER, false)).render();

        assertThat(html)
                .contains("<script")
                .contains("Intl.DateTimeFormat");
    }

    @Test
    void anonymousPageShipsTheScriptEvenThoughItStartsEntryLocal() {
        // Starting entry-local is not the same as staying there: the toggle needs the script.
        String html = BrowserZoneScript.render(new ZoneDisplay(DisplayZone.ENTRY, true)).render();

        assertThat(html)
                .contains("<script");
    }

    @Test
    void rootCarriesTheStartingZoneAndWhetherAToggleExists() {
        String rendered = BrowserZoneScript
                .markRoot(html(body()), new ZoneDisplay(DisplayZone.BROWSER, true))
                .render();

        assertThat(rendered)
                .contains("data-zone=\"browser\"")
                .contains("data-zone-toggleable=\"true\"");
    }

    @Test
    void noJsBaselineAdvertisesEntryLocal() {
        String rendered = BrowserZoneScript
                .markRoot(html(body()), ZoneDisplay.entryOnly())
                .render();

        assertThat(rendered)
                .contains("data-zone=\"entry\"")
                .contains("data-zone-toggleable=\"false\"");
    }
}
