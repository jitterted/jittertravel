package dev.ted.jittertravel.infrastructure;

/** An email to family: a subject and a plain-text body, and nothing else (no HTML part). */
public record FamilyMessage(String subject, String textContent) {
}
