package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.LocationAuditProjector;
import dev.ted.jittertravel.application.LocationZoneAudit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebMvcTest(ZoneAuditController.class)
@WithMockUser(roles = "OWNER")
class ZoneAuditControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    LocationAuditProjector locationAuditProjector;

    @MockitoBean
    LocationZoneAudit locationZoneAudit;

    @Test
    void zoneAuditUrlMapsToOkWithHtmlContentType() {
        given(locationZoneAudit.report(any(), any()))
                .willReturn(new LocationZoneAudit.Report(List.of(), List.of()));

        assertThat(mockMvc.get().uri("/admin/zone-audit"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void rendersResolvedAndUnresolvedRows() {
        given(locationZoneAudit.report(any(), any()))
                .willReturn(new LocationZoneAudit.Report(
                        List.of(new LocationZoneAudit.Entry(
                                LocationZoneAudit.Kind.LOCATION, "Frankfurt, Germany", "Europe/Berlin")),
                        List.of(new LocationZoneAudit.Entry(
                                LocationZoneAudit.Kind.AIRPORT, "XXX", null))));

        assertThat(mockMvc.get().uri("/admin/zone-audit"))
                .hasStatusOk()
                .bodyText()
                .contains("Frankfurt, Germany")
                .contains("Europe/Berlin")
                .contains("XXX");
    }
}
