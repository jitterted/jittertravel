package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.LocationZoneResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Sessionize call-for-speakers page and turns it into values for {@code /plan-conference}.
 * Modelled on {@link AddressParseService}, and for the same reasons: one external host, a
 * descriptive {@code User-Agent}, everything swallowed into an {@link Optional}, and never a thrown
 * exception — see {@code docs/archived/SessionizePrefillPlan.md}.
 *
 * <p><strong>It is a prefill, so it is allowed to be wrong.</strong> Nothing is written until Ted
 * submits a form he can see and edit, which is the licence to guess at a start time the page does
 * not state. Two things it must still never do: invent a value that is not on the page (absent stays
 * absent), and produce a value that is wrong in a way he would not notice — a mangled name or a
 * shifted deadline, as against an obviously blank field.
 *
 * <p><strong>Every field is independently optional.</strong> One pattern per field, and a pattern
 * that does not match yields {@code ""} rather than aborting the prefill or shifting another field's
 * result. That isolation is the whole reason a Sessionize restyle degrades into blanks instead of
 * into wrong values, and it is the invariant most worth keeping.
 *
 * <p><strong>Two sources, and only one of them can rot.</strong> The deadline comes from the
 * {@code .ics} Sessionize serves for its own "add to calendar" button, where it is an exact UTC
 * instant; everything else is scraped out of the page's HTML with the JDK's own regex (Ted,
 * 2026-08-22: no new dependency for this). The anchors are Bootstrap class names and English label
 * text, so they can change without notice.
 */
@Service
public class SessionizePrefillService {

    private static final Logger log = LoggerFactory.getLogger(SessionizePrefillService.class);

    /**
     * The one shape of URL this will fetch. A fixed {@code baseUrl} plus a slug that can only be
     * letters, digits and hyphens means a pasted string cannot steer the request anywhere — this
     * is the SSRF gate, and it is why a non-Sessionize URL is refused without a request being made.
     */
    private static final Pattern SESSIONIZE_URL =
            Pattern.compile("^https?://(?:www\\.)?sessionize\\.com/([A-Za-z0-9][A-Za-z0-9-]{0,99})/?$");

    /** A pathological response cannot be parsed at all. The real page is ~35 KB. */
    private static final int MAX_BODY = 512 * 1024;

