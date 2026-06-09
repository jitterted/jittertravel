# Authorization Policy: Toward a Single Source of Truth (Option 2)

## Status

Not yet implemented. Today the policy is expressed in **two small, role-based places**
(Option 1):

1. `SecurityConfig.securedFilterChain` — the authoritative URL → role enforcement.
2. `GeneralController.home()` — intent-named nav flags (`showDataEntryNav`,
   `showBookingsNav`, `showItineraryNav`) consumed by `index.html`.

These two are intentionally small and named after the nav groups they gate, so the current
risk of drift is low. `AuthorizationMatrixTest` is the canonical, executable statement of the
policy and will fail if enforcement diverges from intent.

## Why move to a single source of truth later

Option 1 still requires editing **two** files when the policy changes (enforcement + nav).
A future change (e.g., granting FAMILY access to the booking lists, or adding a new role/route)
must be applied consistently in both. Option 2 collapses these into **one** declarative
definition that both the security chain and the home navigation are derived from.

## Confirmed current policy (the thing to centralize)

| Route(s)                                                                 | OWNER | FAMILY | Anonymous |
|--------------------------------------------------------------------------|:-----:|:------:|:---------:|
| `/` (home)                                                               |  ✅   |  ✅    |    ✅     |
| `/calendar` (redacted for anonymous via `CalendarEntryRedactor`)         |  ✅   |  ✅    |    ✅     |
| `/itinerary`, `/itinerary/**`                                            |  ✅   |  ✅    |   login   |
| Bookings lists: `/booked-flights`, `/booked-trains`, `/booked-hotels`, `/tentative-conferences`, `/planned-gatherings` |  ✅   | denied |   login   |
| Data entry: `/book-*`, `/plan-*`, `/clear-conflict`, `/api/parse-address`, per-flight edit `/booked-flights/*` |  ✅   | denied |   login   |
| Admin: `/admin`, `/admin/**`                                             |  ✅   | denied |   login   |

- **denied** = authenticated-but-unauthorized → friendly redirect to `/?denied`.
- **login** = anonymous → redirect to `/login`.

## Proposed design

### 1. A central policy enum

Define each nav group once, with the routes it covers and the roles allowed:

```java
public enum NavSection {
    DATA_ENTRY(Set.of("OWNER"),
            List.of("/book-flight", "/book-flight/**",
                    "/book-hotel", "/book-hotel/**",
                    "/book-train", "/book-train/**",
                    "/plan-conference", "/plan-conference/**",
                    "/plan-gathering", "/plan-gathering/**",
                    "/clear-conflict", "/clear-conflict/**",
                    "/api/parse-address",
                    "/booked-flights/*", "/booked-flights/*/lookup")),
    BOOKINGS(Set.of("OWNER"),
            List.of("/booked-flights", "/booked-trains", "/booked-hotels",
                    "/tentative-conferences", "/planned-gatherings")),
    ITINERARY(Set.of("OWNER", "FAMILY"),
            List.of("/itinerary", "/itinerary/**")),
    ADMIN(Set.of("OWNER"),
            List.of("/admin", "/admin/**"));

    // routes + allowed roles accessors...
}
```
