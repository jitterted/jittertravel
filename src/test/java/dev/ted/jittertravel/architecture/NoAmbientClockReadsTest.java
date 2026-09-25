package dev.ted.jittertravel.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import dev.ted.jittertravel.infrastructure.EventSourcingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard: production code may never read the ambient system clock.
 * {@code Instant.now()}, {@code LocalDate.now()}, {@code System.currentTimeMillis()}
 * and friends are unmockable — a class that calls one cannot be tested at a chosen
 * instant, so date-boundary behaviour (a "future" filter at midnight, a cancellation
 * deadline, an expiry) has no way to be pinned down in a test.
 * <p>
 * Take the time from the injected {@link java.time.Clock} instead: {@code
 * Instant.now(clock)} or {@code clock.instant()}, captured at the boundary (controller
 * or importer) and passed inward — see CLAUDE.md, "Time comes from the injected Clock".
 * <p>
 * The single legal source of real time is the {@code Clock} {@code @Bean} factory in
 * {@code EventSourcingConfig}, which is why that one class is exempt. This guard covers
 * production classes only; tests may read the wall clock freely.
 * <p>
 * It reads the compiled accesses, not the source, so a statically imported {@code now()} is caught
 * as surely as {@code Instant.now()}, and so is the method reference {@code Instant::now}.
 */
class NoAmbientClockReadsTest {

    /** The java.time types whose {@code now()} and {@code now(zone)} read the system clock. */
    private static final List<Class<?>> NOW_TYPES = List.of(
            Instant.class, LocalDate.class, LocalDateTime.class, LocalTime.class,
            ZonedDateTime.class, OffsetDateTime.class, OffsetTime.class,
            Year.class, YearMonth.class, MonthDay.class);

    private static final Set<String> SYSTEM_TIME_READS = Set.of("currentTimeMillis", "nanoTime");

    /** Every {@link Clock} factory that hands back a clock on the system's time, zoned or not. */
    private static final Set<String> SYSTEM_CLOCK_FACTORIES = Set.of(
            "systemDefaultZone", "systemUTC", "system", "tickMillis", "tickSeconds", "tickMinutes");

    private static final ArchRule NO_AMBIENT_CLOCK_READS = noClasses()
            .that().doNotBelongToAnyOf(EventSourcingConfig.class)
            .should().accessTargetWhere(readTheAmbientClock())
            .because("time comes from the injected Clock (Instant.now(clock) / clock.instant()), "
                     + "so a test can pin it");

    @Test
    void productionCodeNeverReadsTheAmbientSystemClock() {
        NO_AMBIENT_CLOCK_READS.check(ProjectClasses.PRODUCTION);
    }

    /*
     * The rule is checked against classes that break it, one form each, because a guard that is
     * only ever run over clean code passes just as well when it sees nothing. now(ZoneId) is the
     * form the argument-count test let through: it takes an argument and still reads the system.
     */
    @ParameterizedTest
    @ValueSource(classes = {
            ReadsNow.class, ReadsNowInAZone.class, ReadsNowByReference.class,
            ReadsCurrentTimeMillis.class, BuildsATickingSystemClock.class})
    void catchesEveryWayOfReadingTheSystemClock(Class<?> offender) {
        assertThat(NO_AMBIENT_CLOCK_READS.evaluate(new ClassFileImporter().importClasses(offender))
                                         .hasViolation())
                .as(offender.getSimpleName() + " reads the system clock")
                .isTrue();
    }

    @Test
    void readingTheInjectedClockIsAllowed() {
        assertThat(NO_AMBIENT_CLOCK_READS.evaluate(new ClassFileImporter().importClasses(ReadsTheInjectedClock.class))
                                         .hasViolation())
                .as("now(clock) is the sanctioned read")
                .isFalse();
    }

    static class ReadsNow {
        LocalDate today() { return LocalDate.now(); }
    }

    static class ReadsNowInAZone {
        LocalDate today() { return LocalDate.now(ZoneId.of("Europe/Berlin")); }
    }

    static class ReadsNowByReference {
        Supplier<Instant> clock() { return Instant::now; }
    }

    static class ReadsCurrentTimeMillis {
        long millis() { return System.currentTimeMillis(); }
    }

    static class BuildsATickingSystemClock {
        Clock clock() { return Clock.tickSeconds(ZoneId.of("UTC")); }
    }

    static class ReadsTheInjectedClock {
        LocalDate today(Clock clock) { return LocalDate.now(clock); }
    }

    // Any access, not only a call: Instant::now handed to a Supplier reads the clock just the same.
    private static DescribedPredicate<JavaAccess<?>> readTheAmbientClock() {
        return DescribedPredicate.describe(
                "read the ambient system clock",
                access -> access.getTarget() instanceof CodeUnitAccessTarget target
                          && isAmbientClockRead(target));
    }

    private static boolean isAmbientClockRead(CodeUnitAccessTarget target) {
        String name = target.getName();
        JavaClass owner = target.getOwner();
        if (name.equals("now")) {
            // now(clock) is the one overload that does not read the system: now() and now(zone)
            // both do, so it is the parameter's type that decides, not whether there is one.
            List<JavaClass> parameters = target.getRawParameterTypes();
            boolean fromAClock = parameters.size() == 1 && parameters.getFirst().isEquivalentTo(Clock.class);
            return !fromAClock && NOW_TYPES.stream().anyMatch(owner::isEquivalentTo);
        }
        if (owner.isEquivalentTo(System.class)) {
            return SYSTEM_TIME_READS.contains(name);
        }
        return owner.isEquivalentTo(Clock.class) && SYSTEM_CLOCK_FACTORIES.contains(name);
    }
}
