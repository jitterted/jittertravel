package dev.ted.jittertravel.application;

import dev.ted.jittertravel.application.LocationAuditProjector.AuditedAirport;
import dev.ted.jittertravel.application.LocationAuditProjector.AuditedLocation;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocationZoneAuditTest {

    private final LocationZoneAudit audit =
            new LocationZoneAudit(new LocationZoneResolver(), new AirportZoneResolver());

    @Test
    void reportsResolvedLocationsWithTheirZones() {
        LocationZoneAudit.Report report = audit.report(
                List.of(location("Frankfurt", "Germany"), location("Chicago", "USA")),
                List.of(airport("SFO")));

        assertThat(report.allResolved())
                .as("every supplied location resolves")
                .isTrue();
        assertThat(report.resolved())
                .extracting(LocationZoneAudit.Entry::label, LocationZoneAudit.Entry::zoneId)
                .containsExactlyInAnyOrder(
                        Tuple.tuple("Chicago, USA", "America/Chicago"),
                        Tuple.tuple("Frankfurt, Germany", "Europe/Berlin"),
                        Tuple.tuple("SFO", "America/Los_Angeles"));
    }

    @Test
    void separatesUnresolvableLocationsAndAirports() {
        LocationZoneAudit.Report report = audit.report(
                List.of(location("Frankfurt", "Germany"), location("Nowheresville", "Atlantis")),
                List.of(airport("XXX")));

        assertThat(report.allResolved())
                .as("two of the three locations are unresolved")
                .isFalse();
        assertThat(report.unresolved())
                .extracting(LocationZoneAudit.Entry::label)
                .containsExactlyInAnyOrder("Nowheresville, Atlantis", "XXX");
        assertThat(report.resolved())
                .extracting(LocationZoneAudit.Entry::label)
                .containsExactly("Frankfurt, Germany");
    }

    @Test
    void unresolvedEntriesCarryTheirSourceEvents() {
        EventReference source = new EventReference(42, "HotelBooked", "HotelBooked[city=Nowheresville]");

        LocationZoneAudit.Report report = audit.report(
                List.of(new AuditedLocation(new CityCountry("Nowheresville", "Atlantis"), List.of(source))),
                List.of());

        assertThat(report.unresolved())
                .singleElement()
                .extracting(LocationZoneAudit.Entry::sources)
                .isEqualTo(List.of(source));
    }

    @Test
    void emptyDataResolvesCleanly() {
        LocationZoneAudit.Report report = audit.report(List.of(), List.of());

        assertThat(report.allResolved())
                .isTrue();
        assertThat(report.totalCount())
                .isZero();
    }

    private static AuditedLocation location(String city, String country) {
        return new AuditedLocation(new CityCountry(city, country), List.of());
    }

    private static AuditedAirport airport(String code) {
        return new AuditedAirport(AirportCode.of(code), List.of());
    }
}
