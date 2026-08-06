package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ZoneDisplay;
import j2html.tags.DomContent;
import j2html.tags.specialized.HtmlTag;

import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.span;

/**
 * The browser-zone upgrade: an inline script that rewrites every {@code <time data-fmt>} emitted
 * by {@link ZonedTimeTag} into the viewer's own zone.
 * <p>
 * It is <em>progressive enhancement only</em>. The server has already rendered entry-local text,
 * so with JavaScript off — or for OWNER, who never receives this script — the page still reads
 * correctly (decision 8). Nothing here affects evaluation; the {@code datetime} attribute's UTC
 * instant is the single source both renderings come from.
 * <p>
 * Day bucketing is deliberately <em>not</em> touched: an entry stays in the column of its
 * entry-zone local day (decision 7), so only the time text moves.
 */
public final class BrowserZoneScript {

    private BrowserZoneScript() {
    }

    /**
     * Translates the {@code data-fmt} pattern (a {@code DateTimeFormatter} pattern, so the server
     * stays the single source of the format) into {@code Intl.DateTimeFormat} options, and
     * formats with {@code en-US} to match the server's {@code Locale.ENGLISH} rendering — the
     * viewer's <em>zone</em> is what changes here, not their locale.
     * <p>
     * The first run stashes the server text in {@code data-entry-text}, so switching back to
     * entry-local restores the exact bytes the server sent rather than re-deriving them.
     */
    private static final String SCRIPT = """
            (function () {
                var root = document.documentElement;
                var STORAGE_KEY = 'jittertravel.tz';

                function optionsFor(fmt) {
                    var options = {};
                    if (fmt.indexOf('EEE') >= 0) { options.weekday = 'short'; }
                    if (fmt.indexOf('MMM') >= 0) { options.month = 'short'; }
                    if (/(^|[^a-zA-Z])d([^a-zA-Z]|$)/.test(fmt)) { options.day = 'numeric'; }
                    if (fmt.indexOf('yyyy') >= 0) { options.year = 'numeric'; }
                    if (fmt.indexOf('h') >= 0) {
                        options.hour = 'numeric';
                        options.minute = '2-digit';
                        options.hour12 = true;
                    }
                    return options;
                }

                function applyZone(zone) {
                    var elements = document.querySelectorAll('time[data-fmt]');
                    for (var i = 0; i < elements.length; i++) {
                        var element = elements[i];
                        if (element.getAttribute('data-entry-text') === null) {
                            element.setAttribute('data-entry-text', element.textContent);
                        }
                        if (zone === 'browser') {
                            var moment = new Date(element.getAttribute('datetime'));
                            var format = new Intl.DateTimeFormat(
                                'en-US', optionsFor(element.getAttribute('data-fmt')));
                            element.textContent = format.format(moment);
                        } else {
                            element.textContent = element.getAttribute('data-entry-text');
                        }
                    }
                    root.setAttribute('data-zone', zone);
                    var buttons = document.querySelectorAll('.zone-toggle button[data-zone-choice]');
                    for (var j = 0; j < buttons.length; j++) {
                        var button = buttons[j];
                        var selected = button.getAttribute('data-zone-choice') === zone;
                        button.classList.toggle('active', selected);
                        button.setAttribute('aria-pressed', String(selected));
                    }
                }

                function remember(zone) {
                    try {
                        window.localStorage.setItem(STORAGE_KEY, zone);
                    } catch (ignored) { /* private mode: the URL still carries the choice */ }
                    try {
                        var url = new URL(window.location.href);
                        url.searchParams.set('tz', zone);
                        window.history.replaceState({}, '', url);
                    } catch (ignored) {
                        // Opaque origin (about:blank, file://): the toggle still works in-page,
                        // there is just no URL to carry the choice into a reload.
                    }
                }

                function remembered() {
                    try {
                        return window.localStorage.getItem(STORAGE_KEY);
                    } catch (ignored) {
                        return null;
                    }
                }

                var toggleable = root.getAttribute('data-zone-toggleable') === 'true';
                // An explicit ?tz= wins over a stored preference; a stored preference wins over
                // the entry-local default, so the choice survives a reload.
                var explicit = new URL(window.location.href).searchParams.get('tz');
                var initial = root.getAttribute('data-zone') || 'entry';
                    if (toggleable && !explicit && remembered()) {
                    initial = remembered();
                }
                applyZone(initial);

                if (toggleable) {
                    var buttons = document.querySelectorAll('.zone-toggle button[data-zone-choice]');
                    for (var k = 0; k < buttons.length; k++) {
                        buttons[k].addEventListener('click', function () {
                            var zone = this.getAttribute('data-zone-choice');
                            applyZone(zone);
                            remember(zone);
                        });
                    }
                }
            })();
            """;

    /**
     * The script, or nothing at all when the viewer is pinned to entry-local (OWNER). Emitting
     * nothing — rather than a script that decides to do nothing — is what makes "OWNER ships
     * without the script" true rather than merely intended.
     */
    public static DomContent render(ZoneDisplay zoneDisplay) {
        if (!zoneDisplay.needsScript()) {
            return span();
        }
        return rawHtml("<script>" + SCRIPT + "</script>");
    }

    /**
     * Stamps the starting state onto {@code <html>} for the script to read. It is also what the
     * no-JS baseline advertises: {@code data-zone="entry"} on a page nobody re-localized.
     */
    public static HtmlTag markRoot(HtmlTag html, ZoneDisplay zoneDisplay) {
        return html.attr("data-zone", zoneDisplay.active().paramValue())
                   .attr("data-zone-toggleable", String.valueOf(zoneDisplay.toggleable()));
    }
}
