package dev.ted.jittertravel.application;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The cities that count as "home". Nights spent at home need no hotel, and any two home cities
 * are the same place for travel purposes (the Bay Area's SFO, SJC, and OAK are interchangeable
 * departure/arrival airports for the same trip).
 * <p>
 * An empty instance is inert: {@link #includes} is always false and {@link #sameLocation} is a
 * plain case-insensitive name comparison.
 */
public class HomeCities {

    private final Set<String> cities = new LinkedHashSet<>();

    public HomeCities(Collection<String> cityNames) {
        cityNames.stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name.toLowerCase())
                .forEach(cities::add);
    }

    public boolean includes(String city) {
        return city != null && cities.contains(city.trim().toLowerCase());
    }

    /**
     * No home is configured at all. A reader that asks "is he away?" has no answer to give in
     * that case — {@link #includes} says no to every city, which would read as "away, always" —
     * so it wants to know, rather than to ask city by city.
     */
    public boolean isEmpty() {
        return cities.isEmpty();
    }

    public boolean sameLocation(String cityA, String cityB) {
        return cityA.equalsIgnoreCase(cityB)
               || (includes(cityA) && includes(cityB));
    }
}
