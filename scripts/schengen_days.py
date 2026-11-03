#!/usr/bin/env python3
"""Schengen 90/180 day count from a JitterTravel backup file.

Usage:  python3 -I scripts/schengen_days.py <backup.json> [--today YYYY-MM-DD] [--airports <StaticAirportCityResolver.java>]

Follows docs/SchengenDayCounterPlan.md as far as a throwaway can:
  tier 1  external Schengen border crossings (flights + trains) paired entry -> next exit into envelopes;
          every local day inside an envelope counts.
  tier 2  dated sources (hotels, conferences, gatherings) in a Schengen country, for days no envelope covers.
  floor   envelopes + tier-2 days of committed conferences, hotels and gatherings.
  ceiling floor + tier-2 days of live speculative (WATCHING) conferences.
An open envelope (entry with no exit) is reported, not counted past its entry day.
"""
import json
import re
import sys
from datetime import date, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

SCHENGEN = {"AT", "BE", "BG", "HR", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IS", "IT", "LV",
            "LI", "LT", "LU", "MT", "NL", "NO", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "CH"}


def local_date(ts):
    return datetime.fromisoformat(ts["utc"].replace("Z", "+00:00")).astimezone(ZoneInfo(ts["zone"])).date()


def days_between(a, b):
    return {a + timedelta(n) for n in range((b - a).days + 1)}


def load_airports(path):
    table = {}
    for code, _city, _region, country in re.findall(
            r'Map\.entry\("(\w+)",\s*new Location\("([^"]*)",\s*"([^"]*)",\s*"(\w+)"\)\)', Path(path).read_text()):
        table[code] = country
    return table


