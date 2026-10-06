package dev.ted.jittertravel.web;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the last preview send of each {@link EmailGroup} until the app restarts, so the preview
 * page can say "3 emails sent at 3:42 PM" after a reload. One memory per group, so sending the
 * conference emails leaves the flight result where it was. In memory only, for the reason
 * {@link FamilyTestMemory} gives: a preview writes no event and no row. A bean rather than a field
 * so a test gets a fresh one or a mock.
 */
@Component
public class EmailPreviewMemory {

    /** How many of a group's emails went out, who they went to, and why it stopped if it did. */
    public record Result(Instant at, int sent, int of, String address, String failure) {

        public boolean succeeded() {
            return failure.isEmpty();
        }
    }

    private final Map<EmailGroup, Result> last = new ConcurrentHashMap<>();

    public void remember(EmailGroup group, Result result) {
        last.put(group, result);
    }

    public Optional<Result> last(EmailGroup group) {
        return Optional.ofNullable(last.get(group));
    }
}
