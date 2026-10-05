package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.AirportCityResolver;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Chooses the email for a fact and hands it the values it needs. <strong>The words themselves are
 * not here</strong>: they are plain text files in {@code src/main/resources/email/}, one per
 * message, which is where to read or change what family are sent (see {@link EmailTemplates}).
 * This class only prepares the values those files refer to: the city names, the times, the airport
 * chain.
 * <p>
 * <strong>A template names only the values it is given</strong>, so a field this class never passes
 * cannot appear in an email by accident (the allow-list shape, even though the bound on what family
 * may be told is wide: anything an owner surface shows). Deliberately never passed: the airline,
 * the confirmation code, a passenger, or any cancellation reason. Do not widen {@link LegView} to
 * hold a whole booking.
 * <p>
 * <strong>Any change to an email's wording needs Ted's approval before it is committed.</strong>
 * Times are the entry zone's, labelled with the airport they are local to, because an unlabelled local
 * time in an email has no page around it to say which zone it means.
 */
public class FamilyNotificationMessages {

    private static final DateTimeFormatter DAY_AND_CLOCK =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm", Locale.US);

    private final AirportCityResolver cities;
    private final String baseUrl;
    private final EmailTemplates templates = new EmailTemplates();

    /**
     * @param baseUrl the address the app is served from ({@code JITTERTRAVEL_BASE_URL}), used to build
     *                links; blank when none is configured, in which case there is no link rather than a
     *                guessed one
     */
    public FamilyNotificationMessages(AirportCityResolver cities, String baseUrl) {
        this.cities = cities;
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip();
    }

    /**
     * The message for {@code fact}, built from the booked legs it is about (one for a single flight,
     * all of them in departure order for a trip). The {@code switch} is exhaustive over the enum, so
     * a new fact cannot be added without deciding which template it uses.
     */
    public FamilyMessage messageFor(NotifiedFact fact, List<FlightBooked> legs) {
        return switch (fact) {
            case FLIGHT_BOOKED -> templates.render("flight-booked", flightValues(legs));
            case ITINERARY_BOOKED -> templates.render("trip-booked", tripValues(legs));
            case ITINERARY_CANCELLED -> templates.render("trip-cancelled", tripValues(legs));
        };
    }

    /**
     * A link to the calendar opened at the day of the first flight in {@code legs}, or empty when no
     * base URL is configured. The day is the departure's own local date (the entry zone), which is the
     * column the calendar puts the flight in, and {@code day} is the parameter {@code /calendar}
     * already uses to jump to and mark a date. For a booking that is the first flight booked; for a
     * cancellation it is the first flight that was due, since that is where the trip was on the
     * calendar.
     */
    Optional<String> calendarUrl(List<FlightBooked> legs) {
        if (baseUrl.isEmpty() || legs.isEmpty()) {
            return Optional.empty();
        }
        FlightBooked first = legs.stream()
                .min(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .orElseThrow();
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return Optional.of(base + "/calendar?day=" + first.departureDateTime().localDateTime().toLocalDate());
    }

    private Map<String, Object> flightValues(List<FlightBooked> legs) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("leg", view(1, legs.getFirst()));
        calendarUrl(legs).ifPresent(url -> values.put("calendarUrl", url));
        return values;
    }

    /**
     * One leg as the templates see it: every string already formatted, and nothing else. This is the
     * whole list of what an email can say about a flight.
     */
    public record LegView(int number, String flightNumber, String route, String departs, String arrives,
                          String departureCode, String arrivalCode) {
    }

    private Map<String, Object> tripValues(List<FlightBooked> legs) {
        List<LegView> views = IntStream.range(0, legs.size())
                .mapToObj(index -> view(index + 1, legs.get(index)))
                .toList();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("legs", views);
        values.put("count", legs.size());
        values.put("chain", chain(legs));
        calendarUrl(legs).ifPresent(url -> values.put("calendarUrl", url));
        return values;
    }

    private LegView view(int number, FlightBooked leg) {
        String from = leg.departureAirport().code();
        String to = leg.arrivalAirport().code();
        return new LegView(number, leg.flightNumber(),
                city(from) + " → " + city(to),
                when(leg.departureDateTime()) + " (" + from + ")",
                when(leg.arrivalDateTime()) + " (" + to + ")",
                from, to);
    }

    /**
     * The airports in order, joined by arrows while each leg starts where the last ended. A leg
     * that does not continue the previous one starts a new run after a comma, so two separate
     * journeys are never drawn as a connection that does not exist.
     */
    private String chain(List<FlightBooked> legs) {
        StringBuilder chain = new StringBuilder(legs.getFirst().departureAirport().code());
        for (int index = 0; index < legs.size(); index++) {
            FlightBooked leg = legs.get(index);
            if (index > 0 && !leg.departureAirport().equals(legs.get(index - 1).arrivalAirport())) {
                chain.append(", ").append(leg.departureAirport().code());
            }
            chain.append(" → ").append(leg.arrivalAirport().code());
        }
        return chain.toString();
    }

    private String city(String airportCode) {
        return cities.cityFor(airportCode) + " (" + airportCode + ")";
    }

    /**
     * The meridiem is written here rather than by the formatter: current JDKs put a narrow no-break
     * space before "PM" in the US pattern, which reads as a stray character in a plain-text email.
     */
    private String when(ZonedTimestamp timestamp) {
        ZonedDateTime local = timestamp.atEntryZone();
        return DAY_AND_CLOCK.format(local) + (local.getHour() < 12 ? " AM" : " PM");
    }
}
