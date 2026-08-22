package dev.ted.jittertravel.architecture;

import dev.ted.jittertravel.infrastructure.EventSourcingConfig;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard: a projector that is built but never registered is invisible.
 * <p>
 * {@code ProjectorBootstrapper.register} does two things a bare constructor does not — subscribe the
 * projector to the {@link dev.ted.jittertravel.infrastructure.EventStore} for future appends, and
 * replay the existing stream so it is caught up before it becomes a bean. Drop the call and the
 * projector still wires, still injects, still answers every query: with an empty read model, for
 * ever. Nothing throws.
 * <p>
 * That is untestable from the outside in the usual way, because every test that renders a page
 * supplies its projectors as mocks or stubs — so the registration line is covered by nothing at all.
 * It matters most for {@code PublicCalendarProjector}, which since the S2 refactor is the sole
 * source of the anonymous {@code /calendar}: unregistered, every visitor gets a permanently empty
 * calendar and the whole suite stays green.
 * <p>
 * The set of beans checked is derived by <strong>reflection</strong> over
 * {@link EventSourcingConfig}, not listed here, so a new projector bean is covered the day it is
 * written — there is no fixture to forget. Only the "does it call register" question is answered by
 * reading the source, because that call is the whole of what there is to check.
 */
class EveryProjectorBeanIsRegisteredTest {

    private static final Path CONFIG_SOURCE = Path.of(
            "src/main/java/dev/ted/jittertravel/infrastructure/EventSourcingConfig.java");

    private static final Pattern REGISTER_CALL = Pattern.compile("\\bbootstrapper\\.register\\s*\\(");

    @Test
    void everyBeanThatConsumesTheEventStreamIsRegisteredWithTheBootstrapper() {
        List<Method> projectorBeans = projectorBeans();

        List<String> unregistered = new ArrayList<>();
        for (Method bean : projectorBeans) {
            if (!REGISTER_CALL.matcher(bodyOf(bean.getName())).find()) {
                unregistered.add(bean.getName() + " -> " + bean.getReturnType().getSimpleName());
            }
        }

        assertThat(unregistered)
                .as("""
                    These @Bean methods return an EventStreamConsumer but never call \
                    bootstrapper.register(...). An unregistered projector is neither subscribed to \
                    future events nor replayed over past ones, so it answers every query with an \
                    empty read model and never fails — for PublicCalendarProjector that is a blank \
                    /calendar for every anonymous visitor.""")
                .isEmpty();
    }

    /**
     * The guard is only worth having while it is actually looking at something, and both halves can
     * rot independently: a renamed {@code bootstrapper} parameter would make every body stop
     * matching, and a moved config class would make the reflection find nothing. Pin both.
     */
    @Test
    void theGuardIsLookingAtRealProjectorBeans() {
        assertThat(projectorBeans())
                .as("EventSourcingConfig must still declare the projector beans this guard checks")
                .hasSizeGreaterThanOrEqualTo(20)
                .anySatisfy(bean -> assertThat(bean.getReturnType().getSimpleName())
                        .isEqualTo("PublicCalendarProjector"));
        assertThat(REGISTER_CALL.matcher(source()).results().count())
                .as("the register(...) calls this guard greps for must still be spelled that way")
                .isGreaterThanOrEqualTo(20L);
    }

    /** Every {@code @Bean} method whose return type consumes the event stream. */
    private static List<Method> projectorBeans() {
        return Arrays.stream(EventSourcingConfig.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .filter(method -> EventStreamConsumer.class.isAssignableFrom(method.getReturnType()))
                .sorted(Comparator.comparing(Method::getName))
                .toList();
    }

    /**
     * The source text of one {@code @Bean} method: from its signature to the first line that closes
     * a method at class-body indentation. Bean methods here are short and never nest a class, so the
     * closing brace is unambiguous.
     */
    private static String bodyOf(String methodName) {
        List<String> lines = sourceLines();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(" " + methodName + "(")) {
                StringBuilder body = new StringBuilder();
                for (int j = i; j < lines.size(); j++) {
                    body.append(lines.get(j)).append('\n');
                    if (lines.get(j).equals("    }")) {
                        return body.toString();
                    }
                }
            }
        }
        throw new AssertionError(
                "cannot find the source of @Bean method " + methodName + " in " + CONFIG_SOURCE
                + " — the reflection and the source scan have drifted apart");
    }

    private static String source() {
        return String.join("\n", sourceLines());
    }

    private static List<String> sourceLines() {
        try {
            return Files.readAllLines(CONFIG_SOURCE);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + CONFIG_SOURCE, e);
        }
    }
}
