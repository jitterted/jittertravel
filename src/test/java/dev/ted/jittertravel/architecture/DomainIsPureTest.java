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
 * Architecture guard: the {@code domain} package states facts and rules about travel, and depends
 * on nothing that would make those facts untestable or untellable — no I/O or serialization library,
 * no framework, no clock, no randomness, no presentation. See CLAUDE.md and
 * {@code docs/archived/CuratedResolversToDomainPlan.md} for the rule this enforces.
 * <p>
 * The import scan is the load-bearing half: a package whose every import is {@code java.*} or its
 * own can reach a file, a socket, Spring or Jackson only through a class it cannot name. That covers
 * the framework, I/O and presentation clauses in one assertion, which is why it is written as a
 * whitelist of two prefixes rather than a blacklist of the libraries we happen to use today. A
 * curated in-memory table — {@code LocationZoneResolver}, {@code StaticAirportCityResolver} — is
 * data, not I/O, and passes it unchanged.
 * <p>
 * The clock clause is already covered by {@link NoAmbientClockReadsTest} over all of
 * {@code src/main/java}; randomness is checked here because nothing else looks for it.
 * <p>
 * <strong>{@code UUID} is deliberately not scanned for.</strong> Seven {@code *Id.random()}
 * factories call {@code UUID.randomUUID()} in the domain, and they stay (Ted, 2026-08-23): they have
 * no {@code src/main} call site at all, so production already mints ids at the boundary and passes
 * them inward, exactly as it does {@code now}. That — the live half of the rule — is what
 * {@link #idsAreMintedAtTheBoundaryNeverInsideProductionCode()} pins, rather than the 407 test call
 * sites that would have to be rewritten to delete the factories.
 */
class DomainIsPureTest {

    private static final Path DOMAIN = Path.of("src/main/java/dev/ted/jittertravel/domain");

    /** An import line's fully-qualified name, static or not. */
    private static final Pattern IMPORTED_NAME =
            Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)");

    /** {@code Math.random()} and {@code new Random(...)} — {@code UUID} is exempt, see above. */
    private static final Pattern RANDOMNESS =
            Pattern.compile("\\bMath\\.random\\(\\s*\\)|\\bnew\\s+(?:java\\.util\\.)?Random\\s*\\(");

    @Test
    void domainImportsNothingOutsideJavaAndItself() throws IOException {
        List<String> violations = scanDomain((line, report) -> {
            if (!line.startsWith("import ")) {
                return;
            }
            var matcher = IMPORTED_NAME.matcher(line);
            if (matcher.find()
                    && !matcher.group(1).startsWith("java.")
                    && !matcher.group(1).startsWith("dev.ted.jittertravel.domain.")) {
                report.run();
            }
        });

        assertThat(violations)
                .as("The domain package imported something outside java.* and its own package. "
                    + "Domain types state facts about travel: no I/O or serialization library, no "
                    + "framework, no clock, no presentation. Put the adapter in infrastructure and "
                    + "keep the interface here:\n%s", String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void domainNeverReachesForRandomness() throws IOException {
        List<String> violations = scanDomain((line, report) -> {
            if (RANDOMNESS.matcher(line).find()) {
                report.run();
            }
        });

        assertThat(violations)
                .as("Randomness found in the domain — a value the domain invents is a value a test "
                    + "cannot fix. Draw it at the boundary and pass it inward, as with the clock:\n%s",
                    String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void idsAreMintedAtTheBoundaryNeverInsideProductionCode() throws IOException {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Pattern idFactory = Pattern.compile("\\b[A-Z][A-Za-z0-9]*Id\\.random\\(\\s*\\)");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(projectRoot.resolve("src/main/java"))) {
            files.filter(path -> path.toString().endsWith(".java"))
                 .forEach(file -> collectViolations(
                         file, projectRoot, violations,
                         (line, report) -> {
                             if (idFactory.matcher(line).find()) {
                                 report.run();
                             }
                         }));
        }

        assertThat(violations)
                .as("Production code called an *Id.random() factory. Ids are minted at the "
                    + "boundary — a controller does UUID.randomUUID() and passes the value inward — "
                    + "so that an event's id is a fixed input to every test below it. The factories "
                    + "exist for tests only:\n%s", String.join("\n", violations))
                .isEmpty();
    }

    private static List<String> scanDomain(LineCheck check) throws IOException {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(projectRoot.resolve(DOMAIN))) {
            files.filter(path -> path.toString().endsWith(".java"))
                 .forEach(file -> collectViolations(file, projectRoot, violations, check));
        }
        return violations;
    }

    private static void collectViolations(Path file,
                                          Path projectRoot,
                                          List<String> violations,
                                          LineCheck check) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String stripped = lines.get(i).stripLeading();
                if (stripped.isBlank() || stripped.startsWith("//") || stripped.startsWith("*")) {
                    continue;
                }
                int lineNumber = i + 1;
                check.inspect(stripped, () -> violations.add(
                        projectRoot.relativize(file) + ":" + lineNumber + ": " + stripped.strip()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    private interface LineCheck {
        void inspect(String strippedLine, Runnable reportViolation);
    }
}
