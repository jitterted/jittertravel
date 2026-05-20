package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.EventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class ConferenceWebIntegrationTest extends AbstractTestcontainerIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private TentativeConferenceProjector projector;

    @Test
    void planConferenceFlowSavesEventAndRedirectsToList() {
        LocalDateTime futureStart = LocalDateTime.now().plusDays(2);
        LocalDateTime futureEnd = futureStart.plusDays(2);

        assertThat(mockMvc.post().uri("/plan-conference")
                .param("conferenceId", UUID.randomUUID().toString())
                .param("name", "Event Sourcing Conference")
                .param("startDate", futureStart.toString())
                .param("endDate", futureEnd.toString())
                .param("venueName", "ES Venue")
                .param("venueStreet", "ES Street")
                .param("venueCity", "ES City")
                .param("venueCountry", "ES Country")
                .param("venuePostalCode", "ES Postal Code"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/tentative-conferences");

        // Filter to this test's specific conference; other integration tests share
        // the in-memory EventStore so totals across the suite can vary.
        assertThat(projector.views())
                .extracting(v -> v.name())
                .contains("Event Sourcing Conference");

        assertThat(mockMvc.get().uri("/tentative-conferences"))
                .hasStatusOk()
                .bodyText()
                .contains("Event Sourcing Conference", "ES City");
    }
}
