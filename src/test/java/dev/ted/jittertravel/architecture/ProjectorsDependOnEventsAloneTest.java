package dev.ted.jittertravel.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard for R12: a read model is built from events alone, never from another read
 * model. A projector folds events plus the read-time criteria its caller supplies (R9, H8) — it may
 * not hold, be constructed with, or name another projector.
 * <p>
 * <strong>Composing read models is a different operation and stays allowed</strong>, one layer up
 * where the fan-in is visible: {@code CalendarAggregator} over the five calendar projectors, and
 * {@code CalendarController} handing {@code ScheduleGapProjector.awayDays()} to the renderer
 * alongside the calendar entries. Neither is a projector reading a projector, so neither is scanned
 * here — only classes whose name ends in {@code Projector} are.
 * <p>
 * A projector reading another inherits its staleness and its half-folded state mid-batch, and makes
 * the subscriber order in {@code EventSourcingConfig} load-bearing — an ordering dependency nothing
 * declares and no other test would catch. Where two read models genuinely need the same derived
 * value, they share the domain rule that derives it ({@code Place.of}), not the projection.
 * <p>
 * Written as a source scan rather than reflection so that a static call, a generic type argument and
 * a local variable are all caught, not just fields and parameters. Comments and string literals are
 * blanked before scanning, so a comment may name another projector — {@code BookedHotelsProjector}
 * deliberately does — while code may not.
 */
class ProjectorsDependOnEventsAloneTest {

    private static final Path APPLICATION = Path.of("src/main/java/dev/ted/jittertravel/application");

    /** Any identifier naming a read-side component: {@code ScheduleGapProjector}, {@code calendarAggregator}. */
    private static final Pattern READ_MODEL_NAME =
            Pattern.compile("\\b\\w*(?:Projector|Aggregator)\\b");

    @Test
    void noProjectorNamesAnotherReadModel() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path projector : projectorSources()) {
            String ownName = fileName(projector);
            String code = codeOnly(Files.readString(projector));
            Matcher matcher = READ_MODEL_NAME.matcher(code);
            while (matcher.find()) {
                if (namesItself(matcher.group(), ownName)) {
                    continue;
                }
                violations.add(ownName + " names " + matcher.group()
                               + " (line " + lineOf(code, matcher.start()) + ")");
            }
        }

        assertThat(violations)
                .as("A projector named another read model. A read model is built from events alone "
                    + "(R12): fold events and the criteria the caller supplies, and nothing else. "
                    + "Compose read models one layer up — an aggregator or a controller — or, when "
                    + "both need the same derived value, share the domain rule that derives it:\n%s",
                    String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void theScanReadsCodeAndIgnoresCommentsAndStringLiterals() {
        String source = """
                /** Derived by the same rule {@link ScheduleGapProjector} uses. */
                class SampleProjector {
                    // not alongside ScheduleGapProjector
                    private final String label = "CalendarAggregator";
                }
                """;

        String code = codeOnly(source);

        assertThat(code)
                .contains("class SampleProjector")
                .contains("private final String label")
                .doesNotContain("ScheduleGapProjector")
                .doesNotContain("CalendarAggregator");
    }

    @Test
    void theScanCoversEveryProjectorInTheApplicationPackage() throws IOException {
        assertThat(projectorSources())
                .as("the scan found no projectors — the source path has moved and this guard is inert")
                .hasSizeGreaterThan(20);
    }

    private static List<Path> projectorSources() throws IOException {
        try (Stream<Path> files = Files.walk(APPLICATION)) {
            return files.filter(path -> path.toString().endsWith("Projector.java"))
                        .sorted()
                        .toList();
        }
    }

    /**
     * A projector may of course name itself — its own declaration, its constructor, its
     * {@code toString}. Both spellings count: the type and the decapitalized variable form.
     */
    private static boolean namesItself(String name, String ownName) {
        return name.equals(ownName) || name.equals(decapitalized(ownName));
    }

    private static String decapitalized(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static String fileName(Path source) {
        String name = source.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    /**
     * The source with every comment and string literal blanked out, newlines kept so reported line
     * numbers still point at the file.
     */
    private static String codeOnly(String source) {
        char[] code = source.toCharArray();
        int index = 0;
        while (index < code.length) {
            if (startsWith(code, index, "//")) {
                index = blankUntil(code, index, "\n");
            } else if (startsWith(code, index, "/*")) {
                index = blankThrough(code, index + 2, "*/");
            } else if (startsWith(code, index, "\"\"\"")) {
                index = blankThrough(code, index + 3, "\"\"\"");
            } else if (code[index] == '"' || code[index] == '\'') {
                index = blankThroughLiteral(code, index);
            } else {
                index++;
            }
        }
        return new String(code);
    }

    private static boolean startsWith(char[] code, int index, String token) {
        if (index + token.length() > code.length) {
            return false;
        }
        return new String(code, index, token.length()).equals(token);
    }

    /** Blanks from {@code index} up to (not including) the next {@code token}, or end of input. */
    private static int blankUntil(char[] code, int index, String token) {
        while (index < code.length && !startsWith(code, index, token)) {
            blank(code, index++);
        }
        return index;
    }

    /** Blanks from {@code index} through the closing {@code token}, or end of input. */
    private static int blankThrough(char[] code, int index, String token) {
        int closing = blankUntil(code, index, token);
        for (int i = 0; i < token.length() && closing + i < code.length; i++) {
            blank(code, closing + i);
        }
        return closing + token.length();
    }

    /** Blanks a {@code "…"} or {@code '…'} literal, honouring backslash escapes. */
    private static int blankThroughLiteral(char[] code, int index) {
        char quote = code[index];
        blank(code, index++);
        while (index < code.length && code[index] != quote) {
            if (code[index] == '\\') {
                blank(code, index++);
            }
            blank(code, index++);
        }
        if (index < code.length) {
            blank(code, index++);
        }
        return index;
    }

    private static void blank(char[] code, int index) {
        if (index < code.length && code[index] != '\n') {
            code[index] = ' ';
        }
    }

    private static int lineOf(String code, int position) {
        return (int) code.substring(0, position).chars().filter(c -> c == '\n').count() + 1;
    }
}
