package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBandTest {

    @Test
    void conferenceLabelNamesItItsCityAndItsExactDates() {
        ContextBand band = ContextBand.from(new ScheduleContext.Conference("dev2next", "Chicago",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18)));

        assertThat(band)
                .isEqualTo(new ContextBand("dev2next, Chicago · Sep 14–18",
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18)));
    }

    @Test
    void gatheringLabelReadsTheSameWayAsAConference() {
        ContextBand band = ContextBand.from(new ScheduleContext.Gathering("XP Day", "London",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)));

        assertThat(band.label()).isEqualTo("XP Day, London · Sep 14");
    }

    @Test
    void travelLabelNamesBothEnds() {
        ContextBand band = ContextBand.from(new ScheduleContext.Travel("London", "Berlin",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)));

        assertThat(band.label()).isEqualTo("London → Berlin · Sep 14");
    }

    @Test
    void stayLabelSaysHotelSoItIsNotReadAsAGap() {
        ContextBand band = ContextBand.from(new ScheduleContext.Stay("Chicago",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18)));

        assertThat(band.label()).isEqualTo("Hotel, Chicago · Sep 14–18");
    }

    @Test
    void aRangeCrossingAMonthNamesBothMonths() {
        ContextBand band = ContextBand.from(new ScheduleContext.Conference("SoCraTes", "Soltau",
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 2)));

        assertThat(band.label()).isEqualTo("SoCraTes, Soltau · Sep 30 – Oct 2");
    }

    @Test
    void aSingleDaySaysThatDayOnce() {
        ContextBand band = ContextBand.from(new ScheduleContext.Conference("PLoP", "Allerton",
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2)));

        assertThat(band.label()).isEqualTo("PLoP, Allerton · Oct 2");
    }
}
