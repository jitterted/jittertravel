package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.ItineraryChangeKind;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The change-itinerary form: who changed it, the email sent after the change, and a zone for each
 * airport the curated table does not know.
 * <p>
 * {@code kind} is the radio's value, {@code ""} until one is picked. There is no default, because
 * the email cannot tell a schedule change from a rebooking (D3).
 * <p>
 * <strong>{@link #toString()} leaves the paste out, on purpose</strong>, as
 * {@link BookFlightItineraryRequest} does: this object is handed to {@code CommandExecutor} as the
 * request, and the paste holds an eTicket number, a frequent-flyer number and a legal name.
 */
public class ChangeFlightItineraryRequest {

    private String kind = "";
    private String pasted = "";
    private Map<String, String> airportZones = new LinkedHashMap<>();

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind == null ? "" : kind;
    }

    /** The picked kind, or null for none or for a value no radio sends. */
    ItineraryChangeKind kindPicked() {
        for (ItineraryChangeKind candidate : ItineraryChangeKind.values()) {
            if (candidate.name().equals(kind)) {
                return candidate;
            }
        }
        return null;
    }

    public String getPasted() {
        return pasted;
    }

    public void setPasted(String pasted) {
        this.pasted = pasted == null ? "" : pasted;
    }

    public Map<String, String> getAirportZones() {
        return airportZones;
    }

    public void setAirportZones(Map<String, String> airportZones) {
        this.airportZones = airportZones == null ? new LinkedHashMap<>() : airportZones;
    }

    @Override
    public String toString() {
        return "ChangeFlightItineraryRequest {kind='" + kind + "', pasted=<"
               + pasted.length() + " chars, not shown>, airportZones=" + airportZones + "}";
    }
}
