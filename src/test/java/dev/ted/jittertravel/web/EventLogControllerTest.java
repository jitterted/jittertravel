package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.EventLogFilter;
import dev.ted.jittertravel.infrastructure.EventTypes;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.PostgresPersister.EventLogRow;
import org.junit.jupiter.api.Tag;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@Tag("spring")
@WebMvcTest(EventLogController.class)
@WithMockUser(roles = "OWNER")
class EventLogControllerTest {

    private static final String KIT = "https://ka-p.webawesome.com/kit/4c10f20c1e894bb6/webawesome@3.14.0";
    private static final String ITINERARY_ID = "99999999-9999-9999-9999-999999999999";

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PostgresPersister persister;

    @Test
    void eventLogUrlMapsToOkWithHtmlContentType() {
        stubLog(0, EventLogFilter.none());

        assertThat(mockMvc.get().uri("/admin/eventlog"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void rendersEventRow() {
        stubLog(1, EventLogFilter.none(), row(1L, "FlightBooked", "{}"));

        assertThat(mockMvc.get().uri("/admin/eventlog"))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"event-seq\">#1</span>")
                .contains("<span class=\"event-type\">FlightBooked</span>")
                .contains("<pre>{}</pre>");
    }

    @Test
    void theHeadLoadsTheThemeAndTheComboboxFromTheKitAndNothingElseFromIt() {
        stubLog(0, EventLogFilter.none());

        String body = bodyOf("/admin/eventlog");

        assertThat(body)
                .contains("<link rel=\"stylesheet\" href=\"" + KIT + "/styles/themes/default.css\"/>")
                .contains("<script type=\"module\" src=\"" + KIT + "/components/combobox/combobox.js\"></script>")
                .as("the reset would restyle the page, and the loader would fetch every component")
                .doesNotContain("native.css")
                .doesNotContain("utilities.css")
                .doesNotContain("webawesome.loader.js");
        assertThat(body.split("ka-p\\.webawesome\\.com", -1))
                .as("exactly two addresses on the kit's host")
                .hasSize(3);
    }

    @Test
    void everyRegisteredTypeIsAnOptionAndNoneIsSelectedWithoutAFilter() {
        stubLog(0, EventLogFilter.none());

        String body = bodyOf("/admin/eventlog");

        assertThat(body)
                .contains("<wa-combobox id=\"event-types\" name=\"type\" multiple with-clear")
                .contains("placeholder=\"All types — type to filter…\"")
                .as("the component's own limit is ten characters, which cuts every type name short")
                .contains("wa-combobox { --tag-max-size: 40ch; }");
        assertThat(EventTypes.logicalNames())
                .as("the longest type name still fits in a tag")
                .allMatch(type -> type.length() <= 40);
        for (String type : EventTypes.logicalNames()) {
            assertThat(body)
                    .contains("<wa-option value=\"" + type + "\">" + type + "</wa-option>");
        }
        assertThat(body)
                .doesNotContain("selected=\"selected\"");
    }

    @Test
    void theChosenTypesAreTheSelectedOptionsAndTheIdentifiersRefillTheirInput() {
        EventLogFilter filter = new EventLogFilter(List.of("FlightBooked", "FamilyNotified"), List.of(ITINERARY_ID));
        stubLog(2, filter, row(7L, "FamilyNotified", "{}"));

        String body = bodyOf("/admin/eventlog?type=FlightBooked&type=FamilyNotified&id=" + ITINERARY_ID);

        assertThat(body)
                .contains("<wa-option value=\"FlightBooked\" selected=\"selected\">FlightBooked</wa-option>")
                .contains("<wa-option value=\"FamilyNotified\" selected=\"selected\">FamilyNotified</wa-option>")
                .contains("<wa-option value=\"FlightChanged\">FlightChanged</wa-option>")
                .contains("placeholder=\"Type to filter…\"")
                .as("with types chosen the box no longer claims to show all of them")
                .doesNotContain("placeholder=\"All types — type to filter…\"")
                .contains("<input id=\"identifiers\" name=\"id\" type=\"text\" value=\"" + ITINERARY_ID + "\"")
                .contains("<span class=\"event-type\">FamilyNotified</span>");
        assertThat(body.split("selected=\"selected\"", -1))
                .as("two options selected and no more")
                .hasSize(3);
    }

    @Test
    void anUnknownTypeNameIsDroppedBeforeTheQuery() {
        EventLogFilter flightBookedOnly = new EventLogFilter(List.of("FlightBooked"), List.of());
        stubLog(1, flightBookedOnly, row(3L, "FlightBooked", "{}"));

        assertThat(bodyOf("/admin/eventlog?type=NoSuchEvent&type=FlightBooked"))
                .as("the row comes back only for a filter holding FlightBooked alone")
                .contains("<span class=\"event-seq\">#3</span>")
                .contains("<wa-option value=\"FlightBooked\" selected=\"selected\">FlightBooked</wa-option>");
    }

    @Test
    void severalIdentifiersInOneParameterAreSplitBeforeTheQuery() {
        EventLogFilter twoIdentifiers = new EventLogFilter(List.of(), List.of("aaa", "bbb"));
        stubLog(1, twoIdentifiers, row(4L, "HotelBooked", "{}"));

        assertThat(bodyOf("/admin/eventlog?id=aaa, bbb"))
                .contains("<span class=\"event-seq\">#4</span>")
                .contains("<input id=\"identifiers\" name=\"id\" type=\"text\" value=\"aaa bbb\"")
                .as("identifiers narrow the rows, but every type is still shown")
                .contains("placeholder=\"All types — type to filter…\"");
    }

    @Test
    void thePagingLinksCarryTheFilter() {
        EventLogFilter filter = new EventLogFilter(List.of("FlightBooked", "FamilyNotified"), List.of(ITINERARY_ID));
        stubLog(120, filter, row(60L, "FlightBooked", "{}"));

        String body = bodyOf("/admin/eventlog?page=1&type=FlightBooked&type=FamilyNotified&id=" + ITINERARY_ID);

        String carried = "&amp;type=FlightBooked&amp;type=FamilyNotified&amp;id=" + ITINERARY_ID;
        assertThat(body)
                .contains("<a href=\"/admin/eventlog?page=0" + carried + "\">‹ Previous</a>")
                .contains("<a href=\"/admin/eventlog?page=2" + carried + "\">Next ›</a>");
        assertThat(body.split("page=2" + carried, -1))
                .as("the toolbar under the list carries it too")
                .hasSize(3);
    }

    @Test
    void thePagesAreCountedFromTheMatchingRowsNotFromTheWholeLog() {
        EventLogFilter filter = new EventLogFilter(List.of("FlightBooked"), List.of());
        given(persister.countEvents()).willReturn(1214);
        given(persister.countEvents(filter)).willReturn(60);
        given(persister.loadEventPage(50, 50, filter)).willReturn(List.of(row(9L, "FlightBooked", "{}")));

        assertThat(bodyOf("/admin/eventlog?page=2&type=FlightBooked"))
                .as("60 matches are two pages, so the page just past them is clamped to the second and has no next")
                .contains("<span class=\"event-seq\">#9</span>")
                .contains("page <span>2</span> of <span>2</span>")
                .contains("<span class=\"disabled\">Next ›</span>")
                .contains("<a href=\"/admin/eventlog?page=0&amp;type=FlightBooked\">‹ Previous</a>");
    }

    @Test
    void theFirstPageHasNoPreviousLink() {
        stubLog(120, EventLogFilter.none(), row(120L, "FlightBooked", "{}"));

        assertThat(bodyOf("/admin/eventlog"))
                .contains("<span class=\"disabled\">‹ Previous</span>")
                .contains("<a href=\"/admin/eventlog?page=1\">Next ›</a>")
                .as("there is no page before the first to link to")
                .doesNotContain("‹ Previous</a>");
    }

    @Test
    void aMatchedIdentifierIsMarkedInThePayloadAndThePayloadIsStillEscaped() {
        EventLogFilter filter = new EventLogFilter(List.of(), List.of(ITINERARY_ID));
        String payload = "{\n  \"note\" : \"<b>bold</b>\",\n  \"itineraryId\" : {\n    \"id\" : \"" + ITINERARY_ID + "\"\n  }\n}";
        stubLog(1, filter, row(5L, "FlightItineraryBooked", payload));

        assertThat(bodyOf("/admin/eventlog?id=" + ITINERARY_ID))
                .contains("&quot;id&quot; : &quot;<mark>" + ITINERARY_ID + "</mark>&quot;")
                .contains("&lt;b&gt;bold&lt;/b&gt;")
                .as("markup in a payload is text, never an element")
                .doesNotContain("<b>bold</b>");
    }

    @Test
    void withoutAFilterTheMetaLineCountsTheWholeLogAndClearIsGreyed() {
        stubLog(1214, EventLogFilter.none(), row(1214L, "FlightBooked", "{}"));

        assertThat(bodyOf("/admin/eventlog"))
                .contains("<span>1,214</span> events total")
                .contains("<span class=\"clear\" title=\"No filter to clear\">Clear</span>")
                .as("nothing to clear, so no link")
                .doesNotContain("<a class=\"clear\" href=\"/admin/eventlog\">Clear</a>")
                .doesNotContain("events match");
    }

    @Test
    void underAFilterTheMetaLineSaysHowManyMatchOfTheTotalAndClearIsALink() {
        EventLogFilter filter = new EventLogFilter(List.of("FlightBooked"), List.of());
        given(persister.countEvents()).willReturn(1214);
        given(persister.countEvents(filter)).willReturn(2);
        given(persister.loadEventPage(0, 50, filter)).willReturn(List.of(row(8L, "FlightBooked", "{}")));

        assertThat(bodyOf("/admin/eventlog?type=FlightBooked"))
                .contains("<strong>2</strong> of")
                .contains("<span>1,214</span> events match")
                .contains("<a class=\"clear\" href=\"/admin/eventlog\">Clear</a>")
                .doesNotContain("<span class=\"clear\" title=\"No filter to clear\">Clear</span>")
                .doesNotContain("events total");
    }

    @Test
    void anEmptyLogSaysNothingIsRecordedYet() {
        stubLog(0, EventLogFilter.none());

        assertThat(bodyOf("/admin/eventlog"))
                .contains("No events recorded yet.")
                .doesNotContain("<strong>No events match.</strong>");
    }

    @Test
    void aFilterMatchingNothingNamesWhatWasAskedForAndOffersTheWholeLog() {
        EventLogFilter filter = new EventLogFilter(List.of("HotelBooked"), List.of(ITINERARY_ID));
        given(persister.countEvents()).willReturn(1214);
        given(persister.countEvents(filter)).willReturn(0);
        given(persister.loadEventPage(0, 50, filter)).willReturn(List.of());

        assertThat(bodyOf("/admin/eventlog?type=HotelBooked&id=" + ITINERARY_ID))
                .contains("<strong>No events match.</strong>")
                .contains("<span>1 type and 1 identifier selected.</span>")
                .contains("<a class=\"clear\" href=\"/admin/eventlog\">Clear the filter</a>")
                .contains("to see all <span>1,214</span>.")
                .contains("<strong>0</strong> of")
                .as("the log is not empty, only the match is")
                .doesNotContain("No events recorded yet.");
    }

    /** A log where the filter matches {@code matching} rows, which is also the whole log's size. */
    private void stubLog(int matching, EventLogFilter filter, EventLogRow... rows) {
        given(persister.countEvents()).willReturn(matching);
        given(persister.countEvents(filter)).willReturn(matching);
        given(persister.loadEventPage(anyInt(), anyInt(), eq(filter))).willReturn(List.of(rows));
    }

    private EventLogRow row(long sequence, String type, String payloadJson) {
        return new EventLogRow(sequence, UUID.randomUUID(), UUID.randomUUID(), OffsetDateTime.now(), type, payloadJson);
    }

    private String bodyOf(String uri) {
        var result = mockMvc.get().uri(uri).exchange();
        assertThat(result)
                .hasStatusOk();
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
