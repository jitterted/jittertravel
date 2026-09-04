package dev.ted.jittertravel.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>Never have an affordance that relies on {@code :hover}</strong> (Ted, 2026-09-04).
 * <p>
 * A control whose only visible sign of being a control appears when a pointer is over it is a
 * hidden affordance — and on the iPad, this app's primary target, there is <em>no pointer</em>, so
 * such a control is invisible at every moment. CLAUDE.md already said this about menus ("a control
 * that only reveals itself to someone who already knows to click"); this is the same rule stated
 * for every control, and enforced.
 * <p>
 * <strong>What it caught.</strong> On {@code /conferences}, the "CFP date unknown" link — the one
 * affordance for recording a conference's CFP — was {@code color: inherit} inside a
 * {@code var(--muted-text)} sub-line with {@code :hover {text-decoration: underline}}. It rendered
 * as muted grey text identical to the non-link sub-lines beside it, and its own comment said "the
 * underline on hover is the affordance". Ted could not find it (2026-09-04).
 * <p>
 * <strong>The mechanical rule, and it is narrower than the English one.</strong> A selector whose
 * {@code :hover} rule is where {@code text-decoration: underline} first appears must declare its
 * own {@code color} in its base rule — not {@code color: inherit}, which hands its visibility to
 * whatever encloses it. That is exactly the shape of the bug above.
 * <p>
 * <strong>What it therefore does not catch</strong>, so nobody trusts it further than it goes: a
 * hover that reveals by {@code opacity}, {@code display} or {@code visibility}; an element with no
 * base rule at all (it keeps the browser's own link colour, which <em>is</em> a visible
 * affordance); a colour that is declared but too close to its surroundings to see; and anything
 * that is not a CSS class. The English rule is the one to apply when reviewing — this test is the
 * floor, not the ceiling.
 * <p>
 * Legitimately unflagged today: {@code .edit-pencil} and {@code .cancel-bin} are SVG icons, always
 * visible, whose hover changes nothing about whether you can see them; {@code .yo-month} is a whole
 * mini-calendar inside a panel labelled "Jump to month"; {@code .entry-detail a} is underlined
 * permanently already.
 */
class HoverIsNeverTheAffordanceTest {

    private static final Path RENDERERS = Path.of("src/main/java/dev/ted/jittertravel/web");
    private static final Path SITE_CSS = Path.of("src/main/resources/static/site.css");

    /**
     * {@code .some-class:hover { … }} and {@code .some-class { … }} alike — selector, then body.
     * <p>
     * The {@code :} in the class is load-bearing and was missing at first: without it nothing
     * ending in {@code :hover} matched, so the tree-wide scan passed over a tree that had two real
     * violations in it. {@code aHoverOnlyUnderlineOnAnInheritedColourIsReported} is what found
     * that, which is the argument for having written it.
     * <p>
     * No {@code ,}: a selector list is matched from its last selector on, so a rule written
     * {@code .a:hover, .b:hover { … }} is only half seen. Nothing in the tree does that today.
     */
    private static final Pattern RULE = Pattern.compile(
            "([.#][A-Za-z0-9_\\-.:\\[\\]=\"'>+~* ]*?)\\s*\\{([^{}]*)\\}");

    @Test
    void noControlDependsOnHoverToLookLikeOne() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : styleSources()) {
            violations.addAll(violationsIn(source.toString(), Files.readString(source)));
        }

        assertThat(violations)
                .as("""
                    A control's affordance may not depend on :hover. The iPad has no pointer, so a \
                    control that only reveals itself on hover is invisible there at every moment. \
                    Give the base rule its own colour (or a permanent underline, a border, an \
                    icon) and let :hover be reinforcement:
                    %s""", String.join("\n", violations))
                .isEmpty();
    }

    /**
     * The positive case. Without it a broken regex leaves a scan that is green over any tree at
     * all — the gap {@code ProjectorsDependOnEventsAloneTest} was found to have on 2026-09-04.
     */
    @Test
    void aHoverOnlyUnderlineOnAnInheritedColourIsReported() {
        String css = """
                .conf-cfp { color: inherit; text-decoration: none; }
                .conf-cfp:hover { text-decoration: underline; }
                """;

        assertThat(violationsIn("Sample.java", css))
                .containsExactly(
                        "Sample.java: .conf-cfp is `color: inherit` and only underlines on :hover");
    }

    @Test
    void aLinkThatDeclaresItsOwnColourIsFine() {
        String css = """
                .conf-action { color: var(--accent-color); text-decoration: none; }
                .conf-action:hover { text-decoration: underline; }
                """;

        assertThat(violationsIn("Sample.java", css)).isEmpty();
    }

    /** A permanent underline is a visible affordance, so hover may then do whatever it likes. */
    @Test
    void aPermanentUnderlineIsFineEvenOnAnInheritedColour() {
        String css = """
                .conf-cfp { color: inherit; text-decoration: underline; }
                .conf-cfp:hover { color: var(--accent-color); }
                """;

        assertThat(violationsIn("Sample.java", css)).isEmpty();
    }

    @Test
    void theScanReachesEveryRendererAndTheStylesheet() throws IOException {
        assertThat(styleSources())
                .as("the scan found almost nothing — the source paths have moved and it is inert")
                .hasSizeGreaterThan(20)
                .contains(SITE_CSS);
    }

    private static List<String> violationsIn(String where, String css) {
        Map<String, String> bodies = new LinkedHashMap<>();
        Matcher matcher = RULE.matcher(css);
        while (matcher.find()) {
            // Later rules win in CSS, and a selector repeated with a different body is rare enough
            // that keeping the first is a safe simplification; note it if that ever stops holding.
            bodies.putIfAbsent(matcher.group(1).trim(), matcher.group(2));
        }

        List<String> violations = new ArrayList<>();
        bodies.forEach((selector, body) -> {
            if (!selector.endsWith(":hover") || !underlines(body)) {
                return;
            }
            String base = selector.substring(0, selector.length() - ":hover".length()).trim();
            String baseBody = bodies.get(base);
            // No base rule at all means the browser's own link colour still applies, which is a
            // perfectly visible affordance — it is `color: inherit` that throws that away.
            if (baseBody != null && baseBody.contains("color: inherit") && !underlines(baseBody)) {
                violations.add(where + ": " + base + " is `color: inherit` and only underlines on :hover");
            }
        });
        return violations;
    }

    private static boolean underlines(String body) {
        return body.contains("text-decoration: underline");
    }

    private static List<Path> styleSources() throws IOException {
        try (Stream<Path> files = Files.walk(RENDERERS)) {
            List<Path> sources = new ArrayList<>(
                    files.filter(path -> path.toString().endsWith(".java")).sorted().toList());
            sources.add(SITE_CSS);
            return sources;
        }
    }
}
