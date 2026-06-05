package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.PlanGatheringCommand;
import dev.ted.jittertravel.web.PlanGatheringRequest;

import java.util.UUID;

public class PlanGatheringHandler {

    public PlanGatheringCommand handle(PlanGatheringRequest request) {
        return new PlanGatheringCommand(
                GatheringId.of(UUID.fromString(request.getGatheringId())),
                request.getTitle(),
                request.getVenueName(),
                request.getLocation(),
                request.getDate(),
                request.getStartTime(),
                request.getEndTime(),
                request.isSpeaking(),
                request.getInfoUrl()
        );
    }
}
