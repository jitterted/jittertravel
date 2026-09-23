package dev.ted.jittertravel.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
 * {@code src/test/java} is that copy. A test that needs a rejected form drives the real controller
 * through a {@code @WebMvcTest} instead.
 * <p>
 * <strong>This is narrower than the rule.</strong> It cannot see a service method re-typed in an
 * anonymous subclass, or a copy of anything else; it is the floor, not the rule.
 */
class TestsNeverWriteToABindingResultTest {

    private static final Pattern WRITES_A_BINDING_RESULT = Pattern.compile(
            "\\.rejectValue\\(|\\b\\w*[bB]indingResult\\.reject\\(");

    /**
     * Files that still carried the copy when this guard arrived (2026-09-22). <strong>Shrink-only</strong>:
     * a file that stops writing a binding result must come off, which {@link
     * #everyListedFileStillCarriesTheCopy} enforces — so the list cannot quietly outlive its reason.
     * Never add to it.
     */
    private static final Set<String> COPIES_NOT_YET_REPLACED = Set.of(
            "src/test/java/dev/ted/jittertravel/web/ChangeFlightControllerValidationTest.java");

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void noTestWritesToABindingResult() {
        List<String> violations = new ArrayList<>();
        for (String file : filesThatWriteABindingResult()) {
            if (!COPIES_NOT_YET_REPLACED.contains(file)) {
                violations.add(file);
            }
        }

        assertThat(violations)
                .as("A test writes to a BindingResult, which means it re-types a controller's "
                    + "catch block and decides for itself which field an error lands on. Drive the "
                    + "real controller through a @WebMvcTest instead:\n%s",
                    String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void everyListedFileStillCarriesTheCopy() {
        Set<String> stillCopying = filesThatWriteABindingResult();

        assertThat(COPIES_NOT_YET_REPLACED)
                .as("A listed file no longer writes a BindingResult (or is gone) — take it off "
                    + "COPIES_NOT_YET_REPLACED so the list only ever shrinks")
                .allMatch(stillCopying::contains);
    }

    private static Set<String> filesThatWriteABindingResult() {
        Set<String> files = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(PROJECT_ROOT.resolve("src/test/java"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                 .filter(TestsNeverWriteToABindingResultTest::writesABindingResult)
                 .map(path -> PROJECT_ROOT.relativize(path).toString())
                 .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
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
