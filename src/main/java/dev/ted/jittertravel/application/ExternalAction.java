package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Event;

import java.util.stream.Stream;

/**
 * The work of a command that has to reach outside the system — an email, a webhook — and report
 * what happened as events. Run by {@link CommandExecutor#executeExternalAction}, between the
 * write-ahead of the command and the append of its events.
 * <p>
 * <strong>Not in {@code domain}, on purpose.</strong> A {@code DomainCommand} is pure and may not
 * do I/O ({@code DomainIsPureTest}); this is the one place an action is allowed to, which is why
 * the type lives here. Throw to fail the command: the executor records {@code FAILED_SEND} and
 * appends nothing.
 */
@FunctionalInterface
public interface ExternalAction {
    Stream<? extends Event> perform();
}
