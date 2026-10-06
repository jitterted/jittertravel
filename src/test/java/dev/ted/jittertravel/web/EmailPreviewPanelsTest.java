package dev.ted.jittertravel.web;

import dev.ted.jittertravel.web.PreviewPanel.State;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Every state of the send panel, worded as the approved mockup draws it. */
class EmailPreviewPanelsTest {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final Instant AT = Instant.parse("2026-10-05T22:42:00Z");

    private final EmailPreviewPanels panels = new EmailPreviewPanels();

    private PreviewPanel panel(boolean hasKey, String replyTo, Optional<EmailPreviewMemory.Result> last,
                               boolean justSent) {
        return panels.panel(EmailGroup.FLIGHTS, 3, hasKey, replyTo, last, PACIFIC, justSent);
    }

    private PreviewPanel conferencePanel(Optional<EmailPreviewMemory.Result> last) {
        return panels.panel(EmailGroup.CONFERENCES, 4, true, "ted@example.com", last, PACIFIC, false);
    }

    // ---- the conference group, worded as https://claude.ai/artifact/HSWE6X49TxLtN26B7hKspZ ----

    @Test
    void theConferenceGroupIsReadyToSendAllFourAndNamesThemAsConferenceEmails() {
        PreviewPanel panel = conferencePanel(Optional.empty());

        assertThat(panel.state()).isEqualTo(State.READY);
        assertThat(panel.what()).isEqualTo("Send these four conference emails to yourself");
        assertThat(panel.buttonLabel()).isEqualTo("Send all four to ted@example.com");
    }

    @Test
    void aConferenceSuccessSaysAcceptedAllFour() {
        PreviewPanel panel = conferencePanel(
                Optional.of(new EmailPreviewMemory.Result(AT, 4, 4, "ted@example.com", "")));

        assertThat(panel.what()).isEqualTo("4 emails sent to ted@example.com at 3:42 PM PDT");
        assertThat(panel.why())
                .isEqualTo("Brevo accepted all four. Accepted is not the same as arrived: open that inbox, "
                           + "and its spam folder, and look for messages starting \"(JitterTravel)\".");
    }

    @Test
    void aCountPastNineIsWrittenAsDigitsRatherThanFailingToFindAWord() {
        EmailPreviewMemory.Result result = new EmailPreviewMemory.Result(AT, 10, 12, "ted@example.com", "Nope.");

        assertThat(conferencePanel(Optional.of(result)).why())
                .isEqualTo("Nope. The first 10 went out and the last two did not.");
    }

    @Test
    void aConferenceFailureSaysWhichOfTheFourWentOut() {
        String reason = "The send failed: Brevo returned 502.";

        assertThat(conferencePanel(Optional.of(new EmailPreviewMemory.Result(AT, 1, 4, "ted@example.com", reason))).why())
                .isEqualTo(reason + " The first email went out and the other three did not.");
        PreviewPanel two = conferencePanel(Optional.of(new EmailPreviewMemory.Result(AT, 2, 4, "ted@example.com", reason)));
        assertThat(two.what()).isEqualTo("Sending failed after 2 of 4");
        assertThat(two.why())
                .isEqualTo(reason + " The first two went out and the last two did not.");
        assertThat(conferencePanel(Optional.of(new EmailPreviewMemory.Result(AT, 3, 4, "ted@example.com", reason))).why())
                .isEqualTo(reason + " The first three went out and the last did not.");
    }

    @Test
    void readyToSendNamesTheAddressAndSaysItNeverReachesFamily() {
        PreviewPanel panel = panel(true, "ted@example.com", Optional.empty(), false);

        assertThat(panel.state()).isEqualTo(State.READY);
        assertThat(panel.mark()).isEqualTo("✉");
        assertThat(panel.what()).isEqualTo("Send these three emails to yourself");
        assertThat(panel.why())
                .isEqualTo("They go to ted@example.com (your TED_REPLY_EMAIL), never to the family address. "
                           + "Open them in your mail client to check the link, the line breaks and the spam folder.");
        assertThat(panel.buttonLabel()).isEqualTo("Send all three to ted@example.com");
        assertThat(panel.buttonSecondary()).isFalse();
        assertThat(panel.hasNext()).isFalse();
    }

    @Test
    void aSuccessSaysHowManyWhereAndWhenAndWhatToLookFor() {
        PreviewPanel panel = panel(true, "ted@example.com",
                Optional.of(new EmailPreviewMemory.Result(AT, 3, 3, "ted@example.com", "")), false);

        assertThat(panel.state()).isEqualTo(State.SENT);
        assertThat(panel.what()).isEqualTo("3 emails sent to ted@example.com at 3:42 PM PDT");
        assertThat(panel.why())
                .isEqualTo("Brevo accepted all three. Accepted is not the same as arrived: open that inbox, "
                           + "and its spam folder, and look for messages starting \"(JitterTravel)\".");
        assertThat(panel.buttonLabel()).isEqualTo("Send them again");
        assertThat(panel.buttonSecondary()).isTrue();
    }

