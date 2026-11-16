# The Fact You Didn't Write Down

*Talk notes, from a JitterTravel production bug found 2026-11-16. Audience: event-sourcing
practitioners.*

> **About the data.** Every event below is from the production backup of 2026-11-15, with
> sequence numbers, ids and timestamps as stored. Hotel names and streets are replaced: the two
> Berlin hotels are **Motel One Berlin-Alexanderplatz** (hotel A, the one canceled) and **Leonardo
> Hotel Berlin Mitte** (hotel B, its replacement). The other hotels are generic, and private event
> titles are generalized. The structure, the timings and the order are real.

## TL;DR

A command resolved a reference into values, stored the values, and threw the reference away. The
snapshot was the right call. Dropping the identity was not. Once the hotel was canceled, nothing
in the system could connect it to the transfer that still drove there. Every event written before
the fix is missing that fact for good.

A second fact was missing too: that one booking *replaced* another. The user recorded it anyway,
as prose in a free-text `reason` field, because the model had nowhere else to put it.

## The story

I'm in Potsdam on Nov 20. I tour a palace until 16:00, then take a ground transfer to my Berlin
hotel, landing at 17:00.

| seq | when (UTC)          | event                    | what happened                                      |
|-----|---------------------|--------------------------|----------------------------------------------------|
| 226 | Nov 9, 09:57:33     | `HotelBooked`            | Hotel A, Berlin, Nov 20–22                         |
| 240 | Nov 14, 18:25:52    | `PrivateEventPlanned`    | Palace tour, Potsdam, Nov 20 12:10–16:00           |
| 242 | Nov 14, 18:27:13    | `GroundTransferPlanned`  | Palace → **Hotel A**, Nov 20 16:00–17:00           |
| 243 | Nov 14, 18:27:44    | `HotelChanged`           | Hotel A check-in moved to 18:00                    |
| 245 | Nov 14, 18:34:34    | `HotelChanged`           | Hotel A extended to Nov 23                         |
| 251 | Nov 15, 14:16:04    | `HotelBookingCancelled`  | Hotel A canceled. Reason: *"Booked Leonardo Mitte"* |
| 252 | Nov 15, 14:16:44    | `HotelBooked`            | Hotel B, Berlin, Nov 20–23 — **40 seconds later**  |

The itinerary for Nov 20 still says: *Ground transfer — Palace → Motel One Berlin-Alexanderplatz.*
A hotel I no longer have.

## The events

The transfer, as stored. Note what the destination carries: a name and an address. No booking id.

```json
{
  "sequence": 242,
  "type": "GroundTransferPlanned",
  "payload": {
    "groundTransferId": { "id": "b951dc59-…" },
    "originName": "Palace tour",
    "origin": { "city": "Potsdam", "street": "…", "locationForMatching": "Potsdam" },
    "destinationName": "Motel One Berlin-Alexanderplatz",
    "destination": { "city": "Berlin", "street": "…", "locationForMatching": "Berlin" },
    "departsAt": { "utc": "2026-11-20T15:00:00Z", "zone": "Europe/Berlin" },
    "arrivesAt": { "utc": "2026-11-20T16:00:00Z", "zone": "Europe/Berlin" },
    "mode": ""
  }
}
```

Hotel A as it last stood, the cancellation, and hotel B:

```json
{ "sequence": 245, "type": "HotelChanged",
  "payload": { "hotelBookingId": { "id": "57b82858-…" },
               "hotelName": "Motel One Berlin-Alexanderplatz",
               "address": { "city": "Berlin", "street": "…" },
               "checkIn":  { "utc": "2026-11-20T17:00:00Z", "zone": "Europe/Berlin" },
               "checkOut": { "utc": "2026-11-23T10:00:00Z", "zone": "Europe/Berlin" } } }

{ "sequence": 251, "type": "HotelBookingCancelled",
  "payload": { "hotelBookingId": { "id": "57b82858-…" },
               "reason": "Booked Leonardo Mitte" } }

{ "sequence": 252, "type": "HotelBooked",
  "payload": { "hotelBookingId": { "id": "ace43c54-…" },
               "hotelName": "Leonardo Hotel Berlin Mitte",
               "address": { "city": "Berlin", "street": "…" },
               "checkIn":  { "utc": "2026-11-20T14:00:00Z", "zone": "Europe/Berlin" },
               "checkOut": { "utc": "2026-11-23T10:00:00Z", "zone": "Europe/Berlin" } } }
```

