package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Projects the conference events into pre-formatted {@link CalendarEntry} views ready for the
 * calendar swimlane renderer.
 * <p>
 * Both axes are folded here rather than in a second projector, since neither is a separate source
 * of conferences: a {@link ConferencePlanned} lands as {@link AttendanceCommitment#WATCHING}, and
 * everything after it rewrites that same entry. The rules for how the two axes move — and how they
 * touch — live in {@link ConferenceProgress}, shared with the other two conference read models
 * because they are rules rather than rendering.
 * <p>
 * <strong>The collapse to publishable values happens here.</strong> Only the commitment level and
 * the speaking flag reach the {@link CalendarEntry}; the event's
 * {@link dev.ted.jittertravel.domain.AttendanceBasis} — why Ted is going, which is submission
 * status in disguise — and where his talk stands are read and discarded. A field that never enters
 * the view cannot leak from it (CLAUDE.md redaction rule 1). See
 * {@code docs/archived/ConferenceSubmissionTrackingPlan.md}.
 * <p>
 * <strong>A dropped conference leaves the calendar entirely</strong> — whether Ted declined it or
 * a rejection dropped it at a conference where acceptance was the way in. This is where that
 * differs from {@link ConferenceProjector}, which keeps the row as a record.
 */
public class ConferenceCalendarProjector implements EventStreamConsumer {
    private final Map<ConferenceId, Tracked> entries = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case ConferencePlanned event -> {
                    String infoUrl = event.infoUrl().isBlank() ? null : event.infoUrl();
                    String location = event.venueAddress().city() + ", " + event.venueAddress().country();
                    List<SubtitleLine> locationLines = List.of(new SubtitleLine.Text(location));
                    // Calendar days are venue-local days (decision 7): bucket by the wall-clock
                    // the traveler will read off a clock at the venue, not by UTC.
                    entries.put(event.conferenceId(), new Tracked(new CalendarEntry(
                            event.startDate().localDateTime(),
                            event.endDate().localDateTime(),
                            event.name(),
                            locationLines,
                            event.name() + " cont'd",
                            locationLines,
                            // Planning a conference is putting it on the watch list, nothing more:
                            // it is speculative until an attendance confirmation says otherwise,
                            // and it records no speaking evidence either way.
                            new EntryDetails.Conference(AttendanceCommitment.WATCHING, false, infoUrl)
                    ), ConferenceProgress.planned(event.format()), infoUrl));
                }
                // Ted is going. `event.basis()` is read only to answer whether he speaks, and is
                // never carried onto the entry — see the class comment.
                case ConferenceAttendanceConfirmed event ->
                        update(event.conferenceId(), progress -> progress.confirmed(event.basis()));
                case ConferenceCancelled event -> entries.remove(event.conferenceId());
                case ConferenceAttendanceDeclined event ->
                        update(event.conferenceId(), ConferenceProgress::declined);

                // The speaking axis. Only its consequences reach the entry: an acceptance commits
                // attendance and means Ted speaks, a rejection can drop the conference — but that
                // a talk was submitted, turned down or pulled is submission status, and no calendar
                // entry says so.
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
     * Moves the conference along both axes and rebuilds the entry — or removes it, if the move
     * dropped the conference. An event for a conference not on the calendar is a no-op.
     */
    private void update(ConferenceId conferenceId, UnaryOperator<ConferenceProgress> change) {
        entries.computeIfPresent(conferenceId, (id, tracked) -> {
            ConferenceProgress moved = change.apply(tracked.progress());
            return moved.dropped() ? null : tracked.showing(moved);
        });
    }

    public List<CalendarEntry> entries() {
        return entries.values().stream()
                .map(Tracked::entry)
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }

    /**
     * One conference's calendar entry, and where that conference stands on both axes.
     * {@link ConferenceProgress} holds what the entry may not — the format, where the talk stands,
     * and whether the last confirmation named a speaking basis — so none of it can reach a view.
     * <p>
     * {@code infoUrl} rides here rather than being read back off the entry every rebuild: it moves
     * with neither axis, and null when there is none.
     */
    private record Tracked(CalendarEntry entry, ConferenceProgress progress, String infoUrl) {

        Tracked showing(ConferenceProgress moved) {
            return new Tracked(new CalendarEntry(
                    entry.start(), entry.end(),
                    entry.mainTitle(), entry.subTitle(),
                    entry.continuationTitle(), entry.continuationSubTitle(),
                    new EntryDetails.Conference(moved.commitment(), speakingBadge(moved), infoUrl)
            ), moved, infoUrl);
        }

        /**
         * The badge is gated on commitment: speaking evidence can exist before Ted has answered —
         * an invitation he has not taken up — and marking a "Maybe" entry as a talk would publish
         * that he was asked. See {@link EntryDetails.PublicConference}.
         */
        private boolean speakingBadge(ConferenceProgress progress) {
            return progress.commitment() == AttendanceCommitment.GOING && progress.speaking();
        }
    }
}
