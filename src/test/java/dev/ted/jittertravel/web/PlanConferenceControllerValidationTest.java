package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferencePlanning;
import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.application.PlanTentativeConferenceHandler;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.DateRangeNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.PlanTentativeConferenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each way the conference form can be wrong maps to a specific field error, so the re-rendered
 * form points at the input the traveler has to fix. The venue is in San Francisco, so "now" is
 * expressed in that zone — the rules are read there, not in the UTC-pinned test JVM.
 */
class PlanConferenceControllerValidationTest {

    private static final ZoneId VENUE_ZONE = ZoneId.of("America/Los_Angeles");
    private static final Instant NOW =
            LocalDateTime.of(2026, 5, 16, 10, 0).atZone(VENUE_ZONE).toInstant();

    @Test
    void startLaterOnTheSameDayIsRejectedOnTheStartDateField() {
        BindingResult bindingResult = submit(conferenceForm(
                LocalDateTime.of(2026, 5, 16, 18, 0), LocalDateTime.of(2026, 5, 18, 17, 0), null));

        assertThat(bindingResult.hasFieldErrors("startDate"))
                .as("a conference must start on a later day at the venue")
                .isTrue();
    }

    @Test
    void startOnTheNextDayIsAccepted() {
        BindingResult bindingResult = submit(conferenceForm(
                LocalDateTime.of(2026, 5, 17, 9, 0), LocalDateTime.of(2026, 5, 18, 17, 0), null));

        assertThat(bindingResult.hasErrors())
                .as("tomorrow at the venue is valid even though it is under 24 hours away: %s",
                    bindingResult.getAllErrors())
                .isFalse();
    }

    @Test
    void endBeforeStartIsRejectedOnTheEndDateField() {
        BindingResult bindingResult = submit(conferenceForm(
                LocalDateTime.of(2026, 5, 20, 9, 0), LocalDateTime.of(2026, 5, 20, 8, 0), null));

        assertThat(bindingResult.hasFieldErrors("endDate"))
                .isTrue();
    }

    @Test
    void anUnresolvableVenueWithNoZonePickIsRejectedOnTheZoneField() {
        PlanTentativeConferenceRequest request = conferenceForm(
                LocalDateTime.of(2026, 5, 20, 9, 0), LocalDateTime.of(2026, 5, 22, 17, 0), null);
        request.setVenueCity("Springfield");
        request.setVenueCountry("Freedonia");

        BindingResult bindingResult = submit(request);

        assertThat(bindingResult.hasFieldErrors("zone"))
                .as("with no zone derivable and none picked, the form must ask for one")
                .isTrue();
    }

    @Test
    void anUnresolvableVenueIsAcceptedOnceAZoneIsPicked() {
        PlanTentativeConferenceRequest request = conferenceForm(
                LocalDateTime.of(2026, 5, 20, 9, 0), LocalDateTime.of(2026, 5, 22, 17, 0), "US_CENTRAL");
        request.setVenueCity("Springfield");
        request.setVenueCountry("Freedonia");

        BindingResult bindingResult = submit(request);

        assertThat(bindingResult.hasErrors())
                .as("an explicit pick is what makes an unresolvable location usable: %s",
                    bindingResult.getAllErrors())
                .isFalse();
    }

    @Test
    void theChosenFormatRidesThroughTheHandlerOntoTheEvent() {
        // A non-default format (OPEN_SPACE) proves the handler reads the form value rather than
        // defaulting. Runs the real handler → command → event, minus persistence.
        PlanTentativeConferenceRequest request = conferenceForm(
                LocalDateTime.of(2026, 5, 20, 9, 0), LocalDateTime.of(2026, 5, 22, 17, 0), null);
        request.setFormat("OPEN_SPACE");

        ConferenceTentativelyPlanned event =
                new PlanTentativeConferenceHandler(new LocationZoneResolver()).handle(request)
                        .execute(new PlanTentativeConferenceContext(NOW))
                        .toList()
                        .getFirst();

        assertThat(event.format())
                .as("the form's format choice reaches the event through the handler")
                .isEqualTo(ConferenceFormat.OPEN_SPACE);
    }

    /** The controller's catch-and-reject block, exercised against the real handler and command. */
    private static BindingResult submit(PlanTentativeConferenceRequest request) {
        BindingResult bindingResult =
                new BeanPropertyBindingResult(request, "planTentativeConference");
        try {
            plan(request);
        } catch (DateRangeNotInFuture e) {
            bindingResult.rejectValue("startDate", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("endDate", "afterStartDate", e.getMessage());
        } catch (ZoneResolutionException e) {
            bindingResult.rejectValue("zone", "zoneUnresolved",
                    "Could not determine the time zone from the location — please choose one.");
        }
        return bindingResult;
    }

    /** What {@link ConferencePlanning#planConference} does, minus the persistence. */
    private static void plan(PlanTentativeConferenceRequest request) {
        new PlanTentativeConferenceHandler(new LocationZoneResolver()).handle(request)
                .execute(new PlanTentativeConferenceContext(NOW))
                .toList();
    }

    private static PlanTentativeConferenceRequest conferenceForm(LocalDateTime start,
                                                                 LocalDateTime end,
                                                                 String zone) {
        PlanTentativeConferenceRequest request = new PlanTentativeConferenceRequest();
        request.setConferenceId(UUID.randomUUID().toString());
        request.setName("JitterConf");
        request.setStartDate(start);
        request.setEndDate(end);
        request.setVenueName("Moscone Center");
        request.setVenueStreet("747 Howard St");
        request.setVenueCity("San Francisco");
        request.setVenueState("CA");
        request.setVenueCountry("USA");
        request.setVenuePostalCode("94103");
        request.setZone(zone);
        return request;
    }
}
