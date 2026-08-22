package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The entire calendar an anonymous visitor may see, built directly from events.
 * <p>
 * <strong>This class is the public surface of {@code /calendar}.</strong> It replaced
 * {@code CalendarEntryRedactor}, and the difference is the direction of the check: the redactor was
 * a deny-list applied to a read model that already held Ted's hotel names, booking links and
 * departure times, so every new field was public until someone remembered to strip it. This is an
 * allow-list written as code — it reads only the fields it names, so a field it never reads cannot
 * leak, and forgetting to handle a new event or a new field leaves the data <em>absent</em> from
 * the public calendar rather than exposed. A leak now takes a deliberate line of code. That is
 * CLAUDE.md redaction rule 6 ("when in doubt, redact") made structural.
 * <p>
 * Two consequences worth stating, because they are the reason this shape was chosen:
 * <ul>
 *   <li>Every entry is built through {@link #entry}, whose last argument is an
 *       {@link EntryDetails.Publishable}, so the compiler refuses an owner details type — the ones
 *       with slots for edit paths and maps URLs. Note what that rests on: {@code CalendarEntry}'s
 *       own constructor is public and takes any {@code EntryDetails}, so the check holds only while
 *       {@code entry(...)} is the sole way in. {@code PublicCalendarBuildsOnlyPublishableEntriesTest}
 *       is what keeps it that way, and without it this would be a convention, not a check.</li>
 *   <li>The public model may differ in <em>shape</em>, not only in content: a private event is
 *       built as "Busy" from the start rather than reverse-engineered out of the owner's subtitle,
 *       and a declined or cancelled conference is simply never added.</li>
 * </ul>
 * The cost, accepted knowingly: event handling is written twice, here and in the seven owner
 * projectors, and a new event type must be handled in both. See
 * {@code docs/RendererVsProjectorResponsibilities.md} (decision S2, 2026-08-19).
 */
public class PublicCalendarProjector implements EventStreamConsumer {

    private final Map<Object, List<CalendarEntry>> entriesBySubject = new ConcurrentHashMap<>();
    private final TransferEndpointLabel label = new TransferEndpointLabel();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                // Conferences are public events in full: name, venue, city and times. Planning one
                // is putting it on the radar, so it starts out merely WATCHING; confirming
                // attendance rewrites the same entry as GOING. The event's AttendanceBasis — why
                // Ted is going, which is submission status in disguise — is never read here.
                case ConferencePlanned e -> put(e.conferenceId(), conference(e, AttendanceCommitment.WATCHING));
                case ConferenceAttendanceConfirmed e -> going(e.conferenceId());
                // A conference Ted has declined, or that its organizers cancelled, leaves the
                // calendar entirely — for everyone, not just for strangers.
                case ConferenceCancelled e -> entriesBySubject.remove(e.conferenceId());
                case ConferenceAttendanceDeclined e -> entriesBySubject.remove(e.conferenceId());

                // Gatherings are public in full too, speaking marker and info URL included.
                case GatheringPlanned e -> put(e.gatheringId(), gathering(
                        e.title(), e.venueName(), e.location(), e.startsAt(), e.endsAt(),
                        e.speaking(), e.infoUrl()));
                case GatheringChanged e -> put(e.gatheringId(), gathering(
                        e.title(), e.venueName(), e.location(), e.startsAt(), e.endsAt(),
                        e.speaking(), e.infoUrl()));

                // A private event is built as "Busy" here, never as a full entry that something
                // later trims: the title and the venue are simply not read.
                case PrivateEventPlanned e -> put(e.privateEventId(), busy(e));

                // Airport codes and the day are public; the departure and arrival times are not,
                // so a flight's subtitle is left off entirely rather than filtered. The multi-day
                // split still happens, because which day columns a flight occupies is public.
                case FlightBooked e -> putAll(e.flightId(), flight(
                        e.departureAirport(), e.departureDateTime(), e.arrivalAirport(), e.arrivalDateTime()));
                case FlightChanged e -> putAll(e.flightId(), flight(
                        e.departureAirport(), e.departureDateTime(), e.arrivalAirport(), e.arrivalDateTime()));

                // Same for trains — city names are public, the service id and the times are not.
                case TrainBooked e -> putAll(e.tripId(), train(
                        e.departureStation(), e.departureDateTime(), e.arrivalStation(), e.arrivalDateTime()));
                case TrainChanged e -> putAll(e.tripId(), train(
                        e.departureStation(), e.departureDateTime(), e.arrivalStation(), e.arrivalDateTime()));

                // A stay publishes the word "Hotel" and the city. The name, the address, the maps
                // URL and the cancel-by deadline are never read.
                case HotelBooked e -> put(e.hotelBookingId(), lodging(e.address(), e.checkIn(), e.checkOut()));
                case HotelChanged e -> put(e.hotelBookingId(), lodging(e.address(), e.checkIn(), e.checkOut()));
                case HotelBookingCancelled e -> entriesBySubject.remove(e.hotelBookingId());

                // A transfer's owner title names a hotel, so it is not built at all: what goes out
                // is the generic word plus the endpoints in their publishable form, which for a
                // hotel end is its city and never its name.
                case GroundTransferPlanned e -> put(e.groundTransferId(), groundTransfer(e));
                case GroundTransferCancelled e -> entriesBySubject.remove(e.groundTransferId());

                default -> { /* nothing an anonymous viewer may see */ }
            }
        });
    }

    private CalendarEntry conference(ConferencePlanned event, AttendanceCommitment commitment) {
        List<SubtitleLine> location = List.of(new SubtitleLine.Text(cityCountry(event.venueAddress())));
        return entry(
                event.startDate().localDateTime(),
                event.endDate().localDateTime(),
                event.name(),
                location,
                event.name() + " cont'd",
                location,
                new EntryDetails.PublicConference(commitment));
    }

    /** Ted is going: the same entry, no longer speculative. */
    private void going(ConferenceId conferenceId) {
        entriesBySubject.computeIfPresent(conferenceId, (id, entries) -> entries.stream()
                .map(entry -> entry(
                        entry.start(), entry.end(),
                        entry.mainTitle(), entry.subTitle(),
                        entry.continuationTitle(), entry.continuationSubTitle(),
                        new EntryDetails.PublicConference(AttendanceCommitment.GOING)))
                .toList());
    }

    private CalendarEntry gathering(String title, String venueName, Address location,
                                    ZonedTimestamp startsAt, ZonedTimestamp endsAt,
                                    boolean speaking, String infoUrl) {
        List<SubtitleLine> lines = new ArrayList<>();
        if (!venueName.isBlank()) {
            lines.add(new SubtitleLine.Text(venueName));
        }
        lines.add(new SubtitleLine.Text(cityCountry(location)));
        lines.add(new SubtitleLine.Range(startsAt, endsAt));
        return entry(
                startsAt.localDateTime(),
                endsAt.localDateTime(),
                title,
                List.copyOf(lines),
                new EntryDetails.PublicGathering(infoUrl.isBlank() ? null : infoUrl, speaking));
    }

    /**
     * "Busy", the city, and the time in the event's own zone — the one redacted output that keeps a
     * moment, because a private event's time is public in its own zone by decision. It is a
     * {@link SubtitleLine.FixedRange} rather than a {@link SubtitleLine.Range} so the browser-zone
     * script never re-localizes it. The title and venue are not read.
     */
    private CalendarEntry busy(PrivateEventPlanned event) {
        return entry(
                event.startsAt().localDateTime(),
                event.endsAt().localDateTime(),
                "Busy",
                List.of(new SubtitleLine.FixedRange(event.startsAt(), event.endsAt()),
                        new SubtitleLine.Text(cityCountry(event.location()))),
                new EntryDetails.Busy());
    }

    private List<CalendarEntry> flight(AirportCode departureAirport, ZonedTimestamp departure,
                                       AirportCode arrivalAirport, ZonedTimestamp arrival) {
        return legs("✈️ " + departureAirport.code() + "→" + arrivalAirport.code(),
                    departure, arrival, new EntryDetails.PublicFlight());
    }

    private List<CalendarEntry> train(TrainStationAddress departureStation, ZonedTimestamp departure,
                                      TrainStationAddress arrivalStation, ZonedTimestamp arrival) {
        return legs("🚄 " + departureStation.city() + " → " + arrivalStation.city(),
                    departure, arrival, new EntryDetails.PublicTrain());
    }

    /**
     * One segment for a same-day journey, two for one that crosses midnight — the day columns a
     * journey occupies are public, so the split is the same one the owner's calendar makes. The
     * route title carries every segment; there is no subtitle, because the only thing a leg's
     * subtitle could say is when.
     */
    private List<CalendarEntry> legs(String route, ZonedTimestamp departure, ZonedTimestamp arrival,
                                     EntryDetails.Publishable details) {
        LocalDateTime departsAt = departure.localDateTime();
        LocalDateTime arrivesAt = arrival.localDateTime();
        if (departsAt.toLocalDate().equals(arrivesAt.toLocalDate())) {
            return List.of(entry(departsAt, arrivesAt, route, null, details));
        }
        return List.of(entry(departsAt, departsAt, route, null, details),
                       entry(arrivesAt, arrivesAt, route, null, details));
    }

    private CalendarEntry lodging(Address address, ZonedTimestamp checkIn, ZonedTimestamp checkOut) {
        List<SubtitleLine> location = List.of(new SubtitleLine.Text(cityCountry(address)));
        return entry(
                checkIn.localDateTime(),
                checkOut.localDateTime(),
                "Hotel",
                location,
                "Hotel cont'd",
                location,
                new EntryDetails.PublicLodging());
    }

    private CalendarEntry groundTransfer(GroundTransferPlanned event) {
        String route = label.publicLabel(event.originAirportCode(), event.origin())
                       + " → "
                       + label.publicLabel(event.destinationAirportCode(), event.destination());
        return entry(
                event.departsAt().localDateTime(),
                event.arrivesAt().localDateTime(),
                "🚕 Ground transfer",
                route.isBlank() ? List.of() : List.of(new SubtitleLine.Text(route)),
                new EntryDetails.PublicGroundTransfer());
    }

    /** "City, Country" — or just the city when no country was recorded. */
    private String cityCountry(Address address) {
        return address.country().isBlank()
                ? address.city()
                : address.city() + ", " + address.country();
    }

    /**
     * The only two ways this class builds an entry, and both demand
     * {@link EntryDetails.Publishable}. An owner details type — the ones with slots for an edit
     * path or a maps URL — will not compile here, which is what makes the allow-list a compiler
     * check rather than a convention.
     */
    private CalendarEntry entry(LocalDateTime start, LocalDateTime end, String mainTitle,
                                List<SubtitleLine> subTitle, EntryDetails.Publishable details) {
        return new CalendarEntry(start, end, mainTitle, subTitle, details);
    }

    private CalendarEntry entry(LocalDateTime start, LocalDateTime end,
                                String mainTitle, List<SubtitleLine> subTitle,
                                String continuationTitle, List<SubtitleLine> continuationSubTitle,
                                EntryDetails.Publishable details) {
        return new CalendarEntry(start, end, mainTitle, subTitle,
                                 continuationTitle, continuationSubTitle, details);
    }

    private void put(Object subject, CalendarEntry entry) {
        entriesBySubject.put(subject, List.of(entry));
    }

    private void putAll(Object subject, List<CalendarEntry> entries) {
        entriesBySubject.put(subject, entries);
    }

    public List<CalendarEntry> entries() {
        return entriesBySubject.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
