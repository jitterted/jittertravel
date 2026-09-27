package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Whether a conference is live, folded from the authoritative event stream (R1 in
 * {@code EventSourcingRulesHeuristics.md}): its own {@link ConferencePlanned} while it has been
 * planned and neither cancelled by the organizers nor declined by Ted, and nothing after either.
 * <p>
 * Every conference command refuses a conference that is not live, and each used to fold this
 * itself — five copies of one rule, so the next "conference is gone" event would have reached some
 * and not others. The plan is kept rather than a yes/no because two of the commands need facts off
 * it too: the format, and the zone its dates were stamped in.
 * <p>
 * A conference a rejection dropped is still live here. It has left the calendars, but the talk
 * pipeline is where that happened and a later move on it is still Ted's to record.
 */
final class LiveConferencePlan {
    private final ConferenceId conferenceId;

    LiveConferencePlan(ConferenceId conferenceId) {
        this.conferenceId = conferenceId;
    }

    /** The plan in force once {@code events} have happened; empty if it was never planned or is gone. */
    Optional<ConferencePlanned> in(Stream<StoredEvent> events) {
        return Optional.ofNullable(events.map(StoredEvent::payload)
                                         .reduce((ConferencePlanned) null,
                                                 this::after,
                                                 (first, second) -> second));
    }

    /**
     * One step of the fold, for a service that folds other facts in the same pass over the stream.
     * {@code current} is null while there is no live plan.
     */
    ConferencePlanned after(ConferencePlanned current, Object event) {
        return switch (event) {
            case ConferencePlanned e when e.conferenceId().equals(conferenceId) -> e;
            case ConferenceCancelled e when e.conferenceId().equals(conferenceId) -> null;
            case ConferenceAttendanceDeclined e when e.conferenceId().equals(conferenceId) -> null;
            default -> current;
        };
    }
}
