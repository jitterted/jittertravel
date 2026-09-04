package dev.ted.jittertravel.web;

import j2html.tags.DomContent;

import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.script;

/**
 * Publishes the sticky nav's rendered height as the CSS variable {@code --nav-height}, so
 * anything that sticks below it — the calendar's weekday header, the jump anchors'
 * {@code scroll-margin-top} — can offset itself by {@code var(--nav-height)}.
 * <p>
 * <strong>Why not a stylesheet constant.</strong> {@code .view-nav} is {@code flex-wrap: wrap}
 * with up to ten links, so the owner's bar is one line on a laptop and two or three on a phone. A
 * literal wrong by a line's height leaves page content in the gap, or the header hidden under the
 * bar — and is wrong on exactly the screens hardest to check.
 * <p>
 * A {@code ResizeObserver} rather than a resize listener: the height changes when the bar
 * <em>rewraps</em>, which a font swap or a zoom can do without the window resizing at all.
 * <p>
 * Emitted by {@link Page#viewNav} so it always travels with the bar it measures. With no nav,
 * nothing is emitted and {@code --nav-height} stays at its stylesheet fallback of 0.
 */
final class StickyNavScript {

    private StickyNavScript() {
    }

    private static final String SCRIPT = """
            (function () {
              var nav = document.querySelector('nav.view-nav');
              if (!nav) return;
              function publish() {
                document.documentElement.style.setProperty(
                    '--nav-height', nav.offsetHeight + 'px');
              }
              publish();
              if (window.ResizeObserver) new ResizeObserver(publish).observe(nav);
              else window.addEventListener('resize', publish);
            })();
            """;

    static DomContent render() {
        return script(rawHtml(SCRIPT));
    }
}
