package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PlanTentativeConferenceCommand;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.web.PlanTentativeConferenceRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public class ConferencePlanning {
    private final EventStore eventStore;
    private final PostgresPersister persister;

    public ConferencePlanning(EventStore eventStore, PostgresPersister persister) {
        this.eventStore = eventStore;
        this.persister = persister;
    }

    public void planConference(PlanTentativeConferenceRequest request) {
        if (eventStore.isReadOnly()) {
            throw new ReadOnlyModeException("Attempting to execute request while in read-only mode:" + request);
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
