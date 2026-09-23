package dev.ted.jittertravel.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard: a test never contains a copy of production code — see CLAUDE.md, "A test never
 * contains a copy of production code".
 * <p>
 * The shape this catches is a controller's {@code catch} block re-typed in a test, deciding for
 * itself which input an error lands on. Production code writes a {@code BindingResult} and a test
 * only reads one, so {@code rejectValue(} — or {@code reject(} on a binding result — anywhere in
 * {@code src/test/java} is that copy. Which input a refusal lands on is a plain controller test
 * with a stub service programmed to throw (see CLAUDE.md, "Testing a form's validation").
 * <p>
 * Five {@code *ControllerValidationTest} classes carried the copy when this arrived (2026-09-22);
 * all five were replaced, so there is no exemption list.
 * <p>
 * <strong>This is narrower than the rule.</strong> It cannot see a service method re-typed in an
 * anonymous subclass, or a copy of anything else; it is the floor, not the rule.
 */
class TestsNeverWriteToABindingResultTest {

    private static final Pattern WRITES_A_BINDING_RESULT = Pattern.compile(
            "\\.rejectValue\\(|\\b\\w*[bB]indingResult\\.reject\\(");

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void noTestWritesToABindingResult() {
        List<String> violations;
        try (Stream<Path> paths = Files.walk(PROJECT_ROOT.resolve("src/test/java"))) {
            violations = paths.filter(path -> path.toString().endsWith(".java"))
                              .filter(TestsNeverWriteToABindingResultTest::writesABindingResult)
                              .map(path -> PROJECT_ROOT.relativize(path).toString())
                              .sorted()
                              .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertThat(violations)
                .as("A test writes to a BindingResult, which means it re-types a controller's "
                    + "catch block and decides for itself which field an error lands on. Call the "
                    + "real controller with a stub service programmed to throw instead:\n%s",
                    String.join("\n", violations))
                .isEmpty();
    }

    private static boolean writesABindingResult(Path file) {
        try {
            for (String line : Files.readAllLines(file)) {
                String stripped = line.stripLeading();
                if (stripped.startsWith("//") || stripped.startsWith("*")) {
                    continue;
                }
                if (WRITES_A_BINDING_RESULT.matcher(stripped).find()) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
