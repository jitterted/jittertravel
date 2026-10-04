package dev.ted.jittertravel.web;

import dev.ted.jittertravel.web.SetupChecklist.State;
import dev.ted.jittertravel.web.SetupChecklist.Step;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The family-email setup task as a {@link SetupChecklist}: configured, tested, switched on. Three
 * facts the running app can read, so the rows tick themselves.
 * <p>
 * <strong>Every row says what is true now and what to do about it</strong> (Ted, 2026-10-05). A bare
 * state ("Switch: off") means nothing to someone who has not read the code, and an instruction that
 * leans on an undefined phrase ("once the test arrived") cannot be followed. So a row states its
 * current state in full, names the variable or the thing to look for, and gives the next step as a
 * separate line.
 * <p>
 * <strong>A restart forgets the test, and the checklist must not go backwards because of it.</strong>
 * Every deploy and every variable change restarts the app, and the test is remembered in memory only
 * (Ted, 2026-10-05). So once the switch is on, the test step counts as done: nobody switches it on
 * without a test, and a finished checklist must not slip back to "test not sent yet" after the next
 * deploy. Before the switch is on the restart does reset it, which is true.
 */
final class FamilyEmailSetup {

    static final String PROBE_PATH = "/admin/family-notify/probe";

    private static final String TEST_SUBJECT = "JitterTravel test email";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm", Locale.US);
    private static final DateTimeFormatter ZONE = DateTimeFormatter.ofPattern("zzz", Locale.US);

    SetupChecklist checklist(boolean configured, String recipient, boolean enabled,
                             Optional<FamilyTestMemory.Test> last, ZoneId zone, boolean justTested) {
        Step key = keyStep(configured, recipient);
        Step test = testStep(configured, recipient, enabled, last, zone, justTested);
        Step flip = switchStep(enabled, recipient, test);
        List<Step> steps = List.of(key, test, flip);
        int done = (int) steps.stream().filter(step -> step.state() == State.DONE).count();
        boolean finished = done == steps.size();
        String count = done + " of " + steps.size() + " done";
        if (!finished) {
            return new SetupChecklist("Setting up: family email",
                    "Temporary: this checklist is deleted once every step is done.",
                    count, false, steps, "", "", "", false);
        }
        return new SetupChecklist("Family email is set up", finishedSubtitle(last, zone), count, true,
                compact(steps, last.isPresent()),
                "All done.",
                "Tell Claude this is set up and tested, and this checklist can be deleted.",
                "Family email is set up and tested. Delete the setup checklist.",
                justTested);
    }

    private Step keyStep(boolean configured, String recipient) {
        if (configured) {
            return row(State.DONE, 1, "Key and recipient are set",
                    "The app has a Brevo API key, and family email goes to " + recipient + ".", null);
        }
        return row(State.TODO, 1, "Key and recipient are not set",
                "The app is missing its Brevo API key or the address to send to, so it cannot send any email.",
                "Set the variables JITTERTRAVEL_BREVO_API_KEY and FAMILY_NOTIFY_EMAIL on the app service, "
                + "then redeploy.");
    }

    private Step testStep(boolean configured, String recipient, boolean enabled,
                          Optional<FamilyTestMemory.Test> last, ZoneId zone, boolean justTested) {
        if (last.isPresent() && last.get().succeeded()) {
            return withButton(row(State.DONE, 2, "Test email sent at " + when(last.get().at(), zone),
                            "Brevo accepted it for delivery to " + recipient
                            + ". Accepted is not the same as arrived.",
                            "Open that inbox, and its spam folder, and look for a message titled \""
                            + TEST_SUBJECT + "\". If it is there, go on to step 3."),
                    "Send another", true, justTested);
        }
        if (last.isPresent()) {
            return withButton(row(State.FAILED, 2, "Test email failed",
                            last.get().failure() + " Nothing was delivered.",
                            "Fix the cause above (a key for the wrong domain, or a sender that Brevo has not "
                            + "verified, are the usual ones) and try again."),
                    "Try again", false, justTested);
        }
        if (enabled) {
            Step step = row(State.DONE, 2, "Test email: done",
                    "Family emails are switched on, which you only do after a test arrived. The app forgets "
                    + "a test when it restarts, so it cannot show when it was sent.", null);
            return configured ? withButton(step, "Send another", true, false) : step;
        }
        if (!configured) {
            return row(State.TODO, 2, "Test email: not sent yet",
                    "A test email needs the key and recipient from step 1.", "Finish step 1 first.");
        }
        return withButton(row(State.TODO, 2, "Test email: not sent yet",
                        "No test email has been sent since the app last started.",
                        "Press the button to send one to " + recipient + ". Then open that inbox, and its "
                        + "spam folder, and look for a message titled \"" + TEST_SUBJECT + "\"."),
                "Send a test email to " + recipient, false, false);
    }

    private Step switchStep(boolean enabled, String recipient, Step test) {
        if (enabled) {
            return row(State.DONE, 3, "Family emails are switched on",
                    "The variable FAMILY_NOTIFY_ENABLED is true, so a new flight or trip is emailed to "
                    + recipient + ".", null);
        }
        String next = test.state() == State.FAILED
                ? "Fix the test email in step 2 first. Then set the variable FAMILY_NOTIFY_ENABLED to true "
                  + "on the app service (add it if it does not exist yet)."
                : "Once you have received the test email from step 2, set the variable "
                  + "FAMILY_NOTIFY_ENABLED to true on the app service (add it if it does not exist yet).";
        return row(State.TODO, 3, "Family emails are switched off",
                "The variable FAMILY_NOTIFY_ENABLED is not set to true, so booking a flight sends nothing.",
                next);
    }

    private Step row(State state, int number, String what, String why, String next) {
        return new Step(state, number, what, why, next, null, false, null, false);
    }

    private Step withButton(Step step, String label, boolean secondary, boolean highlight) {
        return new Step(step.state(), step.number(), step.what(), step.why(), step.next(),
                label, secondary, PROBE_PATH, highlight);
    }

    /** A finished checklist is a short list of what was done: no explanations, no buttons. */
    private List<Step> compact(List<Step> steps, boolean testRemembered) {
        return List.of(
                compactRow(steps.get(0), "Key and recipient are set"),
                compactRow(steps.get(1), testRemembered ? "Test email sent" : "Test email: done"),
                compactRow(steps.get(2), "Family emails are switched on"));
    }

    private Step compactRow(Step step, String what) {
        return new Step(State.DONE, step.number(), what, null, null, null, false, null, false);
    }

    private String finishedSubtitle(Optional<FamilyTestMemory.Test> last, ZoneId zone) {
        String tail = "Family emails are switched on, so a new flight or trip is emailed to family.";
        return last.map(test -> "Test email sent " + when(test.at(), zone) + ". " + tail).orElse(tail);
    }

    /** The meridiem is written by hand: current JDKs put a narrow no-break space before it. */
    private String when(Instant instant, ZoneId zone) {
        ZonedDateTime local = instant.atZone(zone);
        return CLOCK.format(local) + (local.getHour() < 12 ? " AM " : " PM ") + ZONE.format(local);
    }
}
