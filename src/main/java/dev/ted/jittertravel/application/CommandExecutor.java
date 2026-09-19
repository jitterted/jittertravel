package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class CommandExecutor {
    private final PostgresPersister persister;
    private final EventStore eventStore;

    public CommandExecutor(PostgresPersister persister, EventStore eventStore) {
        this.persister = persister;
        this.eventStore = eventStore;
    }

    /**
     * Runs a domain command, writing the command log ahead of the events (R4).
     * <p>
     * <strong>The logged payload is the {@link DomainCommand}, not the form request.</strong> The
     * command record carries the resolved, typed subject — an id parsed from the path, a value
     * object built at the boundary — so the row names what was acted on <em>by construction</em>.
     * Serializing the web-layer request instead is what shipped the 2026-09-18 defect, where a
     * bean whose id had only a record-style accessor logged
     * {@code {"locationForMatching":"..."}} and a FAILED row named no target; that was patched
     * with a duplicate getter and a convention test policing all fourteen form beans, and both are
     * now gone because a form bean can no longer reach this method's payload at all.
     * <p>
     * The write-ahead property is untouched: {@code command} is fully constructed by the caller
     * before this runs, so it is available to log before {@link DomainCommand#execute} is
     * attempted, and a domain failure afterwards still marks the row FAILED with the payload in
     * place. What it no longer records is raw text that failed to <em>parse</em> — but that never
     * reached here, because a controller cannot build the command without parsing first.
     * <p>
     * {@code request} is still taken, and still used by {@link #refuseWhenReadOnly}: its
     * {@code toString} is what names the attempted write in a {@link ReadOnlyModeException}, and
     * the form's own words are more use in that message than the resolved command's.
     */
    public <C extends DecisionContext> void execute(UUID commandId, Object request, C context,
                                                    DomainCommand<C> command) {
        refuseWhenReadOnly(request);
        persister.saveCommand(commandId, command); // write-ahead: command persisted as PENDING

        List<? extends Event> events;
        try {
            events = command.execute(context).toList();
        } catch (RuntimeException domainException) {
            persister.markCommandFailed(commandId, "FAILED_DOMAIN", domainException.getMessage());
            throw domainException;
        }

        appendOrMarkFailed(commandId, events);
    }

    public boolean isReadOnly() {
        return eventStore.isReadOnly();
    }

    /**
     * The authoritative event stream, for a service to fold the decision facts a command needs
     * (R4 step 3). Named for that single purpose: R1 forbids deciding from a projection, but
     * application services cannot take an {@link EventStore} of their own — that is the rule
     * {@code ApplicationServicesUseCommandExecutorTest} enforces — so the read arrives through the
     * one class already authorized to hold it.
     * <p>
     * Not for building views. Projectors subscribe to the store and are replayed by
     * {@code EventSourcingConfig}; nothing on the read path should come through here.
     */
    public Stream<StoredEvent> eventsForDecision() {
        return eventStore.findAll();
    }

    public void appendEvents(UUID commandId, Object commandRecord, Stream<? extends Event> events) {
        refuseWhenReadOnly(commandRecord);
        var eventList = events.toList();
        persister.saveCommand(commandId, commandRecord); // write-ahead: command persisted as PENDING
        appendOrMarkFailed(commandId, eventList);
    }

    /**
     * Read-only mode can engage while the database is still writable — startup replay can fail for
     * a data reason — so nothing downstream stops a write on its own. Refusing here, before the
     * write-ahead {@code saveCommand}, is what makes the guarantee "no command row is written in
     * read-only mode" hold for every caller, including imports, instead of depending on each
     * controller remembering to check {@link #isReadOnly()} first.
     */
    private void refuseWhenReadOnly(Object request) {
        if (eventStore.isReadOnly()) {
            throw new ReadOnlyModeException(
                    "Attempting to execute request while in read-only mode:" + request);
        }
    }

    private void appendOrMarkFailed(UUID commandId, List<? extends Event> events) {
        try {
            // appendEvents flips the command's status to SUCCEEDED in the same transaction
            eventStore.append(events.stream(), commandId);
        } catch (RuntimeException persistException) {
            // Best-effort: if persistence failed because the database is unreachable,
            // this update may also fail and the command row stays PENDING.
            try {
                persister.markCommandFailed(commandId, "FAILED_PERSIST", persistException.getMessage());
            } catch (RuntimeException ignored) {
                // EventStore has already flipped to read-only; nothing more to do here.
            }
            throw persistException;
        }
    }
}
