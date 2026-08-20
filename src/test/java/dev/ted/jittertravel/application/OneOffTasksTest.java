package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.OneOffTaskCompleted;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registry is declared with test tasks rather than the real ones: the shipped list changes
 * every time a task is added or a done one is deleted, and no test should break for that.
 */
class OneOffTasksTest {

    private static final Instant COMPLETED_ON = Instant.parse("2026-08-20T14:00:00Z");

    private static final OneOffTask MIGRATION = new OneOffTask(
            "run-the-migration", "Run the migration", "Rewrites rows; back up first.",
            "/admin/migrate-legacy-events", "Open the migration page", LocalDate.of(2026, 8, 19));

    private static final OneOffTask BACKFILL = new OneOffTask(
            "backfill-attendance", "Backfill attendance", "Confirm each conference by hand.",
            "/conferences", "Open the conference list", LocalDate.of(2026, 8, 25));

    @Test
    void aDeclaredTaskWithNoCompletionIsOutstanding() {
        OneOffTasks tasks = tasksWith(List.of(MIGRATION), new OneOffTaskProjector());

        assertThat(tasks.outstanding())
                .extracting(OneOffTaskView::id)
                .containsExactly("run-the-migration");
    }

    @Test
    void aCompletedTaskDropsOutOfOutstandingButStaysInTheFullList() {
        OneOffTasks tasks = tasksWith(List.of(MIGRATION), projectorWith("run-the-migration"));

        assertThat(tasks.outstanding())
                .as("the banner counts this, so it must be empty once the job is done")
                .isEmpty();
        assertThat(tasks.views())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.completed()).as("shown greyed on /admin/tasks").isTrue();
                    assertThat(view.completedOn()).isEqualTo(COMPLETED_ON);
                });
    }

    @Test
    void outstandingTasksComeFirstAndOldestDeclarationLeads() {
        OneOffTasks tasks = tasksWith(List.of(BACKFILL, MIGRATION), projectorWith("run-the-migration"));

        assertThat(tasks.views())
                .extracting(OneOffTaskView::id)
                .containsExactly("backfill-attendance", "run-the-migration");
    }

    @Test
    void completingATaskAppendsTheCompletionEvent() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        OneOffTasks tasks = tasksWith(List.of(MIGRATION), new OneOffTaskProjector(), executor);

        boolean recorded = tasks.complete(UUID.randomUUID(), "run-the-migration", COMPLETED_ON);

        assertThat(recorded).as("a real tick-off").isTrue();
        assertThat(executor.appended)
                .singleElement()
                .isEqualTo(new OneOffTaskCompleted("run-the-migration", COMPLETED_ON));
    }

    @Test
    void completingAnAlreadyCompletedTaskWritesNothing() {
        // The latch is one-way: a double submit or a re-opened tab has nothing to add.
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        OneOffTasks tasks = tasksWith(List.of(MIGRATION), projectorWith("run-the-migration"), executor);

        boolean recorded = tasks.complete(UUID.randomUUID(), "run-the-migration", COMPLETED_ON);

        assertThat(recorded).isFalse();
        assertThat(executor.appended).isEmpty();
    }

    @Test
    void completingAnUndeclaredTaskWritesNothing() {
        // Only a hand-edited URL can ask for this, and inventing a completion for an id no code
        // declares would put a fact in the log that nothing can explain.
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        OneOffTasks tasks = tasksWith(List.of(MIGRATION), new OneOffTaskProjector(), executor);

        boolean recorded = tasks.complete(UUID.randomUUID(), "not-a-declared-task", COMPLETED_ON);

        assertThat(recorded).isFalse();
        assertThat(executor.appended).isEmpty();
    }

    private static OneOffTaskProjector projectorWith(String completedTaskId) {
        OneOffTaskProjector projector = new OneOffTaskProjector();
        OneOffTaskCompleted completed = new OneOffTaskCompleted(completedTaskId, COMPLETED_ON);
        projector.handle(Stream.of(new StoredEvent(
                1, completed.getClass(), UUID.randomUUID(), Instant.now(), completed, UUID.randomUUID())));
        return projector;
    }

    private static OneOffTasks tasksWith(List<OneOffTask> declared, OneOffTaskProjector projector) {
        return tasksWith(declared, projector, new RecordingCommandExecutor());
    }

    private static OneOffTasks tasksWith(List<OneOffTask> declared, OneOffTaskProjector projector,
                                         RecordingCommandExecutor executor) {
        return new OneOffTasks(new OneOffTaskRegistry(declared), projector, executor);
    }

    private static final class RecordingCommandExecutor extends CommandExecutor {
        private final List<Event> appended = new ArrayList<>();

        RecordingCommandExecutor() {
            super(null, null);
        }

        @Override
        public void appendEvents(UUID commandId, Object commandRecord, Stream<? extends Event> events) {
            appended.addAll(events.toList());
        }
    }
}
