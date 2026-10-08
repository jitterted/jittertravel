package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCityResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The location values {@link LegacyEventMigration} corrects in stored rows, approved one by one by
 * Ted on 2026-10-07 ({@code docs/LocationDataCleanupPlan.md} §5.2). These are corrections, not a
 * change of format, so they live here and never in a rung: a restore of an older backup brings the old
 * values back, and that is accepted.
 *
 * <p>They run on a payload the read path has <em>already</em> upcast, so a country is a code by the
 * time they see it. Each named correction is <b>compare-and-set</b>: it changes its fields only while
 * every one still holds the value it was approved against, so it never overwrites a value someone has
 * since changed, it does nothing on a database whose event ids do not match production's, and a
 * second run changes nothing.
 *
 * <p>The airport endpoints are fixed by rule rather than by event, because the app wrote them itself
 * and may write more before the deploy: a {@code GroundTransferPlanned} end with an airport code and no
 * country takes its region and country from the airport table.
 *
 * <p>Every outcome is reported, correction by correction and end by end, so the migration page can
 * show each one as made, already made or skipped, rather than a count that hides which.
 */
public class LocationDataCorrections {

    private static final List<Correction> CORRECTIONS = List.of(
            // #26, #27 — Didcot Parkway is in Didcot; Oxfordshire was where Ted was heading.
            new Correction("163499cf-e4e0-47f0-8f2f-7dc656e48647", "Train to Didcot Parkway",
                           new Change("arrivalStation.city", "Oxfordshire", "Didcot")),
            new Correction("841e4a38-2108-4666-869c-3dab59be85ef", "Train from Didcot Parkway",
                           new Change("departureStation.city", "Oxfordshire", "Didcot")),
            // #58, #86 — Best Western Plus St. Raphael: city and region were swapped.
            new Correction("c1598660-df8b-4139-af16-2bdc49dca1e8", "Hotel Best Western Plus St. Raphael",
                           new Change("address.city", "St. Georg", "Hamburg"),
                           new Change("address.region", "Hamburg", "St. Georg")),
            new Correction("00ca54ba-a9d8-4980-8a47-0c4ff947eb62", "Transfer from Best Western Plus St. Raphael",
                           new Change("origin.city", "St. Georg", "Hamburg"),
                           new Change("origin.region", "Hamburg", "St. Georg")),
            // #98 — Devnexus 2027 in Atlanta had no state, and no venue-change event exists.
            new Correction("3f43b7c1-1201-4411-8ed9-a69bbba8fc2f", "Devnexus 2027",
                           new Change("venueAddress.region", "", "GA")),
            // #107, #111, #112, #115 — Aschaffenburg misspelt. #115's station is named "Asch", which
            // was not part of the approval, so only its city changes.
            new Correction("405b92c9-8331-47c2-a52a-5210a81ae287", "Train to Aschaffenburg",
                           new Change("arrivalStation.city", "Aschaffenberg", "Aschaffenburg"),
                           new Change("arrivalStation.name", "Aschaffenberg Hbf", "Aschaffenburg Hbf")),
            new Correction("c93b87cf-d652-4d9f-b987-f6cbc0a6f453", "Train to Aschaffenburg, as changed",
                           new Change("arrivalStation.city", "Aschaffenberg", "Aschaffenburg"),
                           new Change("arrivalStation.name", "Aschaffenberg Hbf", "Aschaffenburg Hbf")),
            new Correction("d5ef4b77-fe2a-4c8d-a4e0-a263b4212ab8", "Transfer from Aschaffenburg Hbf",
                           new Change("origin.city", "Aschaffenberg", "Aschaffenburg"),
                           new Change("origin.locationForMatching", "Aschaffenberg", "Aschaffenburg"),
                           new Change("originName", "Aschaffenberg Hbf", "Aschaffenburg Hbf")),
            new Correction("0bf4b25a-441b-4161-8ec8-b962a07b7749", "Train from Aschaffenburg",
                           new Change("departureStation.city", "Aschaffenberg", "Aschaffenburg")),
            // #123 — The Last Coder's venue is in Johannesberg, as Play4Agile's is; Rückersbach
            // moves to Region rather than being dropped.
            new Correction("99e91405-bed1-41c5-b86d-cbc6460d2163", "The Last Coder 2027",
                           new Change("venueAddress.city", "Rückersbach", "Johannesberg"),
                           new Change("venueAddress.locationForMatching", "Rückersbach", "Johannesberg"),
                           new Change("venueAddress.region", "", "Rückersbach")));

    private static final Map<String, String> AIRPORT_CODE_FOR_END = Map.of(
            "origin", "originAirportCode",
            "destination", "destinationAirportCode");

    private final AirportCityResolver airportCityResolver;

