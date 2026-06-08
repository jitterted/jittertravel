package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;

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

    public <C extends DecisionContext> void execute(UUID commandId, Object request, C context, DomainCommand<C> command) {
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

    public void appendEvents(UUID commandId, Object commandRecord, Stream<? extends Event> events) {
        var eventList = events.toList();
        persister.saveCommand(commandId, commandRecord); // write-ahead: command persisted as PENDING
        appendOrMarkFailed(commandId, eventList);
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
