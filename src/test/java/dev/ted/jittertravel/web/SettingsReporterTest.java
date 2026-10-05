package dev.ted.jittertravel.web;

import dev.ted.jittertravel.web.SettingsReport.Group;
import dev.ted.jittertravel.web.SettingsReport.Level;
import dev.ted.jittertravel.web.SettingsReport.Row;
import dev.ted.jittertravel.web.SettingsReporter.Inputs;
import dev.ted.jittertravel.web.SettingsReporter.SecretSetting;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The settings page's content (option A of https://claude.ai/artifact/7QRKoUEwoq8v9JWgzUzqrF): what
 * each row says in each state, which states are problems, and that a secret is never shown in full.
 */
class SettingsReporterTest {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final Instant SENT = Instant.parse("2026-10-05T22:42:00Z");
    private static final String LONG_KEY = "xkeysib-0123456789abcdef-f3a9";

    private final SettingsReporter reporter = new SettingsReporter();

    /** A fully configured deployment: everything set, family email on, last test sent. */
    private Inputs.Builder healthy() {
        return Inputs.builder()
                .familyEnabled(true).familyConfigured(true)
                .recipient("family@example.com").replyTo("ted@example.com")
                .brevoKey(reporter.secret(LONG_KEY, true))
                .lastTest(Optional.of(new FamilyTestMemory.Test(SENT, true, "")))
                .zone(PACIFIC)
                .cookies(new SecureCookieProbe(true, "https", ""))
                .tedPassword(reporter.secret("pw", false)).familyPassword(reporter.secret("pw", false))
                .rememberMeKey(reporter.secret("a-remember-me-key-value", false))
                .calendarToken(reporter.secret("calendar-token-abcdef1234", true))
                .baseUrl("https://jittertravel.com")
                .aeroDataBoxKey(reporter.secret("aerodatabox-key-0000-81c0", true))
                .homeCities("Hamburg, Berlin").fallbackZone("America/Los_Angeles").environment("production");
    }

    private SettingsReport report(Inputs.Builder inputs) {
        return reporter.report(inputs.build());
    }

    private Group group(SettingsReport report, String title) {
        return report.groups().stream().filter(group -> group.title().equals(title)).findFirst().orElseThrow();
    }

    private Row row(SettingsReport report, String name) {
        return report.groups().stream().flatMap(group -> group.rows().stream())
                .filter(row -> row.name().equals(name)).findFirst().orElseThrow();
    }

    // ---- secrets -------------------------------------------------------------------------------

    @Test
    void aLongSecretShowsOnlyItsLastFourCharacters() {
        SecretSetting key = reporter.secret(LONG_KEY, true);

        assertThat(key.set()).isTrue();
        assertThat(key.hint()).isEqualTo("ends …f3a9");
    }

    @Test
    void aShortSecretShowsNoHintBecauseFourCharactersOfItWouldBeTooMuch() {
        assertThat(reporter.secret("short-key", true).hint()).isNull();
    }

    @Test
    void aPasswordNeverShowsAHintHoweverLong() {
        assertThat(reporter.secret("a-very-long-password-indeed", false).hint()).isNull();
    }

    @Test
    void anUnsetOrBlankSecretIsNotSet() {
        assertThat(reporter.secret(null, true).set()).isFalse();
        assertThat(reporter.secret("   ", true).set()).isFalse();
    }

    @Test
    void aSecretSettingNeverPrintsItsHintWhereALogWouldFindIt() {
        assertThat(reporter.secret(LONG_KEY, true).toString())
                .doesNotContain("f3a9")
                .doesNotContain(LONG_KEY);
    }

    // ---- family email --------------------------------------------------------------------------

