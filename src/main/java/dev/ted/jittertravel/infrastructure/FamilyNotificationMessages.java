package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.AirportCityResolver;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * The words family read, built from what the log says and never stored: an email is presentation,
 * so it lives here and not in {@code domain}.
 * <p>
 * <strong>Every field in a message is named in this class.</strong> Nothing is rendered from a
 * general view of a booking, so a field this class never mentions cannot appear by accident — the
 * allow-list shape, even though the bound on what family may be told is wide (anything an owner
 * surface shows). Do not refactor these to render from a shared view record.
 * <p>
 * Deliberately left out of a flight or a trip: the airline, the confirmation code, a passenger —
 * family cannot use them, and a booking reference is the one value here that could be misused from a
 * mailbox several people read.
 * <p>
 * Times are the entry zone's, labelled with the airport they are local to. An unlabelled local time
 * in an email has no page around it to say which zone it means.
 */
public class FamilyNotificationMessages {

    private static final DateTimeFormatter DAY_AND_CLOCK =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm", Locale.US);

    private final AirportCityResolver cities;

    public FamilyNotificationMessages(AirportCityResolver cities) {
        this.cities = cities;
    }

    /**
     * The message for {@code fact}, built from the booked legs it is about (one for a single flight,
     * all of them in departure order for a trip). The {@code switch} is exhaustive over the enum, so
     * a new fact cannot be added without deciding what it says.
     */
    public FamilyMessage messageFor(NotifiedFact fact, List<FlightBooked> legs) {
        return switch (fact) {
            case FLIGHT_BOOKED -> flight(legs.getFirst());
            case ITINERARY_BOOKED -> trip(legs);
        };
    }

    private FamilyMessage flight(FlightBooked leg) {
        return new FamilyMessage(
                "Ted booked a flight: " + leg.departureAirport().code() + " → " + leg.arrivalAirport().code(),
                leg.flightNumber() + "\n" + String.join("\n", details(leg, "")));
    }

    private FamilyMessage trip(List<FlightBooked> legs) {
        List<String> lines = new ArrayList<>();
        lines.add("Ted booked a trip with " + legs.size() + " flights.");
        IntStream.range(0, legs.size()).forEach(index -> {
            lines.add("");
            FlightBooked leg = legs.get(index);
            lines.add((index + 1) + ". " + leg.flightNumber());
            lines.addAll(details(leg, "   "));
        });
        return new FamilyMessage("Ted booked a trip: " + chain(legs), String.join("\n", lines));
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

    /** Route, departure, arrival — each line prefixed by {@code indent}. */
    private List<String> details(FlightBooked leg, String indent) {
        return List.of(
                indent + city(leg.departureAirport().code()) + " → " + city(leg.arrivalAirport().code()),
                indent + "Departs  " + when(leg.departureDateTime()) + " (" + leg.departureAirport().code() + ")",
                indent + "Arrives  " + when(leg.arrivalDateTime()) + " (" + leg.arrivalAirport().code() + ")");
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
