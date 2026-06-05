# j2html Migration Analysis — Read-Only View Pages

Analysis of converting all read-only view pages (projections) from Thymeleaf to j2html.
Form templates (book-hotel, book-train, book-flight, plan-conference, plan-gathering) are
explicitly excluded — they are well-served by Thymeleaf's `th:field` / `th:errors` binding and
are not candidates for conversion.

Generated from full reading of all templates, fragment files, controllers, and view records.

---

## Architectural Decision: Direct HTML Response

The existing `CalendarViewBuilder` uses a **hybrid pattern**: j2html generates an HTML string,
which is placed in the Spring MVC model as an attribute, then embedded by a Thymeleaf shell
template (`confirmed-calendar.html`) via `th:utext="${calendarMarkup}"`.

**This pattern is being retired.** All new view renderers will return HTML directly:

```java
@GetMapping("/booked-hotels")
public ResponseEntity<String> bookedHotels() {
    String html = BookedHotelsRenderer.render(projector.views());
    return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html);
}
```

**Rationale:**
- Eliminates the Thymeleaf shell entirely — one less file per view
- The "model attribute contains raw HTML" pattern is an anti-pattern; it bypasses Thymeleaf's
  escaping and inverts the usual template responsibility
- The renderer owns the full page: nav, CSS, content, structure
- `confirmed-calendar.html` and its counterparts become dead files and can be deleted

**Migration of CalendarController:** Change the return type from `String` (view name) to
`ResponseEntity<String>`, delete `confirmed-calendar.html`. The `CalendarViewBuilder` already
builds the full markup; only the controller return needs updating.

---

## Existing j2html Pattern (CalendarViewBuilder)

`CalendarViewBuilder.render()` is a static method returning `String`. It builds a `DomContent`
hierarchy using static imports from `j2html.TagCreator` (`a`, `div`, `each`) and calls
`.render()` at the end.

**Known pain points to avoid in new renderers:**

1. **`DomContent` vs typed tags:** `.withClass()` is only available on concrete typed tags
   (`DivTag`, `ATag`, etc.), not on the `DomContent` interface. Call `.withClass()` before
   widening to `DomContent`.

2. **Inline styles as strings:** Styles applied via `.withStyle("grid-column: " + col)` drift
   from CSS if class names or layout change. Prefer CSS classes over inline styles wherever
   possible.

3. **SVG icons:** Inline SVG is rendered via `rawHtml("&lt;svg...&gt;")`. This is unavoidable
   — j2html has no SVG tag model.

4. **Temporal formatting:** `#temporals.format(dt, 'MMM d, h:mm a')` becomes
   `dt.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))`. A shared `TemporalFormatter`
   utility class should own all format patterns used across views.

---

## Shared Infrastructure Required

All renderers share these needs. Build infrastructure first before converting individual views.

### `PageBuilder`
Wraps content in the common page shell used by all view pages: site.css link, outer
`<div class="page">`, nav breadcrumb with home and calendar links, optional `<h1>` title.
Every booked-*, itinerary, schedule-problems, and tentative-conferences page shares this shell.

```java
PageBuilder.page("Booked Hotels")
    .nav(List.of(link("/", "JitterTravel"), link("/calendar", "Calendar")))
    .content(tableHtml)
    .render()
```

Estimated: **~60 lines.**

### `TemporalFormatter`
Centralises all `DateTimeFormatter` patterns in use across templates:

| Pattern | Used in |
|---|---|
| `"EEE, MMM d"` | itinerary day headers |
| `"MMM d – MMM d, yyyy"` | itinerary date range nav |
| `"h:mm a"` | itinerary entry times, booked-trains |
| `"MMM d, h:mm a"` | schedule-problems detail strings |
| `"EEE, MMM d"` | schedule-problems hotel dates |
| `"MMM d, yyyy"` | tentative-conferences start/end |
| `"h:mm a"` | booked-trains departure/arrival display |

