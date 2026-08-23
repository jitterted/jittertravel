package dev.ted.jittertravel.application;

/**
 * The kind-specific half of a {@link CalendarEntry}: everything that applies to some kinds only.
 * The entry itself keeps the fields every kind has — when it starts and ends, its titles, its
 * subtitle lines — and carries exactly one of these.
 * <p>
 * <strong>{@link #kind()} is derived, never stored.</strong> Each record answers with its own
 * constant, so the two hierarchies cannot drift: there is no way to build a
 * {@link Conference} that claims to be a {@link EntryKind#FLIGHT}. That is a condition of this
 * design, not an optional refinement — see decision E2 in
 * {@code docs/RendererVsProjectorResponsibilities.md} (2026-08-19).
 * <p>
 * The records fall into two families, and the split is the security boundary: the owner types below
 * carry Ted's edit paths and map links, while {@link Publishable} and its records carry only what an
 * anonymous visitor may see. {@link PublicCalendarProjector} builds nothing but the second kind.
 */
public sealed interface EntryDetails {

    EntryKind kind();

    /**
     * A conference. {@code commitment} is the collapsed, publishable attendance level — every
     * speculative state has already become {@link AttendanceCommitment#WATCHING} in
     * {@link ConferenceCalendarProjector}, and the private {@code AttendanceBasis} never reaches a
     * calendar entry at all. See {@code docs/ConferenceSubmissionTrackingPlan.md}.
     * <p>
     * {@code speaking} is public by decision, like a gathering's, but it is set only on a
     * conference Ted is committed to — see {@link PublicConference}, which explains why. The owner
     * and public calendars carry the same value here for the same reason they carry the same
     * commitment chip: one rendering path, one collapse, nothing for a renderer to get wrong.
     * <p>
     * {@code infoUrl} is the conference's own web page, which the renderer hangs off the title
     * exactly as it does a gathering's. Public, and carried identically on {@link PublicConference}
     * — the owner is not shown less than a stranger is. {@code null} when none was recorded.
     */
    record Conference(AttendanceCommitment commitment, boolean speaking, String infoUrl)
            implements EntryDetails {
        @Override
        public EntryKind kind() {
            return EntryKind.CONFERENCE;
        }
    }

    /**
     * A gathering — public in full, including {@code speaking}: that Ted speaks at one is public
     * by decision, since the venue and time already are. {@code infoUrl} is the event's own page,
     * which the renderer hangs off the title. {@code editPath} is OWNER-only.
     */
    record Gathering(String infoUrl, boolean speaking, String editPath) implements EntryDetails {
        @Override
        public EntryKind kind() {
            return EntryKind.GATHERING;
        }
    }

    /**
     * A private social event. It carries nothing kind-specific today: there is no edit flow yet
     * (see {@code docs/Cleanup_Tasks.md}, "Change Private Event"), and everything an anonymous
     * viewer may see — "Busy", the zone-labelled time, the city — is decided when the entry is
     * redacted, not stored here.
     */
    record PrivateEvent() implements EntryDetails {
        @Override
        public EntryKind kind() {
            return EntryKind.PRIVATE_EVENT;
        }
    }

    /** A booked flight leg. {@code editPath} is the OWNER-only link to its edit page. */
    record Flight(String editPath) implements EntryDetails {
        @Override
        public EntryKind kind() {
            return EntryKind.FLIGHT;
        }
    }

    /** A booked train trip. {@code editPath} is the OWNER-only link to its edit page. */
    record Train(String editPath) implements EntryDetails {
        @Override
        public EntryKind kind() {
            return EntryKind.TRAIN;
        }
    }

    /**
     * A ground transfer — the taxi or shuttle between two places.
     * <p>
     * {@code cancelPath}, not an edit path: a transfer has nothing to edit, so correcting one
     * means removing it and entering it again.
     */
    record GroundTransfer(String cancelPath) implements EntryDetails {
        @Override
        public EntryKind kind() {
            return EntryKind.GROUND_TRANSFER;
        }
    }

    /**
     * A hotel stay. {@code mapsUrl} points at the hotel and {@code editPath} at its booking page —
     * both private, both dropped for anonymous viewers.
     */
    record Lodging(String mapsUrl, String editPath) implements EntryDetails {
        @Override
        public EntryKind kind() {
            return EntryKind.LODGING;
        }
    }

