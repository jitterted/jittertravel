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
 * {@code EventSourcingConfig}, which is why that one file is exempt. This guard covers
 * {@code src/main/java} only; tests may read the wall clock freely.
 */
class NoAmbientClockReadsTest {

    /**
     * No-arg {@code now()} on the java.time types, plus the two {@code System} time
     * reads and the {@code Clock.system*} factories. The {@code Clock}-taking overloads
     * ({@code Instant.now(clock)}) carry an argument and so never match.
     */
    private static final Pattern AMBIENT_CLOCK_READ = Pattern.compile(
            "\\b(?:Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime"
            + "|OffsetTime|Year|YearMonth|MonthDay)\\.now\\(\\s*\\)"
            + "|\\bSystem\\.(?:currentTimeMillis|nanoTime)\\(\\s*\\)"
            + "|\\bClock\\.system(?:DefaultZone|UTC|)\\(");

    /** The one place real time is allowed to enter: the {@code Clock} bean factory. */
    private static final String CLOCK_BEAN_FACTORY = "EventSourcingConfig.java";

    @Test
    void productionCodeNeverReadsTheAmbientSystemClock() throws IOException {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path mainSources = projectRoot.resolve("src/main/java");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(mainSources)) {
            files.filter(path -> path.toString().endsWith(".java"))
                 .filter(path -> !path.getFileName().toString().equals(CLOCK_BEAN_FACTORY))
                 .forEach(file -> collectViolations(file, projectRoot, violations));
        }

        assertThat(violations)
                .as("Ambient system-clock reads found in production code — take the time from "
                    + "the injected Clock (Instant.now(clock) / clock.instant()) instead:\n%s",
                    String.join("\n", violations))
                .isEmpty();
    }

    private static void collectViolations(Path file, Path projectRoot, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String stripped = lines.get(i).stripLeading();
                if (stripped.isBlank()
                        || stripped.startsWith("import ")
                        || stripped.startsWith("//")
                        || stripped.startsWith("*")) {
                    continue;
                }
                if (AMBIENT_CLOCK_READ.matcher(stripped).find()) {
                    String relative = projectRoot.relativize(file).toString();
                    violations.add(relative + ":" + (i + 1) + ": " + stripped.strip());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
