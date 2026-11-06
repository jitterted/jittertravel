package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.EventLogFilter;
import dev.ted.jittertravel.web.EventLogFilterView.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventLogFilterViewTest {

    private static final String ITINERARY_ID = "99999999-9999-9999-9999-999999999999";

    @Test
    void anEmptyFilterIsNotActive() {
        assertThat(new EventLogFilterView(EventLogFilter.none()).active())
                .as("nothing chosen is the page as it was before the filter existed")
                .isFalse();
    }

    @Test
    void typesAloneOrIdentifiersAloneMakeItActive() {
        assertThat(view(List.of("FlightBooked"), List.of()).active())
                .as("types alone")
                .isTrue();
        assertThat(view(List.of(), List.of(ITINERARY_ID)).active())
                .as("identifiers alone")
                .isTrue();
    }

    @Test
    void typesAreChosenOnlyWhenTheFilterNamesSome() {
        assertThat(view(List.of("FlightBooked"), List.of()).typesChosen())
                .as("a type is named")
                .isTrue();
        assertThat(view(List.of(), List.of(ITINERARY_ID)).typesChosen())
                .as("identifiers alone still mean every type")
                .isFalse();
    }

    @Test
    void onlyTheChosenTypesAreSelected() {
        EventLogFilterView view = view(List.of("FlightBooked"), List.of());

        assertThat(view.selected("FlightBooked"))
                .as("the chosen type")
                .isTrue();
        assertThat(view.selected("FlightChanged"))
                .as("a type not chosen")
                .isFalse();
    }

    @Test
    void theIdentifierInputIsRefilledWithTheIdentifiersSpaceSeparated() {
        assertThat(view(List.of(), List.of("aaa", "bbb")).identifierText())
                .isEqualTo("aaa bbb");
        assertThat(view(List.of("FlightBooked"), List.of()).identifierText())
                .isEmpty();
    }

    @Test
    void theSummaryCountsWhatWasChosenInSingularAndPlural() {
        assertThat(view(List.of("FlightBooked"), List.of("aaa")).summary())
                .isEqualTo("1 type and 1 identifier selected.");
        assertThat(view(List.of("FlightBooked", "FamilyNotified"), List.of("aaa", "bbb", "ccc")).summary())
                .isEqualTo("2 types and 3 identifiers selected.");
        assertThat(view(List.of("FlightBooked", "FamilyNotified"), List.of()).summary())
                .isEqualTo("2 types selected.");
        assertThat(view(List.of(), List.of("aaa")).summary())
                .isEqualTo("1 identifier selected.");
    }

    @Test
    void aPagingLinkCarriesEveryTypeAndTheIdentifiers() {
        EventLogFilterView view = view(List.of("FlightBooked", "FamilyNotified"), List.of("aaa", "bbb"));

        assertThat(view.hrefForPage(2))
                .isEqualTo("/admin/eventlog?page=2&type=FlightBooked&type=FamilyNotified&id=aaa%20bbb");
    }

    @Test
    void aPagingLinkWithNoFilterIsThePageAlone() {
        assertThat(new EventLogFilterView(EventLogFilter.none()).hrefForPage(3))
                .isEqualTo("/admin/eventlog?page=3");
    }

    @Test
    void aPagingLinkEncodesWhatWouldOtherwiseEndOrChangeTheIdentifier() {
        assertThat(view(List.of(), List.of("a&b=c+d")).hrefForPage(0))
                .as("& would start another parameter, and + would come back as a space and split the identifier")
                .isEqualTo("/admin/eventlog?page=0&id=a%26b%3Dc%2Bd");
    }

    @Test
    void withNoIdentifiersThePayloadIsOneUnmatchedSegment() {
        assertThat(view(List.of("FlightBooked"), List.of()).segmentsOf("{ \"a\" : 1 }"))
                .containsExactly(new Segment("{ \"a\" : 1 }", false));
    }

    @Test
    void aMatchedIdentifierIsItsOwnSegmentWithTheTextAroundItKept() {
        String payload = "{ \"id\" : \"" + ITINERARY_ID + "\" }";

        assertThat(view(List.of(), List.of(ITINERARY_ID)).segmentsOf(payload))
                .containsExactly(
                        new Segment("{ \"id\" : \"", false),
                        new Segment(ITINERARY_ID, true),
                        new Segment("\" }", false));
    }

    @Test
    void everyOccurrenceOfEveryIdentifierIsMatched() {
        assertThat(view(List.of(), List.of("aaa", "bbb")).segmentsOf("aaa-bbb-aaa"))
                .containsExactly(
                        new Segment("aaa", true),
                        new Segment("-", false),
                        new Segment("bbb", true),
                        new Segment("-", false),
                        new Segment("aaa", true));
    }

    @Test
    void aPayloadTheIdentifierIsNotInComesBackWhole() {
        assertThat(view(List.of(), List.of("zzz")).segmentsOf("{ \"a\" : 1 }"))
                .as("the row matched on its event id or command id, so nothing in the payload is marked")
                .containsExactly(new Segment("{ \"a\" : 1 }", false));
    }

    @Test
    void whereOneIdentifierBeginsAnotherTheLongerOneIsMatchedWhole() {
        assertThat(view(List.of(), List.of("abc", "abcdef")).segmentsOf("x abcdef y"))
                .containsExactly(
                        new Segment("x ", false),
                        new Segment("abcdef", true),
                        new Segment(" y", false));
    }

    @Test
    void anIdentifierIsMatchedAsTextNotAsAPattern() {
        assertThat(view(List.of(), List.of("a.c")).segmentsOf("abc a.c"))
                .as("the dot is a dot, so abc is not a match")
                .containsExactly(
                        new Segment("abc ", false),
                        new Segment("a.c", true));
    }

    @Test
    void theSegmentsPutBackTogetherAreThePayloadUnchanged() {
        String payload = "{ \"note\" : \"<b>&amp;</b>\", \"id\" : \"aaa\", \"other\" : \"aaa\" }";

        assertThat(view(List.of(), List.of("aaa")).segmentsOf(payload))
                .extracting(Segment::text)
                .as("no markup is built here and nothing is escaped: that is the template's job")
                .containsExactly("{ \"note\" : \"<b>&amp;</b>\", \"id\" : \"", "aaa", "\", \"other\" : \"", "aaa", "\" }");
    }

    private EventLogFilterView view(List<String> types, List<String> identifiers) {
        return new EventLogFilterView(new EventLogFilter(types, identifiers));
    }
}
