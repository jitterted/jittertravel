package dev.ted.jittertravel.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard on the one page anonymous visitors can see.
 * <p>
 * {@code PublicCalendarProjector} builds every entry through its private {@code entry(...)}
 * helper, whose last argument is an {@code EntryDetails.Publishable} — the details records that
 * have no slot for an edit path, a cancel path, a maps URL or a hotel name. That is what makes the
 * public calendar an allow-list the compiler checks.
 * <p>
 * <strong>But only while the helper is the only way in.</strong> {@code CalendarEntry}'s canonical
 * constructor is public and accepts any {@code EntryDetails}, so a branch that calls
 * {@code new CalendarEntry(...)} directly — the obvious thing to do when copying a branch across
 * from an owner projector — would compile happily with {@code new EntryDetails.Gathering(infoUrl,
 * speaking, editPath)} in it, and publish Ted's edit path to the anonymous calendar. Nothing else
 * would fail: the runtime invariant in {@code PublicCalendarProjectorTest} only sees the kinds its
 * fixture feeds it.
 * <p>
 * So this test is what the javadoc's "compiler check rather than a convention" actually rests on.
 * It is a plain source scan, in the style of {@link NoAmbientClockReadsTest}, and it exists because
 * the guarantee is worth more than the constructor call it forbids.
 */
class PublicCalendarBuildsOnlyPublishableEntriesTest {

    private static final Path PUBLIC_PROJECTOR = Path.of(
            "src/main/java/dev/ted/jittertravel/application/PublicCalendarProjector.java");

    /** The two lines that legitimately name the constructor: the {@code entry(...)} overloads. */
    private static final Pattern ENTRY_HELPER = Pattern.compile(
            "private CalendarEntry entry\\(");

    private static final Pattern DIRECT_CONSTRUCTION = Pattern.compile("new CalendarEntry\\(");

    @Test
    void thePublicProjectorNeverCallsTheCalendarEntryConstructorOutsideItsPublishableHelper() {
        List<String> lines = readLines();
        List<Integer> helperBodies = helperBodyLines(lines);

        List<String> offenders = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = DIRECT_CONSTRUCTION.matcher(lines.get(i));
            if (matcher.find() && !helperBodies.contains(i)) {
                offenders.add((i + 1) + ": " + lines.get(i).strip());
            }
        }

        assertThat(offenders)
                .as("""
                    PublicCalendarProjector must build entries through its entry(...) helper, whose \
                    last argument is an EntryDetails.Publishable. Calling the CalendarEntry \
                    constructor directly accepts any EntryDetails — including the owner records \
                    that carry edit paths and maps URLs — and would publish them to the anonymous \
                    calendar. See CLAUDE.md, "Redaction: anonymous viewers are a first-class threat \
                    model", rule 1.""")
                .isEmpty();
    }

    /**
     * The line indices inside an {@code entry(...)} overload — that is, from its signature to the
     * closing brace of its body. Both overloads are one-expression methods, so a small window after
     * the signature is enough, and being generous here only ever makes the guard weaker in a way a
     * reader can see.
     */
    private static List<Integer> helperBodyLines(List<String> lines) {
        List<Integer> inside = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (ENTRY_HELPER.matcher(lines.get(i)).find()) {
                for (int j = i; j < lines.size(); j++) {
                    inside.add(j);
                    if (lines.get(j).equals("    }")) {
                        break;
                    }
                }
            }
        }
        return inside;
    }

    /** Both {@code entry(...)} overloads must still exist, or the scan above guards nothing. */
    @Test
    void thePublishableOnlyHelpersStillExist() {
        String source = String.join("\n", readLines());

        assertThat(source)
                .as("the helper's signature is the compiler check; renaming it must fail here")
                .contains("EntryDetails.Publishable details) {");
        assertThat(helperBodyLines(readLines()))
                .as("both entry(...) overloads are present")
                .isNotEmpty();
    }

    private static List<String> readLines() {
        try {
            return Files.readAllLines(PUBLIC_PROJECTOR);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + PUBLIC_PROJECTOR, e);
        }
    }
}
