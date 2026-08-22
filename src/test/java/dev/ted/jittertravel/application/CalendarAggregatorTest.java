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
    @Mock PrivateEventCalendarProjector privateEventProjector;
    @Mock GroundTransferCalendarProjector groundTransferProjector;

    @Test
    void allEntriesAreReturnedFromAllProjectors() {
        CalendarEntry conference = entry(new EntryDetails.Conference(AttendanceCommitment.WATCHING), "JavaOne");
        CalendarEntry flight = entry(new EntryDetails.Flight(null), "SFO→FRA");
        CalendarEntry train = entry(new EntryDetails.Train(null), "Eurostar");
        CalendarEntry hotel = entry(new EntryDetails.Lodging(null, null), "Grand Hotel");
        CalendarEntry gathering = entry(new EntryDetails.Gathering(null, false, null), "Mob Session");
        CalendarEntry privateEvent = entry(new EntryDetails.PrivateEvent(), "Dinner with friends");
        CalendarEntry groundTransfer = entry(new EntryDetails.GroundTransfer(null, null), "DEN → Marriott Lone Tree");
        given(conferenceProjector.entries()).willReturn(List.of(conference));
        given(flightProjector.entries()).willReturn(List.of(flight));
        given(trainProjector.entries()).willReturn(List.of(train));
        given(hotelProjector.entries()).willReturn(List.of(hotel));
        given(gatheringProjector.entries()).willReturn(List.of(gathering));
        given(privateEventProjector.entries()).willReturn(List.of(privateEvent));
        given(groundTransferProjector.entries()).willReturn(List.of(groundTransfer));

        CalendarAggregator aggregator = new CalendarAggregator(
                conferenceProjector, flightProjector, trainProjector, hotelProjector,
                gatheringProjector, privateEventProjector, groundTransferProjector);

        assertThat(aggregator.allEntries())
                .containsExactlyInAnyOrder(conference, flight, train, hotel, gathering, privateEvent,
                                           groundTransfer);
    }

    @Test
    void emptyProjectorsReturnEmptyList() {
        given(conferenceProjector.entries()).willReturn(List.of());
        given(flightProjector.entries()).willReturn(List.of());
        given(trainProjector.entries()).willReturn(List.of());
        given(hotelProjector.entries()).willReturn(List.of());
        given(gatheringProjector.entries()).willReturn(List.of());
        given(privateEventProjector.entries()).willReturn(List.of());
        given(groundTransferProjector.entries()).willReturn(List.of());

        CalendarAggregator aggregator = new CalendarAggregator(
                conferenceProjector, flightProjector, trainProjector, hotelProjector,
                gatheringProjector, privateEventProjector, groundTransferProjector);

        assertThat(aggregator.allEntries()).isEmpty();
    }

    private static CalendarEntry entry(EntryDetails details, String title) {
        return new CalendarEntry(START, END, title, List.of(), title + " cont'd", List.of(), details);
    }
}
