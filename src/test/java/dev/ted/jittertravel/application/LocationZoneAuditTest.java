package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocationZoneAuditTest {

    private final LocationZoneAudit audit =
            new LocationZoneAudit(new LocationZoneResolver(), new AirportZoneResolver());

    @Test
    void reportsResolvedLocationsWithTheirZones() {
        LocationZoneAudit.Report report = audit.report(
                List.of(new CityCountry("Frankfurt", "Germany"), new CityCountry("Chicago", "USA")),
                List.of(AirportCode.of("SFO")));

        assertThat(report.allResolved())
                .as("every supplied location resolves")
                .isTrue();
        assertThat(report.resolved())
                .extracting(LocationZoneAudit.Entry::label, LocationZoneAudit.Entry::zoneId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Chicago, USA", "America/Chicago"),
                        org.assertj.core.groups.Tuple.tuple("Frankfurt, Germany", "Europe/Berlin"),
                        org.assertj.core.groups.Tuple.tuple("SFO", "America/Los_Angeles"));
    }

    @Test
    void separatesUnresolvableLocationsAndAirports() {
        LocationZoneAudit.Report report = audit.report(
                List.of(new CityCountry("Frankfurt", "Germany"), new CityCountry("Nowheresville", "Atlantis")),
                List.of(AirportCode.of("XXX")));

        assertThat(report.allResolved())
                .as("two of the three locations are unresolved")
                .isFalse();
        assertThat(report.unresolved())
                .extracting(LocationZoneAudit.Entry::label)
                .containsExactlyInAnyOrder("Nowheresville, Atlantis", "XXX");
        assertThat(report.unresolved())
                .allSatisfy(entry -> assertThat(entry.resolved())
                        .as("unresolved entries carry no zone")
                        .isFalse());
        assertThat(report.resolved())
                .extracting(LocationZoneAudit.Entry::label)
                .containsExactly("Frankfurt, Germany");
    }

    @Test
    void emptyDataResolvesCleanly() {
        LocationZoneAudit.Report report = audit.report(List.of(), List.of());

        assertThat(report.allResolved())
                .isTrue();
        assertThat(report.totalCount())
                .isZero();
    }
}
