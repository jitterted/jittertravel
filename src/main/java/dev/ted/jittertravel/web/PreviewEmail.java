package dev.ted.jittertravel.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One email as the preview page shows it: which message it is, the file its words come from, and the
 * subject and body exactly as they would be sent. The body is also split into plain text and links so
 * the page can make a link clickable without altering a single character of what is sent.
 */
public record PreviewEmail(String label, String file, String subject, String body) {

    private static final Pattern LINK = Pattern.compile("https?://\\S+");

    /** A run of the body: either text, or a link that should be shown as one. */
    public record Segment(String text, boolean link) {
    }

    public List<Segment> segments() {
        List<Segment> segments = new ArrayList<>();
        Matcher matcher = LINK.matcher(body);
        int from = 0;
        while (matcher.find()) {
            if (matcher.start() > from) {
                segments.add(new Segment(body.substring(from, matcher.start()), false));
            }
            segments.add(new Segment(matcher.group(), true));
            from = matcher.end();
        }
        if (from < body.length()) {
            segments.add(new Segment(body.substring(from), false));
        }
        return segments;
    }
}
