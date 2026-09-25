package dev.ted.jittertravel.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import dev.ted.jittertravel.infrastructure.EventSourcingConfig;
import org.junit.jupiter.api.Test;

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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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

    /** The java.time types whose no-arg {@code now()} reads the system clock. */
    private static final List<Class<?>> NOW_TYPES = List.of(
            Instant.class, LocalDate.class, LocalDateTime.class, LocalTime.class,
            ZonedDateTime.class, OffsetDateTime.class, OffsetTime.class,
            Year.class, YearMonth.class, MonthDay.class);

    private static final Set<String> SYSTEM_TIME_READS = Set.of("currentTimeMillis", "nanoTime");

    private static final Set<String> SYSTEM_CLOCK_FACTORIES = Set.of("systemDefaultZone", "systemUTC", "system");

    @Test
    void productionCodeNeverReadsTheAmbientSystemClock() {
        noClasses()
                .that().doNotBelongToAnyOf(EventSourcingConfig.class)
                .should().accessTargetWhere(readTheAmbientClock())
                .because("time comes from the injected Clock (Instant.now(clock) / clock.instant()), "
                         + "so a test can pin it")
                .check(ProjectClasses.PRODUCTION);
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
            // now(clock) carries an argument, so it never matches
            return target.getRawParameterTypes().isEmpty()
                   && NOW_TYPES.stream().anyMatch(owner::isEquivalentTo);
        }
        if (owner.isEquivalentTo(System.class)) {
            return SYSTEM_TIME_READS.contains(name);
        }
        return owner.isEquivalentTo(Clock.class) && SYSTEM_CLOCK_FACTORIES.contains(name);
    }
}
