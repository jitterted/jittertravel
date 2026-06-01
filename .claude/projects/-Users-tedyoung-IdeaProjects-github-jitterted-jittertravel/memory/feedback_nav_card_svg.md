---
name: feedback_nav_card_svg
description: Nav card SVG icon style for index.html — use Font Awesome Pro fill-based SVGs, not inline stroke SVGs
metadata:
  type: feedback
---

For new nav cards in `index.html`, use the Font Awesome Pro SVGs from the `class="travel-icons"` section — the `fill="#6b6860"` / `viewBox` / `<path fill="...">` style — not custom inline stroke-based SVGs.

**Why:** Consistency with the existing nav cards that already use Font Awesome Pro icons; stroke SVGs look visually different.

**How to apply:** When adding a nav card, find a matching icon already in the travel-icons row and copy that SVG verbatim. If no match exists, use Font Awesome Pro with `fill="#6b6860"`.
