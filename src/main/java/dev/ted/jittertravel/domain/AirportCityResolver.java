package dev.ted.jittertravel.domain;

import java.util.Optional;

public interface AirportCityResolver {

    String cityFor(String airportCode);

    /**
     * Where the airport is, as an address with no street: the city it serves, its state where the
     * country has a state list, and the country's ISO code. Empty for a code the table does not know.
     */
    Optional<Address> addressFor(String airportCode);

    /**
     * The airport code for {@code city}, <strong>only when the city has exactly one</strong>.
     * <p>
     * The city table is many-to-one — London is LHR/LGW/STN/LCY, New York is JFK/EWR/LGA — so there
     * is no city-to-code answer in general. A fix link from {@code /schedule-problems} therefore
     * carries cities and lets the controller seed a code only where the answer is unambiguous:
     * a wrong prefilled airport is worse than an empty one, because Ted has to notice it to undo it.
     * Empty means "several, or none — leave the field alone".
     */
    Optional<String> soleAirportFor(String city);
}
