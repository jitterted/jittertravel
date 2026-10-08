package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceDatesChanged;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.List;
import java.util.Optional;

/**
 * What a conference stands as, folded from its own events: the conference as planned (with any
 * date change applied), where {@link ConferenceProgress} says he stands, and, once he has stopped
 * going, which of three ways it ended. This is what family are told about, so the answer to "what
 * do family now believe should be true?" is {@link #fact()}.
 * <p>
 * <strong>{@code ConferenceCancelled} is folded separately from the progress</strong>, because it
 * hard-removes the conference from every read model and is not a {@link ConferenceProgress}
 * transition. A cancelled conference is "not going" however committed he was.
 * <p>
 * <strong>The exit is where he <em>stopped</em> going</strong>, not the latest exit-shaped event:
 * a rejection recorded after he had already declined changes nothing family were or will be told.
 * Read the stream, never a projector (R1).
 */
record ConferenceStanding(ConferencePlanned planned, ConferenceProgress progress,
                          AttendanceBasis basis, boolean cancelled, ConferenceNews.Exit exit) {

    /** An explicit loop: the order of events decides, since the last decision wins. */
    static Optional<ConferenceStanding> foldOf(List<StoredEvent> history, ConferenceId conference) {
        ConferenceStanding standing = null;
        for (StoredEvent stored : history) {
            if (stored.payload() instanceof ConferencePlanned planned
                    && planned.conferenceId().equals(conference)) {
                standing = new ConferenceStanding(planned, ConferenceProgress.planned(planned.format()),
                        null, false, null);
            } else if (standing != null) {
                standing = standing.apply(stored.payload(), conference);
            }
        }
        return Optional.ofNullable(standing);
    }

    private ConferenceStanding apply(Object event, ConferenceId conference) {
        return switch (event) {
            case ConferenceDatesChanged changed when changed.conferenceId().equals(conference) ->
                    new ConferenceStanding(new ConferencePlanned(planned.conferenceId(), planned.name(),
                            changed.startDate(), changed.endDate(), planned.venueName(),
                            planned.venueAddress(), planned.format(), planned.infoUrl()),
                            progress, basis, cancelled, exit);
            case TalkSubmitted submitted when submitted.conferenceId().equals(conference) ->
                    withProgress(progress.submitted(), null);
            case TalkAccepted accepted when accepted.conferenceId().equals(conference) ->
                    withProgress(progress.accepted(), null);
            case TalkRejected rejected when rejected.conferenceId().equals(conference) ->
                    withProgress(progress.rejected(), ConferenceNews.Exit.REJECTED);
            case TalkWithdrawn withdrawn when withdrawn.conferenceId().equals(conference) ->
                    withProgress(progress.withdrawn(), null);
            case InvitedToSpeak invited when invited.conferenceId().equals(conference) ->
                    withProgress(progress.invited(), null);
            case ConferenceAttendanceConfirmed confirmed when confirmed.conferenceId().equals(conference) ->
                    new ConferenceStanding(planned, progress.confirmed(confirmed.basis()),
                            confirmed.basis(), cancelled, exit);
            case ConferenceAttendanceDeclined declined when declined.conferenceId().equals(conference) ->
                    withProgress(progress.declined(), ConferenceNews.Exit.DECLINED);
            case ConferenceCancelled cancelledEvent when cancelledEvent.conferenceId().equals(conference) ->
                    new ConferenceStanding(planned, progress, basis, true, ConferenceNews.Exit.CANCELLED);
            default -> this;
        };
    }

    /**
     * Takes the moved progress, and records {@code exitIfDropped} only on the move that drops the
     * conference: an event arriving after he already stopped going does not change how it ended.
     */
    private ConferenceStanding withProgress(ConferenceProgress moved, ConferenceNews.Exit exitIfDropped) {
        boolean newlyDropped = !progress.dropped() && moved.dropped();
        return new ConferenceStanding(planned, moved, basis, cancelled,
                newlyDropped && !cancelled ? exitIfDropped : exit);
    }

    /**
     * What family are to believe: going, not going, or nothing at all. Merely watching a conference
     * is never news, however many talks are submitted for it.
     */
    Optional<NotifiedFact> fact() {
        if (cancelled || progress.dropped()) {
            return Optional.of(NotifiedFact.CONFERENCE_NOT_GOING);
        }
        return switch (progress.commitment()) {
            case GOING -> Optional.of(NotifiedFact.CONFERENCE_GOING);
            case WATCHING -> Optional.empty();
            case NOT_GOING -> Optional.of(NotifiedFact.CONFERENCE_NOT_GOING);
        };
    }

    /**
     * How the conference ended, for choosing the sentence. Only meaningful when {@link #fact()} is
     * {@code CONFERENCE_NOT_GOING}.
     */
    ConferenceNews.Exit endedBy() {
        return exit;
    }

    ConferenceNews news() {
        return new ConferenceNews(planned.name(), planned.venueName(), planned.venueAddress().city(),
                new CityLabel().qualifier(planned.venueAddress()), planned.startDate(), planned.endDate(),
                planned.infoUrl(), speakingLine());
    }

    /**
     * Two sources, and only one is an {@link AttendanceBasis}: an accepted talk is in the stream with
     * no confirmation at all, while a conference recorded before those events existed speaks only
     * through its confirmation's basis. A ticket bought, with no talk, says nothing.
     */
    private ConferenceNews.SpeakingLine speakingLine() {
        if (progress.speakingStatus() == SpeakingStatus.ACCEPTED) {
            return ConferenceNews.SpeakingLine.TALK_ACCEPTED;
        }
        if (!progress.speaking()) {
            return ConferenceNews.SpeakingLine.NONE;
        }
        return basis == AttendanceBasis.SPEAKING_ACCEPTED
                ? ConferenceNews.SpeakingLine.TALK_ACCEPTED
                : ConferenceNews.SpeakingLine.INVITED;
    }
}
