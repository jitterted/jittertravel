package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Country;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.Subdivision;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class PlacesUsedProjectorTest {

    private static final ZonedTimestamp WHEN =
            ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 11, 15, 0), ZoneId.of("America/Denver"));

    private final AtomicLong sequence = new AtomicLong();
    private final PlacesUsedProjector projector = new PlacesUsedProjector();

    @Test
    void theCountriesOfEveryKindOfPlaceAreListedAToZByName() {
        projector.handle(Stream.of(
                stored(hotel(new Address("", "Lone Tree", "CO", "", "US", null))),
                stored(train("Aachen", "DE", "Gembloux", "BE")),
                stored(conference(new Address("", "Steventon", "Abingdon", "", "GB", null))),
                stored(transfer(new Address("", "Denver", "CO", "", "US", null),
                                new Address("", "Toronto", "ON", "", "CA", null)))));

        assertThat(projector.countries())
                .extracting(Country::name)
                .containsExactly("Belgium", "Canada", "Germany", "United Kingdom", "United States");
    }

    /**
     * Each event kind, and each place on it, on its own — so dropping any one of them from the
     * switch, or one end of a trip or a transfer, loses a country this test names.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("eachPlaceOnEachKindOfEvent")
    void everyPlaceOnEveryKindOfEventCounts(String kind, Event event, List<String> codes) {
        projector.handle(Stream.of(stored(event)));

        assertThat(projector.countries())
                .extracting(Country::code)
                .containsExactlyInAnyOrderElementsOf(codes);
    }

    static Stream<Arguments> eachPlaceOnEachKindOfEvent() {
        return Stream.of(
                arguments("HotelBooked", hotel(at("AT")), List.of("AT")),
                arguments("HotelChanged", new HotelChanged(HotelBookingId.random(), "Hotel", at("BE"),
                                                           WHEN, WHEN, BookingIntent.FINAL, null, null),
                          List.of("BE")),
                arguments("ConferencePlanned", conference(at("CH")), List.of("CH")),
                arguments("GatheringPlanned", new GatheringPlanned(GatheringId.random(), "Meetup", "Venue",
                                                                   at("DK"), WHEN, WHEN, false, ""),
                          List.of("DK")),
                arguments("GatheringChanged", new GatheringChanged(GatheringId.random(), "Meetup", "Venue",
                                                                   at("ES"), WHEN, WHEN, false, ""),
                          List.of("ES")),
                arguments("PrivateEventPlanned", new PrivateEventPlanned(PrivateEventId.random(), "Dinner",
                                                                         "Alo", at("FI"), WHEN, WHEN),
                          List.of("FI")),
                arguments("GroundTransferPlanned", transfer(at("FR"), at("IE")), List.of("FR", "IE")),
                arguments("TrainBooked", train("Aachen", "DE", "Gembloux", "BE"), List.of("DE", "BE")),
                arguments("TrainChanged", new TrainChanged(TrainTripId.random(),
                                                           new TrainStationAddress("Wien Hbf", "Vienna", "AT", ""), WHEN,
                                                           new TrainStationAddress("Milano Centrale", "Milan", "IT", ""), WHEN,
                                                           "RJ 1"),
                          List.of("AT", "IT")));
    }

    private static Address at(String country) {
        return new Address("", "Somewhere", "", "", country, null);
    }

    @Test
    void aCountryUsedTwiceIsListedOnce() {
        projector.handle(Stream.of(
                stored(hotel(new Address("", "Aachen", "", "", "DE", null))),
                stored(hotel(new Address("", "Soltau", "", "", "de", null)))));

        assertThat(projector.countries())
                .containsExactly(new Country("DE", "Germany"));
    }

    /**
     * Before the migration the log holds names. A name in this list would put a value on the form
     * that the command refuses, so only codes count.
     */
    @Test
    void aStoredNameIsNotACountryCodeAndIsLeftOut() {
        projector.handle(Stream.of(
                stored(hotel(new Address("", "Denver", "Colorado", "", "United States", null))),
                stored(hotel(new Address("", "Antwerp", "", "", "Brussels", null)))));

        assertThat(projector.countries())
                .isEmpty();
        assertThat(projector.subdivisions("US"))
                .isEmpty();
    }

    @Test
    void theStatesUsedAreListedPerCountry() {
        projector.handle(Stream.of(
                stored(hotel(new Address("", "Lone Tree", "CO", "", "US", null))),
                stored(hotel(new Address("", "New York", "NY", "", "US", null))),
                stored(hotel(new Address("", "Toronto", "ON", "", "CA", null))),
                stored(hotel(new Address("", "Hamburg", "Altona", "", "DE", null)))));

        assertThat(projector.subdivisions("US"))
                .containsExactly(new Subdivision("CO", "Colorado"), new Subdivision("NY", "New York"));
        assertThat(projector.subdivisions("CA"))
                .containsExactly(new Subdivision("ON", "Ontario"));
        assertThat(projector.subdivisions("DE"))
                .as("Germany has no state list; Altona is free text")
                .isEmpty();
    }

    @Test
    void aCancelledBookingStillCountsAsAPlaceBeenTo() {
        HotelBooked booked = hotel(new Address("", "Vienna", "", "", "AT", null));
        projector.handle(Stream.of(
                stored(booked),
                stored(new HotelBookingCancelled(booked.hotelBookingId(), "Plans changed"))));

        assertThat(projector.countries())
                .containsExactly(new Country("AT", "Austria"));
    }

    private static HotelBooked hotel(Address address) {
        return new HotelBooked(HotelBookingId.random(), "Hotel", address, WHEN, WHEN,
                               BookingIntent.FINAL, null, null);
    }

    private static TrainBooked train(String fromCity, String fromCountry, String toCity, String toCountry) {
        return new TrainBooked(TrainTripId.random(),
                               new TrainStationAddress(fromCity + " Hbf", fromCity, fromCountry, ""), WHEN,
                               new TrainStationAddress(toCity + " Station", toCity, toCountry, ""), WHEN,
                               "IC 1");
    }

    private static ConferencePlanned conference(Address venue) {
        return new ConferencePlanned(ConferenceId.random(), "SoCraTes UK", WHEN, WHEN,
                                     "Milton Hill House", venue, ConferenceFormat.OPEN_SPACE, "");
    }

    private static GroundTransferPlanned transfer(Address origin, Address destination) {
        return new GroundTransferPlanned(GroundTransferId.random(), "DEN", "", origin,
                                         "", "Hotel", destination, WHEN, WHEN, "Taxi");
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                               Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
