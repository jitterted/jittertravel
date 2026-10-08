package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.domain.InvalidEnteredLocation;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.LocationRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * What {@code fragments/place-picker.html} renders, through a real form (Book Hotel). The slice has
 * no {@code PlacesUsedProjector}, so the short list is empty and every country is in the
 * "All countries" group — which is the markup the page script rearranges ({@code PlacePickerJsTest}).
 */
@Tag("spring")
@WebMvcTest(BookHotelController.class)
@WithMockUser(roles = "OWNER")
class PlacePickerWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    HotelBooking hotelBooking;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void countryIsASelectOfCodesWithEveryCountryOffered() {
        assertThat(mockMvc.get().uri("/book-hotel"))
                .bodyText()
                .contains("<select class=\"place-select\" data-country-picker id=\"country\" name=\"country\">")
                .contains("<optgroup label=\"All countries\" data-all-countries>")
                .contains("<option value=\"DE\">Germany</option>")
                .contains("<option value=\"US\">United States</option>")
                .as("the old free-text Country input is gone")
                .doesNotContain("<input type=\"text\" id=\"country\"");
    }

    @Test
    void withNoCountryChosenRegionIsTheFreeTextBox() {
        MvcTestResult result = mockMvc.get().uri("/book-hotel").exchange();

        assertThat(result)
                .bodyText()
                .contains("<span data-region-label>Region (optional)</span>");
        assertThat(openingTag(result, "region-list"))
                .as("the state list is neither shown nor submitted")
                .contains("name=\"region\"", "disabled=\"disabled\"", "hidden=\"hidden\"");
        assertThat(openingTag(result, "region"))
                .as("the text box is the live control")
                .contains("type=\"text\"", "name=\"region\"")
                .doesNotContain("disabled", "hidden");
    }

    @Test
    void aRefusedUsStayIsReRenderedWithTheStateListAndBothErrorsUnderTheirInputs() {
        willThrow(new InvalidEnteredLocation(List.of(
                new InvalidLocationEntry(LocationRole.STAY, LocationField.REGION,
                                         "State required for United States"))))
                .given(hotelBooking).bookHotel(any(), any());

        MvcTestResult result = post("US", "");

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookHotel")
                .hasFieldErrorCode("region", "invalidLocation");
        assertThat(result)
                .bodyText()
                .contains("<span data-region-label>State</span>")
                .contains("<optgroup label=\"United States\">")
                .contains("<option value=\"CO\">Colorado</option>")
                .contains("<span class=\"error\">State required for United States</span>");
        assertThat(openingTag(result, "region-list"))
                .as("the state list is the live control")
                .contains("name=\"region\"")
                .doesNotContain("disabled", "hidden");
        assertThat(openingTag(result, "region"))
                .as("the text box is neither shown nor submitted")
                .contains("disabled=\"disabled\"", "hidden=\"hidden\"");
    }

    @Test
    void aRefusedCountryShowsItsErrorUnderCountry() {
        willThrow(new InvalidEnteredLocation(List.of(
                new InvalidLocationEntry(LocationRole.STAY, LocationField.COUNTRY, "Unknown country"))))
                .given(hotelBooking).bookHotel(any(), any());

        MvcTestResult result = post("Freedonia", "");

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookHotel")
                .hasFieldErrorCode("country", "invalidLocation");
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">Unknown country</span>");
    }

    /**
     * A value stored before the pickers existed is kept as an option of its own, selected, so the
     * form does not silently swap it for the first country in the list.
     */
    @Test
    void aValueThatIsNotACodeIsKeptAsItsOwnSelectedOption() {
        willThrow(new InvalidEnteredLocation(List.of(
                new InvalidLocationEntry(LocationRole.STAY, LocationField.COUNTRY, "Unknown country"))))
                .given(hotelBooking).bookHotel(any(), any());

        MvcTestResult result = post("United States", "Colorado");

        assertThat(result)
                .bodyText()
                .contains("<option value=\"United States\" selected=\"selected\">United States</option>");
    }

    @Test
    void aStateFromTheListIsSelectedWhenTheFormComesBack() {
        willThrow(new InvalidEnteredLocation(List.of(
                new InvalidLocationEntry(LocationRole.STAY, LocationField.CITY, "City is required"))))
                .given(hotelBooking).bookHotel(any(), any());

        MvcTestResult result = post("US", "CO");

        assertThat(result)
                .bodyText()
                .contains("<option value=\"CO\" selected=\"selected\">Colorado</option>");
    }

    /**
     * The opening tag of the element with this id. Thymeleaf writes {@code hidden} and
     * {@code disabled} in either order from one run to the next, so a whole-tag string comparison
     * would be flaky; the tag is found by its id and its attributes asserted one by one.
     */
    private static String openingTag(MvcTestResult result, String id) {
        String body;
        try {
            body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
        Matcher tag = Pattern.compile("<(?:select|input)[^>]*\\sid=\"" + id + "\"[^>]*>").matcher(body);
        assertThat(tag.find())
                .as("an element with id " + id)
                .isTrue();
        return tag.group();
    }

    private MvcTestResult post(String country, String region) {
        return mockMvc.post().uri("/book-hotel")
                      .with(csrf())
                      .param("hotelBookingId", "550e8400-e29b-41d4-a716-446655440000")
                      .param("hotelName", "Grand Hotel")
                      .param("street", "123 Main St")
                      .param("city", "Springfield")
                      .param("region", region)
                      .param("country", country)
                      .param("postalCode", "62701")
                      .param("checkIn", "2026-07-01T15:00")
                      .param("checkOut", "2026-07-02T11:00")
                      .param("bookingIntent", "TENTATIVE")
                      .exchange();
    }
}
