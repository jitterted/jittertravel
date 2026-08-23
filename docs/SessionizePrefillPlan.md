# Sessionize Prefill Plan — paste a URL, fill in what we can, correct the rest

> **Status: DESIGNED, nothing built.** Designed 2026-08-22 from Ted's question ("how hard would it
> be?"), against the live `https://sessionize.com/jfokus-2027/` page and its `.ics`, both fetched
> and read on that date.
>
> **Slice 0 is no longer an assumption — it SHIPPED 2026-08-22**, before any of this, as slice 4b of
> `ConferenceSubmissionTrackingPlan.md`. `/plan-conference` now carries **Conference Page**,
> **Closes On** and **Submit At**; one submit produces `PlanConference` then `OpenCfp`; the deadline
> takes the zone the plan resolved; and `OPEN_SPACE` + a CFP is a field error. Two things there
> differ from what this doc specified, and both matter to the widget:
>
> - The refusals happen **before the first command**, so a rejected submit writes nothing at all —
>   the "not atomic, but recoverable" caveat in Slice 0 below now applies only to a failure the form
>   cannot foresee.
> - There is a **third field**: `cfpSubmissionUrl`, on `CfpOpened`. The pasted Sessionize URL is
>   itself a defensible value for it, which the prefill should fill in rather than discard.
>
> **No open questions remain.** `infoUrl` shipped on `ConferencePlanned` in the same change, so the
> scraped website URL has a field to land in; and the button reads **"Fill from Sessionize"** (both
> Ted, 2026-08-22). This plan is ready to implement as written.
>
> **Decided 2026-08-22 (Ted), two things that set the whole posture:**
>
> - **Best-effort prefill, not a perfect import.** Fill in as much as can be found; Ted corrects what
>   is wrong. **Fill beats blank** wherever there is a defensible value, because he is looking at the
>   form either way and fixing a field is faster than typing one. This reversed an earlier, more
>   cautious draft that left anything uncertain empty. It also settles Slice 2 as **in scope**, not
>   optional.
> - **Regex, no new dependency.** Slice 2 parses the page with the JDK's own regex rather than adding
>   jsoup. **Nothing in this plan adds a dependency to the project.**
>
> The doc was called `SessionizeCfpImportPlan.md` until the first of those decisions: it is not an
> import, and it is not only about the CFP.
>
> Companion to `ConferenceSubmissionTrackingPlan.md`, which owns `CfpOpened` and everything
> downstream of it. This doc owns only *how the values get into the form*.

## The point

Ted enters a conference by copying a name, three dates, a venue and a city out of a browser tab and
retyping them. For any conference that runs its CFP on Sessionize — which is most of them — every one
of those values is already on one page, and the CFP deadline is available as a machine-readable
instant. Paste the URL, press a button, correct what's wrong, submit.

**It is a prefill, so it is allowed to be wrong.** Nothing is written until Ted submits a form he can
see and edit, which keeps the whole feature outside the event store and makes a bad scrape a visible
annoyance rather than a bad event. That is the licence to guess.

**What that licence does and does not cover.** It covers *uncertain* values — a start time the page
doesn't state, a date read from markup that might get restyled — because a wrong one is sitting in a
field Ted is already reading. It does **not** cover values that are wrong in a way he would not
notice, and there is exactly one class of those: a value that looks plausible and is silently
mangled. `Devoxx &amp; Friends` is caught at a glance; a date parsed into the wrong month is not.
So the parse still owes correctness on **what it claims to have found** — see "Parsing the page" —
while owing nothing on **how much** it finds.

**The two things it must never do**, both unchanged by best-effort:

- **Invent a value that is not on the page.** A guessed street address is not a prefill, it is
  fiction. Absent stays absent.
- **Throw.** Anything unreadable is a blank field, never an error page — and never a blank *form*,
  because one unreadable field must not cost the other nine.

## Why this is cheaper than it looks: the precedent is already in the tree

The "paste a thing, press a button, fill the fields" widget **already exists** in this codebase, end
to end, and this feature is the same shape with a different source. Copy it rather than invent:

