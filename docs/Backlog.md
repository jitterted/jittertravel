# Backlog — index of every plan doc and open item

**This is an index, not a backlog of its own.** Each row points at the doc that owns the detail;
the "What's left" column exists only so you can tell, without opening twenty files, whether
something is still live. When work lands, update the owning doc first and this table second.

Small cleanups keep living in `Cleanup_Tasks.md` — they are summarized here but not duplicated.

Status verified against the tree on **2026-08-07** (`4efccaf`).

Legend: `open` · `partial` · `done` · `exploration` (deliberately unbuilt design record)
· `decision` (waiting on Ted, not on code)

---

## Open features

| Item | Owning doc | What's left |
|---|---|---|
| **Private social event kind** | `Cleanup_Tasks.md` | `open` — highest-value item here. Today a private dinner can only be modelled as a GATHERING, which renders in full to anonymous viewers on `/calendar`. Needs its own `EntryKind`, a redacting branch in `CalendarEntryRedactor`, and both tiers of redaction test. CLAUDE.md calls this out as a known leak. |
| **Cancel Hotel + Replace Hotel** | `HotelCancelReplacePlan.md` | `partial` — Phases 0 and 1 (`cancelBy`) shipped in `4efccaf`. Phase 2 (cancel + deadline display) and Phase 3 (replace) are unbuilt; no `HotelCancelled` or `CancelHotel` exists in the tree. |
| **`ConferenceCancelled`** | `Future_Feature_Slices.md` | `open` — also the prerequisite for any slice that retracts a booking. Gathering cancellation is the same gap (explicitly out of scope in `ChangeGatheringPlan.md`). |
| **`infoUrl` on conferences** | `Future_Feature_Slices.md` | `open` — gatherings have one; conferences don't. |
| **`mapsUrl` on conferences** | `Future_Feature_Slices.md` | `open` — auto-computed from venue + address, as hotels do. |
| **Sunday/Monday week start** | `Sunday-Monday-Week-Start-Switch.md` | `open` — not started. `CalendarViewBuilder` still hardcodes Sunday (`CalendarViewBuilder.java:55`). Contained to the `web` package. |
| **Viewer-timezone selection** | `Future_Feature_Slices.md` | `partial / needs re-reading` — this entry predates the UTC rollout. `BrowserZoneScript` and `ZoneToggle` shipped with `UtcDatetimeStoragePlan.md` phase 4, so much of what it asks for exists. Re-read it against the current behavior before treating it as work. |

## Open cleanups

Detail lives in `Cleanup_Tasks.md`; this is the roll-call.

| Item | What's left |
|---|---|
| Mockito replacement | `open` — replace with better test doubles. |
| Event-type filter on `/admin/eventlog` | `open` — the command-log filter is already done. |
| `/admin/commandlog` "Out of order" badge | `open` — only detects divergence *within* a page; `PostgresPersister.loadTimelinePage` resets `runningMaxSeq` per call (`PostgresPersister.java:288`), so the first entry of any page can never be flagged. |

### Loose follow-ups not tracked anywhere else

From the Phase 1 `cancelBy` review (bottom of `HotelCancelReplacePlan.md`):

- Cancel-by hint text in `book-hotel.html` / `change-hotel.html` is wrong — it says the deadline
  "never blocks anything", but a deadline after check-in is rejected with `InvalidCancelByDate`.
- `cancelBy(LocalDateTime, ZoneId)` is byte-identical in `BookHotelHandler` and
  `ChangeHotelHandler`; it will drift if the null-preserving zone rule changes.
- Editing check-in earlier than an existing `cancelBy` fails on a field the user never touched
  (the form prefills it). Accepted for now; the alternative is clamping rather than rejecting.

From `GeneralControllerRefactorPlan.md`:

- Stable `data-testid` attributes on the `index.html` nav groups, so the authorization tests stop
  asserting on `href` substrings and `>Admin</span>`.

From `j2html_Migration_Analysis.md`:

