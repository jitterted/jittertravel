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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Collects every <em>distinct</em> location that appears in event data — city/country pairs (hotels,
 * train stations, gatherings, conferences) and airport codes (flights) — so the zone audit can check
 * each against the resolvers. Read-only; it never resolves zones itself (that is {@link LocationZoneAudit}'s
 * job), keeping resolution fresh at request time and this projector trivial.
 */
public class LocationAuditProjector implements EventStreamConsumer {

    private final Map<String, CityCountry> citiesByKey = new LinkedHashMap<>();
    private final Map<String, AirportCode> airportsByCode = new LinkedHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case HotelBooked e -> addAddress(e.address());
                case HotelChanged e -> addAddress(e.address());
                case GatheringPlanned e -> addAddress(e.location());
                case ConferenceTentativelyPlanned e -> addAddress(e.venueAddress());
                case TrainBooked e -> {
                    addStation(e.departureStation());
                    addStation(e.arrivalStation());
                }
                case TrainChanged e -> {
                    addStation(e.departureStation());
                    addStation(e.arrivalStation());
                }
                case FlightBooked e -> {
                    addAirport(e.departureAirport());
                    addAirport(e.arrivalAirport());
                }
                case FlightChanged e -> {
                    addAirport(e.departureAirport());
                    addAirport(e.arrivalAirport());
                }
                default -> { /* event carries no location */ }
            }
        });
    }

    public Collection<CityCountry> cities() {
        return citiesByKey.values();
    }

    public Collection<AirportCode> airports() {
        return airportsByCode.values();
    }

    private void addAddress(Address address) {
        if (address != null) {
            addCity(address.city(), address.country());
        }
    }

    private void addStation(TrainStationAddress station) {
        if (station != null) {
            addCity(station.city(), station.country());
        }
    }

    private void addCity(String city, String country) {
        CityCountry location = new CityCountry(city, country);
        citiesByKey.putIfAbsent(key(location), location);
    }

    private void addAirport(AirportCode airport) {
        if (airport != null) {
            airportsByCode.putIfAbsent(airport.code(), airport);
        }
    }

    private String key(CityCountry location) {
        return location.city().trim().toLowerCase(Locale.ROOT)
               + "|" + location.country().trim().toLowerCase(Locale.ROOT);
    }
}
