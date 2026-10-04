package dev.ted.jittertravel.web;

/**
 * What the admin home says about family email: the two facts that decide whether a booking sends
 * anything (the kill switch, and whether there is a key and a recipient), each read as a verdict
 * rather than a bare value, and the address a test would go to.
 * <p>
 * The recipient is shown because a probe that says "sent" without saying <em>where</em> cannot catch
 * the one mistake the probe exists to catch: a mistyped address that sends travel detail to a
 * stranger. It is on an OWNER-only page.
 */
public record FamilyNotifyStatus(boolean enabled, boolean configured, String recipient) {

    public String summary() {
        if (!configured) {
            return "Family email is not configured.";
        }
        return enabled
                ? "Family email is on: a new flight or trip is emailed to " + recipient + "."
                : "Family email is off: nothing is sent when a flight is booked.";
    }

    public String explanation() {
        if (!configured) {
            return "Set BREVO_API_KEY and FAMILY_NOTIFY_EMAIL on the app service, then redeploy.";
        }
        return enabled
                ? "Switch it off with FAMILY_NOTIFY_ENABLED=false. The test below works either way."
                : "Switch it on with FAMILY_NOTIFY_ENABLED=true once a test has reached the right inbox.";
    }
}