- The shared renderer infrastructure the migration proposed was never extracted — no
  `TemporalFormatter`, `ProblemCardRenderer`, or `EntryCardRenderer`; only `web/Page.java` exists.
  Formatting and card markup are duplicated across renderers.

## Decisions waiting on Ted

| Decision | Owning doc |
|---|---|
| **Adopt Strictland, or revert the spike?** The mapper pin (`EventJsonMapperFactory`) stays either way. Adoption is paused pending the author's feedback. If adopted: pick the first events to cover, the snapshot-file layout, and how coverage is enforced for new event types. | `Event_Serialization_Contract_Tests.md` |
| **Alpha-2 zone aliases for single-zone countries** (e.g. `GB`) — the last piece of the `LocationZoneResolver` state/province fix that unblocked the 2026-08-06 production import. | `UtcDatetimeStoragePlan.md` (improvement 2) |

## Explorations — deliberately unbuilt

These are design records. Do not implement without discussing with Ted first; several say so
explicitly in their own text.

| Doc | What it is |
|---|---|
| `CommandConsistencyEventStore.md` | Conditional/fenced append for a future multi-instance deployment. Nothing built. Unblocked by the CommandExecutor migration, but not committed to. |
| `TaggedEventStoreQueryingDesign.md` | Filtered `EventStore` queries by event type and tag, pushed into Postgres JSONB + GIN. Deferred until a second concrete caller demands it. |
| `ReMoDeL-Specification.md` | A KDL-based read model definition language. Specification only — there is no KDL code anywhere in `src/`. |
| `ReadModelKdlTestDslPlan.md` | Test-support DSL for the above. Its 8 implementation steps reference an acceptance test that does not exist yet. Blocked on ReMoDeL itself. |
| `Refactoring_Opportunities.md` | 7 projection/rendering duplication findings with a priority order. Marked "do not implement without discussion". |
| `authorization_policy_centralization.md` | Authorization "Option 2" — derive `SecurityConfig` matchers and `GeneralController` nav flags from one `NavArea` policy type. Current Option 1 (two small hand-synced places) is judged low-risk because `AuthorizationMatrixTest` catches drift. This doc is the live tracker; the duplicate checklist inside `GeneralControllerRefactorPlan.md` is not maintained. |

## Done

Kept as design records. Each has a status banner at the top of the file.

| Doc | Landed |
|---|---|
| `UtcDatetimeStoragePlan.md` | All 7 phases + review fixes. `25104b9`, 2026-08-05 |
| `UtcDatetimePlanReview.md` | Folded into the plan above; historical record, checkboxes no longer maintained |
| `GatheringConferenceUtcRolloutPlan.md` | Gathering `ddf4ba8` (2026-07-27), conference `67a47af` (2026-08-05) |
| `TrainFlightUtcRolloutPlan.md` | Trains `daa7107`, flights `d2884fb`, 2026-06-24 |
| `HomeCityPlan.md` | `26d15ac`, 2026-07-26 |
| `ChangeGatheringPlan.md` | `f28cb49`, 2026-07-25 |
| `ChangeHotelPlan.md` | `d98af11`, 2026-06-21 |
| `ChangeTrainPlan.md` | `8390262`, 2026-06-17 |
| `Remove-Local-Profile.md` | `e962915`, 2026-06-17 |
| `j2html_Migration_Analysis.md` | `b0e6f11`, 2026-06-06 — views migrated, shared infrastructure not extracted (see loose follow-ups) |
| `GeneralControllerRefactorPlan.md` | Shipped; its "open items" 1 and 2 are resolved, item 3 moved here |

## Not a task list

These describe how things are, not what to do next, and belong in no status column:
`JS-Behavior-Tests.md`, `EventSourcingRulesHeuristics.md`, `DEPLOYMENT.md`, `lodging-event-model.md`,
`ChangeFlightSlices.md`, `Features.md`, `EventModel.txt`, `EventLanesDoc.txt`,
`Tentatively-Plan-Conference-slice.txt`, `docs/slices/`.
