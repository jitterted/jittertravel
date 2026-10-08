package dev.ted.jittertravel.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Every country a place can be in, by ISO 3166-1 alpha-2 code — the value an address stores
 * (Ted, 2026-10-06; see docs/LocationDataCleanupPlan.md D2). A code never changes when a country is
 * renamed, so a renamed country cannot leave two spellings in the log; a page turns the code back
 * into a name with {@link #name}.
 *
 * <p>A curated in-memory table, like {@link LocationZoneResolver}. The 193 UN members plus the few
 * places a traveller picks as a country of its own (Hong Kong, Taiwan, Puerto Rico, Palestine,
 * Vatican City). A code arrives only from a select, so lookup is exact apart from case and
 * surrounding space.
 */
public class Countries {

    private static final Map<String, String> NAME_BY_CODE = nameByCode();

    /** Every country, A to Z by name. */
    public List<Country> all() {
        return NAME_BY_CODE.entrySet().stream()
                           .map(entry -> new Country(entry.getKey(), entry.getValue()))
                           .sorted(Comparator.comparing(Country::name))
                           .toList();
    }

    /** The English name for a code; empty when the code is not a country's. */
    public Optional<String> name(String code) {
        return Optional.ofNullable(NAME_BY_CODE.get(normalized(code)));
    }

    /**
     * The code for a country's name exactly as this table spells it, ignoring case — for a source
     * that writes names (Sessionize's "Stockholm, Sweden"). Not an alias table: "USA" and "UK" are
     * not names here, and a source that writes them gets no code.
     */
    public Optional<String> codeFor(String name) {
        String wanted = name == null ? "" : name.trim();
        return NAME_BY_CODE.entrySet().stream()
                           .filter(entry -> entry.getValue().equalsIgnoreCase(wanted))
                           .map(Map.Entry::getKey)
                           .findFirst();
    }

    public boolean isKnown(String code) {
        return NAME_BY_CODE.containsKey(normalized(code));
    }

    private static String normalized(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, String> nameByCode() {
        String[][] countries = {
                {"AF", "Afghanistan"}, {"AL", "Albania"}, {"DZ", "Algeria"}, {"AD", "Andorra"},
                {"AO", "Angola"}, {"AG", "Antigua and Barbuda"}, {"AR", "Argentina"},
                {"AM", "Armenia"}, {"AU", "Australia"}, {"AT", "Austria"}, {"AZ", "Azerbaijan"},
                {"BS", "Bahamas"}, {"BH", "Bahrain"}, {"BD", "Bangladesh"}, {"BB", "Barbados"},
                {"BY", "Belarus"}, {"BE", "Belgium"}, {"BZ", "Belize"}, {"BJ", "Benin"},
                {"BT", "Bhutan"}, {"BO", "Bolivia"}, {"BA", "Bosnia and Herzegovina"},
                {"BW", "Botswana"}, {"BR", "Brazil"}, {"BN", "Brunei"}, {"BG", "Bulgaria"},
                {"BF", "Burkina Faso"}, {"BI", "Burundi"}, {"CV", "Cabo Verde"},
                {"KH", "Cambodia"}, {"CM", "Cameroon"}, {"CA", "Canada"},
                {"CF", "Central African Republic"}, {"TD", "Chad"}, {"CL", "Chile"},
                {"CN", "China"}, {"CO", "Colombia"}, {"KM", "Comoros"}, {"CG", "Congo"},
                {"CD", "Congo (Democratic Republic)"}, {"CR", "Costa Rica"},
                {"CI", "Côte d’Ivoire"}, {"HR", "Croatia"}, {"CU", "Cuba"}, {"CY", "Cyprus"},
                {"CZ", "Czechia"}, {"DK", "Denmark"}, {"DJ", "Djibouti"}, {"DM", "Dominica"},
                {"DO", "Dominican Republic"}, {"EC", "Ecuador"}, {"EG", "Egypt"},
                {"SV", "El Salvador"}, {"GQ", "Equatorial Guinea"}, {"ER", "Eritrea"},
                {"EE", "Estonia"}, {"SZ", "Eswatini"}, {"ET", "Ethiopia"}, {"FJ", "Fiji"},
                {"FI", "Finland"}, {"FR", "France"}, {"GA", "Gabon"}, {"GM", "Gambia"},
                {"GE", "Georgia"}, {"DE", "Germany"}, {"GH", "Ghana"}, {"GR", "Greece"},
                {"GD", "Grenada"}, {"GT", "Guatemala"}, {"GN", "Guinea"},
                {"GW", "Guinea-Bissau"}, {"GY", "Guyana"}, {"HT", "Haiti"}, {"HN", "Honduras"},
                {"HK", "Hong Kong"}, {"HU", "Hungary"}, {"IS", "Iceland"}, {"IN", "India"},
                {"ID", "Indonesia"}, {"IR", "Iran"}, {"IQ", "Iraq"}, {"IE", "Ireland"},
                {"IL", "Israel"}, {"IT", "Italy"}, {"JM", "Jamaica"}, {"JP", "Japan"},
                {"JO", "Jordan"}, {"KZ", "Kazakhstan"}, {"KE", "Kenya"}, {"KI", "Kiribati"},
                {"KW", "Kuwait"}, {"KG", "Kyrgyzstan"}, {"LA", "Laos"}, {"LV", "Latvia"},
                {"LB", "Lebanon"}, {"LS", "Lesotho"}, {"LR", "Liberia"}, {"LY", "Libya"},
                {"LI", "Liechtenstein"}, {"LT", "Lithuania"}, {"LU", "Luxembourg"},
                {"MG", "Madagascar"}, {"MW", "Malawi"}, {"MY", "Malaysia"}, {"MV", "Maldives"},
                {"ML", "Mali"}, {"MT", "Malta"}, {"MH", "Marshall Islands"},
                {"MR", "Mauritania"}, {"MU", "Mauritius"}, {"MX", "Mexico"},
                {"FM", "Micronesia"}, {"MD", "Moldova"}, {"MC", "Monaco"}, {"MN", "Mongolia"},
                {"ME", "Montenegro"}, {"MA", "Morocco"}, {"MZ", "Mozambique"},
                {"MM", "Myanmar"}, {"NA", "Namibia"}, {"NR", "Nauru"}, {"NP", "Nepal"},
                {"NL", "Netherlands"}, {"NZ", "New Zealand"}, {"NI", "Nicaragua"},
                {"NE", "Niger"}, {"NG", "Nigeria"}, {"KP", "North Korea"},
                {"MK", "North Macedonia"}, {"NO", "Norway"}, {"OM", "Oman"},
                {"PK", "Pakistan"}, {"PW", "Palau"}, {"PS", "Palestine"}, {"PA", "Panama"},
                {"PG", "Papua New Guinea"}, {"PY", "Paraguay"}, {"PE", "Peru"},
                {"PH", "Philippines"}, {"PL", "Poland"}, {"PT", "Portugal"},
                {"PR", "Puerto Rico"}, {"QA", "Qatar"}, {"RO", "Romania"}, {"RU", "Russia"},
                {"RW", "Rwanda"}, {"KN", "Saint Kitts and Nevis"}, {"LC", "Saint Lucia"},
                {"VC", "Saint Vincent and the Grenadines"}, {"WS", "Samoa"},
                {"SM", "San Marino"}, {"ST", "Sao Tome and Principe"}, {"SA", "Saudi Arabia"},
                {"SN", "Senegal"}, {"RS", "Serbia"}, {"SC", "Seychelles"},
                {"SL", "Sierra Leone"}, {"SG", "Singapore"}, {"SK", "Slovakia"},
                {"SI", "Slovenia"}, {"SB", "Solomon Islands"}, {"SO", "Somalia"},
                {"ZA", "South Africa"}, {"KR", "South Korea"}, {"SS", "South Sudan"},
                {"ES", "Spain"}, {"LK", "Sri Lanka"}, {"SD", "Sudan"}, {"SR", "Suriname"},
                {"SE", "Sweden"}, {"CH", "Switzerland"}, {"SY", "Syria"}, {"TW", "Taiwan"},
                {"TJ", "Tajikistan"}, {"TZ", "Tanzania"}, {"TH", "Thailand"},
                {"TL", "Timor-Leste"}, {"TG", "Togo"}, {"TO", "Tonga"},
                {"TT", "Trinidad and Tobago"}, {"TN", "Tunisia"}, {"TR", "Türkiye"},
                {"TM", "Turkmenistan"}, {"TV", "Tuvalu"}, {"UG", "Uganda"}, {"UA", "Ukraine"},
                {"AE", "United Arab Emirates"}, {"GB", "United Kingdom"},
                {"US", "United States"}, {"UY", "Uruguay"}, {"UZ", "Uzbekistan"},
                {"VU", "Vanuatu"}, {"VA", "Vatican City"}, {"VE", "Venezuela"},
                {"VN", "Vietnam"}, {"YE", "Yemen"}, {"ZM", "Zambia"}, {"ZW", "Zimbabwe"}
        };
        Map<String, String> table = new LinkedHashMap<>();
        for (String[] country : countries) {
            table.put(country[0], country[1]);
        }
        return Map.copyOf(table);
    }
}
