package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

@WebMvcTest(EventLogController.class)
@WithMockUser
class EventLogControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PostgresPersister persister;

    @Test
    void eventLogUrlMapsToOkWithHtmlContentType() {
        given(persister.countEvents()).willReturn(0);
        given(persister.loadEventPage(anyInt(), anyInt())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/admin/eventlog"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void rendersEventRow() {
        given(persister.countEvents()).willReturn(1);
        given(persister.loadEventPage(anyInt(), anyInt())).willReturn(List.of(
                new PostgresPersister.EventLogRow(
                        1L,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        OffsetDateTime.now(),
                        "FlightBooked",
                        "{}")
        ));

        assertThat(mockMvc.get().uri("/admin/eventlog"))
                .hasStatusOk()
                .bodyText()
                .contains("FlightBooked");
    }
}
