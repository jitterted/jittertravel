package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Projects the conference events into the OWNER-only {@code /conferences} dashboard.
 * <p>
 * <strong>This is where the two axes meet.</strong> Attendance commitment and speaking status are
 * independent facts folded from independent events, and they touch at exactly the three points the
 * plan names:
 * <ul>
 *   <li>{@link TalkAccepted} <em>commits</em> attendance — {@link AttendanceCommitment#GOING} with
 *       no {@link ConferenceAttendanceConfirmed} anywhere, because submitting was already the
 *       opt-in.</li>
 *   <li>{@link TalkRejected} <em>drops</em> a conference whose {@link ConferenceFormat} is
 *       {@code ACCEPTANCE_REQUIRED}: acceptance gated attendance, so there is no going anyway. For
 *       a {@code CALL_FOR_PAPERS} conference the same event leaves it merely watched, with a
 *       decision to make.</li>
 *   <li>{@link InvitedToSpeak} commits nothing. An invitation is an offer, and Ted saying yes is a
 *       confirmation carrying {@link AttendanceBasis#SPEAKING_INVITED}.</li>
 * </ul>
 * All three are folds, not extra events — so they replay, and they reverse if the event that
 * produced them is ever superseded.
 * <p>
 * It differs from {@link ConferenceCalendarProjector} on one case: a conference Ted
 * <em>declined</em> stays here at {@link AttendanceCommitment#NOT_GOING}, because this is the
 * surface where "looked at it, said no" is worth keeping; {@link DroppedView} hides those rows
 * unless asked for. An organizer cancellation still removes it — there is no conference left.
 */
public class ConferenceProjector implements EventStreamConsumer {

    private final Map<ConferenceId, Tracked> conferences = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case ConferencePlanned event -> conferences.put(event.conferenceId(),
                        new Tracked(
                                new ConferenceView(
                                        event.conferenceId(),
                                        event.name(),
                                        event.venueName(),
                                        event.venueAddress(),
                                        event.startDate(),
                                        event.endDate(),
                                        AttendanceCommitment.WATCHING,
                                        // Planning a conference records no speaking evidence either
                                        // way, and says nothing about whether its CFP has opened.
                                        false,
                                        SpeakingStatus.NOT_SPEAKING,
                                        null,
                                        "",
                                        event.format(),
                                        event.infoUrl()),
                                ConferenceProgress.planned(event.format())));
                // Recording a CFP twice is how a moved deadline is corrected, so this overwrites
                // rather than ignoring the second one — the last recorded CFP wins, submission URL
                // included: the two are one fact, and a re-record replaces both together.
                case CfpOpened event -> conferences.computeIfPresent(event.conferenceId(),
                        (id, tracked) -> tracked.withCfp(event.closesOn(), event.submissionUrl()));
                // The basis is read and immediately collapsed: whether Ted speaks is rendered, but
                // *which* speaking basis applies — accepted, or invited — is submission status, so
                // it stays inside ConferenceProgress and never reaches the view at all.
                case ConferenceAttendanceConfirmed event ->
                        update(event.conferenceId(), progress -> progress.confirmed(event.basis()));
                // The organizers pulled the event: there is no conference left to have a view of.
                case ConferenceCancelled event -> conferences.remove(event.conferenceId());
                // Ted said no. The row stays, at NOT_GOING, hidden behind ?dropped=show.
                case ConferenceAttendanceDeclined event ->
                        update(event.conferenceId(), ConferenceProgress::declined);

                case TalkSubmitted event -> update(event.conferenceId(), ConferenceProgress::submitted);
                case TalkAccepted event -> update(event.conferenceId(), ConferenceProgress::accepted);
                case TalkRejected event -> update(event.conferenceId(), ConferenceProgress::rejected);
                case TalkWithdrawn event -> update(event.conferenceId(), ConferenceProgress::withdrawn);
                case InvitedToSpeak event -> update(event.conferenceId(), ConferenceProgress::invited);
                default -> {}
            }
        });
    }

    /**
     * Every fold arm goes through here: it moves the conference along both axes and rebuilds the
     * view from the result, so the view can never drift from the facts it is derived from. An event
     * for a conference that is not here — never planned, or cancelled — is a no-op.
     */
    private void update(ConferenceId conferenceId, UnaryOperator<ConferenceProgress> change) {
        conferences.computeIfPresent(conferenceId,
                (id, tracked) -> tracked.showing(change.apply(tracked.progress())));
    }

    /**
     * The dashboard's rows, under both of its independent filters: when
     * ({@link TimeView}, the shared FUTURE/ALL convention) and whether Ted is going
     * ({@link DroppedView}). Two parameters rather than one, because they ask unrelated questions —
     * see {@link DroppedView}.
     */
    public List<ConferenceView> views(TimeView timeView, DroppedView droppedView, Instant now) {
        return conferences.values().stream()
                .map(Tracked::view)
                .filter(view -> timeView.includes(view, now))
                .filter(view -> droppedView.includes(view.commitment()))
                .sorted(Comparator.comparing(view -> view.startDate().utc()))
                .toList();
    }

    /**
     * How many conferences {@link DroppedView#HIDE} is leaving out, under the time filter in force.
     * <p>
     * The dashboard's toolbar needs this <em>while they are hidden</em>: its switch reads
     * "Show dropped <em>n</em>" in both states, reporting what the page is currently showing rather
     * than what a click would do, so the number cannot be counted from the rows that rendered.
     * Filtered by {@link TimeView} for the same reason the rows are — the switch says how many the
     * <em>other</em> filter is holding back, not how many exist.
     */
    public int droppedCount(TimeView timeView, Instant now) {
        return (int) conferences.values().stream()
                .map(Tracked::view)
                .filter(view -> timeView.includes(view, now))
                .filter(view -> view.commitment() == AttendanceCommitment.NOT_GOING)
                .count();
    }

    public Optional<ConferenceView> findById(ConferenceId conferenceId) {
        return Optional.ofNullable(conferences.get(conferenceId)).map(Tracked::view);
    }

    /**
     * One conference's row, and where that conference stands on both axes.
     * <p>
     * {@link ConferenceProgress} holds one fact the view may not: whether the last attendance
     * confirmation named a speaking basis. "Which basis" is submission status wearing a different
     * hat, and CLAUDE.md's redaction rule is that a field which never enters a view cannot leak
     * from it — so it stays here, and only its consequence reaches the row.
     */
    private record Tracked(ConferenceView view, ConferenceProgress progress) {

        /** The row as this progress makes it: both derived fields recomputed from scratch. */
        Tracked showing(ConferenceProgress moved) {
            return new Tracked(new ConferenceView(
                    view.conferenceId(), view.name(), view.venueName(), view.venueAddress(),
                    view.startDate(), view.endDate(), moved.commitment(), moved.speaking(),
                    moved.speakingStatus(), view.cfpClosesOn(), view.cfpSubmissionUrl(),
                    view.format(), view.infoUrl()
            ), moved);
        }

        /**
         * A CFP says nothing about either axis: recording one and committing are independent facts
         * about the same conference, so each carries the other's value through untouched.
         */
        Tracked withCfp(ZonedTimestamp closesOn, String submissionUrl) {
            return new Tracked(new ConferenceView(
                    view.conferenceId(), view.name(), view.venueName(), view.venueAddress(),
                    view.startDate(), view.endDate(), view.commitment(), view.speaking(),
                    view.speakingStatus(), closesOn, submissionUrl,
                    view.format(), view.infoUrl()
            ), progress);
        }
    }
}
