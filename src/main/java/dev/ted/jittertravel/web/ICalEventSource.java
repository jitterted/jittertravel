package dev.ted.jittertravel.web;

import java.time.Instant;
import java.util.List;

/**
 * One contributor of events to the private calendar feed.
 * <p>
 * <strong>Deliberately not introduced until now.</strong> Phase 1 of the feed
 * ({@code docs/archived/CalendarSubscriptionFeedPlan.md}) had exactly one contributor — hotel
 * free-cancellation deadlines — and held this interface back under "no abstraction before the second
 * user". CFP closing deadlines are that second contributor, so it arrives here with two real
 * implementations rather than one speculative one.
 * <p>
 * A source is a <strong>pure projection</strong>: it reads a read model against the {@code now} it
 * is handed and returns events. Nothing is scheduled or emitted server-side — every alarm is fired
 * locally by the subscribed device — so a source must not write, and must not read a clock of its
 * own (the instant is captured at the controller boundary and passed inward).
 * <p>
 * <strong>Everything a source contributes is unredacted OWNER data.</strong> The feed is token-gated
 * and is never the public {@code /calendar}: it carries hotel names and CFP deadlines, both on
 * CLAUDE.md's private list. The URL is the only credential, so a new source widens what one leaked
 * URL exposes — which is a reason to weigh what goes in, not a reason to redact inside a source.
 */
public interface ICalEventSource {

    /**
     * The events this source contributes to the feed as of {@code now}. Deadlines already past are
     * left out by each source: a reminder that cannot fire is noise in the subscriber's calendar.
     */
    List<ICalEvent> events(Instant now);
}
