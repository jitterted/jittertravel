package dev.ted.jittertravel.architecture;

import dev.ted.jittertravel.domain.Event;
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

/**
 * The event records that carry a place, found by scanning the domain package, and the source check
 * the guards built on them share: does a projector's switch name each one. {@link Event} is
 * deliberately not sealed (CLAUDE.md, event exhaustiveness), so a scan is what is left.
 */
class LocatedEvents {

    private final Set<Class<?>> locationTypes;

    LocatedEvents(Set<Class<?>> locationTypes) {
        this.locationTypes = locationTypes;
    }

    List<Class<? extends Event>> classes() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Event.class));

        List<Class<? extends Event>> located = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("dev.ted.jittertravel.domain")) {
            Class<?> clazz = classFor(candidate.getBeanClassName());
            if (clazz.isInterface() || !clazz.isRecord()) {
                continue;
            }
            boolean carriesALocation = Arrays.stream(clazz.getRecordComponents())
                    .anyMatch(component -> locationTypes.contains(component.getType()));
            if (carriesALocation) {
                located.add(clazz.asSubclass(Event.class));
            }
        }
        return located;
    }

    /** The located events whose simple name is not a {@code case} in the given source file. */
    List<String> notHandledIn(Path source) {
        String text = read(source);
        List<String> unhandled = new ArrayList<>();
        for (Class<? extends Event> located : classes()) {
            if (!text.contains("case " + located.getSimpleName() + " ")) {
                unhandled.add(located.getSimpleName());
            }
        }
        return unhandled;
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
