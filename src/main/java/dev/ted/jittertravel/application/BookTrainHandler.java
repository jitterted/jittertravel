package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookTrainCommand;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.BookTrainRequest;

import java.util.UUID;

public class BookTrainHandler {

    private final LocationZoneResolver zoneResolver;

    public BookTrainHandler(LocationZoneResolver zoneResolver) {
        this.zoneResolver = zoneResolver;
    }

    public BookTrainCommand handle(BookTrainRequest request) {
        // Departure and arrival resolve independently (a trip can span two zones). Per endpoint an
        // explicit CommonZone pick wins; otherwise the station's city/country must resolve or the
        // command is rejected and the form re-prompts for a CommonZone.
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
        return new BookTrainCommand(
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