## How the id got lost

The plan-transfer form offers hotels as `hotel:<bookingId>`. The command handler resolves that
token, copies the booking's name and address into the event, and moves on. The id was in hand and
was never written.

That was a deliberate design decision, and half of it was right:

> The address is **snapshotted into the event**, never referenced live: changing the hotel later
> must not silently rewrite a past transfer. — `GroundTransferPlan.md`

The snapshot answers **"what did I plan?"** Only a reference answers **"is it still true?"** The
design needed both and stored one. The same plan doc lists "any link between a transfer and the
hotel it serves" as deferred, which is the moment the fact was lost: not in code, in a decision
that a read model would never need it.

## Why nothing noticed

- **The itinerary** renders the snapshot. It is accurate about the past and wrong about the present.
- **The schedule-problems report** compares *cities*. The transfer lands in Berlin, and hotel B is
  in Berlin, so there is no gap to report.
- **The cancel-hotel page** could not say "1 transfer goes to this hotel", because no read model
  knows that.

The only signal was me reading my own itinerary and recognizing a hotel name.

## Two missing facts

1. **Transfer → booking.** Which booking an endpoint was resolved from.
2. **Booking → booking.** That B *replaced* A. The UI has Book and Cancel; there is no Replace.
   So a replace was recorded as two unrelated events, in either order.

The second one was not truly lost. Look at the cancellation reasons in the log:

- `"Booked Leonardo Mitte"`
- `"Booked closer hotel Potsdam hotel 2"`
- `"Booked Hamburg hotel 2"`
- `"Replaced with better hotel"`

Four of seven cancellations say what replaced them, in prose. **A free-text field is where the
unmodelled facts in your domain go.** Read yours.

## Recovering it: all the data is there

Even without the id, the log is enough to trace the transfer forward:

1. **Transfer → booking.** Transfer 242's destination `(name, street)` equals booking `57b82858`'s
   `(name, street)` in one of its snapshots (`HotelBooked` 226 or any `HotelChanged`). Match on
   any version, not just the latest: the booking could have been edited after the transfer was
   planned.
2. **Booking → canceled.** `HotelBookingCancelled` 251 names `57b82858`.
3. **Canceled → replacement.** A booking that is still live, in the same city
   (`locationForMatching`, case-insensitive), with a check-in on the same **local** date in the
   hotel's zone. That finds exactly one: `ace43c54`, hotel B.

Three details the production data forced:

- **Either order.** Berlin and Hamburg canceled first, then booked. Potsdam booked first, then
  canceled. A rule that assumes "cancel, then the next booking" misses half.
- **Chains.** Potsdam went hotel 1 → hotel 2 → hotel 3 in twenty minutes. Requiring the successor
  to be *still live* collapses the chain to where I actually sleep, which is the right answer for
  "where does this transfer go now". It is the wrong answer for "what replaced what".
- **Local date, not UTC date.** Check-ins are stored as instants with a zone. A late check-in can
  be a different UTC date from its local one.

## Three processors, compared

All three start from the same trace. They differ in what they *do* with it, and that decides the
risk.

### 1. A read model that reports

A projector folds bookings, cancellations and transfers, runs the trace, and emits a problem:

> **Transfer goes to a canceled hotel.** Palace → Motel One Berlin-Alexanderplatz, Nov 20 16:00.
> Leonardo Hotel Berlin Mitte is booked in Berlin that night. *[Cancel transfer]*
> *[Plan a new one]*

- **Writes nothing.** A wrong inference is a wrong *suggestion*, and the human decides.
- **Rebuilt on every replay.** Improve the rule tomorrow and it applies to every event already in
  the log. No migration.
- **Built from events alone.** It never consults another read model, so it cannot inherit a bug
  from one.
- **Weakness:** the inference runs forever. The heuristic is the permanent source of the fact.

