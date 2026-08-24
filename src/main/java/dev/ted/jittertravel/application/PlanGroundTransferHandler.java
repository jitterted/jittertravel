package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.PlanGroundTransferCommand;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.PlanGroundTransferRequest;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Maps the ground-transfer form to its domain command, resolving each endpoint token to a real
 * place through {@link GroundTransferEndpointResolver}.
 * <p>
 * <strong>One zone for both ends, taken from the origin.</strong> A transfer that crosses a zone
 * boundary is out of scope; the schedule timeline compares <em>cities</em>, not zones, so nothing
 * downstream is harmed by the simplification.
 */
public class PlanGroundTransferHandler {

    private final GroundTransferEndpointResolver endpoints;

    public PlanGroundTransferHandler(GroundTransferEndpointResolver endpoints) {
        this.endpoints = endpoints;
    }

    public PlanGroundTransferCommand handle(PlanGroundTransferRequest request) {
        // Compared as tokens, before resolution: two tokens that differ can never name the same
        // place (an airport is not a hotel, and each hotel token carries its own booking id).
        if (request.getOrigin() != null && request.getOrigin().equals(request.getDestination())) {
            throw new SameTransferEndpoints("A transfer needs two different places");
        }
        TransferEndpoint origin = endpoints.resolve(request.getOrigin());
        TransferEndpoint destination = endpoints.resolve(request.getDestination());
        ZoneId zone = origin.zone();
        return new PlanGroundTransferCommand(
                GroundTransferId.of(UUID.fromString(request.getGroundTransferId())),
                origin.airportCode(), origin.name(), origin.address(),
                destination.airportCode(), destination.name(), destination.address(),
                ZonedTimestamp.fromLocal(request.getDate().atTime(request.getDepartureTime()), zone),
                ZonedTimestamp.fromLocal(request.getDate().atTime(request.getArrivalTime()), zone),
                mode(request)
        );
    }

    /**
     * The one field on this form Ted types rather than picks, so it is the one that arrives with
     * the whitespace of a paste around it and the one an untouched input sends back as "". Both
     * are the same thing — no mode recorded — and settling that here keeps every reader downstream
     * comparing against one absent value.
     */
    private String mode(PlanGroundTransferRequest request) {
        return request.getMode() == null ? "" : request.getMode().strip();
    }
}
