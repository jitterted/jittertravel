package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;

/**
 * Thrown when a time zone cannot be derived from a location (a city/country, an {@link Address}, or
 * an airport code). At the boundary this signals that the command must instead carry an explicitly
 * chosen {@code CommonZone} (the form re-prompts for one); it is never silently defaulted away.
 */
public class ZoneResolutionException extends RuntimeException {

    public ZoneResolutionException(Address address) {
        super("Could not resolve a time zone from address: " + describe(address));
    }

    public ZoneResolutionException(String city, String country) {
        this(city, "", country);
    }

    public ZoneResolutionException(String city, String region, String country) {
        super("Could not resolve a time zone from location: city='" + city + "', region='" + region
              + "', country='" + country + "'");
    }

    public ZoneResolutionException(String airportCode) {
        super("Could not resolve a time zone from airport: code='" + airportCode + "'");
    }

    private static String describe(Address address) {
        if (address == null) {
            return "<none>";
        }
        return "city='" + address.city() + "', region='" + address.region()
               + "', country='" + address.country() + "'";
    }
}
