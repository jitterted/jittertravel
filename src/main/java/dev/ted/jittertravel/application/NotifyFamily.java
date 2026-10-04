package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FamilyNotified;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.NotifiedSubject;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.FamilyMessage;
import dev.ted.jittertravel.infrastructure.FamilyNotificationMessages;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Tells family something, once. The decision is folded from the event stream (R1): the most recent
 * {@link FamilyNotified} for the subject says what family currently believe, and a notification
 * that would only repeat it is no news.
 * <p>
 * <strong>Three things happen before a command exists, and all three write nothing.</strong> A
 * notifier that is switched off or has no key or recipient must not litter {@code command_log} with
 * rows for mail it never meant to send; and a fact family already have is "nothing to say", not a
 * failure, which would otherwise flood the failed-command surface with noise. The last is safe
 * outside the command because reactors run on one thread, so there is no concurrent second trigger
 * for one subject.
 * <p>
 * Then the send itself goes through {@link CommandExecutor#executeExternalAction}: the intent is
 * written first, the email is the work, and {@link FamilyNotified} is appended only once Brevo
 * accepted it. A failed send leaves a FAILED_SEND row and no event. Never takes an
 * {@code EventStore}, and the switch arrives as a plain {@code boolean} so nothing here imports
 * Spring.
 * <p>
 * The id and the time come from the caller: the boundary captures them and passes them inward.
 */
public class NotifyFamily {

    /** What happened to a request, so the caller can count it without this class knowing about metrics. */
    public enum Outcome { SENT, NOT_ENABLED, NOT_CONFIGURED, ALREADY_TOLD }

    private final CommandExecutor commandExecutor;
    private final BrevoEmailClient brevo;
    private final FamilyNotificationMessages messages;
    private final boolean enabled;

    public NotifyFamily(CommandExecutor commandExecutor, BrevoEmailClient brevo,
                        FamilyNotificationMessages messages, boolean enabled) {
        this.commandExecutor = commandExecutor;
        this.brevo = brevo;
        this.messages = messages;
        this.enabled = enabled;
    }

    public Outcome notifyFamily(UUID commandId, NotifiedSubject subject, NotifiedFact fact,
                                List<FlightBooked> legs, Instant now) {
        if (!enabled) {
            return Outcome.NOT_ENABLED;
        }
        if (!brevo.configured()) {
            return Outcome.NOT_CONFIGURED;
        }
        if (lastToldAbout(subject).filter(fact::equals).isPresent()) {
            return Outcome.ALREADY_TOLD;
        }
        FamilyMessage message = messages.messageFor(fact, legs);
        commandExecutor.executeExternalAction(commandId, new NotifyFamilyCommand(subject, fact, now), () -> {
            brevo.send(message);
            return Stream.of(new FamilyNotified(subject, fact, now));
        });
        return Outcome.SENT;
    }

    /** An explicit loop: what family believe now is the <em>last</em> thing they were told. */
    private Optional<NotifiedFact> lastToldAbout(NotifiedSubject subject) {
        NotifiedFact last = null;
        for (StoredEvent stored : commandExecutor.eventsForDecision().toList()) {
            if (stored.payload() instanceof FamilyNotified told && told.subject().equals(subject)) {
                last = told.fact();
            }
        }
        return Optional.ofNullable(last);
    }
}