    /**
     * The details an anonymous viewer may be shown. {@link PublicCalendarProjector} builds nothing
     * else — its one construction helper takes a {@code Publishable}, so the compiler refuses an
     * owner details type on the public calendar, and {@code PublicCalendarProjectorTest} asserts
     * the same at runtime in a test that never needs editing as kinds grow.
     * <p>
     * The point is what these records <em>cannot</em> hold. There is no slot for an edit path, a
     * cancel path, a maps URL or a hotel name, so a future change cannot fill one in by mistake.
     * That is the allow-list-as-code that replaced {@code CalendarEntryRedactor}'s deny-list.
     */
    sealed interface Publishable extends EntryDetails {}

    /**
     * The publishable form of every travel kind: entirely empty. What an anonymous viewer gets from
     * a flight, train, transfer or stay is the title and the day column, and both live on the entry
     * itself — there is nothing kind-specific left that may be shown.
     * <p>
     * Being a sealed interface rather than a {@code kind}-carrying record is what keeps
     * {@link #kind()} derived: each record below still answers with its own constant, so the two
     * hierarchies cannot drift, while the renderer's switches match all four in one arm.
     */
    sealed interface PublishableTravel extends Publishable {}

    /** A flight, publicly: the route in the title, nothing else. */
    record PublicFlight() implements PublishableTravel {
        @Override
        public EntryKind kind() {
            return EntryKind.FLIGHT;
        }
    }

    /** A train, publicly: the route in the title, nothing else. */
    record PublicTrain() implements PublishableTravel {
        @Override
        public EntryKind kind() {
            return EntryKind.TRAIN;
        }
    }

    /** A ground transfer, publicly: the generic word plus the route line, nothing else. */
    record PublicGroundTransfer() implements PublishableTravel {
        @Override
        public EntryKind kind() {
            return EntryKind.GROUND_TRANSFER;
        }
    }

    /** A hotel stay, publicly: the word "Hotel" and the city, never the name or the map link. */
    record PublicLodging() implements PublishableTravel {
        @Override
        public EntryKind kind() {
            return EntryKind.LODGING;
        }
    }

    /**
     * A conference, publicly. Name, venue, city and times are public by decision, so they ride on
     * the entry unchanged; {@code commitment} is publishable only because
     * {@link ConferenceCalendarProjector} has already collapsed every speculative state into
     * {@link AttendanceCommitment#WATCHING}. There is deliberately no {@code AttendanceBasis} here
     * and no place to put one.
     * <p>
     * <strong>{@code speaking} is set only on a conference Ted is committed to</strong>
     * (Ted, 2026-08-22), and that gate is a redaction rule, not a nicety. Speaking evidence can
     * exist before he has answered — an invitation he has not taken up — and a "Maybe" entry
     * wearing a speaking badge would tell a stranger he had been asked to speak somewhere he has
     * not decided about, which is the submission pipeline leaking one bit at a time. Gated on
     * commitment, the badge says only what the plan says is public: that Ted speaks at a
     * conference he is going to.
     * <p>
     * Note the badge is <em>not</em> the basis in disguise: "attending without speaking" is an
     * ordinary thing, so its absence reveals nothing (Ted, 2026-08-12).
     * <p>
     * <strong>{@code infoUrl} is the conference's own public page</strong> (2026-08-22), published
     * for the same reason its venue and times are: it is a public event, and CLAUDE.md lists a
     * conference's {@code infoUrl} among the things published in full. It comes off
     * {@code ConferencePlanned.infoUrl()} by name, never from an owner entry. {@code null} when
     * none was recorded, which is how a title with nowhere to point stays plain text.
     * <p>
     * <strong>Do not confuse it with the CFP submission URL</strong>, which is the opposite: a link
     * to Ted's talk-submission page says he is considering submitting, which is the pipeline this
     * whole record exists to keep out. That URL lives on {@code CfpOpened}, an event this projector
     * does not read at all.
     */
    record PublicConference(AttendanceCommitment commitment, boolean speaking, String infoUrl)
            implements Publishable {
        @Override
        public EntryKind kind() {
            return EntryKind.CONFERENCE;
        }
    }

    /** A gathering, publicly — which is a gathering in full, speaking marker included. */
    record PublicGathering(String infoUrl, boolean speaking) implements Publishable {
        @Override
        public EntryKind kind() {
            return EntryKind.GATHERING;
        }
    }

    /**
     * Ted is busy, and that is all anyone is told. The entry's title is the literal word, its
     * subtitle the city and a zone-labelled time; this record holds nothing.
     * <p>
     * <strong>Every private kind collapses to this one</strong> — the company-internal speaking
     * engagement that {@code docs/ConferenceSubmissionTrackingPlan.md} anticipates included. That
     * is not economy but redaction: giving a second private kind its own public lane would let a
     * stranger tell it apart from a dinner by lane alone.
     */
    record Busy() implements Publishable {
        @Override
        public EntryKind kind() {
            return EntryKind.PRIVATE_EVENT;
        }
    }
}
