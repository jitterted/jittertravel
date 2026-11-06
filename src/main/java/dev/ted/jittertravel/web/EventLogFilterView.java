package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.EventLogFilter;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The event log's filter as {@code admin-eventlog.html} draws it: what the form is refilled with,
 * the sentence the empty state says, the paging links that carry the filter, and where in a
 * payload a matched identifier sits. See {@code docs/EventLogFilterPlan.md}.
 * <p>
 * Public, with public methods, because the template reaches them by reflection.
 */
public record EventLogFilterView(EventLogFilter filter) {

    /** A stretch of payload text: a matched identifier, or the text between two of them. */
    public record Segment(String text, boolean matched) {
    }

    public boolean active() {
        return !filter.isEmpty();
    }

    public boolean typesChosen() {
        return !filter.types().isEmpty();
    }

    public boolean selected(String type) {
        return filter.types().contains(type);
    }

    /** What the identifier input holds after a submit: the identifiers as understood, not as typed. */
    public String identifierText() {
        return String.join(" ", filter.identifiers());
    }

    /** "2 types and 1 identifier selected." Only read when the filter is active. */
    public String summary() {
        List<String> selections = new ArrayList<>();
        if (!filter.types().isEmpty()) {
            selections.add(counted(filter.types().size(), "type"));
        }
        if (!filter.identifiers().isEmpty()) {
            selections.add(counted(filter.identifiers().size(), "identifier"));
        }
        return String.join(" and ", selections) + " selected.";
    }

    private String counted(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    /**
     * The address of another page of the same filtered view. Built here and not by a Thymeleaf
     * link expression, which writes a parameter with no value for an absent one.
     */
    public String hrefForPage(int page) {
        StringBuilder href = new StringBuilder("/admin/eventlog?page=").append(page);
        for (String type : filter.types()) {
            href.append("&type=").append(encoded(type));
        }
        if (!filter.identifiers().isEmpty()) {
            href.append("&id=").append(encoded(identifierText()));
        }
        return href.toString();
    }

    private String encoded(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * The payload cut at every matched identifier, so the template can draw each match in a
     * {@code <mark>} and leave every piece, matched or not, to the engine's own escaping. Nothing
     * here builds markup, which is what keeps a payload holding {@code <} from being read as one.
     */
    public List<Segment> segmentsOf(String payload) {
        if (filter.identifiers().isEmpty()) {
            return List.of(new Segment(payload, false));
        }
        Matcher matcher = anyIdentifier().matcher(payload);
        List<Segment> segments = new ArrayList<>();
        int unmatchedFrom = 0;
        while (matcher.find()) {
            if (matcher.start() > unmatchedFrom) {
                segments.add(new Segment(payload.substring(unmatchedFrom, matcher.start()), false));
            }
            segments.add(new Segment(matcher.group(), true));
            unmatchedFrom = matcher.end();
        }
        if (unmatchedFrom < payload.length()) {
            segments.add(new Segment(payload.substring(unmatchedFrom), false));
        }
        return segments;
    }

    /** Longest first: where one identifier begins another, the whole of the longer one is marked. */
    private Pattern anyIdentifier() {
        return Pattern.compile(filter.identifiers().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|")));
    }
}
