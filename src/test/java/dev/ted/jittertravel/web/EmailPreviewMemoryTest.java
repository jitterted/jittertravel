package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Each group of preview emails remembers its own last send. */
class EmailPreviewMemoryTest {

    private static final Instant AT = Instant.parse("2026-10-05T22:42:00Z");

    private final EmailPreviewMemory memory = new EmailPreviewMemory();

    @Test
    void nothingIsRememberedBeforeAnythingIsSent() {
        assertThat(memory.last(EmailGroup.FLIGHTS))
                .isEmpty();
        assertThat(memory.last(EmailGroup.CONFERENCES))
                .isEmpty();
    }

    @Test
    void sendingOneGroupLeavesTheOthersResultAlone() {
        EmailPreviewMemory.Result flights = new EmailPreviewMemory.Result(AT, 3, 3, "ted@example.com", "");
        EmailPreviewMemory.Result conferences = new EmailPreviewMemory.Result(AT, 2, 4, "ted@example.com", "Nope.");

        memory.remember(EmailGroup.FLIGHTS, flights);
        memory.remember(EmailGroup.CONFERENCES, conferences);

        assertThat(memory.last(EmailGroup.FLIGHTS))
                .as("the flight result survives the conference send")
                .hasValue(flights);
        assertThat(memory.last(EmailGroup.CONFERENCES))
                .hasValue(conferences);
    }

    @Test
    void aNewSendOfAGroupReplacesThatGroupsLastResult() {
        EmailPreviewMemory.Result first = new EmailPreviewMemory.Result(AT, 1, 4, "ted@example.com", "Nope.");
        EmailPreviewMemory.Result second = new EmailPreviewMemory.Result(AT.plusSeconds(60), 4, 4, "ted@example.com", "");

        memory.remember(EmailGroup.CONFERENCES, first);
        memory.remember(EmailGroup.CONFERENCES, second);

        assertThat(memory.last(EmailGroup.CONFERENCES))
                .hasValue(second);
    }
}
