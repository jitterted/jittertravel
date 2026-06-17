package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeTrainCommand;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.web.ChangeTrainRequest;

import java.util.UUID;

public class ChangeTrainHandler {

    public ChangeTrainCommand handle(ChangeTrainRequest request) {
        return new ChangeTrainCommand(
                TrainTripId.of(UUID.fromString(request.getTrainTripId())),
                new TrainStationAddress(
                        request.getDepartureStationName(),
                        request.getDepartureCityName(),
                        request.getDepartureCountry(),
                        request.getDepartureMapsUrl()),
                request.getDepartureDateTime(),
                new TrainStationAddress(
                        request.getArrivalStationName(),
                        request.getArrivalCityName(),
                        request.getArrivalCountry(),
                        request.getArrivalMapsUrl()),
                request.getArrivalDateTime(),
                request.getServiceId()
        );
    }
}