package dev.ted.jittertravel.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The paste-an-itinerary form: the pasted email text, the itinerary id minted when the form was
 * first shown, and a zone for each airport the curated table does not know.
 * <p>
 * <strong>{@link #toString()} leaves the paste out, on purpose.</strong> This object is passed to
 * {@code CommandExecutor} as the request, which uses its {@code toString} in a read-only refusal's
 * message — and the paste holds the eTicket number, a frequent-flyer number and a legal name. The
 * command logged is built from parsed legs only; this keeps the paste out of the logs as well.
 */
public class BookFlightItineraryRequest {

    private String itineraryId;
    private String pasted = "";
    private Map<String, String> airportZones = new LinkedHashMap<>();

    public String getItineraryId() {
        return itineraryId;
    }

    public void setItineraryId(String itineraryId) {
        this.itineraryId = itineraryId;
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
        return "BookFlightItineraryRequest {itineraryId='" + itineraryId + "', pasted=<"
               + pasted.length() + " chars, not shown>, airportZones=" + airportZones + "}";
    }
}
