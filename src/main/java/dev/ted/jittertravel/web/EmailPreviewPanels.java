package dev.ted.jittertravel.web;

import dev.ted.jittertravel.web.PreviewPanel.State;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Decides which of a send panel's four states the preview page is in for one {@link EmailGroup}, and
 * words it exactly as the approved mockups do. A remembered result is shown only while sending is
 * still possible: with the reply-to or the key gone, a stale "3 emails sent" would claim something
 * the page can no longer do.
 */
final class EmailPreviewPanels {

    private static final List<String> NUMBER_WORDS =
            List.of("none", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine");

    private final LocalTimeText time = new LocalTimeText();

    /**
     * @param count how many emails the group sends, which is what the ready panel and the button say
     */
    PreviewPanel panel(EmailGroup group, int count, boolean hasKey, String replyTo,
                       Optional<EmailPreviewMemory.Result> last, ZoneId zone, boolean justSent) {
        if (!hasKey) {
            return cannotSend("There is no JITTERTRAVEL_BREVO_API_KEY, so nothing can be sent. "
                              + "The previews below still work.", "JITTERTRAVEL_BREVO_API_KEY");
        }
        if (replyTo == null || replyTo.isBlank()) {
            return cannotSend("There is no TED_REPLY_EMAIL, so there is no address of yours to send to. "
                              + "The previews below still work.", "TED_REPLY_EMAIL");
        }
        if (last.isEmpty()) {
            return new PreviewPanel(State.READY,
                    "Send these " + number(count) + " " + group.kind() + "emails to yourself",
                    "They go to " + replyTo + " (your TED_REPLY_EMAIL), never to the family address. "
                    + "Open them in your mail client to check the link, the line breaks and the spam folder.",
                    null, "Send all " + number(count) + " to " + replyTo, false, false);
        }
        EmailPreviewMemory.Result result = last.get();
        if (result.succeeded()) {
            return new PreviewPanel(State.SENT,
                    result.sent() + " emails sent to " + result.address() + " at " + time.when(result.at(), zone),
                    "Brevo accepted all " + number(result.of()) + ". Accepted is not the same as arrived: open "
                    + "that inbox, and its spam folder, and look for messages starting \"(JitterTravel)\".",
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

    /**
     * Which went out and which did not, in the mockup's words: "the first two went out and the last
     * did not", "the first email went out and the other three did not".
     */
    private String howFarItGot(int sent, int of) {
        if (sent == 0) {
            return "None of them went out.";
        }
        int rest = of - sent;
        String went = sent == 1 ? "The first email" : "The first " + number(sent);
        String didNot = rest == 1 ? "the last"
                : (sent == 1 ? "the other " : "the last ") + number(rest);
        return went + " went out and " + didNot + " did not.";
    }

    private String number(int count) {
        return count < NUMBER_WORDS.size() ? NUMBER_WORDS.get(count) : String.valueOf(count);
    }
}
