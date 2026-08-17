package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.TrainBooked;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import static org.assertj.core.api.Assertions.assertThat;

class EventTypesTest {

    /**
     * Every {@link Event} must be registered, otherwise it would persist (or fail to resolve) by a
     * physical class name and silently break replay the moment the class is renamed or moved. This
     * turns "remembered to add the registry line for a new event" from discipline into a failing
     * build. Scans the {@code domain} package only — that is where production events live; test-only
     * Event records elsewhere are deliberately out of scope.
     */
    @Test
    void everyDomainEventIsRegistered() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Event.class));

        for (var candidate : scanner.findCandidateComponents("dev.ted.jittertravel.domain")) {
            Class<?> clazz = Class.forName(candidate.getBeanClassName());
            if (clazz.isInterface()) {
                continue;
            }
            assertThat(EventTypes.isRegistered(clazz.asSubclass(Event.class)))
                    .as("Event %s must be registered in EventTypes", clazz.getName())
                    .isTrue();
        }
    }

    @Test
    void writesLogicalNameAndResolvesItBack() {
        String logical = EventTypes.logicalNameFor(TrainBooked.class);
        assertThat(logical)
                .isEqualTo("TrainBooked");
        assertThat(EventTypes.classFor(logical))
                .isEqualTo(TrainBooked.class);
    }

    @Test
    void legacyFullyQualifiedClassNameStillResolves() {
        assertThat(EventTypes.classFor("dev.ted.jittertravel.domain.TrainBooked"))
                .isEqualTo(TrainBooked.class);
    }

    @Test
    void datetimeBearingTypeIsAtSchemaVersionTwo() {
        // HotelBooked migrated bare-scalar -> ZonedTimestamp, so its current schema version is 2.
        assertThat(EventTypes.currentSchemaVersion(HotelBooked.class))
                .isEqualTo(2);
        assertThat(EventTypes.currentSchemaVersion("HotelBooked"))
                .as("by logical wire id")
                .isEqualTo(2);
        assertThat(EventTypes.currentSchemaVersion("dev.ted.jittertravel.domain.HotelBooked"))
                .as("legacy FQCN wire id resolves to the same version")
                .isEqualTo(2);
    }

    @Test
    void typeBornAfterTheZonedTimestampChangeIsAtSchemaVersionOne() {
        // ConferenceAttendanceDeclined has only ever had one shape, so it stays at version 1.
        assertThat(EventTypes.currentSchemaVersion(ConferenceAttendanceDeclined.class))
                .isEqualTo(1);
        assertThat(EventTypes.currentSchemaVersion("ConferenceAttendanceDeclined"))
                .isEqualTo(1);
    }
}