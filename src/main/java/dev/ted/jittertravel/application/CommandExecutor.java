package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;

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
        var events = command.execute(context).toList();
        persister.saveCommand(commandId, request);
        eventStore.append(events.stream(), commandId);
    }

    public void appendEvents(UUID commandId, Object commandRecord, Stream<? extends Event> events) {
        var eventList = events.toList();
        persister.saveCommand(commandId, commandRecord);
        eventStore.append(eventList.stream(), commandId);
    }
}