A sketch, not shipped code. It is written against JitterTravel's real event types, and it checks
only the destination end; the origin is the same with the origin fields.

```java
class TransfersToCanceledHotelsProjector implements EventStreamConsumer {

    record HotelVersion(String name, String street, Place place, LocalDate checkInDate) {}
    record StaleTransfer(GroundTransferPlanned transfer, HotelBookingId gone,
                         Optional<HotelBookingId> probablyReplacedBy) {}

    private final Map<HotelBookingId, List<HotelVersion>> versions = new HashMap<>();
    private final Set<HotelBookingId> canceled = new HashSet<>();
    private final Map<GroundTransferId, GroundTransferPlanned> transfers = new HashMap<>();

    @Override
    public void handle(Stream<StoredEvent> events) {
        events.forEach(stored -> {
            switch (stored.payload()) {
                case HotelBooked e -> remember(e.hotelBookingId(), e.hotelName(), e.address(), e.checkIn());
                case HotelChanged e -> remember(e.hotelBookingId(), e.hotelName(), e.address(), e.checkIn());
                case HotelBookingCancelled e -> canceled.add(e.hotelBookingId());
                case GroundTransferPlanned e -> transfers.put(e.groundTransferId(), e);
                case GroundTransferCancelled e -> transfers.remove(e.groundTransferId());
                default -> {}
            }
        });
    }

    List<StaleTransfer> staleTransfers() {
        return transfers.values().stream()
                .flatMap(transfer -> bookingAtDestination(transfer)
                        .filter(canceled::contains)
                        .map(gone -> new StaleTransfer(transfer, gone, replacementFor(gone)))
                        .stream())
                .toList();
    }

    // Any version matches: the booking may have been edited after the transfer was planned.
    private Optional<HotelBookingId> bookingAtDestination(GroundTransferPlanned transfer) {
        return versions.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(version ->
                        version.name().equals(transfer.destinationName())
                        && version.street().equals(transfer.destination().street())))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    // Still live, same city, check-in on the same local date. Either order works, because this
    // runs after the fold; a chain of replacements ends at the live one. Exactly one, or none:
    // with two candidates it says nothing rather than guess.
    private Optional<HotelBookingId> replacementFor(HotelBookingId gone) {
        HotelVersion was = latest(gone);
        List<HotelBookingId> candidates = versions.keySet().stream()
                .filter(id -> !canceled.contains(id))
                .filter(id -> latest(id).place().matches(was.place())
                              && latest(id).checkInDate().equals(was.checkInDate()))
                .toList();
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private void remember(HotelBookingId id, String name, Address address, ZonedTimestamp checkIn) {
        versions.computeIfAbsent(id, _ -> new ArrayList<>()).add(new HotelVersion(
                name, address.street(), Place.of(address), checkIn.localDateTime().toLocalDate()));
    }

    private HotelVersion latest(HotelBookingId id) {
        return versions.get(id).getLast();
    }
}
```

What the slide should point at: **there is no command, no write and no `CommandExecutor`.** It
reads five event types and answers a question. Delete it and the log is exactly as it was.

### 2. A "Translator" that publishes `HotelReplaced`

On the second half of a cancel/book pair, emit `HotelReplaced(from: A, to: B)` so every downstream
consumer gets the fact as an event.

**It isn't a Translator.** A Translator is stateless: one event in, a different event out. This
rule needs a fold of every live booking, in either order, across time. Something stateful that
writes is an **Actuator**: it issues a command (`RecordHotelReplacement`), and the decider accepts
or refuses it. Getting the name right is the point. It forces the questions a "translator" hides:

- **The inferred fact becomes permanent.** If the rule is wrong, the fix is a compensating event,
  and every consumer has already acted on the first one.
- **Replay must not re-fire it.** An Actuator records what it has done (JitterTravel's family email
  sender records `FamilyNotified` for exactly this), or it acts again on every boot.
- **The person never said it.** It's a guess, stored as if it were a recorded fact. "Replaced with
  better hotel" was the human saying it, and it's the only version of this fact I would trust.

### 3. An Actuator that fixes the transfer

On a detected replacement, issue a command to re-point the transfer at hotel B.

