package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeTrainCommand;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainStations;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.ChangeTrainRequest;

import java.util.UUID;

public class ChangeTrainHandler {

    private final LocationZoneResolver zoneResolver;

    public ChangeTrainHandler(LocationZoneResolver zoneResolver) {
        this.zoneResolver = zoneResolver;
    }

    public ChangeTrainCommand handle(String tripId, ChangeTrainRequest request) {
        TrainStations stations = new TrainStations(
                new TrainStationAddress(
                        request.departureStationName(),
                        request.departureCityName(),
                        request.departureCountry(),
                        request.departureMapsUrl()),
                new TrainStationAddress(
                        request.arrivalStationName(),
                        request.arrivalCityName(),
                        request.arrivalCountry(),
                        request.arrivalMapsUrl()));

        // Both ends asked in full, location before zone within each — see TrainEndpoints for why
        // that distinction is load-bearing rather than incidental.
        TrainZones zones = new TrainEndpoints(zoneResolver).resolve(stations,
                request.departureZone(), request.arrivalZone());

        return new ChangeTrainCommand(
                TrainTripId.of(UUID.fromString(tripId)),
                stations.departure(),
                ZonedTimestamp.fromLocal(request.departureDateTime(), zones.departure()),
                stations.arrival(),
                ZonedTimestamp.fromLocal(request.arrivalDateTime(), zones.arrival()),
                request.serviceId()
        );
    }
}
