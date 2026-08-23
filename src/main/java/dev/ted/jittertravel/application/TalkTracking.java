package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AcceptTalkCommand;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.InviteToSpeakCommand;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.RejectTalkCommand;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.SubmitTalkCommand;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkPipelineContext;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.WithdrawTalkCommand;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.RecordTalkRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Records where Ted's talk stands with a conference — the whole speaking axis of
 * {@code docs/archived/ConferenceSubmissionTrackingPlan.md}.
 * <p>
 * <strong>One service for five moves, unlike the one-slice-one-service pattern elsewhere.</strong>
 * They are a single state machine over a single stream: they fold the identical facts and differ
 * only in which command they hand to the executor. Five services would be five copies of the fold
 * below, and the fold is the part that has to stay correct.
 * <p>
 * The fold is over the authoritative event stream rather than a projector (R1 in
 * {@code EventSourcingRulesHeuristics.md}), so a read model that has not caught up cannot let an
 * illegal transition through. commandId and the timestamp — the nondeterministic inputs — are
 * captured at the boundary and passed in; this service reads no clock and mints no ids.
 * <p>
 * Note what it does <em>not</em> do: nothing here commits or drops attendance. That an accepted
 * talk means Ted is going, and that a rejected one drops an {@code ACCEPTANCE_REQUIRED}
 * conference, are folds over these events in the read models — so they replay, and so they
 * reverse if the event is ever superseded.
 */
public class TalkTracking {
    private final CommandExecutor commandExecutor;

    public TalkTracking(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * @param recordedOn when Ted recorded this, captured at the boundary — not when the organizers
     *                   decided, which the app never knows.
     */
    public void record(UUID commandId, RecordTalkRequest request, Instant recordedOn) {
        ConferenceId conferenceId = ConferenceId.of(request.conferenceId());
        commandExecutor.execute(commandId, request, contextFor(conferenceId),
                                commandFor(request, conferenceId, recordedOn));
    }

    /**
     * Exhaustive over {@code TalkOutcome}, so a new move cannot be added to the form without
     * deciding which command records it.
     */
    private DomainCommand<TalkPipelineContext> commandFor(RecordTalkRequest request,
                                                         ConferenceId conferenceId,
                                                         Instant recordedOn) {
        return switch (request.outcome()) {
            case SUBMITTED -> new SubmitTalkCommand(conferenceId, recordedOn);
            case ACCEPTED -> new AcceptTalkCommand(conferenceId, recordedOn);
            case REJECTED -> new RejectTalkCommand(conferenceId, recordedOn);
            case WITHDRAWN -> new WithdrawTalkCommand(conferenceId, recordedOn);
            case INVITED -> new InviteToSpeakCommand(conferenceId, recordedOn);
        };
    }

    /**
     * One pass over the stream for all three facts. The conference's own {@link ConferencePlanned}
     * carries two of them — that it is live, and its format — so the fold keeps the event itself
     * and drops it when the conference is cancelled or declined.
     */
    private TalkPipelineContext contextFor(ConferenceId conferenceId) {
        Facts facts = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce(new Facts(null, SpeakingStatus.NOT_SPEAKING),
                        (current, event) -> current.apply(conferenceId, event),
                        (first, second) -> second);
        return new TalkPipelineContext(
                facts.planned() != null,
                facts.speakingStatus(),
                facts.planned() == null ? null : facts.planned().format());
    }

    /**
     * The accumulator: the live plan (null once gone) and where the talk stands.
     * <p>
     * <strong>The speaking status is the last submission event, not the best one</strong> — see
     * {@link SpeakingStatus} for why the plan's original "best outcome wins" was dropped along with
     * waitlisting. It is deliberately <em>not</em> cleared when a conference is cancelled or
     * declined: every command refuses on existence first, so it is never read in that state.
     */
    private record Facts(ConferencePlanned planned, SpeakingStatus speakingStatus) {

        private Facts apply(ConferenceId wanted, Object event) {
            return switch (event) {
                case ConferencePlanned e when e.conferenceId().equals(wanted) ->
                        new Facts(e, speakingStatus);
                case ConferenceCancelled e when e.conferenceId().equals(wanted) ->
                        new Facts(null, speakingStatus);
                case ConferenceAttendanceDeclined e when e.conferenceId().equals(wanted) ->
                        new Facts(null, speakingStatus);
                case TalkSubmitted e when e.conferenceId().equals(wanted) ->
                        moveTo(SpeakingStatus.SUBMITTED);
                case TalkAccepted e when e.conferenceId().equals(wanted) ->
                        moveTo(SpeakingStatus.ACCEPTED);
                case TalkRejected e when e.conferenceId().equals(wanted) ->
                        moveTo(SpeakingStatus.REJECTED);
                case TalkWithdrawn e when e.conferenceId().equals(wanted) ->
                        moveTo(SpeakingStatus.WITHDRAWN);
                case InvitedToSpeak e when e.conferenceId().equals(wanted) ->
                        moveTo(SpeakingStatus.INVITED);
                default -> this;
            };
        }

        private Facts moveTo(SpeakingStatus status) {
            return new Facts(planned, status);
        }
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }
}
