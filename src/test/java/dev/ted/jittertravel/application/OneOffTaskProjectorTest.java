package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.OneOffTaskCompleted;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OneOffTaskProjectorTest {

    private static final Instant COMPLETED_ON = Instant.parse("2026-08-20T14:00:00Z");

    @Test
    void unknownTaskHasNoCompletion() {
        OneOffTaskProjector projector = new OneOffTaskProjector();

        assertThat(projector.completedOn("never-heard-of-it"))
                .isEmpty();
    }

    @Test
    void completedTaskCarriesTheInstantItWasTickedOff() {
        OneOffTaskProjector projector = new OneOffTaskProjector();

        projector.handle(Stream.of(stored(1, new OneOffTaskCompleted("run-the-migration", COMPLETED_ON))));

        assertThat(projector.completedOn("run-the-migration"))
                .contains(COMPLETED_ON);
    }

    @Test
    void oneTasksCompletionSaysNothingAboutAnother() {
        OneOffTaskProjector projector = new OneOffTaskProjector();

        projector.handle(Stream.of(stored(1, new OneOffTaskCompleted("run-the-migration", COMPLETED_ON))));

        assertThat(projector.completedOn("backfill-something-else"))
                .isEmpty();
    }

    @Test
    void theFirstCompletionWinsSoAReplayCannotMoveTheDate() {
        // The latch is one-way. A second completion for the same id — a re-applied restore, a
        // double submit that slipped past the service — must not rewrite when it happened.
        OneOffTaskProjector projector = new OneOffTaskProjector();

        projector.handle(Stream.of(
                stored(1, new OneOffTaskCompleted("run-the-migration", COMPLETED_ON)),
                stored(2, new OneOffTaskCompleted("run-the-migration",
                        Instant.parse("2026-09-01T09:00:00Z")))));

        assertThat(projector.completedOn("run-the-migration"))
                .contains(COMPLETED_ON);
    }

    @Test
    void completionsSurviveForTasksNobodyDeclaresAnyMore() {
        // The normal end state: the declaration is deleted once the job is done, and the event
        // outlives it. The projector must not care that the registry has moved on.
        OneOffTaskProjector projector = new OneOffTaskProjector();

        projector.handle(Stream.of(stored(1, new OneOffTaskCompleted("retired-long-ago", COMPLETED_ON))));

        assertThat(projector.completedOn("retired-long-ago"))
                .contains(COMPLETED_ON);
    }

    private static StoredEvent stored(long sequence, OneOffTaskCompleted payload) {
        return new StoredEvent(sequence, payload.getClass(), UUID.randomUUID(),
                Instant.now(), payload, UUID.randomUUID());
    }
}
