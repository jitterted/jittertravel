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
 * Adding a field here is a per-kind change: it breaks the one branch of
 * {@link CalendarEntryRedactor} that builds this record, rather than all seven. That is redaction
 * rule 1 made sharper — the forcing function still fires, but it names the kind whose decision is
 * missing.
 */
public sealed interface EntryDetails {

    EntryKind kind();

    /**
     * A conference. {@code commitment} is the collapsed, publishable attendance level — every
     * speculative state has already become {@link AttendanceCommitment#WATCHING} in
     * {@link ConferenceCalendarProjector}, and the private {@code AttendanceBasis} never reaches a
     * calendar entry at all. See {@code docs/ConferenceSubmissionTrackingPlan.md}.
     */
    record Conference(AttendanceCommitment commitment) implements EntryDetails {
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
     * {@code publicRoute} is <strong>never rendered</strong>. It carries the publishable form of
     * the route ({@code DEN → Lone Tree, CO, US}) purely so {@link CalendarEntryRedactor} has
     * something true to publish, since it cannot derive a city from the owner's title — which
     * names a hotel.
     * <p>
     * {@code cancelPath}, not an edit path: a transfer has nothing to edit, so correcting one
     * means removing it and entering it again.
     */
    record GroundTransfer(String publicRoute, String cancelPath) implements EntryDetails {
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
}
