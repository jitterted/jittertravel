package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedItinerariesProjector;
import dev.ted.jittertravel.application.BookedItineraryView;
import dev.ted.jittertravel.application.FlightDetailsView;
import dev.ted.jittertravel.application.FlightDetailsViewProjector;
import dev.ted.jittertravel.application.FlightItineraryChange;
import dev.ted.jittertravel.application.ItineraryChangeEvaluation;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ItineraryChangeKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Records a change to one booked itinerary: the airline's schedule change or Ted's own rebooking,
 * from the email sent after it ({@code docs/FlightItineraryRebookingPlan.md} D3). A recording
 * surface: the legs booked now say which itinerary this is, the preview says what applying records.
 * <p>
 * One page and one POST, told apart by the button: {@code action=preview} evaluates and writes
 * nothing; {@code action=apply} re-reads the form, runs the same evaluation and writes only if it is
 * clean; {@code action=open} only fills the form, and is how the Book page hands over an email
 * pasted at the wrong door (D4), so that Ted does not paste it twice and is not met with "Pick who
 * changed it" before he has had the chance.
 * <p>
 * The command id is minted per apply, at the boundary. A resubmit of a form already applied finds
 * nothing left to change and writes nothing, so there is no form id to carry.
 * <p>
 * An unknown, malformed or cancelled itinerary navigates to {@code /booked-flights} in silence, as
 * the cancel page does. OWNER-only in {@code SecurityConfig}, with its row in the
 * {@code AuthorizationMatrixTest} policy.
 */
@Controller
public class ChangeFlightItineraryController {

    private static final Logger log = LoggerFactory.getLogger(ChangeFlightItineraryController.class);
    private static final String VIEW = "change-flight-itinerary";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a z", Locale.ENGLISH);

    private final FlightItineraryChange flightItineraryChange;
    private final BookedItinerariesProjector itinerariesProjector;
    private final FlightDetailsViewProjector detailsProjector;
    private final Clock clock;

    public ChangeFlightItineraryController(FlightItineraryChange flightItineraryChange,
                                           BookedItinerariesProjector itinerariesProjector,
                                           FlightDetailsViewProjector detailsProjector,
                                           Clock clock) {
        this.flightItineraryChange = flightItineraryChange;
        this.itinerariesProjector = itinerariesProjector;
        this.detailsProjector = detailsProjector;
        this.clock = clock;
    }

    /** One leg as booked now, for the table that says which itinerary this is. */
    public record BookedLeg(String flightNumber, String route, String departs, String arrives) {
    }

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @GetMapping("/booked-itineraries/{itineraryId}/change")
    public String form(@PathVariable("itineraryId") String itineraryIdString, Model model) {
        if (flightItineraryChange.isReadOnly()) {
            return "redirect:/read-only";
        }
        Optional<BookedItineraryView> itinerary = lookup(itineraryIdString);
        if (itinerary.isEmpty()) {
            return "redirect:/booked-flights";
        }
        return render(model, itinerary.get(), new ChangeFlightItineraryRequest(), ItineraryChangePreview.NONE);
    }

    @PostMapping("/booked-itineraries/{itineraryId}/change")
    public String submit(@PathVariable("itineraryId") String itineraryIdString,
                         @ModelAttribute("change") ChangeFlightItineraryRequest request,
                         @RequestParam(value = "action", defaultValue = "preview") String action,
                         Model model) {
        if (flightItineraryChange.isReadOnly()) {
            return "redirect:/read-only";
        }
        Optional<BookedItineraryView> maybe = lookup(itineraryIdString);
        if (maybe.isEmpty()) {
            return "redirect:/booked-flights";
        }
        BookedItineraryView itinerary = maybe.get();
        if (action.equals("open")) {
            return render(model, itinerary, request, ItineraryChangePreview.NONE);
        }

        ItineraryChangeKind kind = request.kindPicked();
        Supplier<FlightId> newFlightId = () -> FlightId.of(UUID.randomUUID());
        Instant now = Instant.now(clock);
        ItineraryChangeEvaluation evaluation;
        if (action.equals("apply")) {
            try {
                evaluation = flightItineraryChange.apply(UUID.randomUUID(), request, itinerary.itineraryId(),
                        kind, request.getPasted(), request.getAirportZones(), newFlightId, now);
            } catch (ReadOnlyModeException e) {
                log.warn("Attempted to change a flight itinerary while in read-only mode", e);
                return "redirect:/read-only";
            }
            if (evaluation.applicable()) {
                return "redirect:/booked-flights?changed=" + itinerary.itineraryId().id()
                       + "&how=" + howParam(kind);
            }
        } else {
            evaluation = flightItineraryChange.evaluate(itinerary.itineraryId(), kind, request.getPasted(),
                    request.getAirportZones(), newFlightId, now);
        }
        return render(model, itinerary, request, ItineraryChangePreview.from(evaluation));
    }

    /** The banner's outcome key on {@code /booked-flights}, never its sentence (approved item 2). */
    static String howParam(ItineraryChangeKind kind) {
        return switch (kind) {
            case SCHEDULE_CHANGE -> "schedule-change";
            case REBOOKING -> "rebooked";
        };
    }

    private String render(Model model, BookedItineraryView itinerary, ChangeFlightItineraryRequest request,
                          ItineraryChangePreview preview) {
        List<FlightDetailsView> legs = liveLegs(itinerary);
        model.addAttribute("itinerary", itinerary);
        model.addAttribute("identity", identity(itinerary, legs));
        model.addAttribute("bookedLegs", legs.stream().map(ChangeFlightItineraryController::bookedLeg).toList());
        model.addAttribute("change", request);
        model.addAttribute("preview", preview);
        return VIEW;
    }

    /** "United Airlines · 4 legs, Nov 7 to Nov 26", beside the code's badge. */
    private static String identity(BookedItineraryView itinerary, List<FlightDetailsView> legs) {
        if (legs.isEmpty()) {
            return itinerary.airline();
        }
        String dates = DAY.format(legs.getFirst().departureDateTime().atEntryZone())
                       + " to " + DAY.format(legs.getLast().arrivalDateTime().atEntryZone());
        return itinerary.airline() + " · " + legs.size() + (legs.size() == 1 ? " leg, " : " legs, ") + dates;
    }

    private static BookedLeg bookedLeg(FlightDetailsView leg) {
        return new BookedLeg(leg.flightNumber(),
                leg.departureAirport().code() + " → " + leg.arrivalAirport().code(),
                WHEN.format(leg.departureDateTime().atEntryZone()),
                WHEN.format(leg.arrivalDateTime().atEntryZone()));
    }

    private List<FlightDetailsView> liveLegs(BookedItineraryView itinerary) {
        return itinerary.flightIds().stream()
                .map(detailsProjector::findById)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .toList();
    }

    private Optional<BookedItineraryView> lookup(String itineraryIdString) {
        try {
            return itinerariesProjector.findLive(FlightItineraryId.of(UUID.fromString(itineraryIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
