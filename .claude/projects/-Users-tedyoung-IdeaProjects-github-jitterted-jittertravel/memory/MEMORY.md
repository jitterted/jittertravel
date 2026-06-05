# Memory Index

- [Nav card SVG style](feedback_nav_card_svg.md) — New nav cards in index.html must use Font Awesome Pro fill-based SVGs from the travel-icons row, not custom stroke SVGs
- [datetime-local @DateTimeFormat](feedback_datetime_local_format.md) — Every LocalDateTime field bound to a datetime-local input needs @DateTimeFormat(pattern="yyyy-MM-dd'T'HH:mm"); also write a web integration GET test to catch rendering failures
- [AssertJ assertion style](feedback_assertj_style.md) — Chained calls on new lines; boolean assertions (.isTrue()/.isFalse()) must have .as("readable description") before the terminal assertion
- [No LLM in app code](feedback_no_llm.md) — Never suggest using any LLM/AI API for application logic, parsing, or processing — explicit non-negotiable constraint
- [New page navigation check](feedback_new_page_navigation.md) — When adding a new page/route, always ask whether it needs a link from index.html or other nav before completing the task
