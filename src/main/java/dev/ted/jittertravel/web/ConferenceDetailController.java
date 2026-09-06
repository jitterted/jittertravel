package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferenceDetailView;
import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.domain.ConferenceId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One conference in full, at {@code /conferences/{id}}.
 * <p>
 * <strong>Read-only, and that is a departure from the house pattern.</strong> Everywhere else in
 * this app {@code /{collection}/{id}} <em>is</em> the change form — hotels, flights, trains and
 * gatherings each own the GET and the POST at that path. Conferences have no change form yet, so
 * here it is a detail page instead; read that as the direction the pattern is going rather than as
 * an exception to it (Ted, 2026-09-04: *"was never happy about reusing edit pages as a substitute
 * for a real details page"*). {@code docs/ConferenceDetailAndChangePlan.md} D1.
 * <p>
 * <strong>No new security matcher.</strong> {@code /conferences/*} is already
 * {@code hasRole("OWNER")} in {@code SecurityConfig} — a single {@code *} matches exactly this one
 * segment — which is precisely why the {@code AuthorizationMatrixTest} row for it matters: nothing
 * else would notice if that pattern changed, and this page prints the CFP, the submission pipeline
 * and why Ted is going, all of which CLAUDE.md keeps private.
 * <p>
 * A missing or malformed conference redirects to {@code /conferences}, matching every other
 * conference route: the list cannot render a flash, so it goes back silently.
 */
@Controller
public class ConferenceDetailController {

    private final ConferenceProjector projector;
    private final Clock clock;

    public ConferenceDetailController(ConferenceProjector projector, Clock clock) {
        this.projector = projector;
        this.clock = clock;
    }

    @GetMapping("/conferences/{conferenceId}")
    public ResponseEntity<String> conferenceDetail(
            @PathVariable("conferenceId") String conferenceIdString) {
        Optional<ConferenceDetailView> maybe = lookup(conferenceIdString);
        if (maybe.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                                 .header(HttpHeaders.LOCATION, "/conferences")
                                 .build();
        }
        // The only clock this page reads, and it is captured here rather than inside the renderer:
        // how many days are left on an open CFP (CLAUDE.md, "Time comes from the injected Clock").
        return ResponseEntity.ok()
                             .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                             .body(ConferenceDetailRenderer.render(maybe.get(), Instant.now(clock)));
    }

    /**
     * The {@code try} wraps the parse and <em>nothing else</em>. Widening it to cover the lookup
     * would swallow any {@code IllegalArgumentException} thrown while building the view — a
     * {@code ZonedTimestamp} with no zone, a future component validation — into the same silent
     * redirect a typo'd id gets, so a real data problem would read as "no such conference" with
     * nothing in the log.
     */
    private Optional<ConferenceDetailView> lookup(String conferenceIdString) {
        ConferenceId conferenceId;
        try {
            conferenceId = ConferenceId.of(UUID.fromString(conferenceIdString));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
        return projector.detailById(conferenceId);
    }
}
