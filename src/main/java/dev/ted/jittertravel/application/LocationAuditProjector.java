package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Collects every <em>distinct</em> location that appears in event data — city/country pairs (hotels,
 * train stations, gatherings, conferences) and airport codes (flights) — together with the source
 * events that referenced each, so the zone audit can show the full event behind an unresolved
 * location. Read-only; it never resolves zones itself (that is {@link LocationZoneAudit}'s job),
 * keeping resolution fresh at request time.
 */
public class LocationAuditProjector implements EventStreamConsumer {

    private final Map<String, AuditedLocation> citiesByKey = new LinkedHashMap<>();
    private final Map<String, AuditedAirport> airportsByCode = new LinkedHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            EventReference source = new EventReference(
                    storedEvent.sequence(),
                    storedEvent.payload().getClass().getSimpleName(),
                    String.valueOf(storedEvent.payload()));
            switch (storedEvent.payload()) {
                case HotelBooked e -> addAddress(e.address(), source);
                case HotelChanged e -> addAddress(e.address(), source);
                case GatheringPlanned e -> addAddress(e.location(), source);
                case ConferenceTentativelyPlanned e -> addAddress(e.venueAddress(), source);
                case TrainBooked e -> {
                    addStation(e.departureStation(), source);
                    addStation(e.arrivalStation(), source);
                }
                case TrainChanged e -> {
                    addStation(e.departureStation(), source);
                    addStation(e.arrivalStation(), source);
                }
                case FlightBooked e -> {
                    addAirport(e.departureAirport(), source);
                    addAirport(e.arrivalAirport(), source);
                }
                case FlightChanged e -> {
                    addAirport(e.departureAirport(), source);
                    addAirport(e.arrivalAirport(), source);
                }
                default -> { /* event carries no location */ }
            }
        });
    }

    public Collection<AuditedLocation> cities() {
        return citiesByKey.values();
    }

    public Collection<AuditedAirport> airports() {
        return airportsByCode.values();
    }

    private void addAddress(Address address, EventReference source) {
        if (address != null) {
            addCity(address.city(), address.country(), source);
        }
    }

    private void addStation(TrainStationAddress station, EventReference source) {
        if (station != null) {
            addCity(station.city(), station.country(), source);
        }
    }

    private void addCity(String city, String country, EventReference source) {
        CityCountry location = new CityCountry(city, country);
        citiesByKey.computeIfAbsent(key(location), key -> new AuditedLocation(location, new ArrayList<>()))
                .sources().add(source);
    }

    private void addAirport(AirportCode airport, EventReference source) {
        if (airport != null) {
            airportsByCode.computeIfAbsent(airport.code(), code -> new AuditedAirport(airport, new ArrayList<>()))
                    .sources().add(source);
        }
    }

    private String key(CityCountry location) {
        return location.city().trim().toLowerCase(Locale.ROOT)
               + "|" + location.country().trim().toLowerCase(Locale.ROOT);
    }

    /** A distinct city/country location plus every event that referenced it. */
    public record AuditedLocation(CityCountry location, List<EventReference> sources) {
    }

    /** A distinct airport plus every event that referenced it. */
    public record AuditedAirport(AirportCode airport, List<EventReference> sources) {
    }
}
