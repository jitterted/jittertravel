# Event log filter: several event types, and optionally identifiers

**Status:** `done` — **built 2026-11-05**. Asked for 2026-11-02 (Ted), mockup
drawn the same day. **Ted chose Option B, a Web Awesome combobox** (same day) and supplied the kit
boilerplate 2026-11-05, which settled D8.
Mockup: <https://claude.ai/artifact/CJkwWD5E4D9AZYAvgLpaFk> (the approved option becomes the spec; the
source is `eventlog-filter-mockup.html` in that session's scratchpad).

## Read this first: what the build did differently

The decisions below are the plan as agreed. Six things changed while building it, and where the
text below disagrees with this list, this list is what the code does.

1. **D6 was wrong about storage.** A row's `type` is the logical name (`FlightBooked`), not the
   class name. Rows from before logical names hold the class name, and a renamed type's old rows
   hold its old name. So a type filter matches **every spelling the type was ever stored under**
   (`EventTypes.wireIdsFor`), or it would silently miss the rows that predate a rename.
2. **D3 uses `strpos`, not `LIKE`.** Nothing checks that an identifier is a UUID, and in a `LIKE`
   pattern a typed `_` or `%` is a wildcard: `%` alone would have matched every row.
3. **D4 does not build markup in Java.** `EventLogFilterView.segmentsOf` cuts the payload at each
   match and the template writes every piece with `th:text`, a `<mark>` around the matched ones.
   The engine does all the escaping, so there is no escape-then-mark order to get wrong. (Marking
   in already-escaped text also had a hole: an identifier such as `39` or `quot` would have been
   marked inside `&#39;` and `&quot;`.)
4. **`countEvents()` with no argument stays.** The page shows the whole log's size beside the
   matching count (D7), so it has a production caller. Only the unfiltered `loadEventPage` went.
5. **The component cuts a tag at ten characters.** `FlightBooked` and `FlightChanged` both showed
   as `FlightBo…`. `--tag-max-size: 40ch` on the combobox lets the longest type name through, and
   a test fails if a longer one is ever registered.
6. **The placeholder follows the mockup, not D1's markup.** "All types — type to filter…" with
   nothing chosen, "Type to filter…" once types are chosen. It is set when the page is rendered,
   so it lags behind a pick until the next Apply. Fixing that takes a script of our own.

**Seen on the running app** (headless Chromium, 2026-11-05): picking two types submits
`type=FlightBooked&type=FamilyNotified`; the theme stylesheet changes the computed style of `html`
alone and nothing visible; every request the page makes to Web Awesome is under the kit address;
the anonymous `/calendar` makes none; no horizontal scroll at 1100 or 820 wide.

**Not built, and not asked for:** a row that matched on its event id or command id has nothing
marked, since D4 marks the payload only. The payload stays closed until opened, as before.

## What is wanted

`/admin/eventlog` lists every event, fifty a page, newest first, with no way to narrow it. The
command log has had a status filter since 2026-06; this is the event log's equivalent, asked for
then and carried as a one-line box in `Cleanup_Tasks.md` since. The question that brought it back is
"which emails did family get?" — every send is a `FamilyNotified` event, so the answer is already in
the log and only the view is missing (`FamilyEmailNotificationsPlan.md` says as much).

Two filters, both optional, combined with AND:

1. **Event types, any number of them.** None selected means all. The command log's filter is one
   status at a time and that is enough for six statuses; with 33 event types, the useful questions
   ("everything about flights", "what family were told and what triggered it") span several.
2. **Identifiers, any number of them.** A row matches if any one of them appears as its event id,
   its command id, or an id inside its payload — a `flightId`, `conferenceId`, `itineraryId`, a
   hotel's id, the `subject.id` of a `FamilyNotified`. Several ids are OR'd, so the legs of a trip
   plus its itinerary can be asked for in one go.

## Decisions