def main(argv):
    backup = Path(argv[1])
    today = date.today()
    airports_java = Path(__file__).resolve().parents[1] / "src/main/java/dev/ted/jittertravel/domain/StaticAirportCityResolver.java"
    args = argv[2:]
    while args:
        flag = args.pop(0)
        if flag == "--today":
            today = date.fromisoformat(args.pop(0))
        elif flag == "--airports":
            airports_java = Path(args.pop(0))
        else:
            sys.exit(f"unknown flag {flag}")
    airport_country = load_airports(airports_java)

    events = json.loads(backup.read_text())["events"]

    flights, trains, hotels, conferences, gatherings = {}, {}, {}, {}, {}
    for e in events:
        t, p = e["type"], e["payload"]
        if t in ("FlightBooked", "FlightChanged"):
            flights[p["flightId"]["id"]] = p
        elif t == "FlightCancelled":
            flights.pop(p["flightId"]["id"], None)
        elif t in ("TrainBooked", "TrainChanged"):
            trains[p["tripId"]["id"]] = p
        elif t == "TrainCancelled":
            trains.pop(p["tripId"]["id"], None)
        elif t in ("HotelBooked", "HotelChanged"):
            hotels[p["hotelBookingId"]["id"]] = p
        elif t == "HotelBookingCancelled":
            hotels.pop(p["hotelBookingId"]["id"], None)
        elif t == "ConferencePlanned":
            conferences[p["conferenceId"]["id"]] = dict(p, status="WATCHING", submitted=False)
        elif t == "ConferenceDatesChanged":
            conferences[p["conferenceId"]["id"]].update(startDate=p["startDate"], endDate=p["endDate"])
        elif t == "ConferenceAttendanceConfirmed":
            conferences[p["conferenceId"]["id"]]["status"] = "GOING"
        elif t in ("ConferenceAttendanceDeclined", "ConferenceCancelled"):
            conferences[p["conferenceId"]["id"]]["status"] = "OUT"
        elif t == "TalkSubmitted":
            conferences[p["conferenceId"]["id"]]["submitted"] = True
        elif t == "TalkRejected":
            c = conferences[p["conferenceId"]["id"]]
            if c.get("format") == "ACCEPTANCE_REQUIRED":
                c["status"] = "OUT"
        elif t in ("GatheringPlanned", "GatheringChanged"):
            gatherings[p["gatheringId"]["id"]] = p
        elif t == "GatheringCancelled":
            gatherings.pop(p["gatheringId"]["id"], None)

    # ---- tier 1: external crossings -> envelopes
    crossings = []  # (date, 'ENTRY'|'EXIT', label)
    unknown_airports = set()
    for f in flights.values():
        dep, arr = f["departureAirport"]["code"], f["arrivalAirport"]["code"]
        for code in (dep, arr):
            if code not in airport_country:
                unknown_airports.add(code)
        dep_in, arr_in = airport_country.get(dep) in SCHENGEN, airport_country.get(arr) in SCHENGEN
        if dep_in != arr_in:
            crossings.append((local_date(f["arrivalDateTime"]) if arr_in else local_date(f["departureDateTime"]),
                              "ENTRY" if arr_in else "EXIT", f"{f['flightNumber']} {dep}→{arr}"))
    for t in trains.values():
        dep_in = t["departureStation"]["country"] in SCHENGEN
        arr_in = t["arrivalStation"]["country"] in SCHENGEN
        if dep_in != arr_in:
            crossings.append((local_date(t["arrivalDateTime"]) if arr_in else local_date(t["departureDateTime"]),
                              "ENTRY" if arr_in else "EXIT",
                              f"train {t['departureStation']['city']}→{t['arrivalStation']['city']}"))
    crossings.sort()

    envelopes, open_entry, stray_exits = [], None, []
    for d, kind, label in crossings:
        if kind == "ENTRY":
            if open_entry:
                print(f"!! two entries in a row: {open_entry[0]} {open_entry[2]} then {d} {label} — missing an exit?")
            open_entry = (d, kind, label)
        else:
            if open_entry is None:
                stray_exits.append((d, label))
                continue
            envelopes.append((open_entry[0], d, open_entry[2], label))
            open_entry = None

    envelope_days = set()
    for start, end, _, _ in envelopes:
        envelope_days |= days_between(start, end)

    # ---- tier 2: dated sources in a Schengen country
    committed, speculative = set(), set()
    sources = []  # (start, end, label, bucket)
    for h in hotels.values():
        if h["address"]["country"] in SCHENGEN:
            sources.append((local_date(h["checkIn"]), local_date(h["checkOut"]), f"hotel {h['hotelName']}", committed))
    for c in conferences.values():
        if c["venueAddress"]["country"] in SCHENGEN and c["status"] != "OUT":
            bucket = committed if c["status"] == "GOING" else speculative
            sources.append((local_date(c["startDate"]), local_date(c["endDate"]),
                            f"conf {c['name']} [{c['status']}]", bucket))
    for g in gatherings.values():
        if g["location"]["country"] in SCHENGEN:
            sources.append((local_date(g["startsAt"]), local_date(g["endsAt"]), f"gathering {g['title']}", committed))
    for start, end, _, bucket in sources:
        bucket |= days_between(start, end)

    floor = envelope_days | committed
    ceiling = floor | speculative

    # ---- report
    print(f"Backup: {backup.name}    today: {today}\n")
    print("External crossings:")
    for d, kind, label in crossings:
        print(f"  {d}  {kind:5}  {label}")
    for d, label in stray_exits:
        print(f"!! exit with no entry before it: {d} {label}")
    print("\nEnvelopes:")
    for start, end, a, b in envelopes:
        print(f"  {start} .. {end}  {(end - start).days + 1:3} days   ({a} / {b})")
    if open_entry:
        print(f"!! OPEN envelope: entered {open_entry[0]} via {open_entry[2]}, no exit booked — days after entry NOT counted")
    if unknown_airports:
        print(f"!! airports not in StaticAirportCityResolver, treated as outside Schengen: {sorted(unknown_airports)}")

    uncovered = [(s, e, l) for s, e, l, _ in sources if not days_between(s, e) <= envelope_days]
    print("\nDated sources not fully inside an envelope (tier-2 fallback in use, or a missing leg):")
    for s, e, l in sorted(uncovered):
        print(f"  {s} .. {e}  {l}")

    def used_in_window(days, on):
        window = days_between(on - timedelta(179), on)
        return len(days & window)

    print(f"\nRolling 90/180 on {today}:  floor {used_in_window(floor, today)}   ceiling {used_in_window(ceiling, today)}")

    horizon = max(ceiling, default=today)
    peak_f = max(((used_in_window(floor, d), d) for d in days_between(today, horizon)), default=(0, today))
    peak_c = max(((used_in_window(ceiling, d), d) for d in days_between(today, horizon)), default=(0, today))
    print(f"Peak from today to {horizon}:    floor {peak_f[0]} on {peak_f[1]}   ceiling {peak_c[0]} on {peak_c[1]}")

    print("\nMonth by month (days present, floor / ceiling):")
    months = sorted({(d.year, d.month) for d in ceiling})
    for y, m in months:
        f_n = sum(1 for d in floor if (d.year, d.month) == (y, m))
        c_n = sum(1 for d in ceiling if (d.year, d.month) == (y, m))
        print(f"  {y}-{m:02}  {f_n:3} / {c_n:3}")

    print("\nEvery day the count would exceed 90 (floor | ceiling):")
    over = [(d, used_in_window(floor, d), used_in_window(ceiling, d))
            for d in days_between(min(ceiling), horizon) if used_in_window(ceiling, d) > 90]
    if not over:
        print("  none")
    for d, fl, ce in over:
        print(f"  {d}  {fl:3} | {ce:3}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv)