Estimated: **~50 lines.**

### `EntryCardRenderer`
Builds a single itinerary entry card (`<div class="entry-card entry-card--<type>">`) for any
of the four entry types. Encapsulates SVG icon selection, kind-label conditional (`Flight` vs
`Arriving`), and all inner field rendering. Eliminates the four `th:if` blocks in
`itinerary.html`.

Estimated: **~160 lines** (four rendering branches, inline SVG strings, conditional logic).

### `ProblemCardRenderer`
Builds a single problem card for any of the three `ScheduleProblem` subtypes:
`MissingTravel`, `MissingHotel`, `SchedulingConflict`. Encapsulates the CSS class selection
and temporal string composition.

Estimated: **~60 lines.**

---

## View-by-View Analysis

### `confirmed-calendar.html` (existing j2html — migrate controller only)

**Current state:** `CalendarViewBuilder` already builds all HTML. `CalendarController` places
the result in the model; `confirmed-calendar.html` embeds it via `th:utext`.

**Conversion:** Change `CalendarController` to return `ResponseEntity<String>`. Delete
`confirmed-calendar.html`.

**Effort:** Trivial — ~5 lines changed, one file deleted. **Do this first as a proof of pattern.**

---

### `itinerary.html` — **Medium effort**

**Current:** 216 lines Thymeleaf. Three-column grid with date navigation. Four entry types
(FLIGHT, TRAIN, LODGING, CONFERENCE), each rendered by a separate `th:if` block with
distinct SVG icons, CSS classes, and field access.

**Key constructs:**

| Thymeleaf | j2html equivalent |
|---|---|
| `th:each="day : ${days}"` | `days.stream().map(day -> renderDay(day))` |
| `th:each="entry : ${day.entries()}"` | `day.entries().stream().map(e -> EntryCardRenderer.render(e))` |
| `th:if="${entry.kind().name() == 'FLIGHT'}"` | `switch (entry.kind()) { case FLIGHT -> ... }` |
| `#temporals.format(day.date(), 'EEE, MMM d')` | `TemporalFormatter.dayHeader(day.date())` |
| `entry.role().name() == 'ARRIVAL' ? 'Arriving' : 'Flight'` | `entry.role() == FlightDayRole.ARRIVAL ? "Arriving" : "Flight"` |
| Inline SVG icons (4 variants) | `rawHtml("<svg...>")` constants per entry kind |
| `th:href="@{/itinerary(date=${prevDate})}"` | `a("← Previous").withHref("/itinerary?date=" + prevDate)` |

**Date navigation:** The `prevDate` / `nextDate` model attributes map directly to
`LocalDate` values formatted with `ISO.DATE` for the query parameter.

**Controller change:** `ItineraryController` drops `@RequestParam`, builds the renderer call,
returns `ResponseEntity<String>`. The `firstDateOnOrAfter` logic stays in the controller.

**Estimated renderer:** ~180 lines. Delegates to `EntryCardRenderer` for entry cards and
`TemporalFormatter` for all date/time output.

---

### `schedule-problems.html` — **Medium effort**

**Current:** 166 lines Thymeleaf. Three sections — Missing Travel (amber), Missing Hotel (blue),
Scheduling Conflicts (red) — each with empty-state handling. The "no problems" empty state
requires all three lists to be empty.

**Key constructs:**

| Thymeleaf | j2html equivalent |
|---|---|
| `th:if="${travelProblems.isEmpty() and hotelProblems.isEmpty() and schedulingProblems.isEmpty()}"` | `if (all three lists empty) → render "no problems" div` |
| `th:each="p : ${travelProblems}"` | `travelProblems.stream().map(p -> renderMissingTravel(p))` |
| `'Arrive ' + #temporals.format(p.arrivedAt(), 'MMM d, h:mm a') + ' — next leg departs ' + ...` | `"Arrive " + TemporalFormatter.dateTime(p.arrivedAt()) + " — next leg departs " + ...` |
| Conditional empty column: `th:if="${travelProblems.isEmpty()}"` | `if (list.isEmpty()) div("None").withClass("empty-column")` |
| Three-column CSS grid | `div().withClass("problem-columns").with(travelCol, hotelCol)` + full-width conflicts section |

