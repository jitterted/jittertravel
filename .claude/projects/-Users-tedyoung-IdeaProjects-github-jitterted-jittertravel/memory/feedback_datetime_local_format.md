---
name: feedback_datetime_local_format
description: Any LocalDateTime field in a web request class bound to a datetime-local input requires @DateTimeFormat
metadata:
  type: feedback
---

Every `LocalDateTime` field in a web request/form class that binds to an `<input type="datetime-local">` must carry `@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")`.

**Why:** Without it, Thymeleaf uses Spring's locale-dependent formatter, which renders something like `6/14/2026, 3:00 PM` — a format browsers don't recognise for `datetime-local`, so the field appears blank. The annotation controls both rendering (GET) and parsing (POST). [[feedback_nav_card_svg]]

**How to apply:** Whenever adding a new web request class with `LocalDateTime` fields, annotate each one. See `BookFlightRequest` (departure/arrival) and `BookHotelRequest` (checkIn/checkOut) as reference. A web integration test that GETs the form and asserts the body contains the ISO string (e.g. `"2026-06-14T15:00"`) catches this at the rendering layer — the controller unit test does not, because it only checks the model object.