    @Test
    void aFailureSaysHowFarItGotAndWhy() {
        PreviewPanel none = panel(true, "ted@example.com",
                Optional.of(new EmailPreviewMemory.Result(AT, 0, 3, "ted@example.com", "The send failed: 401 Unauthorized.")),
                false);
        PreviewPanel one = panel(true, "ted@example.com",
                Optional.of(new EmailPreviewMemory.Result(AT, 1, 3, "ted@example.com", "The send failed: 401 Unauthorized.")),
                false);
        PreviewPanel two = panel(true, "ted@example.com",
                Optional.of(new EmailPreviewMemory.Result(AT, 2, 3, "ted@example.com", "The send failed: 401 Unauthorized.")),
                false);

        assertThat(one.state()).isEqualTo(State.FAILED);
        assertThat(one.what()).isEqualTo("Sending failed after 1 of 3");
        assertThat(one.why())
                .isEqualTo("The send failed: 401 Unauthorized. The first email went out and the other two did not.");
        assertThat(none.what()).isEqualTo("Sending failed before any went out");
        assertThat(none.why()).isEqualTo("The send failed: 401 Unauthorized. None of them went out.");
        assertThat(two.why())
                .isEqualTo("The send failed: 401 Unauthorized. The first two went out and the last did not.");
        assertThat(one.buttonLabel()).isEqualTo("Try again");
        assertThat(one.buttonSecondary()).isFalse();
    }

    @Test
    void withNoReplyToThereIsNoAddressOfYoursAndNoButton() {
        PreviewPanel panel = panel(true, "", Optional.empty(), false);

        assertThat(panel.state()).isEqualTo(State.CANNOT_SEND);
        assertThat(panel.what()).isEqualTo("Cannot send yet");
        assertThat(panel.why())
                .isEqualTo("There is no TED_REPLY_EMAIL, so there is no address of yours to send to. "
                           + "The previews below still work.");
        assertThat(panel.next())
                .isEqualTo("Set the variable TED_REPLY_EMAIL on the app service, then redeploy.");
        assertThat(panel.hasButton()).isFalse();
    }

    @Test
    void withNoBrevoKeyNothingCanBeSentAndTheKeyIsNamed() {
        PreviewPanel panel = panel(false, "ted@example.com", Optional.empty(), false);

        assertThat(panel.state()).isEqualTo(State.CANNOT_SEND);
        assertThat(panel.why())
                .isEqualTo("There is no JITTERTRAVEL_BREVO_API_KEY, so nothing can be sent. "
                           + "The previews below still work.");
        assertThat(panel.next())
                .isEqualTo("Set the variable JITTERTRAVEL_BREVO_API_KEY on the app service, then redeploy.");
        assertThat(panel.hasButton()).isFalse();
    }

    @Test
    void theKeyIsReportedBeforeTheAddressWhenBothAreMissing() {
        assertThat(panel(false, "", Optional.empty(), false).next())
                .contains("JITTERTRAVEL_BREVO_API_KEY");
    }

    @Test
    void theResultFlashesOnlyRightAfterTheClick() {
        EmailPreviewMemory.Result ok = new EmailPreviewMemory.Result(AT, 3, 3, "ted@example.com", "");
        EmailPreviewMemory.Result failed = new EmailPreviewMemory.Result(AT, 1, 3, "ted@example.com", "Nope.");

        assertThat(panel(true, "ted@example.com", Optional.of(ok), true).cssClass()).isEqualTo("ok flash");
        assertThat(panel(true, "ted@example.com", Optional.of(ok), false).cssClass()).isEqualTo("ok");
        assertThat(panel(true, "ted@example.com", Optional.of(failed), true).cssClass()).isEqualTo("warn flash");
        assertThat(panel(true, "ted@example.com", Optional.empty(), true).cssClass())
                .as("nothing was sent, so nothing to highlight")
                .isEqualTo("idle");
    }

    @Test
    void aRememberedResultIsNotShownWhenSendingIsNoLongerPossible() {
        EmailPreviewMemory.Result ok = new EmailPreviewMemory.Result(AT, 3, 3, "ted@example.com", "");

        assertThat(panel(true, "", Optional.of(ok), false).state())
                .as("the reply-to was removed, so the panel says it cannot send rather than showing a stale success")
                .isEqualTo(State.CANNOT_SEND);
    }
}