    @Test
    void aHealthyFamilyEmailIsOnAndSaysWhereMailGoes() {
        SettingsReport report = report(healthy());

        Group family = group(report, "Family email");
        assertThat(family.level()).isEqualTo(Level.OK);
        assertThat(family.pill()).isEqualTo("On");
        assertThat(row(report, "FAMILY_NOTIFY_ENABLED").pill()).isEqualTo("On");
        assertThat(row(report, "FAMILY_NOTIFY_ENABLED").what()).isEqualTo("Booking a flight or trip emails family.");
        assertThat(row(report, "FAMILY_NOTIFY_EMAIL").value()).isEqualTo("family@example.com");
        assertThat(row(report, "FAMILY_NOTIFY_EMAIL").what())
                .isEqualTo("Every notification goes to this one address.");
        assertThat(row(report, "TED_REPLY_EMAIL").value()).isEqualTo("ted@example.com");
        assertThat(row(report, "JITTERTRAVEL_BREVO_API_KEY").pill()).isEqualTo("Set");
        assertThat(row(report, "JITTERTRAVEL_BREVO_API_KEY").hint()).isEqualTo("ends …f3a9");
        assertThat(row(report, "Sender").value()).isEqualTo("notifications@jittertravel.com");
        assertThat(report.problems()).isZero();
    }

    @Test
    void switchedOffIsAStateNotAProblem() {
        SettingsReport report = report(healthy().familyEnabled(false));

        assertThat(group(report, "Family email").pill()).isEqualTo("Off");
        assertThat(group(report, "Family email").level()).isEqualTo(Level.NEUTRAL);
        assertThat(row(report, "FAMILY_NOTIFY_ENABLED").level()).isEqualTo(Level.NEUTRAL);
        assertThat(row(report, "FAMILY_NOTIFY_ENABLED").what())
                .isEqualTo("Nothing is sent when a flight is booked. The test email button below works either way.");
        assertThat(report.problems()).isZero();
    }

    @Test
    void notConfiguredAndOffIsNotAProblemEitherBecauseTheFeatureIsSimplyUnused() {
        SettingsReport report = report(healthy().familyEnabled(false).familyConfigured(false)
                .recipient("").replyTo("").brevoKey(reporter.secret("", true)).lastTest(Optional.empty()));

        assertThat(row(report, "FAMILY_NOTIFY_EMAIL").pill()).isEqualTo("Not set");
        assertThat(row(report, "FAMILY_NOTIFY_EMAIL").level()).isEqualTo(Level.NEUTRAL);
        assertThat(row(report, "TED_REPLY_EMAIL").what())
                .isEqualTo("Replies reach the unattended sender mailbox and nobody.");
        assertThat(row(report, "JITTERTRAVEL_BREVO_API_KEY").pill()).isEqualTo("Not set");
        assertThat(row(report, "Last test email").pill()).isEqualTo("Not tested");
        assertThat(report.problems()).isZero();
    }

