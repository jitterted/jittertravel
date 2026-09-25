package dev.ted.jittertravel.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture guard: the {@code domain} package states facts and rules about travel, and depends
 * on nothing that would make those facts untestable or untellable — no I/O or serialization library,
 * no framework, no clock, no randomness, no presentation. See CLAUDE.md and
 * {@code docs/archived/CuratedResolversToDomainPlan.md} for the rule this enforces.
 * <p>
 * The dependency whitelist is the load-bearing half: a package that depends on nothing but
 * {@code java.*} and itself can reach Spring or Jackson only through a class it cannot name. That
 * covers the framework and presentation clauses in one assertion, which is why it is written as a
 * whitelist of two prefixes rather than a blacklist of the libraries we happen to use today. It
 * reads the compiled dependencies, so a fully-qualified name with no import line is caught too.
 * <p>
 * <strong>{@code java.*} is not all harmless, so the I/O clause has a rule of its own</strong>
 * (2026-09-25). The whitelist admits {@code java.io}, {@code java.nio}, {@code java.net} and
 * {@code java.sql} — files, sockets, HTTP and JDBC — which is the whole of the I/O the rule forbids.
 * A curated in-memory table — {@code LocationZoneResolver}, {@code StaticAirportCityResolver} — is
 * data, not I/O, and passes both.
 * <p>
 * The clock clause is already covered by {@link NoAmbientClockReadsTest} over all production code;
 * randomness is checked here because nothing else looks for it.
 * <p>
 * <strong>{@code UUID} is deliberately not checked for.</strong> Seven {@code *Id.random()}
 * factories call {@code UUID.randomUUID()} in the domain, and they stay (Ted, 2026-08-23): they have
 * no {@code src/main} call site at all, so production already mints ids at the boundary and passes
 * them inward, exactly as it does {@code now}. That — the live half of the rule — is what
 * {@link #idsAreMintedAtTheBoundaryNeverInsideProductionCode()} pins, rather than the 407 test call
 * sites that would have to be rewritten to delete the factories.
 */
class DomainIsPureTest {

    private static final String DOMAIN = "dev.ted.jittertravel.domain..";

    @Test
    void domainDependsOnNothingOutsideJavaAndItself() {
        classes()
                .that().resideInAPackage(DOMAIN)
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN)
                .because("domain types state facts about travel: no I/O or serialization library, "
                         + "no framework, no clock, no presentation. Put the adapter in "
                         + "infrastructure and keep the interface here")
                .check(ProjectClasses.PRODUCTION);
    }

    @Test
    void domainNeverReachesForIo() {
        noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.io..", "java.nio..", "java.net..", "java.sql..", "java.rmi..")
                .because("a domain type that reads a file, a socket or a database states a fact a "
                         + "test cannot fix. Put the adapter in infrastructure and keep the "
                         + "interface here")
                .check(ProjectClasses.PRODUCTION);
    }

    @Test
    void domainNeverReachesForRandomness() {
        noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().accessTargetWhere(reach(Math.class, "random"))
                .orShould().dependOnClassesThat().areAssignableTo(RandomGenerator.class)
                .orShould().dependOnClassesThat().belongToAnyOf(
                        Random.class, SecureRandom.class, ThreadLocalRandom.class)
                .because("a value the domain invents is a value a test cannot fix. Draw it at the "
                         + "boundary and pass it inward, as with the clock")
                .check(ProjectClasses.PRODUCTION);
    }

    @Test
    void idsAreMintedAtTheBoundaryNeverInsideProductionCode() {
        noClasses()
                .should().accessTargetWhere(reachAnIdFactory())
                .because("ids are minted at the boundary — a controller does UUID.randomUUID() and "
                         + "passes the value inward — so that an event's id is a fixed input to "
                         + "every test below it. The factories exist for tests only")
                .check(ProjectClasses.PRODUCTION);
    }

    // Accesses rather than calls throughout, so a method reference (Math::random,
    // FlightId::random) is caught as surely as a call.
    private static DescribedPredicate<JavaAccess<?>> reach(Class<?> owner, String methodName) {
        return DescribedPredicate.describe(
                "reach " + owner.getSimpleName() + "." + methodName + "()",
                access -> access.getTargetOwner().isEquivalentTo(owner)
                          && access.getTarget().getName().equals(methodName));
    }

    private static DescribedPredicate<JavaAccess<?>> reachAnIdFactory() {
        return DescribedPredicate.describe(
                "reach a domain *Id.random() factory",
                access -> access.getTarget().getName().equals("random")
                          && access.getTargetOwner().getPackageName().startsWith("dev.ted.jittertravel.domain")
                          && access.getTargetOwner().getSimpleName().endsWith("Id"));
    }
}
