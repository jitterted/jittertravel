package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PlanTentativeConferenceCommand;
import dev.ted.jittertravel.infrastructure.InMemoryEventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.web.PlanTentativeConferenceRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public class ConferencePlanning {
    private final InMemoryEventStore eventStore;
    private final PostgresPersister persister;

    public ConferencePlanning(InMemoryEventStore eventStore, PostgresPersister persister) {
        this.eventStore = eventStore;
        this.persister = persister;
    }

    public void planConference(PlanTentativeConferenceRequest request) {
        if (eventStore.isReadOnly()) {
            throw new IllegalStateException("System is in read-only mode.");
        }

        UUID commandId = UUID.fromString(request.getConferenceId());
        persister.saveCommand(commandId, request);

        PlanTentativeConferenceCommand domainCommand = new PlanTentativeConferenceCommand();
        var events = domainCommand.execute(request, LocalDateTime.now());

        eventStore.append(events, commandId);
    }

    public boolean isReadOnly() {
        return eventStore.isReadOnly();
    }
}
