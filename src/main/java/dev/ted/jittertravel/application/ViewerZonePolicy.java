package dev.ted.jittertravel.application;

/**
 * Decides which zone a viewer sees times in, from their role (decision 4 of
 * {@code docs/UtcDatetimeStoragePlan.md}):
 *
 * <ul>
 *   <li><b>OWNER</b> — the traveler. Entry-local, always: each endpoint in the zone it happens
 *       in. No toggle, and the browser-zone script is never even shipped.</li>
 *   <li><b>FAMILY</b> — following along from home. Their own browser zone, so "when is he
 *       landing" reads in the clock they are actually looking at.</li>
 *   <li><b>ANONYMOUS</b> — entry-local by default (the same baseline the traveler sees), with a
 *       toggle to browser zone; {@code ?tz=} picks the starting mode.</li>
 * </ul>
 *
 * A viewer holding both roles is treated as OWNER: it is the traveler's own account.
 */
public class ViewerZonePolicy {

    public ZoneDisplay forViewer(boolean isOwner, boolean isFamily, String tzParam) {
        if (isOwner) {
            return ZoneDisplay.entryOnly();
        }
        if (isFamily) {
            return new ZoneDisplay(DisplayZone.BROWSER, false);
        }
        return new ZoneDisplay(DisplayZone.fromParam(tzParam), true);
    }
}
