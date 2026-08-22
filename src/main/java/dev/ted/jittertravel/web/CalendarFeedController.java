package dev.ted.jittertravel.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Serves the private iCalendar feed of hotel cancel-deadline reminders (and a probe endpoint for
 * on-device testing). The feed is deliberately <b>unredacted OWNER data</b> — it is NOT run through
 * {@code PublicCalendarProjector}. The URL token is therefore the <b>only</b> credential; treat it
 * like a password.
 * <ul>
 *   <li>Token is a configured secret ({@code jittertravel.calendar-feed.token}). <b>Absent ⇒ the
 *       feed is disabled and every request 404s</b> — a safe, opt-in default.</li>
 *   <li>Comparison is constant-time ({@link MessageDigest#isEqual}). An unknown / missing / disabled
 *       token returns <b>404 with an empty body</b> — never a single VEVENT, never confirmation that
 *       the endpoint exists.</li>
 *   <li>The token authenticates, not the login session (the iOS Calendar app cannot do a form
 *       login), so {@code SecurityConfig} permits {@code /calendar/feed/**} and this controller does
 *       the gating.</li>
 * </ul>
 * {@code now} is captured here at the boundary and passed inward (external-inputs rule).
 */
@Controller
public class CalendarFeedController {

    private static final MediaType TEXT_CALENDAR =
            new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final CalendarFeedAssembler assembler;
    private final ICalWriter icalWriter;
    private final Clock clock;
    private final String configuredToken;

    public CalendarFeedController(CalendarFeedAssembler assembler, ICalWriter icalWriter, Clock clock,
                                  @Value("${jittertravel.calendar-feed.token:}") String configuredToken) {
        this.assembler = assembler;
        this.icalWriter = icalWriter;
        this.clock = clock;
        this.configuredToken = configuredToken;
    }

    @GetMapping("/calendar/feed/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        if (!tokenValid(token)) {
            return notFound();
        }
        Instant now = clock.instant();
        return ok(icalWriter.write("JitterTravel deadlines", now, assembler.feed(now)));
    }

    @GetMapping("/calendar/feed/{token}/probe.ics")
    public ResponseEntity<String> probe(@PathVariable String token) {
        if (!tokenValid(token)) {
            return notFound();
        }
        Instant now = clock.instant();
        return ok(icalWriter.write("JitterTravel test", now, List.of(assembler.probeEvent(now))));
    }

    private boolean tokenValid(String provided) {
        if (configuredToken == null || configuredToken.isBlank()) {
            return false;
        }
        byte[] expected = configuredToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private ResponseEntity<String> ok(String body) {
        return ResponseEntity.ok().contentType(TEXT_CALENDAR).body(body);
    }

    private ResponseEntity<String> notFound() {
        return ResponseEntity.notFound().build();
    }
}
