package dev.ted.jittertravel.web;

import dev.ted.jittertravel.web.SetupChecklist.State;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every state of the setup checklist, worded as Ted asked on 2026-10-05: each row says what is true
 * now (in words that mean something to someone who has not read the code), then, separately, what to
 * do next. A bare "Switch: off", or "once the test arrived" with no definition of arrived, is the
 * thing these cases exist to keep out.
 */
class FamilyEmailSetupTest {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final Instant SENT = Instant.parse("2026-10-05T22:42:00Z");
    private static final String PROBE = "/admin/family-notify/probe";

    private final FamilyEmailSetup setup = new FamilyEmailSetup();

    private SetupChecklist checklist(boolean configured, boolean enabled,
                                     Optional<FamilyTestMemory.Test> last, boolean justTested) {
        return setup.checklist(configured, "ted@example.com", enabled, last, PACIFIC, justTested);
    }

    private static Optional<FamilyTestMemory.Test> sent() {
        return Optional.of(new FamilyTestMemory.Test(SENT, true, ""));
    }

    private static Optional<FamilyTestMemory.Test> failed() {
        return Optional.of(new FamilyTestMemory.Test(SENT, false, "The send failed: 502 Bad Gateway."));
    }

    @Test
    void notYetConfiguredSaysWhatIsMissingAndWhichVariablesToSetAndOffersNoTest() {
        SetupChecklist checklist = checklist(false, false, Optional.empty(), false);

        SetupChecklist.Step key = checklist.steps().get(0);
        assertThat(key.state()).isEqualTo(State.TODO);
        assertThat(key.what()).isEqualTo("Key and recipient are not set");
        assertThat(key.why())
                .isEqualTo("The app is missing its Brevo API key or the address to send to, so it cannot send any email.");
        assertThat(key.next())
                .isEqualTo("Set the variables JITTERTRAVEL_BREVO_API_KEY and FAMILY_NOTIFY_EMAIL on the app "
                           + "service, then redeploy.");

        SetupChecklist.Step test = checklist.steps().get(1);
        assertThat(test.why()).isEqualTo("A test email needs the key and recipient from step 1.");
        assertThat(test.next()).isEqualTo("Finish step 1 first.");
        assertThat(test.hasButton())
                .as("a test cannot be sent without a key and recipient")
                .isFalse();
        assertThat(checklist.count()).isEqualTo("0 of 3 done");
    }

    @Test
    void inProgressBeforeTheTestStatesWhereEachStepStandsAndWhatToDo() {
        SetupChecklist checklist = checklist(true, false, Optional.empty(), false);

        assertThat(checklist.title()).isEqualTo("Setting up: family email");
        assertThat(checklist.subtitle())
                .isEqualTo("Temporary: this checklist is deleted once every step is done.");
        assertThat(checklist.count()).isEqualTo("1 of 3 done");
        assertThat(checklist.finished()).isFalse();

        SetupChecklist.Step key = checklist.steps().get(0);
        assertThat(key.state()).isEqualTo(State.DONE);
        assertThat(key.what()).isEqualTo("Key and recipient are set");
        assertThat(key.why()).isEqualTo("The app has a Brevo API key, and family email goes to ted@example.com.");
        assertThat(key.hasNext()).isFalse();

        SetupChecklist.Step test = checklist.steps().get(1);
        assertThat(test.state()).isEqualTo(State.TODO);
        assertThat(test.what()).isEqualTo("Test email: not sent yet");
        assertThat(test.why()).isEqualTo("No test email has been sent since the app last started.");
        assertThat(test.next())
                .isEqualTo("Press the button to send one to ted@example.com. Then open that inbox, and its spam "
                           + "folder, and look for a message titled \"JitterTravel test email\".");
        assertThat(test.buttonLabel()).isEqualTo("Send a test email to ted@example.com");
        assertThat(test.buttonSecondary())
                .as("the next thing to do is the primary button")
                .isFalse();
        assertThat(test.buttonAction()).isEqualTo(PROBE);

        SetupChecklist.Step flip = checklist.steps().get(2);
        assertThat(flip.state()).isEqualTo(State.TODO);
        assertThat(flip.what()).isEqualTo("Family emails are switched off");
        assertThat(flip.why())
                .isEqualTo("The variable FAMILY_NOTIFY_ENABLED is not set to true, so booking a flight sends nothing.");
        assertThat(flip.next())
                .isEqualTo("Once you have received the test email from step 2, set the variable "
                           + "FAMILY_NOTIFY_ENABLED to true on the app service (add it if it does not exist yet).");
        assertThat(flip.hasButton()).isFalse();
    }