| Piece | Address version (exists) | Sessionize version (to build) |
|---|---|---|
| External call | `infrastructure/AddressParseService` — `RestClient`, fixed `baseUrl`, descriptive `User-Agent`, returns `Optional`, **never throws** | `infrastructure/SessionizePrefillService` |
| Endpoint | `web/AddressParseController` — `GET /api/parse-address?q=`, 200 or 422 | `GET /api/sessionize-prefill?url=` |
| Widget | `templates/fragments/address-paste.html` — markup + inline JS together in one fragment | `templates/fragments/sessionize-prefill.html` |
| Drift guard | `AddressPasteFragmentConventionTest` (plain file reading, runs in the default build) | `SessionizePrefillFragmentConventionTest` |
| Behavior | `AddressParseJsTest` (`js` tier, Playwright, route-intercepts the endpoint) | `SessionizePrefillJsTest` |
| Security | `/api/parse-address` is `hasRole("OWNER")` in `SecurityConfig`; `ApiAccessDeniedSecurityTest` covers 403-not-302 for `/api/**` | one more line, one more case |

**Consequences of that precedent, all of them good:**

- **No new dependency for the HTTP call.** `RestClient.Builder` is already a bean
  (`EventSourcingConfig:164`) and already has two users (`AddressParseService`, `AeroDataBoxClient`).
- **The GET-not-POST decision is already made and already reasoned**: a read-only lookup stays out of
  CSRF's scope so the fetch needn't thread a token. Copy the comment's reasoning, not just the code.
- **The failure vocabulary is already established**: the service swallows everything into
  `Optional.empty()`, the controller turns that into 422, and the widget distinguishes *"the endpoint
  is missing"* (404/405 — a wiring problem, say so) from *"we couldn't read that page"*. Reuse those
  three messages' shapes; they were learned from a real bug.

## What Sessionize actually exposes

Verified 2026-08-22 against `jfokus-2027`.

### The deadline: there is an `.ics`, so this half needs no scraping at all

Every CFP page links `https://sessionize.com/add-to-calendar/cfs/<slug>`. It returns a one-VEVENT
calendar:

```
BEGIN:VEVENT
DESCRIPTION:This is a reminder to submit a session to Jfokus 2027.\nSet th
 e alarm/notification to ensure you don't miss it!\nSessions can be submit
 ted at https://sessionize.com/jfokus-2027/
DTSTART:20261001T063000Z
SUMMARY:Jfokus 2027: deadline to submit a session
LOCATION:https://sessionize.com/jfokus-2027
END:VEVENT
```

`DTSTART` is **the exact deadline as a UTC instant**. That is strictly better than what the form does
today, where Ted reads a wall-clock off the page and the boundary pairs it with the venue zone: here
the instant is unambiguous and the conversion is exact in whichever direction it is done.

Note the folded `DESCRIPTION` — RFC 5545 continuation lines begin with a space. The reader must
unfold before it reads anything, or it will one day read a folded `DTSTART` as a truncated one.

### Everything else: HTML, and no better source exists

