package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.infrastructure.SessionizePrefillService.SessionizePrefill;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Everything here drives {@code assemble(...)}, the seam with no I/O, against the real documents
 * Sessionize served for {@code jfokus-2027} on 2026-08-22 — well over 30 lines each, so files
 * rather than inline text blocks.
 * <p>
 * The invariant most worth watching is the last group: each field is independently optional, so a
 * restyled page must degrade into <em>blanks</em>, never into wrong values and never into an
 * exception.
 */
class SessionizePrefillServiceTest {

    private static final Path FIXTURES = Path.of("src/test/resources/sessionize");

    /** Stands in when a document yields nothing at all, so a test can assert on the fields anyway. */
    private static final SessionizePrefill EMPTY =
            new SessionizePrefill("", "", "", "", "", "", "", "", "", "");

    private final SessionizePrefillService service =
            new SessionizePrefillService(RestClient.builder(), new LocationZoneResolver());

    private final String ics = crlf(fixture("jfokus-2027-cfs.ics"));
    private final String html = fixture("jfokus-2027.html");

    @Nested
    class TheUrlIsValidatedToASlug {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://sessionize.com/jfokus-2027/",
                "https://sessionize.com/jfokus-2027",
                "https://www.sessionize.com/jfokus-2027/",
                "http://sessionize.com/jfokus-2027/",
                "  https://sessionize.com/jfokus-2027/  "
        })
        void aSessionizeUrlYieldsItsSlug(String url) {
            assertThat(service.slugFrom(url)).isEqualTo("jfokus-2027");
        }

        @Test
        void trackingParametersAndFragmentsAreDroppedRatherThanRefused() {
            assertThat(service.slugFrom("https://sessionize.com/jfokus-2027/?utm_source=twitter"))
                    .isEqualTo("jfokus-2027");
            assertThat(service.slugFrom("https://sessionize.com/jfokus-2027/#cfs"))
                    .isEqualTo("jfokus-2027");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://evil.example.com/jfokus-2027/",
                "https://sessionize.com.evil.example.com/jfokus-2027/",
                "https://sessionize.com/jfokus-2027/sessions/12345",
                "https://sessionize.com/../admin",
                "file:///etc/passwd",
                "not a url at all",
                ""
        })
        void anythingElseYieldsNoSlugSoNoRequestIsEverMade(String url) {
            assertThat(service.slugFrom(url))
                    .as("a fixed baseUrl plus a letters-digits-hyphens slug is the SSRF gate")
                    .isEmpty();
        }

        @Test
        void aNullUrlYieldsNoSlug() {
            assertThat(service.slugFrom(null)).isEmpty();
        }
    }

    @Nested
    class TheRealJfokusPage {

        @Test
        void fillsEveryFieldSessionizeActuallyCarries() {
            SessionizePrefill prefill = assembled();

            assertThat(prefill.name())
                    .as("og:title reads 'Jfokus 2027: Call for Speakers'; the suffix is not the name")
                    .isEqualTo("Jfokus 2027");
            assertThat(prefill.infoUrl()).isEqualTo("https://www.jfokus.se/");
            assertThat(prefill.venueName()).isEqualTo("Stockholm Waterfront Congress Centre");
            assertThat(prefill.venueCity()).isEqualTo("Stockholm");
            assertThat(prefill.venueCountry()).isEqualTo("Sweden");
        }

        @Test
        void datesCarryTheGuessedTimesOfDayBecauseThePageStatesNone() {
            SessionizePrefill prefill = assembled();

            assertThat(prefill.startDate()).isEqualTo("2027-02-08T09:00");
            assertThat(prefill.endDate()).isEqualTo("2027-02-10T17:00");
        }

        @Test
        void theDeadlineIsTheIcsInstantExpressedInTheVenueZone() {
            SessionizePrefill prefill = assembled();

            // DTSTART is 20261001T063000Z, and the page itself prints "Call closes at 8:30 AM" —
            // so the conversion is verified against Sessionize's own wall clock, not just our
            // arithmetic. Filling 06:30 here would be two hours early and invisible on the form.
            assertThat(prefill.cfpClosesOn()).isEqualTo("2026-10-01T08:30");
            assertThat(prefill.deadlineZone())
                    .as("never show a time whose zone is unstated")
                    .isEqualTo("Europe/Stockholm");
        }

        @Test
        void theSubmissionUrlIsThePastedUrlNormalized() {
            assertThat(service.assemble("jfokus-2027", ics, html).orElseThrow().cfpSubmissionUrl())
                    .isEqualTo("https://sessionize.com/jfokus-2027/");
        }

        private SessionizePrefill assembled() {
            return service.assemble("jfokus-2027", ics, html).orElseThrow();
        }
    }

    @Nested
    class TheIcsIsReadAsRfc5545 {

        @Test
        void aFoldedSummaryIsUnfoldedRatherThanReadAsATruncatedOne() {
            // The real file already folds DESCRIPTION across three lines (a continuation line
            // begins with a space), and a long enough conference name folds SUMMARY the same way.
            // A reader that skips unfolding does not fail here — it silently returns half a name,
            // which is the mangled-value failure, not the blank-field one.
            assertThat(ics).contains("\r\n e alarm/notification");
            String longName = ics.replace("SUMMARY:Jfokus 2027: deadline to submit a session",
                                          "SUMMARY:Jfokus Stockholm 2027: deadline to submit a s\r\n ession");

            SessionizePrefill prefill =
                    service.assemble("jfokus-2027", longName, "<html>redesigned</html>").orElseThrow();

            assertThat(prefill.name()).isEqualTo("Jfokus Stockholm 2027");
        }

        @Test
        void theAlarmsOwnSummaryIsNeverMistakenForTheEventsName() {
            // The VALARM carries a SUMMARY of its own, so "the first SUMMARY in the file" is one
            // reordering away from naming the conference after a reminder.
            String noEventSummary = ics
                    .replace("SUMMARY:Jfokus 2027: deadline to submit a session\r\nUID:", "UID:")
                    .replace("SUMMARY:Jfokus 2027: deadline to submit a session\r\nTRIGGER:",
                             "SUMMARY:Reminder 47: deadline to submit a session\r\nTRIGGER:");

            SessionizePrefill prefill =
                    service.assemble("jfokus-2027", noEventSummary, "<html>redesigned</html>")
                           .or(() -> Optional.of(EMPTY))
                           .orElseThrow();

            assertThat(prefill.name())
                    .as("no name is right; the alarm's label is wrong")
                    .isEmpty();
        }

        @Test
        void theNameFallsBackToTheIcsSummaryWhenThePageIsUnreadable() {
            SessionizePrefill prefill = service.assemble("jfokus-2027", ics, "<html>redesigned</html>")
                                               .orElseThrow();

            assertThat(prefill.name())
                    .as("SUMMARY reads 'Jfokus 2027: deadline to submit a session'")
                    .isEqualTo("Jfokus 2027");
        }

        @Test
        void anIcsWithNoDtstartLeavesTheDeadlineBlank() {
            String noDeadline = ics.replace("DTSTART:20261001T063000Z", "DTSTART:");

            SessionizePrefill prefill = service.assemble("jfokus-2027", noDeadline, html).orElseThrow();

            assertThat(prefill.cfpClosesOn()).isEmpty();
            assertThat(prefill.venueCity())
                    .as("one document failing must not cost the other its fields")
                    .isEqualTo("Stockholm");
        }

        @Test
        void anUnparseableDtstartIsABlankDeadlineRatherThanAnException() {
            String garbled = ics.replace("DTSTART:20261001T063000Z", "DTSTART:the first of October");

            assertThat(service.assemble("jfokus-2027", garbled, html).orElseThrow().cfpClosesOn())
                    .isEmpty();
        }

        @Test
        void aMissingIcsLeavesTheDeadlineBlankAndStillFillsThePageFields() {
            SessionizePrefill prefill = service.assemble("jfokus-2027", null, html).orElseThrow();

            assertThat(prefill.cfpClosesOn()).isEmpty();
            assertThat(prefill.name()).isEqualTo("Jfokus 2027");
        }
    }

    @Nested
    class TheSubmissionUrlNeverTravelsWithoutADeadline {

        @Test
        void becauseThePairIsRefusedAtSubmit() {
            // CfpDeadlineMissing. A reachable Sessionize page whose .ics fails to parse produces
            // exactly this pair, so it is the prefill that is most likely to trip that refusal.
            SessionizePrefill prefill = service.assemble("jfokus-2027", null, html).orElseThrow();

            assertThat(prefill.cfpClosesOn()).isEmpty();
            assertThat(prefill.cfpSubmissionUrl())
                    .as("Submit At is filled only when Closes On was")
                    .isEmpty();
        }

        @Test
        void andAnUnresolvableCityLeavesBothBlankForTheSameReason() {
            String elsewhere = html.replace("Stockholm, Sweden", "Kiribati City, Nowherestan");

            SessionizePrefill prefill = service.assemble("jfokus-2027", ics, elsewhere).orElseThrow();

            assertThat(prefill.cfpClosesOn())
                    .as("a deadline written in the wrong zone is silently wrong, so it is not written")
                    .isEmpty();
            assertThat(prefill.cfpSubmissionUrl()).isEmpty();
            assertThat(prefill.deadlineZone()).isEmpty();
            assertThat(prefill.venueCity())
                    .as("the city is still prefilled — it just did not resolve to a zone")
                    .isEqualTo("Kiribati City");
        }
    }

    @Nested
    class EveryFieldIsIndependentlyOptional {

        @Test
        void aRestyledPageIsAPartialFillNeverAnException() {
            Optional<SessionizePrefill> prefill =
                    service.assemble("jfokus-2027", ics, "<html><body>completely redesigned</body></html>");

            assertThat(prefill).isPresent();
            assertThat(prefill.orElseThrow().venueName()).isEmpty();
            assertThat(prefill.orElseThrow().startDate()).isEmpty();
            assertThat(prefill.orElseThrow().name())
                    .as("the .ics half does not rot when the HTML half does")
                    .isEqualTo("Jfokus 2027");
        }

        @Test
        void aMissingVenueBlockLeavesTheOtherFieldsIntact() {
            String noVenue = html.replace("<span class=\"block\">", "<span class=\"restyled\">");

            SessionizePrefill prefill = service.assemble("jfokus-2027", ics, noVenue).orElseThrow();

            assertThat(prefill.venueName()).isEmpty();
            assertThat(prefill.venueCity()).isEmpty();
            assertThat(prefill.name()).isEqualTo("Jfokus 2027");
            assertThat(prefill.startDate()).isEqualTo("2027-02-08T09:00");
            assertThat(prefill.infoUrl()).isEqualTo("https://www.jfokus.se/");
        }

        @Test
        void aMissingWebsiteBlockLeavesTheOtherFieldsIntact() {
            String noWebsite = html.replace("class=\"navy-link\"", "class=\"restyled\"")
                                   .replace("https://www.jfokus.se/", "");

            SessionizePrefill prefill = service.assemble("jfokus-2027", ics, noWebsite).orElseThrow();

            assertThat(prefill.infoUrl()).isEmpty();
            assertThat(prefill.venueCity()).isEqualTo("Stockholm");
        }

        @Test
        void anUnreadableDateLeavesThatFieldBlankAndKeepsTheOther() {
            String garbledStart = html.replace("<h2 class=\"no-margins\">8 Feb 2027</h2>",
                                               "<h2 class=\"no-margins\">next February-ish</h2>");

            SessionizePrefill prefill = service.assemble("jfokus-2027", ics, garbledStart).orElseThrow();

            assertThat(prefill.startDate()).isEmpty();
            assertThat(prefill.endDate()).isEqualTo("2027-02-10T17:00");
        }

        @Test
        void bothDatePaddingsParseBecauseSessionizeUsesBoth() {
            // Event dates render unpadded (8 Feb 2027), CFP dates padded (01 Oct 2026).
            String padded = html.replace("<h2 class=\"no-margins\">8 Feb 2027</h2>",
                                         "<h2 class=\"no-margins\">08 Feb 2027</h2>");

            assertThat(service.assemble("jfokus-2027", ics, padded).orElseThrow().startDate())
                    .isEqualTo("2027-02-08T09:00");
        }

        @Test
        void aNameCarryingAnAmpersandIsDecodedRatherThanRecordedRaw() {
            // og:title is an attribute, so it arrives entity-encoded. Unlike a blank field, a
            // mangled name lands in an event, where it is permanent.
            String devoxx = html.replace("content=\"Jfokus 2027: Call for Speakers\"",
                                         "content=\"Devoxx &amp; Friends 2027: Call for Speakers\"");

            assertThat(service.assemble("devoxx-2027", ics, devoxx).orElseThrow().name())
                    .isEqualTo("Devoxx & Friends 2027");
        }

        @Test
        void anEscapedEntityIsDecodedOnceAndNotTwice() {
            String tricky = html.replace("content=\"Jfokus 2027: Call for Speakers\"",
                                         "content=\"&amp;lt;Code/&amp;gt; 2027: Call for Speakers\"");

            assertThat(service.assemble("code-2027", ics, tricky).orElseThrow().name())
                    .as("&amp; is decoded last, or &amp;lt; would collapse all the way to <")
                    .isEqualTo("&lt;Code/&gt; 2027");
        }

        @Test
        void garbageInBothDocumentsIsEmptyRatherThanAnException() {
            assertThat(service.assemble("whatever", "}{ not a calendar", "<<<>>> not html"))
                    .as("nothing usable came back, which the controller reports as 422")
                    .isEmpty();
        }

        @Test
        void twoNullDocumentsAreEmptyRatherThanAnException() {
            assertThat(service.assemble("whatever", null, null)).isEmpty();
        }
    }

    /**
     * The calendar fixture with RFC 5545's own line endings, whatever git handed us.
     * <p>
     * Sessionize really serves CRLF, as the spec requires, and unfolding is precisely a question
     * about line endings — so a checkout that normalized the file to LF would leave the tests below
     * replacing strings that are no longer there, passing while pinning nothing. Normalizing here
     * makes them independent of anyone's {@code core.autocrlf}.
     */
    private String crlf(String text) {
        return text.replace("\r\n", "\n").replace("\n", "\r\n");
    }

    private String fixture(String name) {
        try {
            return Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
