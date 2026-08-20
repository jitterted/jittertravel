package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes one end of a ground transfer as display text, at the two very different levels of detail
 * the two audiences get. Presentation-layer, shared by {@link GroundTransferCalendarProjector} and
 * {@link ItineraryProjector} — the {@code GroundTransferPlanned} event itself carries data, never
 * display strings.
 * <p>
 * {@link #ownerLabel} names the place: {@code DEN}, or {@code Marriott Lone Tree}.
 * {@link #publicLabel} is the redaction rule made concrete — <em>if the airport code is non-blank,
 * publish the code; otherwise publish city / region / country</em> — and it <strong>never</strong>
 * takes the name, because a hotel name is private. Nothing calls {@code ownerLabel} on the path to
 * an anonymous viewer.
 */
public class TransferEndpointLabel {

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

    /** What anyone may see: the airport code, else "City, Region, Country" with blanks skipped. */
    public String publicLabel(String airportCode, Address address) {
        if (!airportCode.isBlank()) {
            return airportCode;
        }
        List<String> parts = new ArrayList<>();
        addWhenPresent(parts, address.city());
        addWhenPresent(parts, address.region());
        addWhenPresent(parts, address.country());
        return String.join(", ", parts);
    }

    private void addWhenPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }
}
