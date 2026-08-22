package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects the conference events into the OWNER-only {@code /conferences} list.
 * <p>
 * Folds attendance commitment nearly the way {@link ConferenceCalendarProjector} does — planned
 * means {@link AttendanceCommitment#WATCHING}, confirmed means {@link AttendanceCommitment#GOING},
 * organizer-cancelled means gone — and differs on the one case that matters here: a conference Ted
 * <em>declined</em> stays, at {@link AttendanceCommitment#NOT_GOING}, because this is the surface
 * where "looked at it, said no" is worth keeping. {@link DroppedView} hides those rows unless asked
 * for. The two folds are deliberately written twice rather than shared: each builds a different
 * view record, and now they do not even agree on what a decline means.
 */
public class ConferenceProjector implements EventStreamConsumer {
    private final Map<ConferenceId, ConferenceView> conferences = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case ConferencePlanned event -> conferences.put(event.conferenceId(),
                        new ConferenceView(
                                event.conferenceId(),
                                event.name(),
                                event.venueName(),
                                event.venueAddress(),
                                event.startDate(),
                                event.endDate(),
                                AttendanceCommitment.WATCHING,
                                // Planning a conference records no speaking evidence either way,
                                // and says nothing about whether its CFP has opened.
                                false,
                                null,
                                event.format()
                        ));
                // Recording a CFP twice is how a moved deadline is corrected, so this overwrites
                // rather than ignoring the second one — the last recorded deadline wins.
                case CfpOpened event ->
                        conferences.computeIfPresent(event.conferenceId(),
                                (id, view) -> withCfpClosing(view, event.closesOn()));
                // The basis is collapsed to a boolean here and then dropped. Whether Ted speaks is
                // rendered; *which* speaking basis applies — accepted, or invited — is submission
                // status, so it never reaches the view at all rather than being carried and hidden.
                case ConferenceAttendanceConfirmed event ->
                        conferences.computeIfPresent(event.conferenceId(),
                                (id, view) -> at(view, AttendanceCommitment.GOING, speaking(event.basis())));
                // The organizers pulled the event: there is no conference left to have a view of,
                // so it goes. Contrast the decline below — that one is Ted's own answer, and the
                // answer is worth keeping.
                case ConferenceCancelled event -> conferences.remove(event.conferenceId());
                // Ted said no. The row stays, at NOT_GOING, and the dashboard hides it behind
                // ?dropped=show: "looked at it, said no" is a record next year's entry benefits
                // from. Every other read model still drops the conference entirely.
                case ConferenceAttendanceDeclined event ->
                        conferences.computeIfPresent(event.conferenceId(),
                                (id, view) -> at(view, AttendanceCommitment.NOT_GOING, view.speaking()));
                default -> {}
            }
        });
    }

    private ConferenceView at(ConferenceView view, AttendanceCommitment commitment, boolean speaking) {
        return new ConferenceView(
                view.conferenceId(), view.name(), view.venueName(), view.venueAddress(),
                view.startDate(), view.endDate(), commitment, speaking,
                view.cfpClosesOn(), view.format()
        );
    }

    /**
     * A CFP deadline says nothing about attendance: confirming and recording a CFP are independent
     * facts about the same conference, so each carries the other's value through untouched.
     */
    private ConferenceView withCfpClosing(ConferenceView view, ZonedTimestamp closesOn) {
        return new ConferenceView(
                view.conferenceId(), view.name(), view.venueName(), view.venueAddress(),
                view.startDate(), view.endDate(), view.commitment(), view.speaking(),
                closesOn, view.format()
        );
    }

    /**
     * The partition {@link AttendanceBasis}'s three values were chosen for: two speaking bases and
     * one that is not. Exhaustive, so a fourth basis cannot be added without deciding which side of
     * the line it falls on.
     */
    private boolean speaking(AttendanceBasis basis) {
        return switch (basis) {
            case SPEAKING_ACCEPTED, SPEAKING_INVITED -> true;
            case TICKET_PURCHASED -> false;
        };
    }

    /**
     * The dashboard's rows, under both of its independent filters: when
     * ({@link TimeView}, the shared FUTURE/ALL convention) and whether Ted is going
     * ({@link DroppedView}). Two parameters rather than one, because they ask unrelated questions —
     * see {@link DroppedView}.
     */
    public List<ConferenceView> views(TimeView timeView, DroppedView droppedView, Instant now) {
        return conferences.values().stream()
                .filter(view -> timeView.includes(view, now))
                .filter(view -> droppedView.includes(view.commitment()))
                .sorted(Comparator.comparing(view -> view.startDate().utc()))
                .toList();
    }

    public Optional<ConferenceView> findById(ConferenceId conferenceId) {
        return Optional.ofNullable(conferences.get(conferenceId));
    }
}