Checked and **absent**: JSON-LD, microdata, RDFa. The [Sessionize API](https://sessionize.com/playbook/api)
is per-event endpoints created by the *organizer* with a secret id, serving sessions/speakers/rooms
— not CFP metadata, not keyed by slug, and not something Ted-as-speaker can obtain. So the page's
HTML is the only source, and it is a scrape.

| Form field | Where it is | Quality |
|---|---|---|
| Name | `og:title` = `"Jfokus 2027: Call for Speakers"` | good — strip the `": Call for Speakers"` suffix |
| Start / End date | `<h2 class="no-margins">8 Feb 2027</h2>`, found via the `event starts` / `event ends` label above it | **date only — no time of day** |
| Venue name | first `<span class="block">` = `"Stockholm Waterfront Congress Centre"` | good |
| City, Country | second `<span class="block">` = `"Stockholm, Sweden"` | good — and `"sweden"` resolves in `LocationZoneResolver` (`:235`) |
| Street, Postal code | — | **not on the page** |
| Website → `infoUrl` | the `navy-link` anchor = `https://www.jfokus.se/` | good — and the field now exists (shipped 2026-08-22) |
| `cfpSubmissionUrl` | **the pasted URL itself** | exact, and free: no fetch, no parse |
| Format | — | **not derivable — do not guess** (see below) |

The anchors are Bootstrap class names and English label text. Sessionize can change them without
notice, and when they do the prefill must degrade *visibly and partially* — fill what it recognized,
leave the rest blank, never invent. That is the same lenient, field-by-field posture
`AddressParseService.parseNominatimResponse` already takes with Nominatim's JSON.

**No LLM extraction.** Non-negotiable project rule, and it would be the wrong tool anyway: the
failure mode of a hallucinated venue is worse than a blank field.

## Design

### Slice 0 — prerequisite (assumed, not designed here): the CFP deadline on the plan form

Ted's framing is that `/plan-conference` grows a place for CFP information so it is all one page.
This plan assumes that and does not design it, but the prefill's shape depends on four facts about it,
so they are recorded here:

1. **Two commands, one submit.** `PlanConferenceCommand` and `OpenCfpCommand` are separate commands
   producing separate events. Both must go through `CommandExecutor` (CLAUDE.md), which means **two
   command rows and two commandIds**, both captured at the boundary like every other
   nondeterministic input.
2. **Order is forced.** `OpenCfp` folds the stream for a live `ConferencePlanned` before it will emit,
   so the plan command must land first. Not atomic: if the second fails, the conference exists with no
   CFP. That is **recoverable** — `/conferences/{id}/cfp` still exists and is exactly the repair — so
   it is an acceptable failure, but the controller should say what happened rather than redirect
   silently (errors render on the form page).
3. **Do not resolve the zone twice.** `PlanConferenceHandler` already resolves the venue zone and
   stamps it on the command's `startDate()`. The CFP deadline takes *that* zone. Resolving from the
   address a second time could only disagree with the first — the same reasoning `OpenCfpController`
   already records for the standalone page.
4. **`OPEN_SPACE` + a deadline throws `ConferenceHasNoCfp`.** An open-space conference has no CFP to
   close. The form must not submit a deadline when that radio is selected, and the controller must map
   the exception to a **field error**, not a 500. (Today it is described as unreachable because the
   dashboard offers no such action; one form carrying both makes it reachable.)

### Slice 1 — the deadline, from the `.ics` (the valuable half)

**`infrastructure/SessionizePrefillService`**, modelled on `AddressParseService`:

- `RestClient` with `baseUrl("https://sessionize.com")` and a descriptive `User-Agent`, exactly as
  the Nominatim service does.
- **The URL is validated to a slug, and the slug is all that varies.** Accept only
  `https://sessionize.com/<slug>/` (optionally with `www.`); anything else returns
  `Optional.empty()` without a request being made. This is the SSRF gate, and a fixed `baseUrl` plus
  a `[a-z0-9-]` slug means a user-supplied string can never steer the request at all.
- `Optional<SessionizeCfp> cfp(String slug)` fetches `/add-to-calendar/cfs/<slug>` and reads it.

**Reading the `.ics`:** unfold continuation lines first (a line beginning with a space continues the
previous one), then read `DTSTART:` as `yyyyMMdd'T'HHmmss'Z'` → `Instant`, and `SUMMARY:` → the name,
minus the `": deadline to submit a session"` suffix. Roughly twenty lines.

**Keep the parse private to the service.** There is an `ICalWriter`, and the symmetry is tempting,
but nothing else reads iCal — no second user, so no abstraction. Extract only if one appears.

**How the instant reaches the form (recommended: (a)).**

- **(a) Render it into the visible `datetime-local` field.** The endpoint resolves the venue zone from
  the *scraped* city/country via `LocationZoneResolver` and returns the deadline as wall-clock in that
  zone, plus the zone's name so the widget can show *"closes 08:30, Europe/Stockholm"*. Submit then
  takes the ordinary `ZonedTimestamp.fromLocal(closesOn, venueZone)` path and round-trips to the same
  instant. **Ted can see it and change it**, and there is one code path into `OpenCfpCommand`.
- **(b) Carry the instant in a hidden field** and construct `new ZonedTimestamp(instant, zone)`
  directly. Rejected: invisible, uneditable, and a second way in.

Known edge of (a), and it is acceptable: if Ted afterwards edits the city to somewhere in a different
zone, the deadline moves with it. That is exactly what happens today when he types a wall-clock, so
it introduces nothing new. If the zone cannot be resolved, return the UTC wall-clock and have the
widget say which zone it is showing — never show a bare time whose zone is unstated.

### Slice 2 — the rest of the fields, from the page HTML

Same service, second method: `Optional<SessionizeConference> conference(String slug)`.

- **Dates arrive without times, so pick times and say so.** Fill `09:00` for the start and `17:00`
  for the end — a conference day, near enough. Ted fixes them if they matter, and a plausible wrong
  time beats an empty field he has to fill from scratch. The widget states plainly that **the times
  are guesses**; that sentence is what makes the guess safe.
- **Street and postal code stay blank.** They are not on the page, and inventing them would be
  fiction rather than prefill. Neither is needed: the zone resolves from city/country.
- **Format: leave the radio where it is.** Not out of caution — it is that the best guess and the
  form's existing default are **the same value**, `CALL_FOR_PAPERS`, so prefilling it is a no-op.
  The only case where they differ is Ted having deliberately chosen `OPEN_SPACE`, and overwriting a
  choice he just made is not prefilling. Instead, **surface the contradiction**: a Sessionize CFP
  page is proof the conference has a CFP, so if `OPEN_SPACE` is selected when a deadline is found,
  the widget says so — otherwise the form silently carries a deadline that Slice 0's submit path
  will reject with `ConferenceHasNoCfp`.
- **`infoUrl` — fill it; the field now exists.** Ted's call was "land `infoUrl` first", and it
  shipped 2026-08-22 on `ConferencePlanned` as a **public** field, so the `navy-link` anchor the
  scrape reads (`https://www.jfokus.se/`) now has somewhere to go. This was the one scraped value
  with no home; there are none left.
- **`cfpSubmissionUrl` — the free field.** `CfpOpened` gained a submission URL the same day, and for
  a Sessionize conference **it is the URL Ted just pasted**: Sessionize *is* where the talk gets
  submitted. Fill it from the widget's own input — no fetch, no parse, no failure mode. Fill the
  normalized `https://sessionize.com/<slug>/` form rather than whatever he pasted, so a URL with
  tracking parameters or a missing trailing slash lands tidy.
  **Do not confuse it with `infoUrl`**, which the same prefill also fills: `infoUrl` is the
  conference's own public site and renders to every viewer; `cfpSubmissionUrl` is on CLAUDE.md's
  **private** list, because a Sessionize link says Ted is thinking of submitting there. They are two
  different fields holding two different URLs, and the widget writes both in one action — which is
  precisely the situation where a copy-paste of the wrong field name leaks. Pin them apart in the
  `js` test.
- **Re-pasting overwrites what was found, and only what was found.** This is exactly the `set()`
  semantic the address widget already has — it writes a field only when the incoming value is
  non-empty — so a second paste refreshes the six fields the page knows about and leaves Ted's typed
  street and postal code untouched. No extra code; just don't "improve" it into clearing fields.
- **Say what happened.** Partial fills are the normal case under best-effort, so the widget reports
  it: *"Filled 6 fields from Sessionize — times are guesses; street and postal code aren't on the
  page."* Without that line, a field left blank by a restyled page is indistinguishable from one
  Sessionize never had, and Ted has no way to know which fields deserve a second look.

#### Parsing the page

**Decided (Ted, 2026-08-22): the JDK's own regex, no new dependency.** jsoup would have been one
dependency with exactly one user, and the markup here is well-labelled enough that a parser's tree
buys little. `AddressParseService` sets the precedent for the *posture* — lenient, field by field,
everything swallowed into an `Optional` — and that posture matters more than the parsing tool.

A regex HTML parse goes wrong in specific, known ways. These are the rules that stop it:

1. **One pattern per field, and every field independently optional.** A field that does not match
   yields `""`. One field's failure must never abort the prefill or shift another field's result —
   that isolation is what makes a Sessionize restyle degrade into blanks instead of into wrong
   values. This is the single most important rule here.
2. **Anchor on the label, not the position.** Find the literal `event starts`, then take the next
   `<h2 class="no-margins">…</h2>` after it — rather than "the first `h2` on the page" or "the third
   one". The labels are the semantic anchors; column order is not. Non-greedy (`.*?`) with `DOTALL`,
   and `[^>]*` wherever attributes appear, so extra or reordered attributes don't break the match.
3. **Decode HTML entities — this is the one that will bite.** `og:title` is an *attribute*, so it
   carries `&amp;`, `&quot;`, `&#39;`. A conference called **Devoxx & Friends** would otherwise be
   recorded as `Devoxx &amp; Friends` — and unlike a blank field, that lands **in an event, where it
   is permanent**. Decode at minimum `&lt; &gt; &quot; &#39; &nbsp;` plus numeric `&#NNN;`, and
   **decode `&amp;` last**, or `&amp;lt;` turns into `<`.
4. **Normalize whitespace after extracting.** The venue `<span class="block">` values are
   pretty-printed across lines; collapse runs to a single space and trim.
5. **Strip tags from a captured region rather than matching nested markup.** Capture the inner span
   of a labelled block, then remove `<[^>]+>` from it. Do not try to match structure.
6. **Never let a regex decide structure.** It pulls a labelled leaf value, nothing more. If a field
   ever genuinely needs "this element inside that one", that is the signal that the tool is wrong —
   and revisiting jsoup means **asking again**, not deciding unilaterally.
7. **Bound the input.** The page is ~35 KB today; cap the body the service will read (say 512 KB) so
   a pathological response cannot be parsed at all. Keep every pattern non-greedy and free of nested
   quantifiers, so there is no catastrophic-backtracking shape to find.

**Dates:** `DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)` — explicitly `ENGLISH`, never
the default locale, which the server's environment could change out from under it. Note the page is
inconsistent in a way this pattern already absorbs: the event dates render unpadded
(`8 Feb 2027`) while the CFP dates render padded (`01 Oct 2026`). `d` accepts both.

**Extra test cases this approach earns**, on top of the table below: a name carrying `&amp;`; a
padded and an unpadded date; each field missing *in isolation* leaving the others intact; and a page
whose markup has been restyled beyond recognition yielding a **partial fill, never an exception**.

### The endpoint and the widget

`GET /api/sessionize-prefill?url=` → 200 with a JSON record, or **422** when nothing usable came back
— mirroring `AddressParseController` exactly, including its "read-only lookup, so a GET, so no CSRF
token to thread" comment.

`templates/fragments/sessionize-prefill.html`: a URL input, a **Fill from Sessionize** button (Ted,
2026-08-22 — it prefills the whole conference and it does not import, so neither word in the original
"Import CFP Details" survived), an error div, and the inline `fetch` — markup and script in the one
file, for the reason that fragment's own comment gives (per-page copies drifted, and the drift
reached the user as a wrong error message).

