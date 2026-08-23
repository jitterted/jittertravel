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
 * <strong>72, 24 and 4 hours before</strong> (Ted, 2026-08-18, gaining the 4h backstop 2026-08-22),
 * against the hotel source's 48/24/4. A CFP needs the earlier first warning because acting on it
 * means writing an abstract, which is days of work rather than the single click that cancels a
 * hotel.
 * <p>
 * The 4h alarm is the same backstop the hotel source carries, and it is here for the same mechanism:
 * iOS silently skips an alarm whose trigger is already in the past, so a CFP recorded inside 72h of
 * closing would otherwise fire nothing at all. It doubles as the last-second submission nudge — the
 * hours in which a talk actually gets written.
 * <p>
 * {@code TimeView.ALL}, not {@code FUTURE}: that filter asks whether the <em>conference</em> is over,
 * and a CFP closes months before its conference starts. The only filter that belongs here is on the
 * deadline itself.
 */
@Component
public class CfpDeadlineSource implements ICalEventSource {

    /**
     * Three alarms. Three days is enough notice to decide whether to submit and write the thing; one
     * day is the "it is now or never" nudge; four hours is both the last-second submission window and
     * the only alarm a CFP recorded inside 72h of closing still has left.
     */
    static final List<String> CFP_ALARMS = List.of("-PT72H", "-PT24H", "-PT4H");

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
