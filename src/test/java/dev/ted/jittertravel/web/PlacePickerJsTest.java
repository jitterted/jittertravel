package dev.ted.jittertravel.web;

import com.microsoft.playwright.options.SelectOption;
import dev.ted.jittertravel.domain.Country;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for the place pickers ({@code templates/fragments/place-picker.html}): the
 * short country list with "Another country…" behind it, and Region switching between a list of
 * states and a text box (docs/LocationDataCleanupPlan.md D1, D3). The markup is
 * {@link PlacePickerMarkup}'s stand-in for the server render; the script is the shipped one.
 */
class PlacePickerJsTest extends JsBehaviorTest {

    private static final List<Country> USED =
            List.of(new Country("DE", "Germany"), new Country("US", "United States"));

    private final PlacePickerMarkup markup = new PlacePickerMarkup(USED);

    @Test
    void theShortListComesFirstAndEveryCountryWaitsBehindAnotherCountry() {
        load("");

        assertThat(page.locator("#country option").allTextContents())
                .as("the countries Ted has used, then the way to the rest")
                .containsExactly("—", "Germany", "United States", "Another country…");
        assertThat(page.locator("select.place-other").isVisible())
                .as("the full list stays out of the way until asked for")
                .isFalse();
    }

    @Test
    void pickingAnotherCountryPutsItInTheShortListAndSelectsIt() {
        load("");

        page.locator("#country").selectOption(new SelectOption().setLabel("Another country…"));
        assertThat(page.locator("select.place-other").isVisible())
                .isTrue();
        page.locator("select.place-other").selectOption("GR");

        assertThat(page.locator("#country").inputValue())
                .as("the form submits the code from the one named select")
                .isEqualTo("GR");
        assertThat(page.locator("#country option").allTextContents())
                .containsExactly("—", "Germany", "United States", "Greece", "Another country…");
        assertThat(page.locator("select.place-other").isVisible())
                .isFalse();
    }

    @Test
    void aStoredCountryFromOutsideTheShortListIsStillTheOneSelected() {
        loadWith(markup.country("country", "BR"), markup.textRegion("country", "region", ""));

        assertThat(page.locator("#country").inputValue())
                .isEqualTo("BR");
        assertThat(page.locator("#country option:checked").textContent())
                .isEqualTo("Brazil");
    }

    @Test
    void choosingTheUnitedStatesTurnsRegionIntoTheStateList() {
        load("");

        page.locator("#country").selectOption("US");

        assertThat(page.locator("[data-region-label]").textContent())
                .isEqualTo("State");
        assertThat(page.locator("#region-list").isEnabled())
                .isTrue();
        assertThat(page.locator("#region").isDisabled())
                .as("only one control is submitted under the name region")
                .isTrue();
        assertThat(page.locator("#region-list option[value='CO']").textContent())
                .as("full names on screen, codes stored")
                .isEqualTo("Colorado");
    }

    @Test
    void canadaCallsItAProvinceAndGermanyGoesBackToTheTextBox() {
        load("");

        // Canada is not on this page's short list, so it comes from the full one.
        page.locator("#country").selectOption(new SelectOption().setLabel("Another country…"));
        page.locator("select.place-other").selectOption("CA");
        assertThat(page.locator("[data-region-label]").textContent())
                .isEqualTo("Province");

        page.locator("#country").selectOption("DE");
        assertThat(page.locator("[data-region-label]").textContent())
                .isEqualTo("Region (optional)");
        assertThat(page.locator("#region").isEnabled())
                .isTrue();
        assertThat(page.locator("#region-list").isDisabled())
                .isTrue();
    }

    /** What Parse ▶ and the Sessionize prefill call, after their codes come back. */
    @Test
    void theFillApiSelectsTheCountryAndThenTheState() {
        load("");

        page.evaluate("""
                () => {
                    const country = document.querySelector('[name="country"]');
                    window.placePicker.setCountry(country, 'CA');
                    window.placePicker.setRegion(country.form, 'region', 'ON');
                }""");

        assertThat(page.locator("#country").inputValue())
                .isEqualTo("CA");
        assertThat(page.locator("#region-list").inputValue())
                .isEqualTo("ON");
    }

    /**
     * A form re-rendered with no form object to read the country from (a date that would not
     * parse) comes back with the text box; the script corrects it to the list on load and keeps
     * what was there.
     */
    @Test
    void aRegionRenderedForTheWrongCountryIsCorrectedOnLoadKeepingItsValue() {
        loadWith(markup.country("country", "US"), markup.textRegion("country", "region", "CO"));

        assertThat(page.locator("#region-list").isEnabled())
                .isTrue();
        assertThat(page.locator("#region-list").inputValue())
                .isEqualTo("CO");
    }

    private void load(String selectedCountry) {
        loadWith(markup.country("country", selectedCountry), markup.textRegion("country", "region", ""));
    }

    private void loadWith(String countryPicker, String regionPicker) {
        loadRendered("<!DOCTYPE html><html><body><form>"
                     + countryPicker + regionPicker
                     + "</form>" + markup.script() + "</body></html>");
    }
}
