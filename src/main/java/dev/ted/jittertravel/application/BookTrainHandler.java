package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookTrainCommand;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainStations;
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

        // Every problem either end has, in one answer — location before zone within an end, and no
        // order at all between the two ends. The command re-checks the locations (it is the gate;
        // this is only the boundary asking early enough to answer well), so the two cannot disagree.
        TrainZones zones = new TrainEndpoints(zoneResolver).resolve(stations,
                request.getDepartureZone(), request.getArrivalZone());

        return new BookTrainCommand(
                TrainTripId.of(UUID.fromString(request.getTrainTripId())),
                stations.departure(),
                ZonedTimestamp.fromLocal(request.getDepartureDateTime(), zones.departure()),
                stations.arrival(),
                ZonedTimestamp.fromLocal(request.getArrivalDateTime(), zones.arrival()),
                request.getServiceId()
        );
    }
}
