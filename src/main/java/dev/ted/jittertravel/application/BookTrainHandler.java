package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookTrainCommand;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.web.BookTrainRequest;

import java.util.UUID;

public class BookTrainHandler {

    public BookTrainCommand handle(BookTrainRequest request) {
        return new BookTrainCommand(
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
