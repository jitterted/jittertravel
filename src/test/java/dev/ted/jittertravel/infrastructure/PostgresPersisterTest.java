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
                        new Address("Street", "City", null, "Country", "12345")
                ),
                commandId
        );

        persister.appendEvents(List.of(event), commandId);
        persister.linkCommandToEvents(commandId, List.of(event.eventId()));

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
