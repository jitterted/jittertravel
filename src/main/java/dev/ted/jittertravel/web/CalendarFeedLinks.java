package dev.ted.jittertravel.web;

/**
 * The three token-bearing URLs shown on the OWNER-only admin card, derived from a base URL and the
 * feed token. Because every URL contains the token, this is only ever rendered on an OWNER-only page.
 * <ul>
 *   <li><b>subscribe</b> — the real feed over {@code webcal://}, which opens the iOS subscribe sheet;</li>
 *   <li><b>probeAdd</b> — the probe over {@code https://}, imported one-off via "Add All" (owned-event
 *       alarm test);</li>
 *   <li><b>probeSubscribe</b> — the probe over {@code webcal://}, subscribed to test the real
 *       subscription alarm path (pull-to-refresh reschedules the alarm ~5 min out).</li>
 * </ul>
 * A {@code webcal://} URL is the {@code https://} one with the scheme swapped — iOS uses it purely to
 * trigger the subscribe flow; the app then fetches over https.
 */
public class CalendarFeedLinks {

    private final String subscribeUrl;
    private final String probeAddUrl;
    private final String probeSubscribeUrl;

    public CalendarFeedLinks(String baseUrl, String token) {
        String https = stripTrailingSlash(baseUrl);
        String webcal = https.replaceFirst("^https?://", "webcal://");
        this.subscribeUrl = webcal + "/calendar/feed/" + token + ".ics";
        this.probeAddUrl = https + "/calendar/feed/" + token + "/probe.ics";
        this.probeSubscribeUrl = webcal + "/calendar/feed/" + token + "/probe.ics";
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String subscribeUrl() {
        return subscribeUrl;
    }

    public String probeAddUrl() {
        return probeAddUrl;
    }

    public String probeSubscribeUrl() {
        return probeSubscribeUrl;
    }
}
