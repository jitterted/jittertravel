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
                                        event.format()),
                                false));
                // Recording a CFP twice is how a moved deadline is corrected, so this overwrites
                // rather than ignoring the second one — the last recorded deadline wins.
                case CfpOpened event ->
                        update(event.conferenceId(), tracked -> tracked.withCfpClosing(event.closesOn()));
                // The basis is collapsed to a boolean here and then dropped: whether Ted speaks is
                // rendered, but *which* speaking basis applies — accepted, or invited — is
                // submission status, so it never reaches the view at all. It is kept beside the
                // view rather than on it, so a renderer cannot reach it even by accident.
                case ConferenceAttendanceConfirmed event ->
                        update(event.conferenceId(),
                               tracked -> tracked.confirmed(speaking(event.basis())));
                // The organizers pulled the event: there is no conference left to have a view of.
                case ConferenceCancelled event -> conferences.remove(event.conferenceId());
                // Ted said no. The row stays, at NOT_GOING, hidden behind ?dropped=show.
                case ConferenceAttendanceDeclined event ->
                        update(event.conferenceId(),
                               tracked -> tracked.at(AttendanceCommitment.NOT_GOING));

                case TalkSubmitted event ->
                        update(event.conferenceId(), tracked -> tracked.moveTo(SpeakingStatus.SUBMITTED));
                // Accepted, so going: the auto-commit.
                case TalkAccepted event ->
                        update(event.conferenceId(), tracked -> tracked.moveTo(SpeakingStatus.ACCEPTED)
                                                                       .at(AttendanceCommitment.GOING));
                // Turned down. Whether that costs the conference depends on its format.
                case TalkRejected event ->
                        update(event.conferenceId(), Tracked::rejected);
                // Pulling a talk says nothing about attending: commitment is left exactly as it was.
                case TalkWithdrawn event ->
                        update(event.conferenceId(), tracked -> tracked.moveTo(SpeakingStatus.WITHDRAWN));
                // An offer, so it commits nothing until Ted confirms.
                case InvitedToSpeak event ->
                        update(event.conferenceId(), tracked -> tracked.moveTo(SpeakingStatus.INVITED));
                default -> {}
            }
        });
    }

    /** Every fold arm goes through here, so an event for a conference that is gone is a no-op. */
    private void update(ConferenceId conferenceId, UnaryOperator<Tracked> change) {
        conferences.computeIfPresent(conferenceId, (id, tracked) -> change.apply(tracked));
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
                .map(Tracked::view)
                .filter(view -> timeView.includes(view, now))
                .filter(view -> droppedView.includes(view.commitment()))
                .sorted(Comparator.comparing(view -> view.startDate().utc()))
                .toList();
    }

    public Optional<ConferenceView> findById(ConferenceId conferenceId) {
        return Optional.ofNullable(conferences.get(conferenceId)).map(Tracked::view);
    }

    /**
     * One conference's view plus the one fact the view may not carry: whether the last attendance
     * confirmation named a speaking basis.
     * <p>
     * <strong>Why it is kept out here.</strong> "Which basis" is submission status wearing a
     * different hat, and CLAUDE.md's redaction rule is that a field which never enters a view
     * cannot leak from it. The boolean is needed to answer whether Ted speaks at a conference he
     * was <em>invited</em> to — he does only if he accepted the invitation rather than merely
     * buying a ticket — but that answer belongs on the view, not its ingredients.
     */
    private record Tracked(ConferenceView view, boolean confirmationNamedSpeaking) {

        Tracked confirmed(boolean basisIsSpeaking) {
            return new Tracked(view, basisIsSpeaking).at(AttendanceCommitment.GOING);
        }

        Tracked moveTo(SpeakingStatus status) {
            return rebuild(view.commitment(), status);
        }

        Tracked at(AttendanceCommitment commitment) {
            return rebuild(commitment, view.speakingStatus());
        }

        /**
         * A rejection always moves the speaking axis; it moves the attendance axis only where
         * acceptance was the way in. The format is the conference's own, from
         * {@code ConferencePlanned}, so the branch cannot disagree with how the conference was
         * entered.
         */
        Tracked rejected() {
            return view.format() == ConferenceFormat.ACCEPTANCE_REQUIRED
                    ? rebuild(AttendanceCommitment.NOT_GOING, SpeakingStatus.REJECTED)
                    : rebuild(view.commitment(), SpeakingStatus.REJECTED);
        }

        Tracked withCfpClosing(ZonedTimestamp closesOn) {
            return new Tracked(new ConferenceView(
                    view.conferenceId(), view.name(), view.venueName(), view.venueAddress(),
                    view.startDate(), view.endDate(), view.commitment(), view.speaking(),
                    view.speakingStatus(), closesOn, view.format()
            ), confirmationNamedSpeaking);
        }

        private Tracked rebuild(AttendanceCommitment commitment, SpeakingStatus status) {
            return new Tracked(new ConferenceView(
                    view.conferenceId(), view.name(), view.venueName(), view.venueAddress(),
                    view.startDate(), view.endDate(), commitment, speaks(commitment, status),
                    status, view.cfpClosesOn(), view.format()
            ), confirmationNamedSpeaking);
        }

        /**
         * Whether Ted speaks, recomputed from scratch every time either axis moves — so the answer
         * cannot drift away from the facts it is derived from.
         * <p>
         * <strong>The stream wins wherever it has spoken.</strong> Exhaustive, so a new
         * {@link SpeakingStatus} cannot be added without deciding whether it counts as speaking.
         */
        private boolean speaks(AttendanceCommitment commitment, SpeakingStatus status) {
            return switch (status) {
                // The talk is in the program. Nothing else is consulted, and no confirmation is
                // needed — being accepted is what made him GOING in the first place.
                case ACCEPTED -> true;
                // The stream has spoken, and it said no talk: waiting to hear, turned down, or
                // pulled. A basis claiming otherwise is a stale manual annotation and loses.
                case SUBMITTED, REJECTED, WITHDRAWN -> false;
                // An offer he has taken up. Going on a ticket after an invitation is attending,
                // not speaking, so the basis is what separates the two.
                case INVITED -> commitment == AttendanceCommitment.GOING && confirmationNamedSpeaking;
                // The stream is silent, which is every conference recorded before these events
                // existed. Here the basis is the only evidence there is.
                case NOT_SPEAKING -> commitment == AttendanceCommitment.GOING && confirmationNamedSpeaking;
            };
        }
    }
}
