package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GroundTransferEndpointChoices;
import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.application.TransferEndpointOption;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a missing-travel gap fills in on the ground-transfer form. Ted's own case throughout
 * (2026-08-21): stuck in Johannesberg until the 13th, due in Frankfurt the same day.
 */
class GroundTransferPreselectionTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static final TransferEndpointOption SEMINAR_ZENTRUM = new TransferEndpointOption(
            "hotel:seminarzentrum",
            "SeminarZentrum Rückersbach — Johannesberg · check out Sun Sep 13, 11:00 AM",
            "Johannesberg", "2026-09-13", "11:00");
    private static final TransferEndpointOption HOLIDAY_INN = new TransferEndpointOption(
            "hotel:holiday-inn",
            "Holiday Inn Frankfurt - Alte Oper — Frankfurt · check in Sun Sep 13, 3:00 PM",
            "Frankfurt", "2026-09-13", "15:00");

    @Test
    void bothEndsSettledPutTheirTokensAndTheirMomentsOnTheForm() {
        PlanGroundTransferRequest request = formWithDefaults();

        preselect(both()).applyTo(request);

        assertThat(request.getOrigin()).isEqualTo("hotel:seminarzentrum");
        assertThat(request.getDestination()).isEqualTo("hotel:holiday-inn");
        assertThat(request.getDate()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(request.getDepartureTime())
                .as("the ride starts when he checks out")
                .isEqualTo(LocalTime.of(11, 0));
        assertThat(request.getArrivalTime())
                .as("and has to get him there by check-in")
                .isEqualTo(LocalTime.of(15, 0));
    }

    /**
     * An end the gap cannot settle keeps its empty select — and the other end still fills in.
     * Half an answer is worth having; a guessed one is not.
     */
    @Test
    void anUnsettledEndIsLeftForTedToChoose() {
        PlanGroundTransferRequest request = formWithDefaults();

        preselect(new GroundTransferEndpointChoices(
                List.of(), List.of(), List.of(SEMINAR_ZENTRUM), List.of())).applyTo(request);

        assertThat(request.getOrigin()).isEqualTo("hotel:seminarzentrum");
        assertThat(request.getDestination())
                .as("nothing in Frankfurt to choose, so the select stays on its placeholder")
                .isNull();
        assertThat(request.getArrivalTime())
                .as("and its time keeps the form's own default")
                .isEqualTo(LocalTime.of(12, 45));
    }

    /** Only the far end known: its day is the best the form can say. */
    @Test
    void aSettledDestinationAloneStillSeedsTheDay() {
        PlanGroundTransferRequest request = formWithDefaults();

        preselect(new GroundTransferEndpointChoices(
                List.of(), List.of(), List.of(), List.of(HOLIDAY_INN))).applyTo(request);

        assertThat(request.getOrigin()).isNull();
        assertThat(request.getDate()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(request.getArrivalTime()).isEqualTo(LocalTime.of(15, 0));
    }

    /**
     * The pair has to stay valid, or the POST comes back with
     * {@code InvalidGroundTransferTimeRange} for a range this class produced. Same 45 minutes the
     * form's own script uses.
     */
    @Test
    void anArrivalThatWouldLandBeforeTheDepartureIsPushedPastIt() {
        PlanGroundTransferRequest request = formWithDefaults();
        TransferEndpointOption earlyFlight = new TransferEndpointOption(
                "airport:FRA", "FRA — Frankfurt · depart Sun Sep 13, 9:00 AM (LH 1)",
                "Frankfurt", "2026-09-13", "09:00");

        preselect(new GroundTransferEndpointChoices(
                List.of(), List.of(earlyFlight), List.of(SEMINAR_ZENTRUM), List.of())).applyTo(request);

        assertThat(request.getDepartureTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(request.getArrivalTime())
                .as("09:00 is before the 11:00 check-out, so it moves to check-out + 45 min")
                .isEqualTo(LocalTime.of(11, 45));
    }

    private GroundTransferPreselection preselect(GroundTransferEndpointChoices choices) {
        return new GroundTransferPreselection(choices, johannesbergToFrankfurt());
    }

    private static GroundTransferEndpointChoices both() {
        return new GroundTransferEndpointChoices(
                List.of(), List.of(), List.of(SEMINAR_ZENTRUM), List.of(HOLIDAY_INN));
    }

    /** The form as the controller hands it over: today's date and its short midday hop. */
    private static PlanGroundTransferRequest formWithDefaults() {
        PlanGroundTransferRequest request = new PlanGroundTransferRequest();
        request.setDate(LocalDate.of(2026, 9, 1));
        request.setDepartureTime(LocalTime.of(12, 0));
        request.setArrivalTime(LocalTime.of(12, 45));
        return request;
    }

    private static ScheduleProblem.MissingTravel johannesbergToFrankfurt() {
        return new ScheduleProblem.MissingTravel(
                "Johannesberg", ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-09T17:00"), BERLIN),
                "Frankfurt", ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-13T15:00"), BERLIN));
    }
}
