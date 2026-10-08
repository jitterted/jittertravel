package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Countries;
import dev.ted.jittertravel.domain.Country;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The place pickers as the JS tier sees them. That tier runs no Thymeleaf, so this stands in for
 * the server half of {@code fragments/place-picker.html}: it writes the markup the fragment renders
 * — the exact tags {@link PlacePickerWebIntegrationTest} pins through a real render — and takes the
 * <em>script</em> from the shipped fragment untouched, with the state lists filled in from the real
 * {@link PlacePicker}. What the JS tests prove is the script; the markup it works on is held by the
 * render tier.
 */
class PlacePickerMarkup {

    private static final Pattern COUNTRY_REFERENCE = Pattern.compile(
            "<div th:replace=\"~\\{fragments/place-picker :: country\\('(\\w+)'\\)}\"></div>");
    private static final Pattern REGION_REFERENCE = Pattern.compile(
            "<div th:replace=\"~\\{fragments/place-picker :: region\\('(\\w+)', '(\\w+)'\\)}\"></div>");
    private static final String SCRIPT_REFERENCE =
            "<div th:replace=\"~{fragments/place-picker :: placePickerScript}\"></div>";

    private final List<Country> used;

    /** The short list a page offers first, as {@code PlacesUsedProjector} would supply it. */
    PlacePickerMarkup(List<Country> used) {
        this.used = used;
    }

    /** Every picker reference in a template replaced by what the fragment renders for it. */
    String expand(String template) {
        String countries = COUNTRY_REFERENCE.matcher(template)
                                            .replaceAll(match -> Matcher.quoteReplacement(country(match.group(1), "")));
        String regions = REGION_REFERENCE.matcher(countries)
                                         .replaceAll(match -> Matcher.quoteReplacement(
                                                 textRegion(match.group(1), match.group(2), "")));
        return regions.replace(SCRIPT_REFERENCE, script());
    }

    /** A country select with {@code selected} chosen, from the short list or the full one. */
    String country(String field, String selected) {
        String usedOptions = used.stream()
                                 .map(country -> option(country.code(), country.name(), false))
                                 .collect(Collectors.joining());
        boolean selectedIsUsed = used.stream().anyMatch(country -> country.code().equals(selected));
        String all = new Countries().all().stream()
                                    .map(country -> option(country.code(), country.name(),
                                                           country.code().equals(selected) && !selectedIsUsed))
                                    .collect(Collectors.joining());
        String usedSelected = selectedIsUsed
                ? usedOptions.replace("value=\"" + selected + "\">",
                                      "value=\"" + selected + "\" selected=\"selected\">")
                : usedOptions;
        return "<div class=\"field\"><label>Country"
               + "<select class=\"place-select\" data-country-picker id=\"" + field + "\" name=\"" + field + "\">"
               + option("", "—", selected.isEmpty())
               + usedSelected
               + "<optgroup label=\"All countries\" data-all-countries>" + all + "</optgroup>"
               + "</select></label></div>";
    }

    /** A Region picker rendered for a country with no state list: the text box is live. */
    String textRegion(String countryField, String regionField, String value) {
        return "<div class=\"field narrow\" data-region-picker data-country-field=\"" + countryField + "\">"
               + "<label><span data-region-label>Region (optional)</span>"
               + "<select class=\"place-select\" id=\"" + regionField + "-list\" data-region-list "
               + "hidden=\"hidden\" disabled=\"disabled\" name=\"" + regionField + "\">"
               + "<option value=\"\">—</option></select>"
               + "<input type=\"text\" data-region-text id=\"" + regionField + "\" name=\"" + regionField
               + "\" value=\"" + value + "\"/>"
               + "</label></div>";
    }

    /** The shipped script, with the state lists Thymeleaf would inline. */
    String script() {
        String fragment = new TemplateSources().read(TemplateSources.ROOT.resolve("fragments/place-picker.html"));
        int start = fragment.indexOf('>', fragment.indexOf("th:fragment=\"placePickerScript\"")) + 1;
        String body = fragment.substring(start, fragment.indexOf("</th:block>", start));
        return body.replace(" th:inline=\"javascript\"", "")
                   .replace("/*[[${placePicker.stateListsForScript()}]]*/ {}",
                            json(new PlacePicker(used, country -> List.of()).stateListsForScript()));
    }

    private static String option(String value, String text, boolean selected) {
        return "<option value=\"" + value + "\"" + (selected ? " selected=\"selected\"" : "") + ">"
               + text + "</option>";
    }

    /** Enough JSON for maps, lists and strings — all the state lists are made of. */
    private static String json(Object value) {
        return switch (value) {
            case Map<?, ?> map -> map.entrySet().stream()
                                     .map(entry -> json(entry.getKey()) + ":" + json(entry.getValue()))
                                     .collect(Collectors.joining(",", "{", "}"));
            case List<?> list -> list.stream().map(PlacePickerMarkup::json)
                                     .collect(Collectors.joining(",", "[", "]"));
            default -> "\"" + value.toString().replace("\"", "\\\"") + "\"";
        };
    }
}