    @Test
    void aSentTestSaysWhenInTheViewersZoneAndWhatToLookForAndOffersAnotherAsTheSecondaryButton() {
        SetupChecklist checklist = checklist(true, false, sent(), false);

        SetupChecklist.Step test = checklist.steps().get(1);
        assertThat(test.state()).isEqualTo(State.DONE);
        assertThat(test.what()).isEqualTo("Test email sent at 3:42 PM PDT");
        assertThat(test.why())
                .isEqualTo("Brevo accepted it for delivery to ted@example.com. Accepted is not the same as arrived.");
        assertThat(test.next())
                .as("this is where 'arrived' is defined: a message with this title, in the inbox or in spam")
                .isEqualTo("Open that inbox, and its spam folder, and look for a message titled "
                           + "\"JitterTravel test email\". If it is there, go on to step 3.");
        assertThat(test.buttonLabel()).isEqualTo("Send another");
        assertThat(test.buttonSecondary()).isTrue();
        assertThat(checklist.count()).isEqualTo("2 of 3 done");
    }

    @Test
    void aMorningTestIsAmNotPm() {
        Optional<FamilyTestMemory.Test> morning = Optional.of(
                new FamilyTestMemory.Test(Instant.parse("2026-10-05T16:05:00Z"), true, ""));

        assertThat(checklist(true, false, morning, false).steps().get(1).what())
                .isEqualTo("Test email sent at 9:05 AM PDT");
    }

    @Test
    void aFailedTestIsAmberWithTheReasonAndTheUsualCausesAndTheSwitchStepSaysToFixItFirst() {
        SetupChecklist checklist = checklist(true, false, failed(), false);

        SetupChecklist.Step test = checklist.steps().get(1);
        assertThat(test.state()).isEqualTo(State.FAILED);
        assertThat(test.what()).isEqualTo("Test email failed");
        assertThat(test.why()).isEqualTo("The send failed: 502 Bad Gateway. Nothing was delivered.");
        assertThat(test.next())
                .isEqualTo("Fix the cause above (a key for the wrong domain, or a sender that Brevo has not "
                           + "verified, are the usual ones) and try again.");
        assertThat(test.buttonLabel()).isEqualTo("Try again");
        assertThat(test.buttonSecondary()).isFalse();
        assertThat(checklist.steps().get(2).next())
                .isEqualTo("Fix the test email in step 2 first. Then set the variable FAMILY_NOTIFY_ENABLED to "
                           + "true on the app service (add it if it does not exist yet).");
        assertThat(checklist.count()).isEqualTo("1 of 3 done");
    }

    @Test
    void theTestRowFlashesOnlyOnThePageThatFollowsAClick() {
        assertThat(checklist(true, false, sent(), true).steps().get(1).cssClass())
                .isEqualTo("ok flash");
        assertThat(checklist(true, false, sent(), false).steps().get(1).cssClass())
                .isEqualTo("ok");
        assertThat(checklist(true, false, failed(), true).steps().get(1).cssClass())
                .isEqualTo("warn flash");
        assertThat(checklist(true, false, Optional.empty(), true).steps().get(1).cssClass())
                .as("nothing was tested, so there is nothing to highlight")
                .isEqualTo("idle");
    }

