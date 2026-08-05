package dev.ted.jittertravel.architecture;

import dev.ted.jittertravel.application.CommandExecutor;
import dev.ted.jittertravel.infrastructure.EventStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard: no class in the {@code application} package may take an {@link EventStore} as
 * a constructor dependency. Appending events goes through {@code CommandExecutor}, which persists
 * the command row <em>before</em> the events — {@code EventStore.append} requires the command to
 * already exist in {@code command_log} (foreign key), so bypassing the executor causes FK
 * violations and partial writes where some events land and others don't. The executor also refuses
 * to write at all in read-only mode.
 * <p>
 * {@code ConferencePlanning} was the last service injecting {@code EventStore} directly; this test
 * exists so that never comes back. Written as plain reflection rather than adding an ArchUnit
 * dependency for one rule.
 */
class ApplicationServicesUseCommandExecutorTest {

    private static final String APPLICATION_PACKAGE = "dev.ted.jittertravel.application";

    @Test
    void noApplicationClassTakesAnEventStoreConstructorDependency() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Class<?> type : applicationClasses()) {
            if (CommandExecutor.class.equals(type)) {
                continue;  // the one authorized holder — it *is* the enforced route
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                for (Class<?> parameterType : constructor.getParameterTypes()) {
                    if (EventStore.class.equals(parameterType)) {
                        violations.add(type.getSimpleName() + " takes an EventStore constructor parameter");
                    }
                }
            }
        }

        assertThat(violations)
                .as("application services must append events via CommandExecutor, never EventStore directly")
                .isEmpty();
    }

    private static List<Class<?>> applicationClasses() throws IOException {
        Path root = Path.of(System.getProperty("user.dir"), "src/main/java",
                            APPLICATION_PACKAGE.replace('.', '/'));
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                        .map(p -> root.relativize(p).toString())
                        .map(name -> name.substring(0, name.length() - ".java".length()))
                        .map(name -> APPLICATION_PACKAGE + "." + name.replace('/', '.'))
                        .<Class<?>>map(ApplicationServicesUseCommandExecutorTest::load)
                        .toList();
        }
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new UncheckedIOException(new IOException("Could not load " + className, e));
        }
    }
}