**Sealed interface advantage:** `ScheduleProblem` is a sealed interface. The renderer can use
an exhaustive `switch` on the three subtypes — compile-time safety that Thymeleaf's
`th:if` chain cannot provide.

**Controller change:** `ScheduleProblemsController` calls the renderer with the three filtered
lists and returns `ResponseEntity<String>`. The filtering stays in the controller.

**Estimated renderer:** ~130 lines. Uses `ProblemCardRenderer` for individual cards.

---

### `booked-hotels.html` — **Small effort**

**Current:** 144 lines Thymeleaf (includes per-page CSS). Table with hotel name as maps link,
city/country, check-in/check-out, status badge.

**Key constructs:**

| Thymeleaf | j2html equivalent |
|---|---|
| `th:if="${hotels.isEmpty()}"` | `if (hotels.isEmpty()) → empty state div` |
| `th:each="hotel : ${hotels}"` | `hotels.stream().map(hotel -> renderRow(hotel))` |
| `th:href="${hotel.mapsUrl()}"` | `a(hotel.hotelName()).withHref(hotel.mapsUrl()).withTarget("_blank").withRel("noopener")` |
| `th:text="${hotel.status() == T(...).TENTATIVE ? 'Tentative' : 'Final'}"` | `hotel.status() == BookingIntent.TENTATIVE ? "Tentative" : "Final"` |
| Status badge class: `tentative` vs `final` | `"status-badge " + (hotel.status() == TENTATIVE ? "status-tentative" : "status-final")` |
| `#temporals.format(hotel.checkIn(), ...)` | `TemporalFormatter.dateTime(hotel.checkIn())` |

**Estimated renderer:** ~80 lines.

---

### `booked-trains.html` — **Small effort**

**Current:** 114 lines Thymeleaf. Grid layout showing departure and arrival info side-by-side,
with optional station mapsUrl links.

**Key constructs:**

| Thymeleaf | j2html equivalent |
|---|---|
| Conditional station link: `th:if="${!entry.departureMapsUrl().isEmpty()}"` | `entry.departureMapsUrl().isBlank() ? span(name) : a(name).withHref(url)` |
| `th:text="${entry.serviceId()}"` shown only if non-empty | `if (!entry.serviceId().isBlank()) div(entry.serviceId())` |
| Grid card header row | `div().withClass("train-card-header").with(span("Departure"), span("Departs"), ...)` |
| Temporal formatting | `TemporalFormatter.time(entry.departureDateTime())` |

**Estimated renderer:** ~90 lines.

---

### `booked-flights.html` + `fragments/booked-flights-fragment.html` — **Small effort**

**Current:** Shell is 128 lines (mostly per-page CSS), actual list content is in
`booked-flights-fragment.html` (52 lines). In j2html there is no fragment indirection —
the fragment content becomes a private render method.

**Fragment contents:**
- Header row: Departure, Route, Airline, Flight Number, Chevron columns
- For flights **with history:** `<details><summary>` expandable row + `<ul>` of change entries.
  j2html supports `details()` and `summary()` tag creators natively.
- For flights **without history:** plain div row
- Link to flight detail: `th:href="@{/booked-flights/{id}(id=${flight.flightId().id()})}"` →
  `a().withHref("/booked-flights/" + flight.flightId().id())`
- Empty state: `th:if="${flights.isEmpty()}"`

**Key constructs:**

