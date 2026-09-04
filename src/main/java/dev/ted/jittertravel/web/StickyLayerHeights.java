package dev.ted.jittertravel.web;

import j2html.tags.DomContent;

import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.script;

/**
 * Publishes each sticky layer's measured height as a CSS variable, so anything parking below one
 * offsets itself by {@code var(...)} instead of a literal.
 * <p>
 * <strong>Why not stylesheet constants.</strong> {@code .view-nav} is {@code flex-wrap: wrap} with
 * up to ten links, so the owner's bar is one line on a laptop and two or three on a phone. The
 * weekday header is one line, but its height still moves with font size and zoom — and the jump
 * anchors' {@code scroll-margin-top} is the <em>sum</em> of the two, so a literal in either lands a
 * jumped-to month underneath the bars. A {@code 47px} literal here did exactly that, by enough for
 * the year overview to name the previous month.
 * <p>
 * A {@code ResizeObserver} rather than a resize listener: a bar's height changes when it
 * <em>rewraps</em>, which a font swap or a zoom can do without the window resizing at all.
 * <p>
 * Emitted by {@link Page#viewNav} so it always travels with the layers it measures. A layer that is
 * not on the page publishes nothing, and its stylesheet fallback stands.
 */
final class StickyLayerHeights {

    private StickyLayerHeights() {
    }

    private static final String SCRIPT = """
            (function () {
              var layers = [['nav.view-nav', '--nav-height'],
                            ['.calendar-header', '--calendar-weekday-header-height']];
              function measure() {
                layers.forEach(function (layer) {
                  var element = document.querySelector(layer[0]);
                  if (!element) return;
                  function publish() {
                    document.documentElement.style.setProperty(
                        layer[1], element.offsetHeight + 'px');
                  }
                  publish();
                  if (window.ResizeObserver) new ResizeObserver(publish).observe(element);
                  else window.addEventListener('resize', publish);
                });
              }
              // Deferred: this script rides with the nav, which renders BEFORE the layers below it,
              // so measuring inline finds only the bar. A 47px literal for the weekday header was
              // 5px out against its real 42 — the drift this exists to remove.
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', measure);
              } else {
                measure();
              }
            })();
            """;

    static DomContent render() {
        return script(rawHtml(SCRIPT));
    }
}
