package dev.ted.jittertravel.web;

import j2html.TagCreator;
import j2html.tags.DomContent;

import java.util.List;
import java.util.regex.Pattern;

import static j2html.TagCreator.*;

/**
 * Shared scaffolding for the standalone HTML documents our j2html renderers
 * produce: the {@code <head>} wiring (charset, page title, the global
 * {@code site.css} link, and a per-page {@code <style>} block) and the common
 * top {@code nav}. Keeps every renderer from repeating the same boilerplate.
 * <p>
 * Chrome styling shared across {@code .page} views (nav, h1, empty-state) lives
 * in {@code site.css}; each renderer's own {@code <style>} carries only the CSS
 * unique to that view (and its {@code .page} max-width).
 */
public final class Page {

    private Page() {
    }

    /**
     * The viewer's access tier, which decides <em>which</em> lateral nav links
     * {@link #viewNav} renders. Deny-by-default: a link is only shown to a tier
     * that can actually reach the page (a link to a page the viewer would 403 on
     * is both a papercut and a hint that the page exists), so an anonymous
     * visitor on the public calendar sees only the home link. The tiers mirror
     * the access rules in {@code SecurityConfig}.
     */
    public enum NavAudience {
        /** Not logged in — can see only the redacted calendar and the home page. */
        ANONYMOUS,
        /** Family — can view the itinerary and the full calendar only. */
        FAMILY,
        /** Ted — full access to every view. */
        OWNER;

        /**
         * Derives the tier from the two flags controllers already hold:
         * {@code isPublicUser} (no authenticated user) and {@code isOwner}
         * (the OWNER role). Anyone authenticated who is not the owner is FAMILY.
         */
        public static NavAudience of(boolean isPublicUser, boolean isOwner) {
            if (isPublicUser) {
                return ANONYMOUS;
            }
            return isOwner ? OWNER : FAMILY;
        }
    }

    private record NavLink(String label, String href) {
        DomContent render(String activePath) {
            if (href.equals(activePath)) {
                return span(label).withClass("active").attr("aria-current", "page");
            }
            return a(label).withHref(href);
        }
    }

    /**
     * The shared lateral navigation across the read-only view pages: a
     * flex-wrapping bar (no horizontal scroll — it wraps) linking each view to
     * the others the viewer may reach. The page the viewer is on ({@code
     * activePath}) renders as a non-link {@code <span class="active">} carrying
     * {@code aria-current="page"} so it reads as "you are here" rather than a
     * self-link. Styling lives in {@code site.css} under {@code .view-nav}.
     * <p>
     * The link set depends only on the viewer's tier, never on what the linked
     * pages happen to contain: Schedule Problems is always in the owner's bar,
     * and the report page renders its own empty state when the schedule is
     * clean. (The home card on {@code index.html} <em>is</em> state-aware; that
     * one lives in {@code GeneralController} and stays that way.)
     * <p>
     * <strong>The trailing slot is for the current page's controls, never for more
     * navigation.</strong> The link set stays a pure function of the viewer's tier; this is where a
     * page puts a control that has to be reachable from anywhere in a long document, which is a
     * different thing from a link to another view. Today's only caller is {@code CalendarRenderer},
     * whose "Jump to month" trigger has to stay on screen through 150 weeks of scrolling — and this
     * bar is the only thing on {@code /calendar} that does (see {@code docs/archived/YearOverviewPlan.md}
     * D9/Q3). Putting a second page's controls here would make a shared bar page-aware, which is
     * what the paragraph above exists to prevent.
     * <p>
     * Trailing content goes inside the {@code nav} rather than after it: that is what makes it
     * sticky and keeps it under the bar's opaque background, where a sibling would just scroll
     * away. It is also a flex item in a {@code flex-wrap} row that is {@code align-items: baseline},
     * so a control with its own padding and border needs rules to sit level with plain text links —
     * style it in the caller's CSS, not here.
     * <p>
     * Varargs rather than an overload, so the ten callers that pass no controls keep the call they
     * already had.
     */
    public static DomContent viewNav(NavAudience audience, String activePath, DomContent... trailing) {
        // The measuring script rides with the bar rather than being wired up per page: a page that
        // rendered the nav but forgot the script would stack its sticky layers against a zero.
        return each(
                nav(each(navLinks(audience), link -> link.render(activePath)))
                        .with(trailing)
                        .withClass("view-nav"),
                StickyLayerHeights.render());
    }

