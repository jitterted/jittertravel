package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Family were emailed about {@code subject}, and what they were told is {@code fact}.
 * <p>
 * Recorded <strong>after</strong> the email was accepted, so its presence means the mail went out;
 * a send that failed leaves a FAILED_SEND command row and no event. The recipient address is
 * deliberately not a field: it is configuration, and every event is written into every backup, so
 * putting it here would make each backup a small contact list. Nor is there any prose — the subject
 * line and body are rebuilt from the log as it stood, never stored.
 * <p>
 * {@code notifiedAt} is when the send was recorded, captured at the boundary; a displayed time is
 * a payload field, never the store's envelope (R11).
 */
public record FamilyNotified(
        NotifiedSubject subject,
        NotifiedFact fact,
        Instant notifiedAt
) implements Event {
    public FamilyNotified {
        if (subject == null || fact == null || notifiedAt == null) {
            throw new IllegalArgumentException("A family notification needs a subject, a fact and a time");
        }
    }
}
