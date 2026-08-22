package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for the ground-transfer form's prefill script: choosing an endpoint fills in
 * when it happens, so the times never have to be remembered or looked up in another tab (Ted,
 * 2026-08-20; extended to hotels 2026-08-21).
 * <p>
 * The <strong>script</strong> is lifted verbatim out of the shipped template, so there is no second
 * copy to drift. The <strong>markup</strong> is built here instead of rendered, because the options
 * come from a Thymeleaf {@code th:each} that only exists after Spring runs — that half of the
 * contract (options really carry {@code data-date}/{@code data-time}, fields really carry these
 * ids) is asserted in {@code PlanGroundTransferWebIntegrationTest}. Between the two, the loop is
 * closed with no server, Spring, DB or auth in this tier.
 */
class GroundTransferPrefillJsTest extends JsBehaviorTest {

    private static final Path FORM =
            Path.of("src/main/resources/templates/plan-ground-transfer.html");

    private static final String ARRIVAL_OPTION =
            "<option value=\"airport:DEN\" data-date=\"2026-09-14\" data-time=\"11:30\">"
            + "DEN arrive 11:30 AM</option>";
    private static final String DEPARTURE_OPTION =
            "<option value=\"airport:SFO\" data-date=\"2026-09-18\" data-time=\"14:00\">"
            + "SFO depart 2:00 PM</option>";
    // A hotel carries a moment too, since 2026-08-21: its check-out on the "From" side and its
    // check-in on the "To" side, which is why the two sides get different options here.
    private static final String HOTEL_CHECK_OUT_OPTION =
            "<option value=\"hotel:abc\" data-date=\"2026-09-16\" data-time=\"11:00\">"
            + "Marriott Lone Tree check out 11:00 AM</option>";
    private static final String HOTEL_CHECK_IN_OPTION =
            "<option value=\"hotel:abc\" data-date=\"2026-09-13\" data-time=\"15:00\">"
            + "Marriott Lone Tree check in 3:00 PM</option>";
    /** An option with no moment behind it — the script must leave the form untouched for one. */
    private static final String MOMENTLESS_OPTION =
            "<option value=\"hotel:xyz\" data-date=\"\" data-time=\"\">Somewhere, sometime</option>";

    @Test
    void choosingAnArrivalFillsInTheDateAndTheDepartureTime() {
        loadForm("2026-08-20", "09:00", "09:45");

        page.selectOption("#origin", "airport:DEN");

        assertThat(valueOf("date")).isEqualTo("2026-09-14");
        assertThat(valueOf("departureTime"))
                .as("the transfer starts when the plane gets in")
                .isEqualTo("11:30");
    }

    @Test
    void choosingADepartureFillsInTheDateAndTheArrivalTime() {
        loadForm("2026-08-20", "09:00", "09:45");

        page.selectOption("#destination", "airport:SFO");

        assertThat(valueOf("date")).isEqualTo("2026-09-18");
        assertThat(valueOf("arrivalTime"))
                .as("the transfer has to get you there by the time the plane goes")
                .isEqualTo("14:00");
    }

    /**
     * The far end is nudged only to keep the pair valid — otherwise the form would come back with
     * {@code InvalidGroundTransferTimeRange} for a range the script itself created.
     */
    @Test
    void anArrivalPushesTheFarEndLaterWhenItWouldOtherwiseInvertTheRange() {
        loadForm("2026-08-20", "09:00", "09:45");

        page.selectOption("#origin", "airport:DEN");

        assertThat(valueOf("arrivalTime"))
                .as("09:45 is before the new 11:30 departure, so it moves to 11:30 + 45 min")
                .isEqualTo("12:15");
    }

    @Test
    void aDeparturePullsTheFarEndEarlierWhenItWouldOtherwiseInvertTheRange() {
        loadForm("2026-08-20", "15:00", "15:45");

        page.selectOption("#destination", "airport:SFO");

        assertThat(valueOf("departureTime"))
                .as("15:00 is after the new 14:00 arrival, so it moves to 14:00 - 45 min")
                .isEqualTo("13:15");
    }

    @Test
    void aTimeThatIsAlreadyValidIsLeftAlone() {
        // Arrival 23:00 is comfortably after an 11:30 departure, so the script has no business
        // rewriting a time Ted deliberately typed.
        loadForm("2026-08-20", "09:00", "23:00");

        page.selectOption("#origin", "airport:DEN");

        assertThat(valueOf("arrivalTime")).isEqualTo("23:00");
    }

    /** Leaving a hotel: the ride starts when he checks out. */
    @Test
    void choosingAHotelToLeaveFillsInItsCheckOut() {
        loadForm("2026-08-20", "09:00", "09:45");

        page.selectOption("#origin", "hotel:abc");

        assertThat(valueOf("date")).isEqualTo("2026-09-16");
        assertThat(valueOf("departureTime")).isEqualTo("11:00");
    }

    /** Arriving at one: the ride has to get him there by check-in. */
    @Test
    void choosingAHotelToArriveAtFillsInItsCheckIn() {
        loadForm("2026-08-20", "09:00", "09:45");

        page.selectOption("#destination", "hotel:abc");

        assertThat(valueOf("date")).isEqualTo("2026-09-13");
        assertThat(valueOf("arrivalTime")).isEqualTo("15:00");
    }

    @Test
    void choosingAnOptionWithNoMomentLeavesEveryFieldAlone() {
        loadForm("2026-08-20", "09:00", "09:45");

        page.selectOption("#origin", "hotel:xyz");

        assertThat(valueOf("date")).isEqualTo("2026-08-20");
        assertThat(valueOf("departureTime")).isEqualTo("09:00");
        assertThat(valueOf("arrivalTime")).isEqualTo("09:45");
    }

    @Test
    void reselectingThePlaceholderLeavesTheFormAsItWas() {
        loadForm("2026-08-20", "09:00", "09:45");

        page.selectOption("#origin", "airport:DEN");
        page.selectOption("#origin", "");

        assertThat(valueOf("date")).isEqualTo("2026-09-14");
        assertThat(valueOf("departureTime")).isEqualTo("11:30");
    }

    private void loadForm(String date, String departureTime, String arrivalTime) {
        loadRendered("""
                <!DOCTYPE html><html lang="en"><body>
                <select id="origin" name="origin">
                  <option value=""></option>
                  %s
                  %s
                  %s
                </select>
                <select id="destination" name="destination">
                  <option value=""></option>
                  %s
                  %s
                  %s
                </select>
                <input type="date" id="date" name="date" value="%s"/>
                <input type="time" id="departureTime" name="departureTime" value="%s"/>
                <input type="time" id="arrivalTime" name="arrivalTime" value="%s"/>
                %s
                </body></html>
                """.formatted(ARRIVAL_OPTION, HOTEL_CHECK_OUT_OPTION, MOMENTLESS_OPTION,
                              DEPARTURE_OPTION, HOTEL_CHECK_IN_OPTION, MOMENTLESS_OPTION,
                              date, departureTime, arrivalTime,
                              shippedPrefillScript()));
    }

    /** The script exactly as the form ships it — lifted, never retyped. */
    private String shippedPrefillScript() {
        String template = new TemplateSources().read(FORM);
        int open = template.lastIndexOf("<script>");
        int close = template.lastIndexOf("</script>");
        assertThat(open)
                .as("the ground-transfer form must still carry its prefill script")
                .isGreaterThan(-1);
        return template.substring(open, close + "</script>".length());
    }

    private String valueOf(String fieldId) {
        return page.inputValue("#" + fieldId);
    }
}
