package dev.ted.jittertravel.web;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.ted.jittertravel.application.CalendarAggregator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.ConferenceCalendarProjector;
import dev.ted.jittertravel.application.EntryKind;
import dev.ted.jittertravel.application.FlightCalendarProjector;
import dev.ted.jittertravel.application.GatheringCalendarProjector;
import dev.ted.jittertravel.application.GroundTransferCalendarProjector;
import dev.ted.jittertravel.application.HotelCalendarProjector;
import dev.ted.jittertravel.application.ItineraryDay;
import dev.ted.jittertravel.application.ItineraryProjector;
import dev.ted.jittertravel.application.PrivateEventCalendarProjector;
import dev.ted.jittertravel.application.TrainCalendarProjector;
import dev.ted.jittertravel.application.ZoneDisplay;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every entry the owner can cancel carries a bin, on the calendar and on the itinerary.
 * <p>
 * Why this exists: Cancel Hotel shipped 2026-08-07 as a section of the change-hotel page, so the
 * pencil was the way to cancel. When cancel moved to its own page (`a0f9085`) the calendar and
 * itinerary lost their only route to it, and the bins that arrived later were each added by the
 * slice that built that kind's cancel — none of which was the hotel's. See
 * {@code docs/archived/CancelBinEverywherePlan.md}.
 * <p>
 * Three mechanisms, because that failure came from the <em>route</em> side, not the kind side, and
 * a guard keyed on {@link EntryKind} alone (as {@code CalendarDayMenuTest} is) would not have seen
 * it:
 * <ol>
 *   <li>every {@code @GetMapping} in {@code web} ending {@code /cancel} is discovered, so a new
 *       cancel route needs nobody to list it;</li>
 *   <li>{@link #cancelable(EntryKind)} is an exhaustive switch, so a new kind stops this class
 *       compiling until someone writes down how — or why not — it is canceled;</li>
 *   <li>the discovered routes must be exactly the claimed ones plus {@link #NOT_ONE_ENTRY}, so a
 *       route nobody claims fails by name, and so does a claim for a route nobody serves.</li>
 * </ol>
 * The entries rendered come from real events through the real projectors, so the claim covers the
 * projector filling the path in as well as the renderer drawing it.
 * <p>
 * <strong>Limit:</strong> discovery is by the {@code /cancel} suffix. A cancel by another name
 * ({@code /decline}) is invisible here; the {@code CONFERENCE} arm records the one that exists.
 */
class EveryCancelableKindShowsItsBinTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 10);
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    private static final FlightId FLIGHT_ID =
            FlightId.of(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final TrainTripId TRIP_ID =
            TrainTripId.of(UUID.fromString("20000000-0000-0000-0000-000000000002"));
    private static final HotelBookingId HOTEL_ID =
            HotelBookingId.of(UUID.fromString("30000000-0000-0000-0000-000000000003"));
    private static final GroundTransferId TRANSFER_ID =
            GroundTransferId.of(UUID.fromString("40000000-0000-0000-0000-000000000004"));
    private static final PrivateEventId PRIVATE_EVENT_ID =
            PrivateEventId.of(UUID.fromString("50000000-0000-0000-0000-000000000005"));

    /**
     * Cancel routes that do not cancel one calendar entry, so no kind's bin points at them. Keep
     * this short and give each a reason; an entry here is a route the guard has stopped watching.
     */
    private static final Set<String> NOT_ONE_ENTRY = Set.of(
            // Cancels every leg of a multi-flight itinerary at once; reached from /booked-flights.
            // Each leg already carries its own bin, to /booked-flights/{flightId}/cancel.
            "/booked-itineraries/{itineraryId}/cancel"
    );

    /** How a kind is canceled: the route as its controller maps it, an event that creates one, and the bin's href for that event. */
    private record Cancelable(String route, Event created, String href) {
    }

    private static Optional<Cancelable> cancelable(EntryKind kind) {
        return switch (kind) {
            case FLIGHT -> Optional.of(new Cancelable(
                    "/booked-flights/{flightId}/cancel", flightBooked(),
                    "/booked-flights/" + FLIGHT_ID.id() + "/cancel"));
            case TRAIN -> Optional.of(new Cancelable(
                    "/booked-trains/{tripId}/cancel", trainBooked(),
                    "/booked-trains/" + TRIP_ID.id() + "/cancel"));
            case LODGING -> Optional.of(new Cancelable(
                    "/booked-hotels/{hotelBookingId}/cancel", hotelBooked(),
                    "/booked-hotels/" + HOTEL_ID.id() + "/cancel"));
            case GROUND_TRANSFER -> Optional.of(new Cancelable(
                    "/ground-transfers/{groundTransferId}/cancel", transferPlanned(),
                    "/ground-transfers/" + TRANSFER_ID.id() + "/cancel"));
            case PRIVATE_EVENT -> Optional.of(new Cancelable(
                    "/planned-private-events/{privateEventId}/cancel", privateEventPlanned(),
                    "/planned-private-events/" + PRIVATE_EVENT_ID.id() + "/cancel"));
            // No GatheringCancelled exists (out of scope in archived/ChangeGatheringPlan.md). When
            // one is built, its /cancel route fails the routes test below until claimed here.
            case GATHERING -> Optional.empty();
            // Declined, not canceled — a state-machine move made from /conferences, by decision
            // (CalendarViewBuilder.ownerActions). Its route ends /decline, so discovery cannot see it.
            case CONFERENCE -> Optional.empty();
        };
    }

    @Test
    void everyServedCancelRouteIsClaimedByAKindOrNamedAsNotOneEntry() {
        Set<String> claimed = Arrays.stream(EntryKind.values())
                .map(EveryCancelableKindShowsItsBinTest::cancelable)
                .flatMap(Optional::stream)
                .map(Cancelable::route)
                .collect(Collectors.toCollection(TreeSet::new));
        claimed.addAll(NOT_ONE_ENTRY);

        assertThat(servedCancelRoutes())
                .as("every GET .../cancel route, and nothing else, is claimed by cancelable(kind) or NOT_ONE_ENTRY")
                .containsExactlyInAnyOrderElementsOf(claimed);
    }

    @ParameterizedTest
    @EnumSource(EntryKind.class)
    void theOwnersCalendarEntryCarriesTheBin(EntryKind kind) {
        Optional<Cancelable> cancelable = cancelable(kind);
        if (cancelable.isEmpty()) {
            return; // not cancelable, and cancelable(kind) says why
        }

        String html = CalendarViewBuilder.render(
                calendarEntriesFrom(cancelable.get().created()),
                DAY, DAY, TODAY,
                false,  // isPublicUser
                true);  // isOwner

        // The path only: the query string saying where the bin was clicked is the renderer's own
        // claim, pinned in CalendarViewBuilderTest. One claim per test.
        assertThat(html)
                .as(kind + " on /calendar")
                .contains("class=\"cancel-bin\" href=\"" + cancelable.get().href() + "?");
    }

    @ParameterizedTest
    @EnumSource(EntryKind.class)
    void theOwnersItineraryEntryCarriesTheBin(EntryKind kind) {
        Optional<Cancelable> cancelable = cancelable(kind);
        if (cancelable.isEmpty()) {
            return; // not cancelable, and cancelable(kind) says why
        }

        ItineraryProjector projector = new ItineraryProjector();
        projector.handle(Stream.of(stored(cancelable.get().created())));
        String html = ItineraryRenderer.render(
                List.of(new ItineraryDay(DAY, projector.entriesForDate(DAY))),
                DAY.minusDays(1), DAY.plusDays(1), TODAY,
                Optional.empty(), true, ZoneDisplay.entryOnly());

        // The path only, as on the calendar; ItineraryRendererTest pins the query string.
        assertThat(html)
                .as(kind + " on /itinerary")
                .contains("class=\"cancel-bin\" href=\"" + cancelable.get().href() + "?");
    }

    // --- Route discovery ---

    private static Set<String> servedCancelRoutes() {
        JavaClasses web = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.ted.jittertravel.web");
        return web.stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(GetMapping.class))
                .flatMap(EveryCancelableKindShowsItsBinTest::paths)
                .filter(path -> path.endsWith("/cancel"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Stream<String> paths(JavaMethod method) {
        GetMapping mapping = method.getAnnotationOfType(GetMapping.class);
        return Stream.concat(Arrays.stream(mapping.value()), Arrays.stream(mapping.path()));
    }

    // --- Real events through the real projectors ---

    private static List<CalendarEntry> calendarEntriesFrom(Event event) {
        ConferenceCalendarProjector conferences = new ConferenceCalendarProjector();
        FlightCalendarProjector flights = new FlightCalendarProjector();
        TrainCalendarProjector trains = new TrainCalendarProjector();
        HotelCalendarProjector hotels = new HotelCalendarProjector();
        GatheringCalendarProjector gatherings = new GatheringCalendarProjector();
        PrivateEventCalendarProjector privateEvents = new PrivateEventCalendarProjector();
        GroundTransferCalendarProjector transfers = new GroundTransferCalendarProjector();
        Stream.of(conferences, flights, trains, hotels, gatherings, privateEvents, transfers)
                .forEach(projector -> projector.handle(Stream.of(stored(event))));
        return new CalendarAggregator(conferences, flights, trains, hotels, gatherings,
                privateEvents, transfers).allEntries();
    }

    private static FlightBooked flightBooked() {
        return new FlightBooked(FLIGHT_ID, "British Airways", "BA902",
                AirportCode.of("LHR"), at(9, 0),
                AirportCode.of("FRA"), ZonedTimestamp.fromLocal(DAY.atTime(11, 40), ZoneId.of("Europe/Berlin")));
    }

    private static TrainBooked trainBooked() {
        return new TrainBooked(TRIP_ID,
                new TrainStationAddress("London Euston", "London", "GB", ""), at(9, 0),
                new TrainStationAddress("Manchester Piccadilly", "Manchester", "GB", ""), at(11, 15),
                "");
    }

    private static HotelBooked hotelBooked() {
        return new HotelBooked(HOTEL_ID, "Grand Hotel",
                new Address("1 Strand", "London", "", "WC2N 5EJ", "GB", null),
                at(15, 0), ZonedTimestamp.fromLocal(DAY.plusDays(2).atTime(11, 0), LONDON),
                BookingIntent.FINAL, null, null);
    }

    private static GroundTransferPlanned transferPlanned() {
        return new GroundTransferPlanned(TRANSFER_ID,
                "LHR", "", new Address("", "London", "", "", "GB", "London"),
                "", "Grand Hotel", new Address("1 Strand", "London", "", "WC2N 5EJ", "GB", "London"),
                at(12, 0), at(12, 45), "");
    }

    private static PrivateEventPlanned privateEventPlanned() {
        return new PrivateEventPlanned(PRIVATE_EVENT_ID, "Dinner with the Smiths", "Chez Moi",
                new Address("2 High St", "London", "", "N1 1AA", "GB", null),
                at(19, 0), at(22, 0));
    }

    private static ZonedTimestamp at(int hour, int minute) {
        return ZonedTimestamp.fromLocal(DAY.atTime(hour, minute), LONDON);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-05-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
