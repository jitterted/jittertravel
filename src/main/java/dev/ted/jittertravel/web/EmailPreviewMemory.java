package dev.ted.jittertravel.web;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Remembers the last preview send until the app restarts, so the preview page can say "3 emails sent
 * at 3:42 PM" after a reload. In memory only, for the reason {@link FamilyTestMemory} gives: a preview
 * writes no event and no row. A bean rather than a field so a test gets a fresh one or a mock.
 */
@Component
public class EmailPreviewMemory {

    /** How many of the three went out, who they went to, and why it stopped if it did. */
    public record Result(Instant at, int sent, int of, String address, String failure) {

        public boolean succeeded() {
            return failure.isEmpty();
        }
    }

    private final AtomicReference<Result> last = new AtomicReference<>();

    public void remember(Result result) {
        last.set(result);
    }

    public Optional<Result> last() {
        return Optional.ofNullable(last.get());
    }
}