    private static List<NavLink> navLinks(NavAudience audience) {
        NavLink home = new NavLink("JitterTravel", "/");
        NavLink itinerary = new NavLink("Itinerary", "/itinerary");
        NavLink calendar = new NavLink("Calendar", "/calendar");
        return switch (audience) {
            case ANONYMOUS -> List.of(home);
            case FAMILY -> List.of(home, itinerary, calendar);
            case OWNER -> List.of(
                    home, itinerary, calendar,
                    new NavLink("Flights", "/booked-flights"),
                    new NavLink("Trains", "/booked-trains"),
                    new NavLink("Hotels", "/booked-hotels"),
                    new NavLink("Gatherings", "/planned-gatherings"),
                    new NavLink("Private Events", "/planned-private-events"),
                    new NavLink("Conferences", "/conferences"),
                    new NavLink("Schedule Problems", "/schedule-problems"));
        };
    }

    /**
     * The document {@code <head>}: UTF-8 charset, {@code title}, the shared
     * {@code site.css} stylesheet, and {@code pageCss} inlined as a
     * {@code <style>} block for view-specific rules — <strong>with its comments
     * removed</strong>, see {@link #withoutComments}.
     */
    public static DomContent head(String title, String pageCss) {
        return TagCreator.head(
                meta().withCharset("UTF-8"),
                title(title),
                link().withRel("stylesheet").withHref("/site.css"),
                rawHtml("<style>" + withoutComments(pageCss) + "</style>")
        );
    }

    private static final Pattern CSS_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern BLANK_LINE = Pattern.compile("(?m)^[ \\t]*\\r?\\n");

    /**
     * Strips {@code /* … *}{@code /} comments out of the inlined stylesheet, so a renderer's CSS
     * comments never reach a browser.
     * <p>
     * <strong>This is why the renderers may keep commenting their CSS freely</strong> (Ted,
     * 2026-09-07). The alternative was banning CSS comments and hoisting every one into Javadoc,
     * and it costs more than it saves: these comments are trap warnings — the {@code transform}
     * that silently becomes a containing block, {@code max-content} being the <em>unwrapped</em>
     * width of a wrapping flex row, the missing border-box reset — and their value is sitting on
     * the declaration somebody would otherwise tidy away. Prose hoisted above a 200-line CSS
     * constant loses that anchor, and a note that loses its anchor is the one that gets deleted.
     * <p>
     * Two things it buys beyond keeping them. <strong>It closes a leak by construction:</strong>
     * this stylesheet is inlined into every page including the anonymous {@code /calendar}, so a
     * comment naming an owner-only surface is a disclosure that no redaction test was watching for
     * — which is exactly how a comment naming {@code .year-overview} reached
     * {@code CalendarRedactionSecurityTest} on 2026-09-07. There is now no comment in the output to
     * name anything. And it takes roughly <strong>13 KB off every {@code /calendar} render</strong>,
     * uncached, on the iPad's connection.
     * <p>
     * <strong>Scope, deliberately narrow.</strong> Only the inlined {@code <style>} block. Inline
     * {@code <script>} text blocks are left alone — stripping JS comments safely means handling
     * regex literals and string contents, which is a different job with a worse failure mode.
     * {@code site.css} is served as a static file and never passes through here, and the Thymeleaf
     * templates carry their own {@code <style>} blocks; both are documented exceptions.
     * <p>
     * Non-greedy, so adjacent comments do not swallow the CSS between them. Safe against a comment
     * marker inside a value ({@code content:}, {@code url()}) only because no such value exists in
     * this tree — checked when this shipped, and the reason
     * {@code PageTest.aCommentMarkerInsideAValueWouldNotSurviveThis} is written down rather than
     * left as a surprise for whoever first needs one.
     */
    static String withoutComments(String css) {
        return BLANK_LINE.matcher(CSS_COMMENT.matcher(css).replaceAll("")).replaceAll("");
    }

}
