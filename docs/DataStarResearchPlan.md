# Datastar Research and Plan

**Status: exploration — nothing built. Do not implement without discussion with Ted.**
Written 2026-08-20. Question: replace the inline JavaScript UI behaviors (dropdown prefill,
toggles, fetch-and-fill) with [Datastar](https://data-star.dev/)?

## What Datastar is

- A hypermedia framework. One script tag, 11.75 KiB. Version 1.0.2 (1.0 shipped 2025). Core is MIT.
- Reactivity comes from `data-*` attributes: `data-signals` declares state, `data-bind` two-way
  binds inputs, `data-on:<event>` runs an expression, `data-show`/`data-text`/`data-attr`/`data-class`
  render from signals, `data-computed` derives values.
- Two modes:
  1. **Client-only.** Signals and expressions run in the browser. No server call.
  2. **Backend-driven.** `@get()`/`@post()` sends all signals to the server. The server replies
     with plain HTML or with SSE events (`datastar-patch-elements` morphs DOM,
     `datastar-patch-signals` merges state). Logic lives in Java, not in JS.
- Official Java SDK exists: `dev.data-star:datastar-java-sdk` (Maven Central). It only helps
  format SSE responses. Client-only use needs no SDK.
- **Pro tier is paid** ($349 solo, lifetime): `data-persist` (localStorage), `data-replace-url`,
  `@clipboard()`, `@intl()`, `data-animate`, and more. The free core does not have these.
- Expressions compile with `Function()`. A strict CSP would need `unsafe-eval`. We set no CSP
  today, but this closes that door.

## Current JavaScript inventory

All behavior is inline `<script>`. No `.js` files exist. ~275 lines total. 27 Playwright tests
in the `js` tier cover five of the eight behaviors.

| # | Behavior | Where | Type | Tests |
|---|---|---|---|---|
| 1 | Ground-transfer endpoint prefill: dropdown fills date/time fields, 45-min gap nudge with 23:59 clamp | `plan-ground-transfer.html:218-271`, data attrs from `TransferEndpointOption` | client, data from `<option data-*>` | 7 |
| 2 | Address paste-and-parse: button GETs `/api/parse-address`, fills 6 inputs, 3 error branches | `fragments/address-paste.html:21-59`, used by 5 form pages | client + fetch (the only `fetch()`) | 4 |
| 3 | Calendar past-week collapse/expand + "toggle all" with relabel | `CalendarRenderer.TOGGLE_SCRIPT` | client, class toggle | 5 |
| 4 | Day-menu popup: no stacking, outside-click close, Escape close | `CalendarRenderer.DAY_MENU_SCRIPT` on native `<details>` | client, dismiss state | 3 |
| 5 | Browser-zone time rewrite + Event time/My time toggle: per-element `Intl` reformat, localStorage persist, `history.replaceState` | `BrowserZoneScript` (~86 lines) | client, data from `<time>` attrs | 8 |
| 6 | Login browser-zone capture into hidden field | `login.html:106-117` | client, one `Intl` call on load | 0 (string-presence only) |
| 7 | Admin restore: `FileReader` file → textarea | `admin-restore.html:86-96` | client, local file read | 0 |
| 8 | Admin calendar-feed copy buttons (`navigator.clipboard`) | `admin-calendar-feed.html:201-213` | client | 0 |

## Fit analysis, per behavior

| # | Datastar shape | Verdict |
|---|---|---|
| 1 | Client-only: `data-bind` the select, `data-on:change` expression reads `selectedOptions[0].dataset`, writes signals bound to the inputs. The clamp/nudge logic becomes an attribute string — logic in markup, still Playwright-tested. Backend-driven: `data-on:change="@get(...)"`, server patches the inputs; logic moves to Java and JUnit, but each change costs a round trip and a new OWNER endpoint. | **Fits.** Backend-driven is the honest version. Best pilot. |
| 2 | Replace `fetch()` with `@get()`; server returns patch-elements for the six inputs and for the error line. In-flight button state via `data-indicator` (free core). | **Fits best.** Already server-bound. Deletes the largest hand-rolled script after #5. |
| 3 | One `data-signals` set + `data-class` per week row; toggle-all writes one signal; label via `data-text`. | **Fits.** Modest gain (~30 lines). |
| 4 | `data-on:click__outside` and `data-on:keydown` exist in core, but coordinating N native `<details>` through expressions is awkward. | **Marginal.** Current script is smaller than the replacement. |
| 5 | Needs `@intl()` (Pro), `data-persist` (Pro), `data-replace-url` (Pro). Free core cannot express it. | **Does not fit.** Keep as-is, or pay $349. |
| 6 | `data-on:load` expression can set the hidden field. | **No gain.** Six lines either way. |
| 7 | `data-bind` on `<input type=file>` yields base64 signals — wrong shape for a text-restore textarea. | **Does not fit.** |
| 8 | `@clipboard()` is Pro. | **Does not fit** without Pro. |

## Gains

- Deletes ~150 lines of bespoke JS (behaviors 1, 2, 3): fewer hand-rolled listeners.
- One declarative idiom for future form interactivity, instead of a new inline script each time.
- Backend-driven variants move UI logic into Java. JUnit tests replace some Playwright tests.
- SSE machinery, once in, gives server-push (live banners, progress) with no new library.

## Costs

- **Dependencies (hard gate — this doc is the ask):** one vendored JS file, plus the Java SDK
  if any backend-driven use ships. Two new dependencies. Ted must approve first.
- **The free tier covers only half the inventory.** #5 (the largest script), #7, #8 stay
  hand-written. Two idioms coexist permanently unless Ted buys Pro.
- **Tests move, they do not disappear.** Client-only Datastar attributes still need the
  Playwright `js` tier. 27 green tests get rewritten for equal coverage.
- **New endpoints for backend-driven mode** must enter `SecurityConfig` and
  `AuthorizationMatrixTest` (deny-by-default rule). Each one is redaction-adjacent review load.
- **Latency:** backend-driven prefill turns an instant local fill into a network round trip.
- **`Function()` eval** blocks any future strict CSP.
- **Framework risk:** 1.0 is one year old; the Pro split shows monetization pressure on the
  attribute set.
- Learning curve, and attribute-string logic is harder to read and diff than a named JS function.

## Recommendation

Do not adopt now. The current JS is small, shipped, and tested. Datastar cleanly replaces three
of eight behaviors, cannot touch the largest one without a paid tier, and retires no test
machinery. Net effect today: one more dependency, two idioms, equal test load.

Revisit when any trigger fires:

1. A slice needs server-push (live updates on an open page). SSE is where Datastar earns its keep.
2. A form needs cascading interactivity clearly beyond the prefill pattern.
3. The inline-script count roughly doubles (~15 behaviors) and drift between them shows.

## Pilot plan, if adopted later

1. Vendor `datastar.js` into `static/` (no CDN). Emit the script tag from the shared page head.
2. Pilot on **address paste-and-parse** (#2): already server-bound, shared by five forms, has
   4 Playwright tests to prove equivalence. Add the Java SDK only if SSE patching beats a plain
   HTML fragment response.
3. Second slice: **ground-transfer prefill** (#1), backend-driven; move the 45-min gap logic
   into a JUnit-tested Java class.
4. Then #3 (calendar toggle) client-only.
5. Leave #4–#8 as inline scripts. Record that as deliberate.
6. Decision point after step 2: keep going, or stop and revert the pilot.

## Sources

- [Datastar site](https://data-star.dev/) and [docs](https://data-star.dev/docs.md)
- [Datastar releases](https://github.com/starfederation/datastar/releases)
- [Datastar Pro reference](https://data-star.dev/reference/datastar_pro)
- [Java SDK](https://github.com/starfederation/datastar-java) /
  [Maven](https://mvnrepository.com/artifact/dev.data-star/datastar-java-sdk)
