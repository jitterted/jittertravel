package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.GatheringChanged;
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
                // A change can move a gathering to a venue the tables don't know, and the upcaster
                // reads *every* stored event — so changes must be audited too, like their siblings.
                case GatheringChanged e -> addAddress(e.location(), source);
                case ConferencePlanned e -> addAddress(e.venueAddress(), source);
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
                // HotelBookingCancelled is deliberately NOT handled here. Cancelling removes the
                // booking from every *view*, but the HotelBooked/HotelChanged rows stay in the log
                // forever and the read-time upcaster still resolves their zone on every replay — so
                // the audit must keep reporting that location. Dropping it would hide exactly the
                // unresolvable location that breaks startup.
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
            addCity(address.city(), address.region(), address.country(), source);
        }
    }

    private void addStation(TrainStationAddress station, EventReference source) {
        if (station != null) {
            addCity(station.city(), "", station.country(), source);
        }
    }

    private void addCity(String city, String region, String country, EventReference source) {
        CityCountry location = new CityCountry(city, region, country);
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
               + "|" + location.region().trim().toLowerCase(Locale.ROOT)
               + "|" + location.country().trim().toLowerCase(Locale.ROOT);
    }

    /** A distinct city/region/country location plus every event that referenced it. */
    public record AuditedLocation(CityCountry location, List<EventReference> sources) {
    }

    /** A distinct airport plus every event that referenced it. */
    public record AuditedAirport(AirportCode airport, List<EventReference> sources) {
    }
}
