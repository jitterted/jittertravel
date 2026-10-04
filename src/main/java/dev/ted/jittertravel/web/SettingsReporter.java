package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.web.SettingsReport.Group;
import dev.ted.jittertravel.web.SettingsReport.Level;
import dev.ted.jittertravel.web.SettingsReport.Row;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Turns what the running app can read about its own configuration into the settings page, saying
 * for every setting what state it is in and what that means (option A of
 * https://claude.ai/artifact/7QRKoUEwoq8v9JWgzUzqrF, with every environment setting and the end of a
 * secret, Ted 2026-10-05).
 * <p>
 * <strong>A secret is never held in full past {@link #secret}.</strong> The inputs carry only
 * {@link SecretSetting}: whether it is set and, for a key or token long enough that four characters
 * give nothing away, how it ends, which is what lets two keys be told apart (a key belongs to one
 * account and domain, and the wrong one being loaded is the mix-up this page exists to show). A
 * password never gets a hint, and a short secret never does either.
 * <p>
 * <strong>A problem is wrong now, not merely unset.</strong> An optional feature that is off is a
 * state in words; the card goes amber only for the switch being on without the means to send, and a
 * failed test. The cookie readout is deliberately never a problem: over plain http locally "not
 * Secure" is correct, and amber that fires every day locally stops being read.
 */
final class SettingsReporter {

    /** How long a secret must be before its last four characters are safe to show. */
    private static final int HINT_MIN_LENGTH = 16;

    private final LocalTimeText time = new LocalTimeText();

    /**
     * Whether a secret is set and how it ends. The raw value is read here and dropped; {@code
     * toString} says nothing about it, so a log line cannot leak it.
     */
    record SecretSetting(boolean set, String hint) {

        @Override
        public String toString() {
            return "SecretSetting[set=" + set + "]";
        }
    }

    SecretSetting secret(String raw, boolean showEnd) {
        if (raw == null || raw.isBlank()) {
            return new SecretSetting(false, null);
        }
        String value = raw.strip();
        boolean hintable = showEnd && value.length() >= HINT_MIN_LENGTH;
        return new SecretSetting(true, hintable ? "ends …" + value.substring(value.length() - 4) : null);
    }

    /** Everything the page reads, with no secret in full. Built fluently: there are many fields. */
    record Inputs(
            boolean familyEnabled, boolean familyConfigured, String recipient, String replyTo,
            SecretSetting brevoKey, Optional<FamilyTestMemory.Test> lastTest, ZoneId zone,
            SecureCookieProbe cookies,
            SecretSetting tedPassword, SecretSetting familyPassword, SecretSetting rememberMeKey,
            SecretSetting calendarToken, String baseUrl, SecretSetting aeroDataBoxKey,
            String homeCities, String fallbackZone, String environment) {

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            private boolean familyEnabled;
            private boolean familyConfigured;
            private String recipient = "";
            private String replyTo = "";
            private SecretSetting brevoKey = new SecretSetting(false, null);
            private Optional<FamilyTestMemory.Test> lastTest = Optional.empty();
            private ZoneId zone = ZoneId.of("UTC");
            private SecureCookieProbe cookies = new SecureCookieProbe(false, "", "");
            private SecretSetting tedPassword = new SecretSetting(false, null);
            private SecretSetting familyPassword = new SecretSetting(false, null);
            private SecretSetting rememberMeKey = new SecretSetting(false, null);
            private SecretSetting calendarToken = new SecretSetting(false, null);
            private String baseUrl = "";
            private SecretSetting aeroDataBoxKey = new SecretSetting(false, null);
            private String homeCities = "";
            private String fallbackZone = "";
            private String environment = "";

            Builder familyEnabled(boolean value) { this.familyEnabled = value; return this; }
            Builder familyConfigured(boolean value) { this.familyConfigured = value; return this; }
            Builder recipient(String value) { this.recipient = value; return this; }
            Builder replyTo(String value) { this.replyTo = value; return this; }
            Builder brevoKey(SecretSetting value) { this.brevoKey = value; return this; }
            Builder lastTest(Optional<FamilyTestMemory.Test> value) { this.lastTest = value; return this; }
            Builder zone(ZoneId value) { this.zone = value; return this; }
            Builder cookies(SecureCookieProbe value) { this.cookies = value; return this; }
            Builder tedPassword(SecretSetting value) { this.tedPassword = value; return this; }
            Builder familyPassword(SecretSetting value) { this.familyPassword = value; return this; }
            Builder rememberMeKey(SecretSetting value) { this.rememberMeKey = value; return this; }
            Builder calendarToken(SecretSetting value) { this.calendarToken = value; return this; }
            Builder baseUrl(String value) { this.baseUrl = value; return this; }
            Builder aeroDataBoxKey(SecretSetting value) { this.aeroDataBoxKey = value; return this; }
            Builder homeCities(String value) { this.homeCities = value; return this; }
            Builder fallbackZone(String value) { this.fallbackZone = value; return this; }
            Builder environment(String value) { this.environment = value; return this; }

            Inputs build() {
                return new Inputs(familyEnabled, familyConfigured, recipient, replyTo, brevoKey, lastTest, zone,
                        cookies, tedPassword, familyPassword, rememberMeKey, calendarToken, baseUrl,
                        aeroDataBoxKey, homeCities, fallbackZone, environment);
            }
        }
    }

    SettingsReport report(Inputs in) {
        return new SettingsReport(List.of(
                familyEmail(in),
                cookies(in.cookies()),
                signIn(in),
                calendarFeed(in),
                flightLookup(in),
                whereItRuns(in)));
    }

    // ---- family email --------------------------------------------------------------------------

    private Group familyEmail(Inputs in) {
        boolean wantsToSend = in.familyEnabled();
        boolean cannotSend = wantsToSend && !in.familyConfigured();
        boolean recipientSet = !in.recipient().isBlank();

        Row flip = in.familyEnabled()
                ? (cannotSend
                    ? new Row("FAMILY_NOTIFY_ENABLED", "The switch", Level.PROBLEM, "On, cannot send", null, null,
                            "The switch is on but the key or the recipient is missing, so a booking cannot be emailed.")
                    : new Row("FAMILY_NOTIFY_ENABLED", "The switch", Level.OK, "On", null, null,
                            "Booking a flight or trip emails family."))
                : new Row("FAMILY_NOTIFY_ENABLED", "The switch", Level.NEUTRAL, "Off", null, null,
                        "Nothing is sent when a flight is booked. The test button on /admin works either way.");

        Row to = recipientSet
                ? new Row("FAMILY_NOTIFY_EMAIL", "Where it goes", Level.OK, null, in.recipient(), null,
                        "Every notification goes to this one address.")
                : new Row("FAMILY_NOTIFY_EMAIL", "Where it goes", wantsToSend ? Level.PROBLEM : Level.NEUTRAL,
                        "Not set", null, null, "No family email can be sent without an address to send it to.");

        Row reply = in.replyTo().isBlank()
                ? new Row("TED_REPLY_EMAIL", "Reply-to", Level.NEUTRAL, "Not set", null, null,
                        "Replies reach the unattended sender mailbox and nobody.")
                : new Row("TED_REPLY_EMAIL", "Reply-to", Level.OK, null, in.replyTo(), null,
                        "Where a family reply lands.");

        Row key = in.brevoKey().set()
                ? new Row("JITTERTRAVEL_BREVO_API_KEY", "Brevo key", Level.OK, "Set", null, in.brevoKey().hint(),
                        "Which key is loaded, so it can be told from another app's. Never shown in full.")
                : new Row("JITTERTRAVEL_BREVO_API_KEY", "Brevo key", wantsToSend ? Level.PROBLEM : Level.NEUTRAL,
                        "Not set", null, null, "No email can be sent without a Brevo key.");

        Row sender = new Row("Sender", "Fixed in the app", Level.NEUTRAL, null, BrevoEmailClient.SENDER_EMAIL, null,
                "Brevo only sends from a domain verified for the key's account.");

        List<Row> rows = List.of(flip, to, reply, key, sender, lastTest(in));
        boolean problem = rows.stream().anyMatch(row -> row.level() == Level.PROBLEM);
        if (problem) {
            return new Group("Family email", null, Level.PROBLEM, "Needs attention", rows);
        }
        return in.familyEnabled()
                ? new Group("Family email", null, Level.OK, "On", rows)
                : new Group("Family email", null, Level.NEUTRAL, "Off", rows);
    }

    private Row lastTest(Inputs in) {
        if (in.lastTest().isEmpty()) {
            return new Row("Last test email", "Since the app started", Level.NEUTRAL, "Not tested", null, null,
                    "No test email has been sent since the app started.");
        }
        FamilyTestMemory.Test test = in.lastTest().get();
        String when = time.when(test.at(), in.zone());
        return test.succeeded()
                ? new Row("Last test email", "Since the app started", Level.OK, "Sent", when + ", to " + in.recipient(),
                        null, "Forgotten when the app restarts.")
                : new Row("Last test email", "Since the app started", Level.PROBLEM, "Failed", when, null,
                        test.failure());
    }

    // ---- cookies -------------------------------------------------------------------------------

    private Group cookies(SecureCookieProbe probe) {
        List<Row> rows = probe.values().stream()
                .map(reading -> new Row(reading.label(), null, Level.NEUTRAL, null, reading.value(), null,
                        reading.verdict()))
                .toList();
        return new Group("Browser cookies", probe.summary() + " " + probe.explanation(), Level.NEUTRAL,
                probe.secure() ? "Marked Secure" : "Not marked Secure", rows);
    }

    // ---- everything else -----------------------------------------------------------------------

    private Group signIn(Inputs in) {
        List<Row> rows = List.of(
                requiredSecret("TED_PASSWORD", "Owner login", in.tedPassword()),
                requiredSecret("FAMILY_PASSWORD", "Family login", in.familyPassword()),
                requiredSecret("REMEMBER_ME_KEY", "Stay-signed-in cookies", in.rememberMeKey()));
        boolean problem = rows.stream().anyMatch(row -> row.level() == Level.PROBLEM);
        return problem
                ? new Group("Sign-in", null, Level.PROBLEM, "Needs attention", rows)
                : new Group("Sign-in", null, Level.OK, "All set", rows);
    }

    private Row requiredSecret(String name, String label, SecretSetting setting) {
        return setting.set()
                ? new Row(name, label, Level.OK, "Set", null, null, "Required, and present.")
                : new Row(name, label, Level.PROBLEM, "Not set", null, null,
                        "Required: the app should not have started without it.");
    }

    private Group calendarFeed(Inputs in) {
        Row token = in.calendarToken().set()
                ? new Row("CALENDAR_FEED_TOKEN", "Calendar subscription", Level.OK, "Set", null,
                        in.calendarToken().hint(), "The private calendar subscription feed is enabled.")
                : new Row("CALENDAR_FEED_TOKEN", "Calendar subscription", Level.NEUTRAL, "Not set", null, null,
                        "The calendar subscription feed is disabled.");
        Row base = in.baseUrl().isBlank()
                ? new Row("JITTERTRAVEL_BASE_URL", "Address used in links", Level.NEUTRAL, "Not set", null, null,
                        "Links use the address of the request itself, which can be wrong behind Railway's proxy.")
                : new Row("JITTERTRAVEL_BASE_URL", "Address used in links", Level.OK, null, in.baseUrl(), null,
                        "Subscribe and test links are built from this address.");
        return in.calendarToken().set()
                ? new Group("Calendar feed", null, Level.OK, "Enabled", List.of(token, base))
                : new Group("Calendar feed", null, Level.NEUTRAL, "Disabled", List.of(token, base));
    }

    private Group flightLookup(Inputs in) {
        Row key = in.aeroDataBoxKey().set()
                ? new Row("AERODATABOX_API_KEY", "Flight lookup", Level.OK, "Set", null, in.aeroDataBoxKey().hint(),
                        "Flight lookup by number is on.")
                : new Row("AERODATABOX_API_KEY", "Flight lookup", Level.NEUTRAL, "Not set", null, null,
                        "Flight lookup by number does not work.");
        return in.aeroDataBoxKey().set()
                ? new Group("Flight lookup", null, Level.OK, "On", List.of(key))
                : new Group("Flight lookup", null, Level.NEUTRAL, "Off", List.of(key));
    }

    private Group whereItRuns(Inputs in) {
        return new Group("Where it runs", null, Level.NEUTRAL, in.environment(), List.of(
                new Row("RAILWAY_ENVIRONMENT_NAME", "Environment", Level.NEUTRAL, null, in.environment(), null,
                        "\"production\" when hosted, \"local\" otherwise."),
                new Row("jittertravel.home-cities", "Home cities", Level.NEUTRAL, null,
                        in.homeCities().isBlank() ? "none" : in.homeCities(), null,
                        "No hotel is needed for nights spent in these cities."),
                new Row("jittertravel.today.fallback-zone", "Fallback time zone", Level.NEUTRAL, null,
                        in.fallbackZone(), null,
                        "Used for \"today\" when the browser has not reported its own zone.")));
    }
}
