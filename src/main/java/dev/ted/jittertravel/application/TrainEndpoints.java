package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.EnteredLocation;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.InvalidTrainEntry;
import dev.ted.jittertravel.domain.LocationRole;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainStations;
import dev.ted.jittertravel.domain.UnresolvedStationZone;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks both ends of a submitted trip everything that can be asked before a command is built, and
 * answers once.
 *
 * <p><strong>Each end is asked independently, and asked in full.</strong> Within one end the order
 * is location then zone, because a station that is not a place has no meaningful zone — that is
 * what stopped "Frankfurt (Main) Hbf" in a city box from being reported as a zone problem. Across
 * the two ends there is no order at all: a departure with no city does not stop the arrival being
 * asked about its country, so both land on the same page.
 *
 * <p>Getting that distinction wrong is what shipped on the first attempt at this: the location
 * check ran for the whole trip before any zone did, so a location problem at one end silently
 * suppressed a zone problem at the other, and the form still took two submits to clear.
 */
public class TrainEndpoints {

    private final StationZone stationZone;

    public TrainEndpoints(LocationZoneResolver zoneResolver) {
        this.stationZone = new StationZone(zoneResolver);
    }

    /**
     * @throws InvalidTrainEntry carrying every problem either end has.
     */
    public TrainZones resolve(TrainStations stations, String departurePick, String arrivalPick) {
        List<InvalidLocationEntry> locations = new ArrayList<>();
        List<UnresolvedStationZone> zones = new ArrayList<>();

        ZoneId departure = endpoint(locations, zones,
                LocationRole.DEPARTURE, departurePick, stations.departure());
        ZoneId arrival = endpoint(locations, zones,
                LocationRole.ARRIVAL, arrivalPick, stations.arrival());

        InvalidTrainEntry problems = new InvalidTrainEntry(locations, zones);
        if (!problems.isEmpty()) {
            throw problems;
        }
        return new TrainZones(departure, arrival);
    }

    /** Null when this end contributed a problem; the caller is throwing rather than using it. */
    private ZoneId endpoint(List<InvalidLocationEntry> locations,
                            List<UnresolvedStationZone> zones,
                            LocationRole role, String pick, TrainStationAddress station) {
        List<InvalidLocationEntry> invalid = EnteredLocation.of(station).problems(role);
        if (!invalid.isEmpty()) {
            locations.addAll(invalid);
            return null;
        }
        try {
            return stationZone.resolve(role, pick, station);
        } catch (UnresolvedStationZone unresolved) {
            zones.add(unresolved);
            return null;
        }
    }
}
