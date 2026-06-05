# Future Feature Slices

Deferred slices and features, captured here so they aren't forgotten.

---

## ConferenceCancelled

**Event:** `ConferenceCancelled(conferenceId, reason: String)`

Conferences (and eventually gatherings) need a cancellation mechanism. Deferred because the immediate need (re-entering a handful of gatherings-as-conferences) was resolved by waiting them out — they all expire within the current month.

**When to build:** When the first real cancelled conference arises, or as a prerequisite to any slice that needs to remove/retract a booking.

---

## infoUrl on ConferenceTentativelyPlanned

Conferences have event-specific web pages just like gatherings. Add `infoUrl: String` (empty string when absent) to `ConferenceTentativelyPlanned` and the plan-conference form.

**When to build:** When conference detail views or itinerary links are needed.

---

## mapsUrl on ConferenceTentativelyPlanned

Conferences have a physical venue. Add `mapsUrl: String` to `ConferenceTentativelyPlanned`, auto-computed from venue name + address in compact constructor if blank, and expose it in calendar/itinerary views.

**When to build:** When conference venue links are needed in views.
