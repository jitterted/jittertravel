package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every field of a form request bean must be readable by Jackson, because the bean <em>is</em> the
 * command log's payload: {@code CommandExecutor.execute(commandId, request, …)} hands it to
 * {@code PostgresPersister.saveCommand}, which calls {@code writeValueAsString} on it.
 * <p>
 * <strong>Jackson reads {@code getXxx}/{@code isXxx} and nothing else.</strong> A record-style
 * accessor — {@code privateEventId()} — is invisible to it on a plain class, so a field with only
 * that accessor is silently dropped from the stored payload while the bean itself works perfectly:
 * the form binds, the command runs, the events land, and only the row in {@code command_log} is
 * wrong. That is what shipped in {@code ChangePrivateEventMatchingLocationRequest} on 2026-09-18,
 * where the payload recorded the new location and not <em>which evening</em> it applied to, so a
 * FAILED row named no target at all.
 * <p>
 * <strong>The claim is about the command log, not about bean style.</strong> The row is the only
 * durable record of what was asked for — it is what {@code /admin/pending-commands} shows, what a
 * FAILED row explains itself with, and what the event-oriented backup carries along as opaque
 * history for a future undo ({@code docs/archived/EventOrientedBackupRestorePlan.md}). A payload
 * missing its subject is a row that can answer none of those.
 * <p>
 * It is action at a distance, which is why it needs a test of its own: nothing at a request bean
 * says Jackson will serialize it — the coupling runs through a controller, an application service
 * and {@code CommandExecutor} before reaching the mapper. The same arrangement
 * {@link TrimmedTypedTextConventionTest} has for the binder advice. Written as plain reflection
 * rather than adding an ArchUnit dependency for one rule, like
 * {@code ApplicationServicesUseCommandExecutorTest}.
 * <p>
 * <strong>Records are exempt, by construction rather than by exception</strong>: Jackson serializes
 * a record's components natively, so a record cannot have this bug. Half the request types are
 * records today, and this rule is the reason to prefer one where the binding allows it — Thymeleaf's
 * {@code th:object}/{@code th:field} needs a mutable bean, which is what the rest are.
 */
class CommandLogPayloadConventionTest {

    private static final String WEB_PACKAGE = "dev.ted.jittertravel.web";

    @Test
    void everyRequestFieldHasAGetterJacksonCanSee() throws IOException {
        List<Class<?>> requestTypes = requestTypes();
        List<String> violations = new ArrayList<>();

        for (Class<?> type : requestTypes) {
            if (type.isInterface() || type.isRecord()) {
                continue;
            }
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (!hasReadableGetter(type, field)) {
                    violations.add("%s.%s has no public %s() — it would be missing from the command log payload"
                            .formatted(type.getSimpleName(), field.getName(), getterName(field)));
                }
            }
        }

        assertThat(violations)
                .as("a request bean is serialized into command_log, and Jackson reads getXxx/isXxx only")
                .isEmpty();

        assertThat(requestTypes)
                .as("the scan found no request types at all, so it proved nothing — has the naming "
                    + "convention or the package moved?")
                .isNotEmpty();
    }

    private static boolean hasReadableGetter(Class<?> type, Field field) {
        String suffix = capitalized(field.getName());
        boolean isBoolean = field.getType() == boolean.class || field.getType() == Boolean.class;
        return hasPublicNoArgMethod(type, "get" + suffix)
               || (isBoolean && hasPublicNoArgMethod(type, "is" + suffix));
    }

    private static boolean hasPublicNoArgMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)
                && method.getParameterCount() == 0
                && method.getReturnType() != void.class) {
                return true;
            }
        }
        return false;
    }

    /** What the violation message should tell you to write, boolean fields included. */
    private static String getterName(Field field) {
        String prefix = field.getType() == boolean.class || field.getType() == Boolean.class
                ? "is" : "get";
        return prefix + capitalized(field.getName());
    }

    private static String capitalized(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Discovered from the source tree by name rather than from a list, so a request bean added
     * tomorrow is covered without anyone editing this file — a test that has to be edited on every
     * change stops guarding, because editing it is what a breaking change would do.
     */
    private static List<Class<?>> requestTypes() throws IOException {
        Path root = Path.of(System.getProperty("user.dir"), "src/main/java",
                            WEB_PACKAGE.replace('.', '/'));
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.getFileName().toString().endsWith("Request.java"))
                        .map(path -> root.relativize(path).toString())
                        .map(name -> name.substring(0, name.length() - ".java".length()))
                        .map(name -> WEB_PACKAGE + "." + name.replace('/', '.'))
                        .<Class<?>>map(CommandLogPayloadConventionTest::load)
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
