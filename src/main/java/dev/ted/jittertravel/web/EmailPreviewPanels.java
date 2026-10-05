package dev.ted.jittertravel.web;

import dev.ted.jittertravel.web.PreviewPanel.State;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Decides which of the send panel's four states the preview page is in, and words it exactly as the
 * approved mockup does. A remembered result is shown only while sending is still possible: with the
 * reply-to or the key gone, a stale "3 emails sent" would claim something the page can no longer do.
 */
final class EmailPreviewPanels {

    private final LocalTimeText time = new LocalTimeText();

    PreviewPanel panel(boolean hasKey, String replyTo, Optional<EmailPreviewMemory.Result> last, ZoneId zone,
                       boolean justSent) {
        if (!hasKey) {
            return cannotSend("There is no JITTERTRAVEL_BREVO_API_KEY, so nothing can be sent. "
                              + "The previews below still work.", "JITTERTRAVEL_BREVO_API_KEY");
        }
        if (replyTo == null || replyTo.isBlank()) {
            return cannotSend("There is no TED_REPLY_EMAIL, so there is no address of yours to send to. "
                              + "The previews below still work.", "TED_REPLY_EMAIL");
        }
        if (last.isEmpty()) {
            return new PreviewPanel(State.READY, "Send these three emails to yourself",
                    "They go to " + replyTo + " (your TED_REPLY_EMAIL), never to the family address. "
                    + "Open them in your mail client to check the link, the line breaks and the spam folder.",
                    null, "Send all three to " + replyTo, false, false);
        }
        EmailPreviewMemory.Result result = last.get();
        if (result.succeeded()) {
            return new PreviewPanel(State.SENT,
                    result.sent() + " emails sent to " + result.address() + " at " + time.when(result.at(), zone),
                    "Brevo accepted all three. Accepted is not the same as arrived: open that inbox, and its "
                    + "spam folder, and look for messages starting \"(JitterTravel)\".",
                    null, "Send them again", true, justSent);
        }
        return new PreviewPanel(State.FAILED,
                result.sent() == 0 ? "Sending failed before any went out"
                                   : "Sending failed after " + result.sent() + " of " + result.of(),
                result.failure() + " " + howFarItGot(result.sent(), result.of()),
                null, "Try again", false, justSent);
    }

    private PreviewPanel cannotSend(String why, String variable) {
        return new PreviewPanel(State.CANNOT_SEND, "Cannot send yet", why,
                "Set the variable " + variable + " on the app service, then redeploy.", null, false, false);
    }

    private String howFarItGot(int sent, int of) {
        if (sent == 0) {
            return "None of them went out.";
        }
        if (sent == 1 && of == 3) {
            return "The first email went out and the other two did not.";
        }
        if (sent == 2 && of == 3) {
            return "The first two went out and the last did not.";
        }
        return sent + " of " + of + " went out and the rest did not.";
    }
}
