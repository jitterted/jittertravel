package dev.ted.jittertravel.web;

import j2html.tags.DomContent;

import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.script;

/**
 * Publishes the sticky nav's rendered height as the CSS variable {@code --nav-height}, so
 * anything that sticks below it can offset itself by {@code var(--nav-height)}.
 * <p>
 * <strong>Why this cannot be a constant in the stylesheet.</strong> {@code .view-nav} is
 * {@code flex-wrap: wrap} with up to ten links, so the owner's bar is one line on a laptop and
 * two or three on a phone — 40px or 72px or 104px, decided by the viewport. The calendar then
 * stacks two more sticky layers on top of that (the weekday header, then the month band), and
 * each one's offset is the sum of everything above it. A literal that is wrong by a line's height
 * shows up either as page content in the gap or as a band hidden underneath the bar, and it is
 * wrong on exactly the screens hardest to check.
 * <p>
 * A {@link //ResizeObserver} rather than a resize listener, because the height changes when the
 * bar <em>rewraps</em> — which a font swap or a zoom can do without the window resizing at all.
 * <p>
 * Emitted by {@link Page#viewNav} so it always travels with the bar it measures: a page cannot
 * acquire the nav and forget the script. When no nav is present nothing is emitted and
 * {@code --nav-height} stays at its stylesheet fallback of 0.
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