    @Test
    void withTheSwitchOnATestTheAppHasForgottenStillCountsAndSaysWhy() {
        // Key unset keeps the checklist unfinished, so the row keeps its explanation.
        SetupChecklist.Step test = checklist(false, true, Optional.empty(), false).steps().get(1);

        assertThat(test.state())
                .as("the switch would not be on without a test, and a restart forgets the test")
                .isEqualTo(State.DONE);
        assertThat(test.what()).isEqualTo("Test email: done");
        assertThat(test.why())
                .isEqualTo("Family emails are switched on, which you only do after a test arrived. The app "
                           + "forgets a test when it restarts, so it cannot show when it was sent.");
    }

    @Test
    void aFinishedChecklistAfterARestartStillShowsTheTestAsDone() {
        SetupChecklist checklist = checklist(true, true, Optional.empty(), false);

        assertThat(checklist.finished()).isTrue();
        assertThat(checklist.steps().get(1).what()).isEqualTo("Test email: done");
    }

    @Test
    void aFailedTestIsNotHiddenByTheSwitchBeingOn() {
        SetupChecklist checklist = checklist(true, true, failed(), false);

        assertThat(checklist.steps().get(1).state()).isEqualTo(State.FAILED);
        assertThat(checklist.finished()).isFalse();
    }

    @Test
    void theSwitchStepWhenOnSaysWhatItDoesAndTo() {
        // Key unset keeps the checklist unfinished, so the row keeps its explanation.
        SetupChecklist.Step flip = checklist(false, true, sent(), false).steps().get(2);

        assertThat(flip.state()).isEqualTo(State.DONE);
        assertThat(flip.what()).isEqualTo("Family emails are switched on");
        assertThat(flip.why())
                .isEqualTo("The variable FAMILY_NOTIFY_ENABLED is true, so a new flight or trip is emailed to "
                           + "ted@example.com.");
    }

    @Test
    void finishedAsksToBeDeletedAndSaysWhatToTellClaude() {
        SetupChecklist checklist = checklist(true, true, sent(), false);

        assertThat(checklist.finished()).isTrue();
        assertThat(checklist.title()).isEqualTo("Family email is set up");
        assertThat(checklist.subtitle())
                .isEqualTo("Test email sent 3:42 PM PDT. Family emails are switched on, so a new flight or trip "
                           + "is emailed to family.");
        assertThat(checklist.count()).isEqualTo("3 of 3 done");
        assertThat(checklist.steps())
                .extracting(SetupChecklist.Step::what)
                .containsExactly("Key and recipient are set", "Test email sent", "Family emails are switched on");
        assertThat(checklist.steps())
                .as("a finished checklist is compact: no explanations, no next steps, no buttons")
                .allMatch(step -> !step.hasWhy() && !step.hasNext() && !step.hasButton());
        assertThat(checklist.finishedHeadline()).isEqualTo("All done.");
        assertThat(checklist.finishedAdvice())
                .isEqualTo("Tell Claude this is set up and tested, and this checklist can be deleted.");
        assertThat(checklist.finishedHandoff())
                .isEqualTo("Family email is set up and tested. Delete the setup checklist.");
    }

    @Test
    void finishedAfterARestartDropsTheTestTimeRatherThanInventingOne() {
        assertThat(checklist(true, true, Optional.empty(), false).subtitle())
                .isEqualTo("Family emails are switched on, so a new flight or trip is emailed to family.");
    }

    @Test
    void theFinishedMessageFlashesOnlyRightAfterTheClickThatFinishedIt() {
        assertThat(checklist(true, true, sent(), true).highlightFinished()).isTrue();
        assertThat(checklist(true, true, sent(), false).highlightFinished()).isFalse();
        assertThat(checklist(true, false, sent(), true).highlightFinished())
                .as("not finished, so nothing to flash")
                .isFalse();
    }

    @Test
    void notFinishedWhenTheKeyOrRecipientIsMissingEvenIfTheSwitchIsOn() {
        assertThat(checklist(false, true, sent(), false).finished()).isFalse();
    }
}
