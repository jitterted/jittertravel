package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.DroppedView;
import dev.ted.jittertravel.application.TimeView;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The feed's second contributor: a conference's CFP closing deadline, as a VEVENT at the closing
 * instant with alarms the device fires locally.
 * <p>
 * <strong>72 and 24 hours before</strong> (Ted, 2026-08-18), not the hotel source's 48/24/4. A CFP
 * needs the earlier warning because acting on it means writing an abstract, which is days of work
 * rather than the single click that cancels a hotel.
 * <p>
 * Note what that trades away: the hotel source's 4h alarm exists as a backstop for a deadline
 * recorded less than 24h before it falls, where iOS silently skips the already-past earlier alarms.
 * A CFP recorded three days before it closes has the same hazard and no backstop — worth revisiting
 * if a late-recorded CFP ever slips past.
 * <p>
 * {@code TimeView.ALL}, not {@code FUTURE}: that filter asks whether the <em>conference</em> is over,
 * and a CFP closes months before its conference starts. The only filter that belongs here is on the
 * deadline itself.
 */
@Component
public class CfpDeadlineSource implements ICalEventSource {

    /**
     * Two alarms, both early. Three days is enough notice to decide whether to submit and write the
     * thing; one day is the "it is now or never" nudge.
     */
    static final List<String> CFP_ALARMS = List.of("-PT72H", "-PT24H");

    private static final Duration EVENT_DURATION = Duration.ofMinutes(15);
    private static final DateTimeFormatter DESCRIPTION_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ConferenceProjector projector;

    public CfpDeadlineSource(ConferenceProjector projector) {
        this.projector = projector;
    }

    @Override
    public List<ICalEvent> events(Instant now) {
        // DroppedView.HIDE, and it matters: a CFP reminder for a conference Ted has said no to is
        // an alarm on his phone for a decision he already made.
        return projector.views(TimeView.ALL, DroppedView.HIDE, now).stream()
                .filter(view -> view.cfpClosesOn() != null)
                .filter(view -> view.cfpClosesOn().utc().isAfter(now))
                .map(this::cfpEvent)
                .toList();
    }

    private ICalEvent cfpEvent(ConferenceView view) {
        Instant deadline = view.cfpClosesOn().utc();
        return new ICalEvent(
                view.conferenceId().id() + "-cfp@jittertravel",
                deadline,
                deadline.plus(EVENT_DURATION),
                "CFP closes: " + view.name(),
                description(view),
                CFP_ALARMS);
    }

    /**
     * Where and when the conference itself is — the two facts that decide whether submitting is
     * worth it. Both are already on the private feed's side of the line.
     */
    private String description(ConferenceView view) {
        String start = DESCRIPTION_DATE.format(view.startDate().atEntryZone());
        String end = DESCRIPTION_DATE.format(view.endDate().atEntryZone());
        return view.city() + ", " + view.country() + " — conference runs " + start + " to " + end;
    }
}