    @Test
    void switchedOnWithoutAKeyIsAProblem() {
        SettingsReport report = report(healthy().familyConfigured(false).brevoKey(reporter.secret("", true)));

        assertThat(group(report, "Family email").level()).isEqualTo(Level.PROBLEM);
        assertThat(group(report, "Family email").pill()).isEqualTo("Needs attention");
        assertThat(row(report, "FAMILY_NOTIFY_ENABLED").level()).isEqualTo(Level.PROBLEM);
        assertThat(row(report, "FAMILY_NOTIFY_ENABLED").pill()).isEqualTo("On, cannot send");
        assertThat(row(report, "FAMILY_NOTIFY_ENABLED").what())
                .isEqualTo("The switch is on but the key or the recipient is missing, so a booking cannot be emailed.");
        assertThat(row(report, "JITTERTRAVEL_BREVO_API_KEY").level()).isEqualTo(Level.PROBLEM);
        assertThat(report.problems()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void switchedOnWithoutARecipientMarksTheRecipientAsTheProblem() {
        SettingsReport report = report(healthy().familyConfigured(false).recipient(""));

        assertThat(row(report, "FAMILY_NOTIFY_EMAIL").level()).isEqualTo(Level.PROBLEM);
        assertThat(row(report, "JITTERTRAVEL_BREVO_API_KEY").level())
                .as("the key is fine, so it is not blamed")
                .isEqualTo(Level.OK);
    }

    @Test
    void aFailedLastTestIsAProblemWithTheReason() {
        SettingsReport report = report(healthy().lastTest(Optional.of(
                new FamilyTestMemory.Test(SENT, false, "The send failed: 401 Unauthorized."))));

        Row last = row(report, "Last test email");
        assertThat(last.level()).isEqualTo(Level.PROBLEM);
        assertThat(last.pill()).isEqualTo("Failed");
        assertThat(last.value()).isEqualTo("3:42 PM PDT");
        assertThat(last.what()).isEqualTo("The send failed: 401 Unauthorized. Nothing was delivered.");
        assertThat(group(report, "Family email").level()).isEqualTo(Level.PROBLEM);
    }

    @Test
    void aFailedLastTestOffersTryAgainAndFlashesOnlyRightAfterTheClick() {
        Optional<FamilyTestMemory.Test> failed = Optional.of(new FamilyTestMemory.Test(SENT, false, "Failed."));

        Row later = row(report(healthy().lastTest(failed)), "Last test email");
        Row justNow = row(report(healthy().lastTest(failed).justTested(true)), "Last test email");

        assertThat(later.button())
                .isEqualTo(new SettingsReport.Button("/admin/family-notify/probe", "Try again", false));
        assertThat(later.flash()).as("a later visit to the page does not flash").isFalse();
        assertThat(justNow.flash()).as("the page right after the click does").isTrue();
    }

    @Test
    void aSentLastTestOffersAQuieterSendAnother() {
        Row last = row(report(healthy().justTested(true)), "Last test email");

        assertThat(last.button())
                .isEqualTo(new SettingsReport.Button("/admin/family-notify/probe", "Send another", true));
        assertThat(last.flash()).isTrue();
    }

    @Test
    void anUntestedConfiguredNotifierOffersTheButtonNamingTheRecipient() {
        Row last = row(report(healthy().lastTest(Optional.empty())), "Last test email");

        assertThat(last.button())
                .isEqualTo(new SettingsReport.Button("/admin/family-notify/probe",
                        "Send a test email to family@example.com", false));
        assertThat(last.flash()).isFalse();
    }

    @Test
    void anUnconfiguredNotifierOffersNoButtonAndSaysWhatItNeeds() {
        Row last = row(report(healthy().familyConfigured(false).lastTest(Optional.empty())), "Last test email");

        assertThat(last.hasButton()).isFalse();
        assertThat(last.what()).isEqualTo("Cannot send until the Brevo key and the family address are set.");
    }

    @Test
    void aSentLastTestSaysWhenAndToWhom() {
        Row last = row(report(healthy()), "Last test email");

        assertThat(last.level()).isEqualTo(Level.OK);
        assertThat(last.pill()).isEqualTo("Sent");
        assertThat(last.value()).isEqualTo("3:42 PM PDT, to family@example.com");
        assertThat(last.what()).isEqualTo("Forgotten when the app restarts.");
    }

    // ---- cookies -------------------------------------------------------------------------------

    @Test
    void theCookieReadoutMovesHereWithEveryValueAndItsVerdict() {
        SettingsReport report = report(healthy().cookies(new SecureCookieProbe(true, "https", "")));

        Group cookies = group(report, "Browser cookies");
        SecureCookieProbe probe = new SecureCookieProbe(true, "https", "");
        assertThat(cookies.sub()).isEqualTo(probe.summary() + " " + probe.explanation());
        assertThat(cookies.rows())
                .extracting(Row::name)
                .containsExactly("request.isSecure()", "scheme", "X-Forwarded-Proto");
        assertThat(cookies.rows())
                .as("every reading carries its verdict")
                .allMatch(row -> row.what() != null && !row.what().isBlank());
    }

    @Test
    void cookiesNeverTurnTheCardAmberBecauseLocalHttpLegitimatelyReadsNotSecure() {
        SettingsReport report = report(healthy().cookies(new SecureCookieProbe(false, "http", "")));

        assertThat(group(report, "Browser cookies").level()).isEqualTo(Level.NEUTRAL);
        assertThat(group(report, "Browser cookies").pill()).isEqualTo("Not marked Secure");
        assertThat(report.problems()).isZero();
    }

    // ---- everything else -----------------------------------------------------------------------

    @Test
    void theSignInSettingsAreSetWithNoHintsBecauseTheyArePasswords() {
        SettingsReport report = report(healthy());

        assertThat(row(report, "TED_PASSWORD").pill()).isEqualTo("Set");
        assertThat(row(report, "TED_PASSWORD").hasHint()).isFalse();
        assertThat(row(report, "FAMILY_PASSWORD").hasHint()).isFalse();
        assertThat(row(report, "REMEMBER_ME_KEY").hasHint()).isFalse();
    }

    @Test
    void theCalendarFeedIsEnabledWithATokenHintAndABaseUrl() {
        SettingsReport report = report(healthy());

        assertThat(row(report, "CALENDAR_FEED_TOKEN").pill()).isEqualTo("Set");
        assertThat(row(report, "CALENDAR_FEED_TOKEN").hint()).isEqualTo("ends …1234");
        assertThat(row(report, "CALENDAR_FEED_TOKEN").what())
                .isEqualTo("The private calendar subscription feed is enabled.");
        assertThat(row(report, "JITTERTRAVEL_BASE_URL").value()).isEqualTo("https://jittertravel.com");
    }

    @Test
    void anUnsetCalendarFeedAndBaseUrlAreStatesSaidInWords() {
        SettingsReport report = report(healthy().calendarToken(reporter.secret("", true)).baseUrl(""));

        assertThat(row(report, "CALENDAR_FEED_TOKEN").pill()).isEqualTo("Not set");
        assertThat(row(report, "CALENDAR_FEED_TOKEN").what())
                .isEqualTo("The calendar subscription feed is disabled.");
        assertThat(row(report, "JITTERTRAVEL_BASE_URL").pill()).isEqualTo("Not set");
        assertThat(row(report, "JITTERTRAVEL_BASE_URL").what())
                .isEqualTo("Links use the address of the request itself, which can be wrong behind Railway's "
                           + "proxy, and family emails have no calendar link.");
        assertThat(report.problems()).isZero();
    }

    @Test
    void flightLookupSaysWhetherItWorks() {
        assertThat(row(report(healthy()), "AERODATABOX_API_KEY").what())
                .isEqualTo("Flight lookup by number is on.");
        assertThat(row(report(healthy()), "AERODATABOX_API_KEY").hint()).isEqualTo("ends …81c0");

        Row unset = row(report(healthy().aeroDataBoxKey(reporter.secret("", true))), "AERODATABOX_API_KEY");
        assertThat(unset.pill()).isEqualTo("Not set");
        assertThat(unset.what()).isEqualTo("Flight lookup by number does not work.");
    }

    @Test
    void whereItRunsHomeCitiesAndTheFallbackZoneAreShown() {
        SettingsReport report = report(healthy());

        assertThat(row(report, "RAILWAY_ENVIRONMENT_NAME").value()).isEqualTo("production");
        assertThat(row(report, "jittertravel.home-cities").value()).isEqualTo("Hamburg, Berlin");
        assertThat(row(report, "jittertravel.today.fallback-zone").value()).isEqualTo("America/Los_Angeles");
        assertThat(row(report(healthy().homeCities("")), "jittertravel.home-cities").value()).isEqualTo("none");
    }

    @Test
    void noRowAnywhereContainsAWholeSecret() {
        SettingsReport report = report(healthy());

        assertThat(report.toString())
                .doesNotContain(LONG_KEY)
                .doesNotContain("calendar-token-abcdef1234")
                .doesNotContain("aerodatabox-key-0000-81c0")
                .doesNotContain("a-remember-me-key-value");
    }
}