    private static final DateTimeFormatter ICS_INSTANT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));

    /**
     * Explicitly {@code ENGLISH}, never the default locale — the server's environment could change
     * that out from under us. The page is inconsistent in a way {@code d} already absorbs: event
     * dates render unpadded ({@code 8 Feb 2027}), CFP dates padded ({@code 01 Oct 2026}).
     */
    private static final DateTimeFormatter PAGE_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final DateTimeFormatter FORM_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /**
     * A conference day, near enough. The page carries dates with no time of day at all, and a
     * plausible wrong time in a field Ted is already reading beats one he has to fill from scratch —
     * the widget says in so many words that these two are guesses, which is what makes them safe.
     */
    private static final String GUESSED_START_TIME = "T09:00";
    private static final String GUESSED_END_TIME = "T17:00";

    private final RestClient restClient;
    private final LocationZoneResolver zoneResolver;

    public SessionizePrefillService(RestClient.Builder restClientBuilder,
                                    LocationZoneResolver zoneResolver) {
        this.zoneResolver = zoneResolver;
        this.restClient = restClientBuilder
                .baseUrl("https://sessionize.com")
                .defaultHeader("User-Agent", "JitterTravel/1.0 (travel scheduling app)")
                .defaultHeader("Accept-Language", "en")
                .build();
    }

    /**
     * The form's own property names, so the widget writes each straight across by {@code name}.
     * Absent is {@code ""} throughout, never null.
     *
     * @param cfpSubmissionUrl the normalized pasted URL — free, since Sessionize <em>is</em> where
     *                         the talk gets submitted. Filled only when {@code cfpClosesOn} was:
     *                         a submission URL with no deadline is refused at submit
     *                         ({@code CfpDeadlineMissing}), and the prefill is the thing most
     *                         likely to produce that pair.
     * @param deadlineZone     the zone {@code cfpClosesOn} is expressed in, for the widget to say
     *                         out loud. Never written into a form field — the form derives the
     *                         conference's zone from the venue, and this only names it.
     */
    public record SessionizePrefill(
            String name,
            String infoUrl,
            String startDate,
            String endDate,
            String cfpClosesOn,
            String cfpSubmissionUrl,
            String venueName,
            String venueCity,
            String venueCountry,
            String deadlineZone
    ) {}

    public Optional<SessionizePrefill> prefill(String rawUrl) {
        String slug = slugFrom(rawUrl);
        if (slug.isEmpty()) {
            return Optional.empty();
        }
        return assemble(slug, fetch("/add-to-calendar/cfs/" + slug), fetch("/" + slug + "/"));
    }

    /**
     * The slug, or {@code ""} for anything this will not fetch. Query strings and fragments are
     * dropped rather than refused, so a URL carrying tracking parameters still works and still
     * lands tidy in {@code cfpSubmissionUrl}.
     */
    String slugFrom(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String trimmed = rawUrl.trim();
        int cut = indexOfFirst(trimmed, '?', '#');
        Matcher matcher = SESSIONIZE_URL.matcher(cut < 0 ? trimmed : trimmed.substring(0, cut));
        return matcher.matches() ? matcher.group(1) : "";
    }

    /**
     * Everything the two documents say, with no I/O — the seam the parsing tests drive.
     * Empty only when neither document yielded anything at all, which the controller reports as
     * "we couldn't read that page". A page that yielded even one field is a partial fill, which
     * under best-effort is an ordinary outcome rather than a failure.
     */
    Optional<SessionizePrefill> assemble(String slug, String ics, String html) {
        String name = conferenceName(html, ics);
        String venueName = "";
        String city = "";
        String country = "";
        List<String> venueLines = venueLines(html);
        if (!venueLines.isEmpty()) {
            venueName = venueLines.getFirst();
        }
        if (venueLines.size() > 1) {
            String[] cityCountry = splitCityCountry(venueLines.get(1));
            city = cityCountry[0];
            country = cityCountry[1];
        }

        String deadlineZone = "";
        String cfpClosesOn = "";
        Instant deadline = cfpDeadline(ics);
        ZoneId venueZone = venueZone(city, country);
        if (deadline != null && venueZone != null) {
            deadlineZone = venueZone.getId();
            cfpClosesOn = FORM_DATE_TIME.format(deadline.atZone(venueZone));
        }

        SessionizePrefill prefill = new SessionizePrefill(
                name,
                websiteUrl(html),
                startOfDay(labelledDate(html, "event starts"), GUESSED_START_TIME),
                startOfDay(labelledDate(html, "event ends"), GUESSED_END_TIME),
                cfpClosesOn,
                cfpClosesOn.isEmpty() ? "" : "https://sessionize.com/" + slug + "/",
                venueName,
                city,
                country,
                deadlineZone);
        return isEmpty(prefill) ? Optional.empty() : Optional.of(prefill);
    }

    /**
     * The venue zone, or null when the location does not resolve — in which case the deadline is
     * <strong>not</strong> filled.
     * <p>
     * This is the one field where blank beats a defensible guess. {@code cfpClosesOn} is read at
     * submit as a wall clock in the conference's own zone, so writing the deadline in any other zone
     * silently shifts it — the jfokus deadline is {@code 06:30Z}, which is 08:30 in Stockholm, and
     * two hours early is exactly the kind of wrong Ted would not catch by looking. A blank field he
     * fills himself is a papercut; a plausible wrong deadline is a missed CFP.
     */
    private ZoneId venueZone(String city, String country) {
        if (city.isEmpty() && country.isEmpty()) {
            return null;
        }
        try {
            return zoneResolver.resolve(city, country);
        } catch (RuntimeException e) {
            // Unresolvable is ordinary: the form will re-prompt for a zone at submit anyway.
            return null;
        }
    }

    /**
     * {@code og:title} first — it is the page's own name for itself — with the {@code .ics}
     * {@code SUMMARY} as the fallback for a restyled page, since the two agree today and the
     * calendar file is the half that cannot rot.
     */
    private String conferenceName(String html, String ics) {
        String fromPage = stripSuffix(attribute(html, "og:title"), ": Call for Speakers");
        return fromPage.isEmpty()
                ? stripSuffix(icsProperty(ics, "SUMMARY"), ": deadline to submit a session")
                : fromPage;
    }

    /**
     * The CFP deadline as the exact instant Sessionize publishes it, or null.
     * <p>
     * Unfolding comes first: RFC 5545 continues a long line by starting the next one with a space,
     * and a reader that skips it will one day read a folded {@code DTSTART} as a truncated one. The
     * {@code VALARM} is cut off before anything is read, because it carries a {@code SUMMARY} of its
     * own and "the first SUMMARY" should not one day become the alarm's.
     */
    private Instant cfpDeadline(String ics) {
        String value = icsProperty(ics, "DTSTART");
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Instant.from(ICS_INSTANT.parse(value));
        } catch (RuntimeException e) {
            log.warn("Sessionize .ics carried an unreadable DTSTART: {}", value);
            return null;
        }
    }

    private String icsProperty(String ics, String property) {
        if (ics == null) {
            return "";
        }
        String unfolded = ics.replace("\r\n", "\n").replace("\n ", "");
        int alarm = unfolded.indexOf("BEGIN:VALARM");
        String vevent = alarm < 0 ? unfolded : unfolded.substring(0, alarm);
        Matcher matcher = Pattern.compile("^" + property + ":(.*)$", Pattern.MULTILINE)
                                 .matcher(vevent);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /**
     * The value under an English label — {@code event starts}, {@code event ends}, {@code location},
     * {@code website} — taken as the next {@code <h2>} after it.
     * <p>
     * Anchored on the label rather than a position, because the labels are the semantic anchors and
     * column order is not: "the third h2 on the page" would be one restyle from reading the wrong
     * value, which is worse than reading none.
     */
    private String labelledBlock(String html, String label) {
        if (html == null) {
            return "";
        }
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "\\s*</div>\\s*<h2[^>]*>(.*?)</h2>",
                                          Pattern.DOTALL)
                                 .matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String labelledDate(String html, String label) {
        return normalize(stripTags(labelledBlock(html, label)));
    }

    /** Venue name, then "City, Country" — the two {@code block} spans of the location block. */
    private List<String> venueLines(String html) {
        Matcher matcher = Pattern.compile("<span class=\"block\"[^>]*>(.*?)</span>", Pattern.DOTALL)
                                 .matcher(labelledBlock(html, "location"));
        List<String> lines = new ArrayList<>();
        while (matcher.find()) {
            String line = normalize(stripTags(matcher.group(1)));
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * City and country out of one line. A middle part (a state) is dropped rather than guessed into
     * {@code venueState}: the zone resolves from city and country, and inventing a region is the
     * kind of fiction this must not do.
     */
    private String[] splitCityCountry(String line) {
        String[] parts = line.split(",");
        if (parts.length == 1) {
            return new String[]{parts[0].trim(), ""};
        }
        return new String[]{parts[0].trim(), parts[parts.length - 1].trim()};
    }

    private String websiteUrl(String html) {
        Matcher matcher = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>", Pattern.DOTALL)
                                 .matcher(labelledBlock(html, "website"));
        return matcher.find() ? decodeEntities(matcher.group(1)) : "";
    }

    private String attribute(String html, String property) {
        if (html == null) {
            return "";
        }
        Matcher matcher = Pattern
                .compile("<meta[^>]*property=\"" + Pattern.quote(property) + "\"[^>]*content=\"([^\"]*)\"")
                .matcher(html);
        return matcher.find() ? decodeEntities(matcher.group(1)) : "";
    }

    /** A date with the guessed time of day appended, or {@code ""} when the date did not parse. */
    private String startOfDay(String date, String guessedTime) {
        if (date.isEmpty()) {
            return "";
        }
        try {
            return LocalDate.parse(date, PAGE_DATE) + guessedTime;
        } catch (RuntimeException e) {
            log.warn("Sessionize page carried an unreadable date: {}", date);
            return "";
        }
    }

    private String fetch(String path) {
        try {
            String body = restClient.get()
                                    .uri(path)
                                    .retrieve()
                                    .body(String.class);
            if (body == null) {
                return null;
            }
            return body.length() > MAX_BODY ? body.substring(0, MAX_BODY) : body;
        } catch (Exception e) {
            log.warn("Sessionize fetch failed for {}", path, e);
            return null;
        }
    }

    /**
     * Entities are decoded because {@code og:title} is an <em>attribute</em>: a conference called
     * "Devoxx &amp; Friends" would otherwise be recorded as {@code Devoxx &amp;amp; Friends}, and
     * unlike a blank field that lands in an event, where it is permanent. {@code &amp;} is decoded
     * <strong>last</strong>, or {@code &amp;lt;} would turn into {@code <}.
     */
    private String decodeEntities(String raw) {
        String decoded = raw.replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&quot;", "\"")
                            .replace("&#39;", "'")
                            .replace("&apos;", "'")
                            .replace("&nbsp;", " ");
        Matcher numeric = Pattern.compile("&#(\\d{1,7});").matcher(decoded);
        StringBuilder out = new StringBuilder();
        while (numeric.find()) {
            numeric.appendReplacement(out,
                                      Matcher.quoteReplacement(
                                              Character.toString(Integer.parseInt(numeric.group(1)))));
        }
        numeric.appendTail(out);
        return out.toString().replace("&amp;", "&");
    }

    /** Tags are stripped from a captured region rather than matched: a regex decides no structure. */
    private String stripTags(String region) {
        return region.replaceAll("<[^>]+>", " ");
    }

    private String normalize(String text) {
        return decodeEntities(text).replaceAll("\\s+", " ").trim();
    }

    private String stripSuffix(String text, String suffix) {
        return text.endsWith(suffix) ? text.substring(0, text.length() - suffix.length()).trim() : text;
    }

    /**
     * The submission URL alone does not count: it comes from what Ted pasted rather than from
     * anything Sessionize answered, so a prefill carrying only that has read nothing.
     */
    private boolean isEmpty(SessionizePrefill p) {
        return p.name().isEmpty() && p.infoUrl().isEmpty()
               && p.startDate().isEmpty() && p.endDate().isEmpty()
               && p.cfpClosesOn().isEmpty()
               && p.venueName().isEmpty() && p.venueCity().isEmpty() && p.venueCountry().isEmpty();
    }

    private int indexOfFirst(String text, char... candidates) {
        int found = -1;
        for (char candidate : candidates) {
            int at = text.indexOf(candidate);
            if (at >= 0 && (found < 0 || at < found)) {
                found = at;
            }
        }
        return found;
    }
}
