package dev.ted.jittertravel.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CalendarAggregatorTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 5, 17, 0);

    @Mock ConferenceCalendarProjector conferenceProjector;
    @Mock FlightCalendarProjector flightProjector;
    @Mock TrainCalendarProjector trainProjector;
    @Mock HotelCalendarProjector hotelProjector;
    @Mock GatheringCalendarProjector gatheringProjector;

    @Test
    void allEntriesAreReturnedFromAllProjectors() {
        CalendarEntry conference = entry(EntryKind.CONFERENCE, "JavaOne");
        CalendarEntry flight = entry(EntryKind.FLIGHT, "SFO→FRA");
        CalendarEntry train = entry(EntryKind.TRAIN, "Eurostar");
        CalendarEntry hotel = entry(EntryKind.LODGING, "Grand Hotel");
        CalendarEntry gathering = entry(EntryKind.GATHERING, "Mob Session");
        given(conferenceProjector.entries()).willReturn(List.of(conference));
        given(flightProjector.entries()).willReturn(List.of(flight));
        given(trainProjector.entries()).willReturn(List.of(train));
        given(hotelProjector.entries()).willReturn(List.of(hotel));
        given(gatheringProjector.entries()).willReturn(List.of(gathering));

        CalendarAggregator aggregator = new CalendarAggregator(
                conferenceProjector, flightProjector, trainProjector, hotelProjector, gatheringProjector);

        assertThat(aggregator.allEntries())
                .containsExactlyInAnyOrder(conference, flight, train, hotel, gathering);
    }

    @Test
    void emptyProjectorsReturnEmptyList() {
        given(conferenceProjector.entries()).willReturn(List.of());
        given(flightProjector.entries()).willReturn(List.of());
        given(trainProjector.entries()).willReturn(List.of());
        given(hotelProjector.entries()).willReturn(List.of());
        given(gatheringProjector.entries()).willReturn(List.of());

        CalendarAggregator aggregator = new CalendarAggregator(
                conferenceProjector, flightProjector, trainProjector, hotelProjector, gatheringProjector);

        assertThat(aggregator.allEntries()).isEmpty();
    }

    private static CalendarEntry entry(EntryKind kind, String title) {
        return new CalendarEntry(kind, START, END, title, List.of(), title + " cont'd", List.of(), null);
    }
}
