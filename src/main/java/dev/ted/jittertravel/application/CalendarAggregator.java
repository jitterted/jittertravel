package dev.ted.jittertravel.application;

import java.util.ArrayList;
import java.util.List;

public class CalendarAggregator {

    private final ConferenceCalendarProjector conferenceCalendarProjector;
    private final FlightCalendarProjector flightCalendarProjector;
    private final TrainCalendarProjector trainCalendarProjector;
    private final HotelCalendarProjector hotelCalendarProjector;
    private final GatheringCalendarProjector gatheringCalendarProjector;

    public CalendarAggregator(ConferenceCalendarProjector conferenceCalendarProjector,
                              FlightCalendarProjector flightCalendarProjector,
                              TrainCalendarProjector trainCalendarProjector,
                              HotelCalendarProjector hotelCalendarProjector,
                              GatheringCalendarProjector gatheringCalendarProjector) {
        this.conferenceCalendarProjector = conferenceCalendarProjector;
        this.flightCalendarProjector = flightCalendarProjector;
        this.trainCalendarProjector = trainCalendarProjector;
        this.hotelCalendarProjector = hotelCalendarProjector;
        this.gatheringCalendarProjector = gatheringCalendarProjector;
    }

    public List<CalendarEntry> allEntries() {
        List<CalendarEntry> entries = new ArrayList<>();
        entries.addAll(conferenceCalendarProjector.entries());
        entries.addAll(flightCalendarProjector.entries());
        entries.addAll(trainCalendarProjector.entries());
        entries.addAll(hotelCalendarProjector.entries());
        entries.addAll(gatheringCalendarProjector.entries());
        return entries;
    }
}
