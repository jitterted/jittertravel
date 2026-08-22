package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelView;
import dev.ted.jittertravel.application.BookedHotelsProjector;
import dev.ted.jittertravel.application.TimeView;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The feed's original contributor, lifted out of {@code CalendarFeedAssembler} unchanged when
 * {@link ICalEventSource} arrived with the second one. A free-cancellation deadline becomes a
 * VEVENT at the deadline instant, and the device fires the alarms.
 */
@Component
public class HotelCancelDeadlineSource implements ICalEventSource {

    /**
     * Three alarms per deadline. 48h and 24h give early warning; 4h is the backstop that also covers
     * a booking made less than 24h before its deadline (iOS silently skips the already-past 48h/24h
     * alarms and still fires the 4h one).
     */
    static final List<String> DEADLINE_ALARMS = List.of("-PT48H", "-PT24H", "-PT4H");

    private static final Duration EVENT_DURATION = Duration.ofMinutes(15);
    private static final DateTimeFormatter DESCRIPTION_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BookedHotelsProjector projector;

    public HotelCancelDeadlineSource(BookedHotelsProjector projector) {
        this.projector = projector;
    }

    @Override
    public List<ICalEvent> events(Instant now) {
        return projector.views(TimeView.ALL, now).stream()
                .filter(view -> !view.cancelled())
                .filter(view -> view.cancelBy() != null)
                .filter(view -> view.cancelBy().utc().isAfter(now))
                .map(this::deadlineEvent)
                .toList();
    }

    private ICalEvent deadlineEvent(BookedHotelView view) {
        Instant deadline = view.cancelBy().utc();
        return new ICalEvent(
                view.hotelBookingId().id() + "-cancelby@jittertravel",
                deadline,
                deadline.plus(EVENT_DURATION),
                "Free-cancel deadline: " + view.hotelName(),
                deadlineDescription(view),
                DEADLINE_ALARMS);
    }

    private String deadlineDescription(BookedHotelView view) {
        String checkIn = DESCRIPTION_DATE.format(view.checkIn().atEntryZone());
        String checkOut = DESCRIPTION_DATE.format(view.checkOut().atEntryZone());
        return view.city() + " — check-in " + checkIn + ", check-out " + checkOut;
    }
}