One difference from the address widget worth writing down: it fills sibling inputs **by `name`**, and
the conference form's names are `venueName`, `venueCity`, `venueCountry`, not `city`/`country`. Note
also that `plan-conference.html` does **not** currently include the address-paste fragment — its
fields are `venueStreet` etc. rather than `th:field="*{street}"`, so that convention test does not
fire on it. The two widgets are independent; do not assume one implies the other.

**Security:** add `/api/sessionize-prefill` beside `/api/parse-address` in `SecurityConfig`
(`hasRole("OWNER")`) in the same change, and add a case to `ApiAccessDeniedSecurityTest`. Note that
`/api/*` routes are **not** in `AuthorizationMatrixTest.policy()` — that security test is where they
are covered, so put it in the right place rather than adding a row that doesn't belong.

## Redaction check

Required by CLAUDE.md whenever CFP data is touched, so recorded explicitly:

- CFP dates are on the private list; the new route is **OWNER-only**, like every other CFP surface.
- Nothing here reaches a calendar read model. `PublicCalendarProjector` is untouched — no new event,
  no new field on an existing event, no new `EntryDetails` component.
- Most prefilled values are *public facts about a public conference* (name, venue, city, `infoUrl`),
  but **that Ted is looking at this CFP is not**, which is why the endpoint is gated even though
  everything it returns is already on the open web.
