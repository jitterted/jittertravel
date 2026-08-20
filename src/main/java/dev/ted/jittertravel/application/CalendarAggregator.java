package dev.ted.jittertravel.application;

import java.util.ArrayList;
import java.util.List;

public class CalendarAggregator {

    private final ConferenceCalendarProjector conferenceCalendarProjector;
    private final FlightCalendarProjector flightCalendarProjector;
    private final TrainCalendarProjector trainCalendarProjector;
    private final HotelCalendarProjector hotelCalendarProjector;
    private final GatheringCalendarProjector gatheringCalendarProjector;
    private final PrivateEventCalendarProjector privateEventCalendarProjector;
    private final GroundTransferCalendarProjector groundTransferCalendarProjector;

    public CalendarAggregator(ConferenceCalendarProjector conferenceCalendarProjector,
                              FlightCalendarProjector flightCalendarProjector,
                              TrainCalendarProjector trainCalendarProjector,
                              HotelCalendarProjector hotelCalendarProjector,
                              GatheringCalendarProjector gatheringCalendarProjector,
                              PrivateEventCalendarProjector privateEventCalendarProjector,
                              GroundTransferCalendarProjector groundTransferCalendarProjector) {
        this.conferenceCalendarProjector = conferenceCalendarProjector;
        this.flightCalendarProjector = flightCalendarProjector;
        this.trainCalendarProjector = trainCalendarProjector;
        this.hotelCalendarProjector = hotelCalendarProjector;
        this.gatheringCalendarProjector = gatheringCalendarProjector;
        this.privateEventCalendarProjector = privateEventCalendarProjector;
        this.groundTransferCalendarProjector = groundTransferCalendarProjector;
    }

    public List<CalendarEntry> allEntries() {
        List<CalendarEntry> entries = new ArrayList<>();
        entries.addAll(conferenceCalendarProjector.entries());
        entries.addAll(flightCalendarProjector.entries());
        entries.addAll(trainCalendarProjector.entries());
        entries.addAll(hotelCalendarProjector.entries());
        entries.addAll(gatheringCalendarProjector.entries());
        entries.addAll(privateEventCalendarProjector.entries());
        entries.addAll(groundTransferCalendarProjector.entries());
        return entries;
    }
}
