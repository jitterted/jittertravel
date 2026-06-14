package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import static org.assertj.core.api.Assertions.assertThat;

class ImportableCommandTypesTest {

    /**
     * Every {@link ImportableCommand} must be registered, otherwise it cannot be exported or
     * imported and would silently break a backup round trip. This turns "remembered to add the
     * registry line for a new command" from discipline into a failing build.
     */
    @Test
    void everyImportableCommandIsRegistered() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ImportableCommand.class));

        for (var candidate : scanner.findCandidateComponents("dev.ted.jittertravel")) {
            Class<?> clazz = Class.forName(candidate.getBeanClassName());
            if (clazz.isInterface()) {
                continue;
            }
            assertThat(ImportableCommandTypes.isRegistered(clazz.asSubclass(ImportableCommand.class)))
                    .as("ImportableCommand %s must be registered in ImportableCommandTypes", clazz.getName())
                    .isTrue();
        }
    }

    @Test
    void legacyApplicationPackageNamesStillResolveAfterRecordMove() {
        assertThat(ImportableCommandTypes.classFor(
                "dev.ted.jittertravel.application.MigrateConferenceToGathering"))
                .isEqualTo(MigrateConferenceToGathering.class);
        assertThat(ImportableCommandTypes.classFor(
                "dev.ted.jittertravel.application.ClearDifferentCityConflict"))
                .isEqualTo(ClearDifferentCityConflict.class);
    }

    @Test
    void exportsLogicalNameAndImportsItBack() {
        String logical = ImportableCommandTypes.logicalNameFor(BookFlightRequest.class.getName());
        assertThat(logical)
                .isEqualTo("BookFlight");
        assertThat(ImportableCommandTypes.classFor(logical))
                .isEqualTo(BookFlightRequest.class);
    }
}
