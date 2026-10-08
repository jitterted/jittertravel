package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Countries;
import dev.ted.jittertravel.domain.Country;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.Subdivision;
import dev.ted.jittertravel.domain.Subdivisions;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The countries Ted has been to, and the US, Canadian and Australian states — the short lists the
 * address forms offer first (Ted, 2026-10-06; docs/LocationDataCleanupPlan.md D1). "Been to" means
 * named on any booking or plan, cancelled ones included: a country he once planned for is one he is
 * likely to pick again, and a list that shrank on a cancellation would move its options around.
 *
 * <p>Only values that are real codes count. Before the stored events are migrated they hold names
 * ({@code "Germany"}, {@code "USA"}), and a name in this list would put a value on the form that the
 * command refuses — so until then the list is short, which the "Another country…" list covers.
 *
 * <p>A curated table in the domain says what a code is; this says which ones Ted uses. Every event
 * that carries an address or a station is a case below, guarded by
 * {@code LocatedEventsReachPlacesUsedTest}.
 */
public class PlacesUsedProjector implements EventStreamConsumer {

    private final Countries countries = new Countries();
    private final Subdivisions subdivisions = new Subdivisions();

    private final Set<String> countryCodes = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> subdivisionCodesByCountry = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case HotelBooked e -> record(e.address());
                case HotelChanged e -> record(e.address());
                case ConferencePlanned e -> record(e.venueAddress());
                case GatheringPlanned e -> record(e.location());
                case GatheringChanged e -> record(e.location());
                case PrivateEventPlanned e -> record(e.location());
                case GroundTransferPlanned e -> {
                    record(e.origin());
                    record(e.destination());
                }
                case TrainBooked e -> {
                    record(e.departureStation());
                    record(e.arrivalStation());
                }
                case TrainChanged e -> {
                    record(e.departureStation());
                    record(e.arrivalStation());
                }
                default -> { /* carries no place */ }
            }
        });
    }

    /** The countries used, A to Z by name. */
    public List<Country> countries() {
        return countryCodes.stream()
                           .map(code -> new Country(code, countries.name(code).orElseThrow()))
                           .sorted(Comparator.comparing(Country::name))
                           .toList();
    }

    /** The states used in this country, A to Z by name; empty for a country with no state list. */
    public List<Subdivision> subdivisions(String countryCode) {
        Set<String> used = subdivisionCodesByCountry.getOrDefault(countryCode, Set.of());
        return subdivisions.of(countryCode).stream()
                           .filter(subdivision -> used.contains(subdivision.code()))
                           .sorted(Comparator.comparing(Subdivision::name))
                           .toList();
    }

    private void record(Address address) {
        if (address == null || !countries.isKnown(address.country())) {
            return;
        }
        String country = address.country().toUpperCase(Locale.ROOT);
        countryCodes.add(country);
        subdivisions.find(country, address.region())
                    .ifPresent(subdivision -> subdivisionCodesByCountry
                            .computeIfAbsent(country, code -> ConcurrentHashMap.newKeySet())
                            .add(subdivision.code()));
    }

    private void record(TrainStationAddress station) {
        if (station != null && countries.isKnown(station.country())) {
            countryCodes.add(station.country().toUpperCase(Locale.ROOT));
        }
    }
}
