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

    public <C extends DecisionContext> void execute(UUID commandId, Object request, C context,
                                                    DomainCommand<C> command) {
        refuseWhenReadOnly(request);
        persister.saveCommand(commandId, request); // write-ahead: command persisted as PENDING

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