- **The widget's own input is private data**, which is new since the first draft: the pasted
  Sessionize URL becomes `cfpSubmissionUrl`, and CLAUDE.md puts that on the private list because a
  Sessionize link is the submission pipeline in one field. Two consequences. First, the URL travels
  in a **query string** (`/api/sessionize-prefill?url=…`), so it lands in access logs in a way a POST
  body would not — acceptable, since the route is OWNER-only and the same is already true of other
  CFP surfaces, but it is a deliberate acceptance rather than an oversight. Second, and more
  important: the widget writes **`infoUrl` (public) and `cfpSubmissionUrl` (private) in the same
  action**. That is exactly the shape a leak takes — one wrong field name in the `set()` calls puts a
  Sessionize link on the public calendar. The `js` test must pin each to its own field by name.

## Tests

| Tier | Test | What it pins |
|---|---|---|
| unit | `SessionizePrefillServiceTest` | the folded-`DESCRIPTION` `.ics` parses; a missing `DTSTART` yields empty; a non-Sessionize URL is refused **without a request**; garbage HTML yields empty, not an exception |
| unit | HTML extraction cases | each field found; each field *absent* leaves a blank rather than a wrong value; **a page missing one field still returns the other five** — the best-effort invariant, and the one most worth mutation-verifying |
| `js` | partial-fill reporting | the "filled N fields" line names what was found, so a blank from a restyled page is distinguishable from a blank Sessionize never had |
| `@WebMvcTest` | `SessionizePrefillControllerTest` | 200 body shape, 422 on empty |
| security | `ApiAccessDeniedSecurityTest` (new case) | FAMILY/anonymous get **403, not a 302** |
| convention | `SessionizePrefillFragmentConventionTest` | only the fragment talks to the endpoint; the plan form includes the fragment; the fetch shape matches the controller's mapping |
| `js` | `SessionizePrefillJsTest extends JsBehaviorTest` | fields fill; 404/405 reports a wiring problem; 422 reports an unreadable page; a network failure reports itself |
| slice (Slice 0) | one submit → `ConferencePlanned` **then** `CfpOpened`; `OPEN_SPACE` + deadline → field error, not 500 |

