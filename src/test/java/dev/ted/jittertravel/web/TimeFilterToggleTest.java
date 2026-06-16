package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TimeView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeFilterToggleTest {

    @Test
    void futureFilterMarksUpcomingLinkActive() {
        String html = TimeFilterToggle.render("/booked-flights", TimeView.FUTURE).render();

        assertThat(html)
                .contains("<a href=\"/booked-flights?filter=future\" class=\"active\">Upcoming</a>")
                .contains("<a href=\"/booked-flights?filter=all\">All</a>");
    }

    @Test
    void allFilterMarksAllLinkActive() {
        String html = TimeFilterToggle.render("/booked-flights", TimeView.ALL).render();

        assertThat(html)
                .contains("<a href=\"/booked-flights?filter=all\" class=\"active\">All</a>")
                .contains("<a href=\"/booked-flights?filter=future\">Upcoming</a>");
    }

    @Test
    void basePathFlowsIntoBothLinks() {
        String html = TimeFilterToggle.render("/planned-gatherings", TimeView.FUTURE).render();

        assertThat(html)
                .contains("/planned-gatherings?filter=future")
                .contains("/planned-gatherings?filter=all");
    }
}