package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The banner is wired by {@link ProblemContextAdvice}, which no controller mentions — that is the
 * accepted cost of handling it in one place instead of giving six unrelated controllers a
 * dependency they do not otherwise use (D6). This test is what stops it from being invisible: it
 * walks every href {@link ProblemFix} can produce and fails when the page it lands on does not
 * render the fragment.
 * <p>
 * A new fix target therefore fails here twice over — once because its path is not in
 * {@link #TEMPLATE_FOR_PATH}, and again if its template forgets the fragment. That is deliberate:
 * a form Ted arrives at from the report and which cannot say why he is there is exactly the
 * papercut this whole change is about.
 */
class ProblemContextFragmentConventionTest {

    private static final String FRAGMENT_REFERENCE = "~{fragments/problem-context :: problemContext}";
    private static final Path FRAGMENT = TemplateSources.ROOT.resolve("fragments/problem-context.html");

    /** Every path a fix link can point at, and the template that answers it. */
    private static final Map<String, String> TEMPLATE_FOR_PATH = Map.of(
            "/book-hotel", "book-hotel.html",
            "/book-flight", "book-flight.html",
            "/book-train", "book-train.html",
            "/plan-ground-transfer", "plan-ground-transfer.html",
            "/booked-hotels/{id}/cancel", "cancel-hotel.html",
            // Both reached from an OverlappingTravel fix link: "cancel one of these two legs".
            // A cancel page IS a legitimate fix target — cancel-hotel has been one since the
            // duplicate-hotel fixes shipped — and the banner is what says why Ted is there.
            "/booked-trains/{id}/cancel", "cancel-train.html",
            "/ground-transfers/{id}/cancel", "cancel-ground-transfer.html",
            "/clear-conflict", "clear-conflict.html");

    private final TemplateSources templates = new TemplateSources();

    @Test
    void everyPageAFixLinkReachesRendersTheSharedBanner() {
        Set<String> paths = fixTargetPaths();

        assertThat(paths).isNotEmpty();
        assertThat(paths)
                .as("a fix link points somewhere this test does not know about — add it to "
                    + "TEMPLATE_FOR_PATH, and put the fragment on that page")
                .isSubsetOf(TEMPLATE_FOR_PATH.keySet());
        assertThat(paths).allSatisfy(path -> {
            Path template = TemplateSources.ROOT.resolve(TEMPLATE_FOR_PATH.get(path));
            assertThat(templates.read(template))
                    .as("%s is reached from a fix link, so it must say why Ted is there", path)
                    .contains(FRAGMENT_REFERENCE);
        });
    }

    @Test
    void onlyTheFragmentRendersTheBannerMarkup() {
        assertThat(templates.containing("class=\"problem-context\""))
                .as("a page with its own copy of the banner is a copy that can drift")
                .containsExactly(FRAGMENT);
    }

    /**
     * The advice supplies {@code problemContext} and leaves it null on every page reached any other
     * way — from the index nav card, the calendar day-menu, or a POST. Without the guard the
     * fragment would blow up on exactly those pages.
     */
    @Test
    void theFragmentRendersNothingWhenThereIsNoProblemToExplain() {
        assertThat(templates.read(FRAGMENT))
                .contains("th:if=\"${problemContext != null}\"");
    }

    /**
     * The other half of a fix link's round trip: having arrived, the action has to come <em>back</em>.
     * <p>
     * Ted, 2026-09-06: <em>"I'm fixing schedule problems and keep ending up somewhere else and have
     * to return to the schedule problems page."</em> The banner's Back link only helps someone who
     * changes their mind; a reader who actually performs the fix lands wherever that controller
     * always redirected. So every fix target's form carries the origin through its own POST, and
     * this fails when a new one forgets — the same silent wiring the banner has, guarded the same
     * way.
     */
    @Test
    void everyPageAFixLinkReachesCarriesItsOriginThroughThePost() {
        Set<String> paths = fixTargetPaths();

        assertThat(paths).isNotEmpty();
        assertThat(paths).allSatisfy(path -> {
            Path template = TemplateSources.ROOT.resolve(TEMPLATE_FOR_PATH.get(path));
            assertThat(templates.read(template))
                    .as("%s is reached from a fix link, so its form must carry ?from= through the "
                        + "POST or the action lands somewhere else", path)
                    .contains("name=\"from\"")
                    .contains("th:value=\"${fixOrigin}\"");
        });
    }

    /**
     * The origin decides a <strong>redirect</strong>, and it arrives in a query parameter — so it
     * has to be resolved through {@link FixOrigin} and never used as a path. An unrecognized value
     * comes back as the calendar, which is what stops a hand-edited {@code ?from=} being an open
     * redirect.
     */
    @Test
    void anUnknownOriginCannotBecomeARedirectTarget() {
        assertThat(FixOrigin.returnTo("https://evil.example.com"))
                .contains("/schedule-problems?view=calendar");
        assertThat(FixOrigin.returnTo("/admin/database"))
                .contains("/schedule-problems?view=calendar");
    }

    /**
     * And absent must stay distinguishable from {@code calendar}: an ordinary booking made from the
     * nav card or a calendar day-menu has no origin, and must keep its controller's own landing
     * place rather than being sent to a report it never came from.
     */
    @Test
    void noOriginLeavesTheControllersOwnDestinationAlone() {
        assertThat(FixOrigin.returnTo(null))
                .isEmpty();
        assertThat(FixOrigin.returnTo("  "))
                .isEmpty();
    }

    /**
     * Every path {@link ProblemFix} emits, with the query string dropped and hotel ids collapsed —
     * one sample of every {@link ScheduleProblem} variant, so a new variant with a new target
     * shows up here.
     */
    private Set<String> fixTargetPaths() {
        Set<String> paths = new LinkedHashSet<>();
        for (ScheduleProblem problem : oneOfEveryProblem()) {
            for (ProblemFix fix : ProblemFix.forProblem(problem, FixOrigin.PROBLEM_LIST)) {
                String path = fix.href().split("\\?")[0];
                paths.add(path
                        .replaceFirst("^/booked-hotels/[^/]+/cancel$", "/booked-hotels/{id}/cancel")
                        .replaceFirst("^/booked-trains/[^/]+/cancel$", "/booked-trains/{id}/cancel")
                        .replaceFirst("^/ground-transfers/[^/]+/cancel$", "/ground-transfers/{id}/cancel"));
            }
        }
        return paths;
    }

    private static List<ScheduleProblem> oneOfEveryProblem() {
        ZoneId denver = ZoneId.of("America/Denver");
        return List.of(
                new ScheduleProblem.MissingHotel("Denver",
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18), "dev2next"),
                new ScheduleProblem.MissingTravel("Denver",
                        zoned(LocalDateTime.of(2026, 9, 14, 11, 30), denver),
                        "Lone Tree", zoned(LocalDateTime.of(2026, 9, 15, 9, 0), denver)),
                new ScheduleProblem.DuplicateHotel(LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 28),
                        List.of(new ScheduleProblem.DuplicateStay(HotelBookingId.random(),
                                        "Reichshof", "Hamburg", BookingIntent.FINAL),
                                new ScheduleProblem.DuplicateStay(HotelBookingId.random(),
                                        "Park Hotel", "Soltau", BookingIntent.TENTATIVE))),
                new ScheduleProblem.DifferentCityConflict("Aachen JUG", "Aachen", "DDD Europe", "Antwerp",
                        LocalDate.of(2026, 6, 11), GatheringId.random(), ConferenceId.random()),
                new ScheduleProblem.SchedulingConflict(
                        new ScheduleProblem.ConflictingGathering("Aachen JUG", "Aachen",
                                zoned(LocalDateTime.of(2026, 9, 8, 19, 0), denver),
                                zoned(LocalDateTime.of(2026, 9, 8, 22, 0), denver)),
                        new ScheduleProblem.ConflictingGathering("Cologne JUG", "Cologne",
                                zoned(LocalDateTime.of(2026, 9, 8, 20, 0), denver),
                                zoned(LocalDateTime.of(2026, 9, 8, 23, 0), denver))));
    }

    private static ZonedTimestamp zoned(LocalDateTime local, ZoneId zone) {
        return ZonedTimestamp.fromLocal(local, zone);
    }
}
