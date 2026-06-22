package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.AddressParseService;
import dev.ted.jittertravel.infrastructure.AddressParseService.ParsedAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(AddressParseController.class)
@WithMockUser(roles = "OWNER")
class AddressParseControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    AddressParseService parseService;

    @Test
    void getWithQueryReturnsParsedAddressAsJson() {
        // The {q} template is expanded and URL-encoded by the test client, exercising the
        // same query-param path the browser uses (no CSRF token needed for a GET).
        given(parseService.parse("Kolpingstr. 1, 63867 Johannesberg"))
                .willReturn(Optional.of(new ParsedAddress(
                        "Kolpingstr. 1", "Johannesberg", "Bavaria", "63867", "Germany", "Johannesberg")));

        assertThat(mockMvc.get().uri("/api/parse-address?q={q}", "Kolpingstr. 1, 63867 Johannesberg"))
                .hasStatusOk()
                .bodyText()
                .contains("\"city\":\"Johannesberg\"")
                .contains("\"postalCode\":\"63867\"");
    }

    @Test
    void getWithUnresolvableQueryReturnsUnprocessableEntity() {
        given(parseService.parse("nowhere")).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/api/parse-address?q={q}", "nowhere"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
