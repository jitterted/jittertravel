package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.NotifiedSubject;

import java.time.Instant;

/**
 * The intent written ahead to {@code command_log} before an email is sent: who it is about and what
 * family are to be told, and nothing else. No recipient (configuration) and no prose (rebuilt from
 * the log), so a FAILED_SEND row still says <em>which</em> notification was lost without storing
 * either. Not a domain command: its work is a side effect, which {@code domain} may not have.
 */
public record NotifyFamilyCommand(NotifiedSubject subject, NotifiedFact fact, Instant requestedAt) {
}
