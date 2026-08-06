package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.DisplayZone;
import dev.ted.jittertravel.application.ZoneDisplay;
import j2html.tags.DomContent;

import static j2html.TagCreator.button;
import static j2html.TagCreator.div;
import static j2html.TagCreator.span;

/**
 * The anonymous viewer's "Event time / My time" switch, mirroring {@link TimeFilterToggle}'s
 * shape (styling lives in {@code site.css} under {@code .zone-toggle}).
 * <p>
 * Unlike the time filter these are buttons, not links: {@link BrowserZoneScript} switches the
 * rendered times in place and rewrites {@code ?tz=} via {@code history.replaceState}, so the
 * choice survives a reload without a round trip that would have to carry every other query
 * parameter along with it. Only shown where a choice exists — OWNER and FAMILY have their zone
 * decided by role (decision 4).
 */
public final class ZoneToggle {

    private ZoneToggle() {
    }

    public static DomContent render(ZoneDisplay zoneDisplay) {
        if (!zoneDisplay.toggleable()) {
            return span();
        }
        return div().withClass("zone-toggle").with(
                toggleButton("Event time", DisplayZone.ENTRY, zoneDisplay.active()),
                toggleButton("My time", DisplayZone.BROWSER, zoneDisplay.active())
        );
    }

    private static DomContent toggleButton(String label, DisplayZone choice, DisplayZone active) {
        boolean selected = choice == active;
        return button(label)
                .withType("button")
                .attr("data-zone-choice", choice.paramValue())
                .attr("aria-pressed", String.valueOf(selected))
                .withClass(selected ? "active" : "");
    }
}
