package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;

/**
 * Writes one end of a ground transfer as display text, at the two very different levels of detail
 * the two audiences get. Presentation-layer, shared by {@link GroundTransferCalendarProjector} and
 * {@link ItineraryProjector} — the {@code GroundTransferPlanned} event itself carries data, never
 * display strings.
 * <p>
 * {@link #ownerLabel} names the place: {@code DEN}, or {@code Marriott Lone Tree}.
 * {@link #publicLabel} is the redaction rule made concrete — <em>if the airport code is non-blank,
 * publish the code; otherwise publish the city and its state or country</em> — and it <strong>never</strong>
 * takes the name, because a hotel name is private. Nothing calls {@code ownerLabel} on the path to
 * an anonymous viewer.
 */
public class TransferEndpointLabel {

    private final CityLabel cityLabel = new CityLabel();

    /** What Ted sees: the airport code, else the place's name, else its city. */
    public String ownerLabel(String airportCode, String name, Address address) {
        if (!airportCode.isBlank()) {
            return airportCode;
        }
        if (!name.isBlank()) {
            return name;
        }
        return address.city();
    }

    /** What anyone may see: the airport code, else the {@link CityLabel} of the place. */
    public String publicLabel(String airportCode, Address address) {
        if (!airportCode.isBlank()) {
            return airportCode;
        }
        return cityLabel.label(address);
    }
}