**D1 — how the types are picked: a Web Awesome combobox, multiple (Ted, 2026-11-02).** The mockup
drew three. Option A, grouped checkboxes inside a `<details>` whose summary line shows the chosen
types as chips, was the first recommendation. Option C, every type as an always-visible toggle chip,
was drawn for comparison. Ted asked for **Option B**: a multi-select with autocomplete, so he types a
few letters and picks from what matches instead of scanning dozens of names and giving them the
space to be shown — the Web Awesome Pro `wa-combobox` in `multiple` mode
(<https://webawesome.com/docs/components/combobox/#multiple>), which he has a subscription for. A
native `<select multiple>` was rejected without drawing: iPadOS opens it as a picker that hides what
is already chosen. **For this page the iPad is secondary** (Ted's words); the combobox works there
with the on-screen keyboard, just less comfortably than a row of checkboxes.

Markup, one option per registered event type, alphabetical, straight from `EventTypes`:

```html
<wa-combobox name="type" multiple with-clear placeholder="All types — type to filter…">
  <wa-option th:each="type : ${eventTypes}" th:value="${type}" th:text="${type}"
             th:attr="selected=${selected.contains(type)} ? 'selected' : null"></wa-option>
</wa-combobox>
```

Chosen types render as tags inside the box, each with its own remove; `max-options-visible` stays at
its default of three, which collapses a longer pick to "+n" — fine, the URL and the meta line say
the rest. Filtering is the component's own, case-insensitive on the label.

**D2 — one GET form.** The filter is the URL (`?type=FlightBooked&type=FamilyNotified&id=…`), so a
view can be bookmarked, pasted into a plan doc, and carried by the Previous/Next links. Spring binds
a repeated `type` parameter to a `List<String>` with nothing added. The combobox is a form-associated
custom element and the docs say a multiple value submits as one entry per chosen option under the
same name (their own example reads it back with `formData.getAll`); **verify that on the running app
before building on it**, since the docs imply it rather than state it. Apply is a real submit
button, styled as a button (solid accent fill). Clear is a link to the bare path, greyed as a
non-interactive span when there is nothing to clear.

**Without script the type filter does not exist.** A custom element with no definition renders its
options as nothing and submits nothing; there is no progressive-enhancement fallback and none is
planned. The identifier box is a plain input and keeps working. This page is OWNER-only and script
is on wherever Ted reads it, so this is a recorded loss, not a problem.

**D3 — identifiers are matched as text, in SQL.** Ids are stored inside the payload JSON as
`{"id": "<uuid>"}` objects under field names that differ per event, so there is no one JSON path to
query. `payload::text LIKE '%<id>%'` OR'd with `event_id::text = ` and `command_id::text = ` is
one where clause that reaches every id the log will ever carry, including fields nobody has added
yet. The log is ~1,200 rows and admin-only; a sequential scan is fine and `EXPLAIN` need not be
consulted. The input is split on whitespace and commas, trimmed (`TrimTypedTextAdvice` already
trims the whole string), and anything left blank is dropped. Nothing is validated as a UUID: a
partial id, or for that matter a confirmation code, simply matches or does not. This was written
as "a consequence, not a goal"; **Ted confirmed on 2026-11-05 that matching a partial id is
wanted**, so do not tighten it to whole ids. The label and placeholder still talk about ids.

**D4 — a matched identifier is highlighted in the payload.** The row is there because of that id,
and with a long payload the reason is otherwise a search of the JSON by eye. `<mark>` around each
occurrence, in the rendered `<pre>`. This is the one place the renderer touches the payload text, so
it escapes first and marks second (`HtmlUtils.htmlEscape`, then a replace of the escaped id, which a
UUID never changes).

**D5 — the option list comes straight from `EventTypes`, so a new event is in it on arrival.** No
hand-curated list and no grouping: with autocomplete, grouping buys nothing, and a list derived from
the registry cannot be left behind by a new event. (Option A would have needed a grouping record and
a completeness test on the pattern of `CalendarDayMenuTest`; that is gone with it.)

**D8 — Web Awesome Pro is loaded from its kit CDN, on this page only, with the kit code written
into the template (decided 2026-11-05).** This is the first third-party component library in the
app, so it was asked about rather than assumed. Pro is not on the public CDN: Ted's workspace
boilerplate loads everything from `https://ka-p.webawesome.com/kit/<kit code>/webawesome@3.14.0/…`,
and the kit code is the access token. The alternatives were self-hosting the Pro files under
`static/` (they would land in a public GitHub repo, which the licence very likely forbids) and a
private npm registry in the Maven build (far too much machinery for one component on one admin
page). Both rejected.

What is loaded, and where. **Two tags in the head of `admin-eventlog.html` and nowhere else**: the
default theme stylesheet, and a module script importing the combobox component alone, not the
autoloader. The boilerplate's `utilities.css` and `native.css` are **not** loaded: the second is a
reset that would restyle the page, and `site.css` already owns the colours. The anonymous calendar
must never fetch a third-party bundle and nothing else needs it, which is why this is per-template
and not in a shared head.

```html
<link rel="stylesheet" href="https://ka-p.webawesome.com/kit/<kit code>/webawesome@3.14.0/styles/themes/default.css">
<script type="module" src="https://ka-p.webawesome.com/kit/<kit code>/webawesome@3.14.0/components/combobox/combobox.js"></script>
```

**The kit code is a literal in the template, and that is a recorded exception to the "never commit
a credential" rule in `DEPLOYMENT.md` (Ted, 2026-11-05).** An environment variable was planned first
— `WEBAWESOME_KIT_CODE`, empty default, a settings row, a pre-push box, an amber "filter is off"
line when blank — and Ted struck it: the code is revealed in the page source to anyone who can load
the page, which is only him, so hiding it from the repo buys nothing and costs a variable, a
settings row and a failure state. **A Web Awesome Pro kit cannot be restricted to domains, and the
exposure is accepted (Ted, 2026-11-05).** Font Awesome kits have a "Limit Domains" setting and the
first draft of this section assumed Web Awesome Pro had the same; it does not — Ted asked Web
Awesome support on their Discord, and their answer was to accept it. So a committed code is usable
by anyone who copies it from GitHub to load Pro components on their own site on Ted's subscription.
That is a licence exposure against his account, not a way into the app or its data; the page's
OWNER gate does not help, because the repo is the exposure; and the bare environment variable that
would have closed it was weighed and declined as not worth a setting for this. With a literal code
there is no blank state to render for: the filter is never "off", and the only failure is the CDN
being unreachable, which the combobox shows as an empty box.

`DEPLOYMENT.md`'s secrets paragraph gets one sentence naming this exception and why, so the next
reader does not move the code into a variable as a fix. Nothing in `Pre-Push-Tasks.md`: there is no
environment to prepare.

**D6 — the stored type is the fully qualified class name; the page speaks in simple names.** The
parameter carries the simple name (what the row shows). The controller maps it back through
`EventTypes` before the query, and an unknown name is ignored rather than refused — a stale
bookmark after a rename should show everything else, not a 400.

**D7 — the empty state names the filter.** "No events match. 1 type and 1 identifier selected.
Clear the filter to see all 1,214." The existing "No events recorded yet" stays for a genuinely empty
log. The meta line reads "2 of 1,214 events match" under a filter and is unchanged without one.

## What it touches

| Layer | Change |
|---|---|
| `PostgresPersister` | `countEvents(filter)` and `loadEventPage(offset, limit, filter)` take an `EventLogFilter` (types as logical names, ids as strings) and build one where clause. The unfiltered `loadEventPage` goes; `countEvents()` stays for the total. |
| `EventLogFilter` (new) | the filter itself, and `from(typeNames, identifierText)`: drops unknown type names (D6) and splits the identifier text (D3). |
| `EventLogController` | binds `type` (list) and `id` (string), builds the filter, passes it to both queries, and puts the type list and an `EventLogFilterView` on the model. |
| `EventLogFilterView` (new) | the filter as the page draws it: the paging links that carry it, the empty state's sentence (D7), and where a matched identifier sits in a payload (D4). |
| `admin-eventlog.html` | the Web Awesome script and theme stylesheet in this page's head only, the form above the toolbar with the combobox, the meta line, the empty state, `<mark>` in the payload, and the filter on every paging href. |
| `EventTypes` | exposes the registered logical names, alphabetical, for the options, and every stored spelling of one, for the query (D6). |
| `DEPLOYMENT.md` | a paragraph under the secrets one: the Web Awesome kit code is committed on purpose, nothing restricts where it can be used, and that exposure is accepted (D8). |
| `Cleanup_Tasks.md` | the box is moved to Done with a pointer here, and the "no coverage at all" entry for the event log page is closed by the tests below. |

## Tests

As built: `EventLogFilterTest` (8) and `EventLogFilterViewTest` (16) are plain unit tests, and PIT
kills every mutant in both classes. `PostgresPersisterTest` gained 8 cases and
`EventLogControllerTest` has 14. The SQL and the template are out of PIT's sight, so fourteen
mutations were made by hand and each failed a test: identifiers ANDed, types ORed with identifiers,
`LIKE` for `strpos`, the event id and the command id each dropped from the match, oldest first, old
type spellings not matched, the count ignoring the filter, the Next link dropping the filter, the
payload written unescaped, no option selected, pages counted from the whole log, the tag width back
at ten characters, and the placeholder keyed on the whole filter instead of on the types.

The plan as written:

- `PostgresPersisterTest` (`@JdbcTest`): types alone, ids alone, both; several ids OR'd; an id
  matching the command id and nothing in the payload; count and page agree.
- `EventLogControllerTest` (`@WebMvcTest`, `@Tag("spring")`): the page renders with a filter in the
  URL, every registered type is a `<wa-option>` and the chosen ones carry `selected`, the paging
  links carry the filter, an unknown type name is dropped, the `<mark>` is in the body and the
  escaped-then-marked order holds for a payload containing `<`. The head carries exactly the two
  kit URLs from D8 (whole `href`/`src` attributes, per the precise-assertion rule) and no
  `native.css`, `utilities.css` or loader.
- **No `js`-tier test for the combobox itself**: that tier is for scripts we write, and this one is
  the library's. What has to be seen on the running app, once, before the first commit: that a
  multiple pick submits `type=…&type=…`, that the theme stylesheet does not restyle the rest of the
  page, and that the bundle is fetched from where D8 says and nowhere else (network tab on
  `/calendar` as anonymous: no request to webawesome).
- Each new test mutation-verified before commit (drop the OR between ids, drop the filter from the
  Next link, swap escape and mark) per the standing practice.

## Not in scope

- Filtering by date or sequence range. Not asked for; the URL shape leaves room for it.
- A dedicated "what family were told" page. This filter answers the question; a read model built
  from `FamilyNotified` alone would be the next step if the answer needs to be readable by someone
  who does not read JSON.
- Any change to the command log's filter.
