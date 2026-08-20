package dev.ted.jittertravel.architecture;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.TrainStationAddress;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard: every event that says <em>where</em> Ted is must reach
 * {@code ScheduleGapProjector}.
 * <p>
 * Schedule-problem detection is a location trace — a chronological sequence of presence facts, one
 * per located thing on the schedule. An entry kind the projector cannot see does not merely go
 * unreported: it breaks the trace for everything <em>after</em> it. A private dinner in Berlin
 * between two Hamburg stays, left out, hides the Berlin nights and both travel legs around it, and
 * nothing anywhere says so. That is exactly what happened to {@code PrivateEventPlanned}, which
 * shipped with a city and a time and never reached the projector at all.
 * <p>
 * So this is not a completeness nicety; it is the standing rule from
 * {@code docs/ScheduleProblemsRewritePlan.md} D9, made to fail the build. {@link Event} is
 * deliberately not sealed (see CLAUDE.md on event exhaustiveness), so a compile-time check is not
 * available and a source scan is what is left.
 */
class LocatedEventsReachScheduleProblemsTest {

    private static final Path PROJECTOR =
            Path.of("src/main/java/dev/ted/jittertravel/application/ScheduleGapProjector.java");

    /**
     * The types that carry a location. An event holding one of these places Ted somewhere and owes
     * the timeline a presence fact.
     */
    private static final Set<Class<?>> LOCATION_TYPES =
            Set.of(Address.class, AirportCode.class, TrainStationAddress.class);

    @Test
    void everyEventCarryingALocationIsHandledByTheProjector() {
        String projectorSource = read(PROJECTOR);
        List<String> unhandled = new ArrayList<>();

        for (Class<? extends Event> located : locatedEventClasses()) {
            if (!projectorSource.contains("case " + located.getSimpleName() + " ")) {
                unhandled.add(located.getSimpleName());
            }
        }

        assertThat(unhandled)
                .as("""
                    These events carry a location but never reach ScheduleGapProjector, so the \
                    location trace cannot see them — and every problem after one of them is wrong, \
                    silently. Add a case to its switch that builds a ScheduleTimeline presence \
                    fact: an Occupancy for something Ted attends, a Stay for a booking, a Movement \
                    for a leg. See docs/ScheduleProblemsRewritePlan.md D9.""")
                .isEmpty();
    }

    @Test
    void theScanFindsTheLocatedEventsItIsMeantToCover() {
        // Without this, a broken scan (wrong package, filter that matches nothing) would leave the
        // guard above passing vacuously forever.
        assertThat(locatedEventClasses())
                .extracting(Class::getSimpleName)
                .contains("FlightBooked", "TrainBooked", "HotelBooked",
                          "ConferencePlanned", "GatheringPlanned", "PrivateEventPlanned");
    }

    private static List<Class<? extends Event>> locatedEventClasses() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Event.class));

        List<Class<? extends Event>> located = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("dev.ted.jittertravel.domain")) {
            Class<?> clazz = classFor(candidate.getBeanClassName());
            if (clazz.isInterface() || !clazz.isRecord()) {
                continue;
            }
            boolean carriesALocation = Arrays.stream(clazz.getRecordComponents())
                    .anyMatch(component -> LOCATION_TYPES.contains(component.getType()));
            if (carriesALocation) {
                located.add(clazz.asSubclass(Event.class));
            }
        }
        return located;
    }

    private static Class<?> classFor(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("scanned class disappeared: " + className, e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
