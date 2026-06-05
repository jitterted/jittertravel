package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class PlanGatheringWebIntegrationTest extends AbstractTestcontainerIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void planGatheringFormRendersSuccessfully() {
        assertThat(mockMvc.get().uri("/plan-gathering"))
                .hasStatusOk();
    }

    @Test
    void planGatheringPostRedirectsToPlannedGatherings() {
        LocalDate nextWeek = LocalDate.now().plusWeeks(1);

        assertThat(mockMvc.post().uri("/plan-gathering")
                .param("gatheringId", UUID.randomUUID().toString())
                .param("title", "London Java Community — November Meetup")
                .param("venueName", "Skills Matter")
                .param("street", "1 Example Street")
                .param("city", "London")
                .param("region", "")
                .param("country", "GB")
                .param("postalCode", "EC1A 1BB")
                .param("date", nextWeek.toString())
                .param("startTime", "18:00")
                .param("endTime", "21:00")
                .param("speaking", "true")
                .param("infoUrl", "https://www.meetup.com/londonjavacommunity/events/123456/"))
                .hasStatus3xxRedirection();
    }
}