- **There is no command to issue.** `GroundTransferChanged` does not exist. Changing a transfer is
  cancel-then-plan, by hand.
- **It makes the user's decision for them.** A different hotel is a different street and maybe a
  different ride. The Mannheim case below shows a stay replaced by two hotels in two cities. No
  automatic re-point can be right there.
- **It's the one most likely to fix the problem silently and wrongly.** A wrong fix also removes
  the evidence that anything was wrong.

### The comparison

|                               | Report (read model) | Record (`HotelReplaced`) | Fix (re-point transfer) |
|-------------------------------|---------------------|--------------------------|-------------------------|
| Writes to the log             | no                  | yes, an inferred fact    | yes, a decision          |
| A wrong inference costs       | a wrong suggestion  | a compensating event     | a wrong trip             |
| Rule improvements apply to    | all history         | future events only       | future events only       |
| Safe on replay by default     | yes                 | no                       | no                       |
| Who decides                   | the human           | the heuristic            | the heuristic            |

**What I'd ship:** the report now, because it is safe and covers the existing log. Then fix the
source. Store the booking id on each transfer endpoint going forward. And give the UI a real
**Replace** action, so the *human* records `HotelReplaced` and nobody has to infer it.

## Validating the rule against production

Before trusting "same city, same check-in date", I ran it over every hotel cancellation in the log.

| seq | canceled                        | reason as typed                     | rule finds              | what really happened                               |
|-----|---------------------------------|-------------------------------------|-------------------------|----------------------------------------------------|
| 201 | Hamburg hotel 1, Nov 8          | "Booked Hamburg hotel 2"            | Hamburg hotel 2 ✓       | Replace, cancel then book (40 s)                   |
| 228 | Leonardo Mitte, Nov 21          | "Rebooked for one day later"        | nothing                 | Same hotel, check-in a day later: new date         |
| 244 | Leonardo Mitte, Nov 22          | "leaving Berlin early to Darmstadt" | nothing                 | Trip shortened; hotel A extended 13 s later        |
| 250 | Mannheim hotel 1, Nov 23–25     | "Booked different hotel"            | nothing                 | **Split:** Darmstadt hotel 23–25 + Mannheim hotel 2 25–26 |
| 251 | Motel One Alexanderplatz, Nov 20| "Booked Leonardo Mitte"             | Leonardo Mitte ✓        | **The bug in this doc**                            |
| 267 | Potsdam hotel 1, Nov 19         | "Booked closer hotel Potsdam hotel 2" | Potsdam hotel 3 ✓     | Book then cancel; hotel 2 later replaced by 3      |
| 268 | Potsdam hotel 2, Nov 19         | "Replaced with better hotel"        | Potsdam hotel 3 ✓       | Replace, cancel then book                          |

- **No false positives:** every match is where I actually slept.
- **Three misses, and all three are correct misses.** None of them is a one-for-one replacement.
  - **244:** the replacement was a `HotelChanged` on a *different* booking (extending hotel A).
  - **250:** one stay became two hotels in two cities.
  - **228:** a different night.

  "Replace" is not one relationship. It is at least four: substitute, reschedule, absorb into
  another stay, split.
- **The order varies:** cancel-then-book three times, book-then-cancel once.
- **Seven cancellations is a small sample.** It's enough to show the rule doesn't misfire. It isn't
  enough to say it never will.

## Takeaways

- **When a command resolves a reference into values, store the identity as well as the values.**
  Snapshot for history, id for "is it still true". It costs one field.
- **"Must not change later" is a rule about projections, not about what you store.** A read model
  can freeze a value and still follow a link.
- **You can rebuild any projection; you can't backfill a fact.** Every event written before the fix
  will be missing the id forever. Inference is the only way back, and it's only as good as the
  rule.
- **If users pair two events to mean one thing, the model is missing an event.** Cancel + book =
  replace. Look at your free-text fields for the evidence.
- **Name the processor honestly.** If it needs state and it writes, it's an Actuator, and it owes
  you idempotency, replay safety and a story for being wrong.
- **Prefer the processor that writes nothing** until the human has a way to say the fact
  themselves.
