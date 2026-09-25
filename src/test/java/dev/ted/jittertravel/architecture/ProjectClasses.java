package dev.ted.jittertravel.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The compiled classes the ArchUnit guards in this package check, imported once per test JVM
 * rather than once per rule. Production and test classes are kept apart because every rule here is
 * about one side or the other: the production rules must not trip over a test's legitimate
 * {@code Instant.now()}, and the test rules must not scan the application.
 */
final class ProjectClasses {

    private static final String ROOT_PACKAGE = "dev.ted.jittertravel";

    static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

    static final JavaClasses TESTS = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

    private ProjectClasses() {
    }
}
