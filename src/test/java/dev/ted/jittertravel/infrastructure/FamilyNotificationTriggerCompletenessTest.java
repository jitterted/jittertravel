package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceDatesChanged;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.DifferentCityConflictCleared;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FamilyNotified;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightItineraryBooked;
import dev.ted.jittertravel.domain.FlightItineraryCancelled;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.OneOffTaskCompleted;
import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventMatchingLocationChanged;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainCancelled;
import dev.ted.jittertravel.domain.TrainChanged;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether family hear about an event is a decision, and a new event class must not be able to skip
 * it. {@code Event} is deliberately not sealed (CLAUDE.md), so the compiler cannot ask; this test
 * does, in the style of {@code CalendarDayMenuTest}: it finds every event class in {@code domain}
 * and requires each to be written down below, either as telling family or as silent <em>with the
 * reason</em>.
 * <p>
 * The scope rule they are held to (Ted, 2026-09-15): family want to know that there is a
 * <em>new trip</em>, and that a trip they were told about is <em>no longer happening</em>. Anything
 * else is silent. Editing this list is exactly what a change that makes family hear something new
 * has to do, which is the point of it being here rather than computed.
 * <p>
 * This pins the decision, not the behaviour: that each telling event really notifies is
 * {@code FamilyNotificationTranslatorTest}'s claim.
 */
class FamilyNotificationTriggerCompletenessTest {

    private static final Set<Class<? extends Event>> TELLS_FAMILY = Set.of(
            FlightBooked.class,
            FlightItineraryBooked.class,
            FlightItineraryCancelled.class,
            ConferenceAttendanceConfirmed.class,
            ConferenceAttendanceDeclined.class,
            ConferenceCancelled.class,
            TalkAccepted.class,
            TalkRejected.class);

    private static final Map<Class<? extends Event>, String> SILENT = silent();

    @Test
    void everyEventClassIsEitherToldToFamilyOrSilentForAWrittenReason() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Event.class));

        Set<String> undecided = new LinkedHashSet<>();
        for (var candidate : scanner.findCandidateComponents("dev.ted.jittertravel.domain")) {
            Class<?> event = Class.forName(candidate.getBeanClassName());
            if (event.isInterface()) {
                continue;
            }
            if (!TELLS_FAMILY.contains(event) && !SILENT.containsKey(event)) {
                undecided.add(event.getSimpleName());
            }
        }

        assertThat(undecided)
                .as("a new event class needs a decision: does family hear about it? Add it to "
                    + "TELLS_FAMILY (and teach FamilyNotificationTranslator), or to SILENT with a reason")
                .isEmpty();
    }

    @Test
    void noEventIsBothToldAndSilent() {
        assertThat(SILENT.keySet())
                .doesNotContainAnyElementsOf(TELLS_FAMILY);
    }

    @Test
    void everySilentEventSaysWhy() {
        assertThat(SILENT.values())
                .as("every silent event carries a reason")
                .noneMatch(String::isBlank);
    }

    private static Map<Class<? extends Event>, String> silent() {
        Map<Class<? extends Event>, String> silent = new LinkedHashMap<>();
        because("a changed flight is not a new trip and not a cancelled one (decided 2026-09-15)",
                silent, FlightChanged.class);
        because("decided 2026-10-05 (Ted): a single dropped leg is often a rebooking and not a trip "
                + "that is off, so cancelling one flight on its own tells family nothing. (Cancelling "
                + "a whole itinerary does, via FlightItineraryCancelled, which is in TELLS_FAMILY.)",
                silent, FlightCancelled.class);
        because("a schedule change is not a new trip; the FlightBooked it writes for an added or "
                + "reinstated leg is suppressed by FlightItineraryChanged being in the same batch",
                silent, FlightItineraryChanged.class);
        because("moves only the speaking axis, never the commitment, so it changes nothing family "
                + "were told (Q1, 2026-09-10)",
                silent, TalkSubmitted.class, TalkWithdrawn.class, InvitedToSpeak.class);
        because("the submission pipeline stays private; a CFP opening is not a trip",
                silent, CfpOpened.class);
        because("planning or editing a conference is not a commitment to go; only the commitment is news",
                silent, ConferencePlanned.class, ConferenceDatesChanged.class);
        because("a hotel is detail of a trip family are told about by its flights, not a new trip of its own",
                silent, HotelBooked.class, HotelChanged.class, HotelBookingCancelled.class);
        because("trains and ground transfers happen inside a trip already announced by its flights",
                silent, TrainBooked.class, TrainChanged.class, TrainCancelled.class,
                GroundTransferPlanned.class, GroundTransferCancelled.class);
        because("a local gathering or a private event is not travel family plan around",
                silent, GatheringPlanned.class, GatheringChanged.class, PrivateEventPlanned.class,
                PrivateEventCancelled.class, PrivateEventMatchingLocationChanged.class);
        because("housekeeping with no meaning to family",
                silent, DifferentCityConflictCleared.class, OneOffTaskCompleted.class);
        because("the record of the notification itself; reacting to it would loop",
                silent, FamilyNotified.class);
        return Map.copyOf(silent);
    }

    @SafeVarargs
    private static void because(String reason, Map<Class<? extends Event>, String> into,
                                Class<? extends Event>... events) {
        for (Class<? extends Event> event : events) {
            into.put(event, reason);
        }
    }
}