    public LocationDataCorrections(AirportCityResolver airportCityResolver) {
        this.airportCityResolver = airportCityResolver;
    }

    /** Every approved correction — what a migration expects to find and settle, in approval order. */
    public List<Approved> approved() {
        return CORRECTIONS.stream()
                          .map(Correction::approved)
                          .toList();
    }

    /**
     * Applies whatever corrects this event, in place, and says what it did, so the migration can
     * report expected against actual; leaves every other payload untouched.
     */
    public Outcome apply(UUID eventId, String logicalType, ObjectNode payload) {
        NamedFix namedFix = CORRECTIONS.stream()
                                       .filter(correction -> correction.eventId().equals(eventId))
                                       .findFirst()
                                       .map(correction -> correction.settle(payload))
                                       .orElse(NamedFix.NONE);
        List<AirportEndFill> airportEnds = new ArrayList<>();
        if (logicalType.equals("GroundTransferPlanned")) {
            AIRPORT_CODE_FOR_END.forEach((end, codeField) ->
                    completeAirportEnd(payload, end, codeField).ifPresent(airportEnds::add));
        }
        return new Outcome(namedFix, airportEnds);
    }

    private Optional<AirportEndFill> completeAirportEnd(ObjectNode payload, String end, String codeField) {
        String airportCode = text(payload, codeField);
        if (airportCode.isBlank()
            || !(payload.get(end) instanceof ObjectNode place)
            || !text(place, "country").isBlank()) {
            return Optional.empty();
        }
        return Optional.of(airportCityResolver.addressFor(airportCode)
                                              .map(airport -> fillIn(place, airportCode, airport))
                                              .orElse(new AirportEndFill(airportCode, "", "", false)));
    }

    private static AirportEndFill fillIn(ObjectNode place, String airportCode, Address airport) {
        place.put("country", airport.country());
        if (text(place, "region").isBlank()) {
            place.put("region", airport.region());
        }
        return new AirportEndFill(airportCode, text(place, "region"), airport.country(), true);
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    /** What a named correction found on its event. */
    public enum NamedFix {
        /** The event has no named correction. */
        NONE,
        /** Every field held its approved old value, and now holds the new one. */
        APPLIED,
        /** Every field already holds the new value — a migration already ran. */
        ALREADY_APPLIED,
        /** Neither: something changed the values since the fix was approved, so nothing moved. */
        VALUES_DIFFER
    }

    /** One approved correction as a person reads it: which event, what it is, and what it changes. */
    public record Approved(UUID eventId, String label, List<FieldChange> changes) {}

    /** One field of a correction, by its own name, and the value it moves from and to. */
    public record FieldChange(String field, String from, String to) {}

    /**
     * An airport transfer end that had a code and no country: the code, and the region and country it
     * took from the airport table, or {@code filled} false and blanks when the table lacks the code.
     */
    public record AirportEndFill(String airportCode, String region, String country, boolean filled) {}

    /** What {@link #apply} did to one event: its named correction, and every airport end it met. */
    public record Outcome(NamedFix namedFix, List<AirportEndFill> airportEnds) {}

    /** One approved correction: every change in it applies together, or none does. */
    private record Correction(UUID eventId, String label, List<Change> changes) {
        Correction(String eventId, String label, Change... changes) {
            this(UUID.fromString(eventId), label, List.of(changes));
        }

        Approved approved() {
            return new Approved(eventId, label, changes.stream()
                                                       .map(Change::asFieldChange)
                                                       .toList());
        }

        NamedFix settle(ObjectNode payload) {
            if (changes.stream().allMatch(change -> change.holds(payload, change.from()))) {
                changes.forEach(change -> change.applyTo(payload));
                return NamedFix.APPLIED;
            }
            if (changes.stream().allMatch(change -> change.holds(payload, change.to()))) {
                return NamedFix.ALREADY_APPLIED;
            }
            return NamedFix.VALUES_DIFFER;
        }
    }

    /** A field, by a dotted path at most one object deep, and the value it moves from and to. */
    private record Change(String path, String from, String to) {

        boolean holds(ObjectNode payload, String value) {
            ObjectNode owner = owner(payload);
            return owner != null && owner.has(field()) && text(owner, field()).equals(value);
        }

        void applyTo(ObjectNode payload) {
            owner(payload).put(field(), to);
        }

        FieldChange asFieldChange() {
            return new FieldChange(field(), from, to);
        }

        private ObjectNode owner(ObjectNode payload) {
            int dot = path.indexOf('.');
            if (dot < 0) {
                return payload;
            }
            return payload.get(path.substring(0, dot)) instanceof ObjectNode nested ? nested : null;
        }

        private String field() {
            return path.substring(path.indexOf('.') + 1);
        }
    }
}
