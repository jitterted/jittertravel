package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Runs every distinct stored location through the strict zone resolvers and reports which resolve
 * (and to what zone) and which do not. This is the pre-migration check: with no default zone, an
 * unresolved location would block the UTC-storage migration / read-time upcaster, so it must be
 * surfaced and fixed (extend the resolver tables, or correct the entry) <em>before</em> any change.
 *
 * <p>Pure given its inputs — {@link #report(Collection, Collection)} resolves the collections
 * supplied by {@link LocationAuditProjector} — so it is unit-testable without a database.
 */
public class LocationZoneAudit {

    private static final Comparator<Entry> BY_KIND_THEN_LABEL =
            Comparator.comparing(Entry::kind)
                    .thenComparing(entry -> entry.label().toLowerCase());

    private final LocationZoneResolver locationResolver;
    private final AirportZoneResolver airportResolver;

    public LocationZoneAudit(LocationZoneResolver locationResolver, AirportZoneResolver airportResolver) {
        this.locationResolver = locationResolver;
        this.airportResolver = airportResolver;
    }

    public Report report(Collection<CityCountry> cities, Collection<AirportCode> airports) {
        List<Entry> resolved = new ArrayList<>();
        List<Entry> unresolved = new ArrayList<>();

        for (CityCountry location : cities) {
            classify(Kind.LOCATION, location.label(), () -> locationResolver.resolve(location.city(), location.country()),
                    resolved, unresolved);
        }
        for (AirportCode airport : airports) {
            classify(Kind.AIRPORT, airport.code(), () -> airportResolver.resolve(airport),
                    resolved, unresolved);
        }

        resolved.sort(BY_KIND_THEN_LABEL);
        unresolved.sort(BY_KIND_THEN_LABEL);
        return new Report(resolved, unresolved);
    }

    private void classify(Kind kind, String label, ZoneSupplier resolve,
                          List<Entry> resolved, List<Entry> unresolved) {
        try {
            ZoneId zone = resolve.get();
            resolved.add(new Entry(kind, label, zone.getId()));
        } catch (ZoneResolutionException e) {
            unresolved.add(new Entry(kind, label, null));
        }
    }

    @FunctionalInterface
    private interface ZoneSupplier {
        ZoneId get();
    }

    public enum Kind {
        LOCATION, AIRPORT
    }

    /** One audited location: {@code zoneId} is the resolved zone, or {@code null} when unresolved. */
    public record Entry(Kind kind, String label, String zoneId) {
        public boolean resolved() {
            return zoneId != null;
        }
    }

    public record Report(List<Entry> resolved, List<Entry> unresolved) {
        public boolean allResolved() {
            return unresolved.isEmpty();
        }

        public int totalCount() {
            return resolved.size() + unresolved.size();
        }
    }
}
