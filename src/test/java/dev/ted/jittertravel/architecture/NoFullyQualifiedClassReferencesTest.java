package dev.ted.jittertravel.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard: no Java source file may reference a project class by its
 * fully-qualified name in a code body. Use an import and the simple name instead.
 * <p>
 * Skipped lines: {@code package}, {@code import}, blank lines, and lines whose
 * first non-whitespace character is {@code *} or {@code //} (Javadoc / block
 * comment continuations and single-line comments).
 */
class NoFullyQualifiedClassReferencesTest {

    // Matches a FQCN that is NOT directly preceded by a quote character,
    // distinguishing type references in code from FQCN strings used as data.
    private static final Pattern FQCN =
            Pattern.compile("(?<!\")dev\\.ted\\.jittertravel\\.[a-z]+\\.[A-Z]");

    @Test
    void noFullyQualifiedProjectClassReferencesInCodeBodies() throws IOException {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        List<String> violations = new ArrayList<>();

        for (String srcDir : List.of("src/main/java", "src/test/java")) {
            Path root = projectRoot.resolve(srcDir);
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".java"))
                     .forEach(file -> collectViolations(file, projectRoot, violations));
            }
        }

        assertThat(violations)
                .as("Fully-qualified project class references found — add an import instead:\n%s",
                    String.join("\n", violations))
                .isEmpty();
    }

    private static void collectViolations(Path file, Path projectRoot, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String stripped = lines.get(i).stripLeading();
                if (stripped.isBlank()
                        || stripped.startsWith("package ")
                        || stripped.startsWith("import ")
                        || stripped.startsWith("//")
                        || stripped.startsWith("*")) {
                    continue;
                }
                if (FQCN.matcher(stripped).find()) {
                    String relative = projectRoot.relativize(file).toString();
                    violations.add(relative + ":" + (i + 1) + ": " + stripped.strip());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
