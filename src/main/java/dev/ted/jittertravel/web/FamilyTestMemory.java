package dev.ted.jittertravel.web;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Remembers the last test email until the app restarts (Ted, 2026-10-05), so the setup checklist can
 * say "sent at 3:42 PM" after a reload. Deliberately in memory only: a test writes no event and no
 * row, because a probe subject in the log, and in every backup, forever, for a message that was never
 * about travel, is exactly what the probe was designed not to do.
 * <p>
 * A bean rather than a field on the controller so that tests get a fresh one (or a mock) instead of
 * inheriting whatever the previous test left in a shared singleton.
 */
@Component
public class FamilyTestMemory {

    public record Test(Instant at, boolean succeeded, String failure) {
    }

    private final AtomicReference<Test> last = new AtomicReference<>();

    public void succeeded(Instant at) {
        last.set(new Test(at, true, ""));
    }

    public void failed(Instant at, String failure) {
        last.set(new Test(at, false, failure));
    }

    public Optional<Test> last() {
        return Optional.ofNullable(last.get());
    }
}
