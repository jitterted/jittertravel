package dev.ted.jittertravel.web;

/**
 * A set of preview emails that is sent to Ted together, with its own panel, its own button and its
 * own remembered result (Option A of the approved mockup,
 * https://claude.ai/artifact/HSWE6X49TxLtN26B7hKspZ): the flight emails and the conference emails
 * are separate groups, so using one never replaces the result shown for the other.
 */
public enum EmailGroup {
    FLIGHTS("Flight emails", "/admin/email-preview/send", ""),
    CONFERENCES("Conference emails", "/admin/email-preview/send-conferences", "conference ");

    private final String title;
    private final String sendPath;
    private final String kind;

    EmailGroup(String title, String sendPath, String kind) {
        this.title = title;
        this.sendPath = sendPath;
        this.kind = kind;
    }

    public String title() {
        return title;
    }

    /** Where this group's send button posts. */
    public String sendPath() {
        return sendPath;
    }

    /** What the ready panel calls the emails: nothing for flights, "conference " for conferences. */
    String kind() {
        return kind;
    }

    /** The name of the flash attribute that makes the next render highlight this group's result. */
    String justSentKey() {
        return "justSent" + name();
    }
}
