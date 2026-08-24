package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeTrainCommand;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.TrainStationAddress;
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
        // Departure and arrival resolve independently; an explicit CommonZone pick wins per endpoint,
        // otherwise the station's city/country must resolve or the command is rejected.
        TrainStationAddress departureStation = new TrainStationAddress(
                request.getDepartureStationName(),
                request.getDepartureCityName(),
                request.getDepartureCountry(),
                request.getDepartureMapsUrl());
        TrainStationAddress arrivalStation = new TrainStationAddress(
                request.getArrivalStationName(),
                request.getArrivalCityName(),
                request.getArrivalCountry(),
                request.getArrivalMapsUrl());
        return new ChangeTrainCommand(
                TrainTripId.of(UUID.fromString(request.getTrainTripId())),
                departureStation,
                ZonedTimestamp.fromLocal(request.getDepartureDateTime(),
                        new StationZone(zoneResolver).resolve(request.getDepartureZone(), departureStation)),
                arrivalStation,
                ZonedTimestamp.fromLocal(request.getArrivalDateTime(),
                        new StationZone(zoneResolver).resolve(request.getArrivalZone(), arrivalStation)),
                request.getServiceId()
        );
    }
}