| Thymeleaf | j2html equivalent |
|---|---|
| `th:if="${flight.hasChanges()}"` with `<details>/<summary>` | `if (flight.hasChanges()) details(summary(...), historyList) else div(...)` |
| `th:each="entry : ${flight.history}"` | `flight.history().stream().map(e -> li(...))` |
| Fragment indirection `th:replace="~{fragments/booked-flights-fragment :: list}"` | Disappears — becomes a private `renderFlightList(List<BookedFlightView>)` method |

**Estimated renderer:** ~120 lines. The fragment/shell split collapses to one class.

---

### `tentative-conferences.html` + `fragments/tentative-conferences-fragment.html` — **Small effort**

**Current:** Shell is 65 lines, fragment is 30 lines. Simple table: name, start date, end date,
city, country. No conditional logic beyond the empty state.

**Fragment contents:**
- Table with 5 columns
- `th:each="conference : ${conferences}"` over `TentativeConferenceView` records
- Temporal formatting: `#temporals.format(conference.startDate(), 'MMM d, yyyy')`
- No links, no conditional CSS

**Key constructs:**

| Thymeleaf | j2html equivalent |
|---|---|
| `th:each="conference : ${conferences}"` | `conferences.stream().map(c -> tr(...))` |
| `#temporals.format(conference.startDate(), 'MMM d, yyyy')` | `TemporalFormatter.longDate(conference.startDate())` |
| Fragment indirection | Disappears — one private method |

**Estimated renderer:** ~60 lines.

---

## Summary Table

| Page | Template Lines | Fragment Lines | Renderer Est. | Effort | Controller Change |
|---|---|---|---|---|---|
| confirmed-calendar | 176 | — | 0 (already built) | **Trivial** | Return type only |
| itinerary | 216 | — | ~180 | Medium | Drop `@RequestParam`, return `ResponseEntity` |
| schedule-problems | 166 | — | ~130 | Medium | Pass 3 filtered lists to renderer |
| booked-hotels | 144 | — | ~80 | Small | Return `ResponseEntity` |
| booked-trains | 114 | — | ~90 | Small | Return `ResponseEntity` |
| booked-flights | 128 | 52 | ~120 | Small | Return `ResponseEntity` |
| tentative-conferences | 65 | 30 | ~60 | Small | Return `ResponseEntity` |
| **Shared infrastructure** | — | — | ~270 | — | — |
| **Total** | **1,009** | **82** | **~930** | | |

The line count is roughly neutral, but the renderer code is type-safe, compile-checked, and
eliminates template-syntax errors. The fragment indirection in booked-flights and
tentative-conferences disappears entirely — in j2html reuse is just a method call.

---

## Migration Order

**Step 1 — Infrastructure** *(do before any view conversion)*
1. `TemporalFormatter` — all date/time format strings, validated against every template
2. `PageBuilder` — common page shell (nav, CSS, h1, page div)
3. `ProblemCardRenderer` — three `ScheduleProblem` subtypes
4. `EntryCardRenderer` — four itinerary entry kinds (FLIGHT, TRAIN, LODGING, CONFERENCE)

**Step 2 — Confirm the pattern** *(single small change)*
5. Migrate `CalendarController` to `ResponseEntity<String>`, delete `confirmed-calendar.html`

**Step 3 — Simple views** *(build confidence, validate infrastructure)*
6. `TentativeConferencesRenderer` + update `PlanConferenceController`
7. `BookedHotelsRenderer` + update `BookedHotelsController`
8. `BookedTrainsRenderer` + update `BookedTrainsController`

**Step 4 — Fragment-bearing views**
9. `BookedFlightsRenderer` (inlines fragment, handles `<details>` history) + update `BookedFlightsController`

**Step 5 — Complex views**
10. `ScheduleProblemsRenderer` + update `ScheduleProblemsController`
11. `ItineraryRenderer` (uses `EntryCardRenderer`) + update `ItineraryController`

**Thymeleaf templates deleted at each step.** No Thymeleaf shell survives for any of these views.
Form templates (`book-hotel`, `book-train`, `book-flight`, `plan-conference`, `plan-gathering`)
are untouched throughout.