Fixtures are the real fetched `.ics` and page, saved under `src/test/resources/` — well over 30 lines,
so files rather than inline text blocks. Mutation-verify every assertion.

## What this deliberately does not do

- **No re-checking of moved deadlines.** Organizers extend CFPs routinely. Re-pasting the URL or
  editing by hand is the answer; `OpenCfp` already allows recording twice and takes the last one.
- **No session or speaker fetching.** Different problem, different data, and it would need the
  organizer's API key.
- **No other CFP platform.** Papercall, Pretalx and Konfhub all differ. One source, done properly.
- **No accuracy promise.** Worth stating as a non-goal rather than a shortcoming: a prefill that is
  right eight fields out of nine has done its job, and hardening the ninth is not what this feature
  is for.

## Effort

- **Slice 1 (deadline via `.ics`): about half a session.** One service, one endpoint, one fragment,
  the tests above. No scraping, no dependency, and the value it delivers — an exact deadline — is the
  half that never rots.
- **Slice 2 (page fields): about a session**, plus permanent low-grade maintenance, because it breaks
  whenever Sessionize restyles. **Now in scope** rather than optional (Ted, 2026-08-22); the
  maintenance is the accepted cost of it, and best-effort is what makes that cost small — a restyle
  degrades the prefill instead of breaking the page.
- **Slice 0: already built** (2026-08-22), so it costs this plan nothing. Same for `infoUrl`.

**Ship Slice 1 first, on its own.** The deadline is the exact, durable half — and it now arrives
alongside `cfpSubmissionUrl`, which is free, so Slice 1 fills three fields (name, deadline,
submission URL) before a single line of HTML gets parsed. Slice 2 is the one that can rot, and
holding it back one commit keeps the two kinds of failure separately diagnosable.

## Decided

All 2026-08-22, with Ted.

- **Best-effort prefill, not a perfect import.** Fill as much as can be found; he corrects the rest.
  Fill beats blank wherever there is a defensible value. This settles **Slice 2 as in scope**, and
  settles the scraped start/end times as **`09:00` / `17:00`, labelled as guesses**.
- **Regex, not jsoup.** Slice 2 parses with the JDK's own regex; nothing in this plan adds a
  dependency. The rules that keep that honest are in "Parsing the page" — entity decoding and
  per-field isolation especially. Reopening it (because a field needs real structure) means asking,
  not deciding.
- **`infoUrl` on conferences: land it first.** Done the same day, publicly, on `ConferencePlanned` —
  so the scraped website URL has a field to land in, and every scraped value now has a home.
- **The button reads "Fill from Sessionize".** Neither word of the original "Import CFP Details"
  survived contact with what the feature became: it prefills the whole conference, not just the CFP,
  and it does not import. The past-tense-label rule does not apply either way — this records nothing.
- **The doc was renamed** from `SessionizeCfpImportPlan.md`, for the same two reasons as the button.

## Open questions

**None.** Everything this plan needed a decision on has one, and both prerequisites
(Slice 0, `infoUrl`) are built. It is ready to implement as written: **Slice 1, then Slice 2.**

Decisions that would reopen it, so they are worth naming: a field that needs real HTML structure
rather than a labelled leaf value (revisit jsoup — by **asking**, not deciding), or Sessionize
publishing structured data or a public CFP endpoint, which would retire the scrape entirely.
