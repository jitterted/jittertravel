package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;

import java.time.ZoneId;

/**
 * One end of a ground transfer, resolved from a form token by
 * {@link GroundTransferEndpointResolver}.
 * <p>
 * Exactly one of {@code airportCode} / {@code name} is non-blank, and that is what decides how the
 * end is published: an airport shows its code (public), a place shows only its city / region /
 * country — the {@code name} (a hotel name) is <strong>private</strong> and never reaches an
 * anonymous viewer. The {@code address} is a snapshot, copied at submit time.
 * <p>
 * Each end resolves its own {@code zone}, so an unresolvable destination is caught as well as an
 * unresolvable origin; the transfer itself is timestamped in the <em>origin's</em> zone (a transfer
 * that crosses a zone boundary is out of scope — see docs/archived/GroundTransferPlan.md).
 */
public record TransferEndpoint(String airportCode, String name, Address address, ZoneId zone) {
}
