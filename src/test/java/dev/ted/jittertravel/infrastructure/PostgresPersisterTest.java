package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.web.PlanTentativeConferenceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresPersister.class)
class PostgresPersisterTest extends AbstractTestcontainerIntegrationTest {

    @Autowired
    private PostgresPersister persister;

    @TestConfiguration
    static class TestConfig {
        // needed for the instantiation of the PostgresPersister
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().findAndAddModules().build();
        }
    }

    @Test
    void loadTimelinePageReturnsCommandsWithEventsAndFlagsFailedCommand() {
        // command #1 — succeeds, produces one event
        UUID cmd1 = UUID.randomUUID();
        PlanTentativeConferenceRequest req1 = newRequest(cmd1, "Conf One");
        persister.saveCommand(cmd1, req1);
        persister.appendEvents(List.of(storedEvent(1L, cmd1, "Conf One", req1)), cmd1);

        // command #2 — "failed" (no events appended)
        UUID cmd2 = UUID.randomUUID();
        PlanTentativeConferenceRequest req2 = newRequest(cmd2, "Conf Two (failed)");
        persister.saveCommand(cmd2, req2);

        // command #3 — succeeds, produces two events
        UUID cmd3 = UUID.randomUUID();
        PlanTentativeConferenceRequest req3 = newRequest(cmd3, "Conf Three");
        persister.saveCommand(cmd3, req3);
        persister.appendEvents(
                List.of(storedEvent(2L, cmd3, "Conf Three", req3),
                        storedEvent(3L, cmd3, "Conf Three", req3)),
                cmd3
        );

        assertThat(persister.countCommands())
                .isEqualTo(3);

        List<TimelineEntry> page = persister.loadTimelinePage(0, 50);

        assertThat(page)
                .hasSize(3);
        assertThat(page.get(0).command().commandId()).isEqualTo(cmd1);
        assertThat(page.get(0).events()).hasSize(1);
        assertThat(page.get(0).failed()).isFalse();

        assertThat(page.get(1).command().commandId()).isEqualTo(cmd2);
        assertThat(page.get(1).events()).isEmpty();
        assertThat(page.get(1).failed()).isTrue();

        assertThat(page.get(2).command().commandId()).isEqualTo(cmd3);
        assertThat(page.get(2).events()).hasSize(2);
        assertThat(page.get(2).events().get(0).sequence()).isEqualTo(2L);
        assertThat(page.get(2).events().get(1).sequence()).isEqualTo(3L);

        // payloads should be pretty-printed JSON (multi-line)
        assertThat(page.get(0).command().payloadJson()).contains("\n");
        assertThat(page.get(0).events().getFirst().payloadJson()).contains("\n");

        // none are out-of-order in this happy path
        assertThat(page).allSatisfy(entry -> assertThat(entry.outOfOrder()).isFalse());
    }

    private PlanTentativeConferenceRequest newRequest(UUID id, String name) {
        PlanTentativeConferenceRequest r = new PlanTentativeConferenceRequest();
        r.setConferenceId(id.toString());
        r.setName(name);
        r.setStartDate(LocalDateTime.now().plusDays(10));
        r.setEndDate(LocalDateTime.now().plusDays(12));
        r.setVenueName("Venue");
        r.setVenueStreet("Street");
        r.setVenueCity("City");
        r.setVenueCountry("Country");
        r.setVenuePostalCode("12345");
        return r;
    }

    private StoredEvent storedEvent(long sequence, UUID commandId, String name, PlanTentativeConferenceRequest req) {
        return new StoredEvent(
                sequence,
                ConferenceTentativelyPlanned.class,
                UUID.randomUUID(),
                Instant.now(),
                new ConferenceTentativelyPlanned(
                        ConferenceId.of(commandId),
                        name,
                        req.getStartDate(),
                        req.getEndDate(),
                        "Venue",
                        new Address("Street", "City", null, "12345", "Country", null)
                ),
                commandId
        );
    }

    @Test
    void canSaveAndLoadCommandAndEvents() {
        UUID commandId = UUID.randomUUID();
        PlanTentativeConferenceRequest request = new PlanTentativeConferenceRequest();
        request.setConferenceId(commandId.toString());
        request.setName("Test Conference");
        request.setStartDate(LocalDateTime.now().plusDays(10));
        request.setEndDate(LocalDateTime.now().plusDays(12));
        request.setVenueName("Test Venue");
        request.setVenueStreet("Street");
        request.setVenueCity("City");
        request.setVenueCountry("Country");
        request.setVenuePostalCode("12345");

        persister.saveCommand(commandId, request);

        StoredEvent event = new StoredEvent(
                1L,
                ConferenceTentativelyPlanned.class,
                UUID.randomUUID(),
                Instant.now(),
                new ConferenceTentativelyPlanned(
                        ConferenceId.of(commandId),
                        "Test Conference",
                        request.getStartDate(),
                        request.getEndDate(),
                        "Test Venue",
                        new Address("Street", "City", null, "12345", "Country", null)
                ),
                commandId
        );

        persister.appendEvents(List.of(event), commandId);

        assertThat(persister.loadAllEvents())
                .hasSize(1);
        assertThat(persister.loadAllEvents().getFirst().sequence())
                .isEqualTo(1L);
        assertThat(((ConferenceTentativelyPlanned) persister.loadAllEvents().getFirst().payload()).name())
                .isEqualTo("Test Conference");

        assertThat(persister.getMaxSequence())
                .isEqualTo(1L);
    }
}
