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

    public ChangeTrainCommand handle(ChangeTrainRequest request) {
        TrainStations stations = new TrainStations(
                new TrainStationAddress(
                        request.getDepartureStationName(),
                        request.getDepartureCityName(),
                        request.getDepartureCountry(),
                        request.getDepartureMapsUrl()),
                new TrainStationAddress(
                        request.getArrivalStationName(),
                        request.getArrivalCityName(),
                        request.getArrivalCountry(),
                        request.getArrivalMapsUrl()));

        // Both ends asked in full, location before zone within each — see TrainEndpoints for why
        // that distinction is load-bearing rather than incidental.
        TrainZones zones = new TrainEndpoints(zoneResolver).resolve(stations,
                request.getDepartureZone(), request.getArrivalZone());

        return new ChangeTrainCommand(
                TrainTripId.of(UUID.fromString(request.getTrainTripId())),
                stations.departure(),
                ZonedTimestamp.fromLocal(request.getDepartureDateTime(), zones.departure()),
                stations.arrival(),
                ZonedTimestamp.fromLocal(request.getArrivalDateTime(), zones.arrival()),
                request.getServiceId()
        );
    }
}
