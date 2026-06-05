---
name: feedback_new_page_navigation
description: When adding a new page/route, always ask whether it needs a link from index.html or other nav before completing the task
metadata:
  type: feedback
---

When creating a new controller or HTML template (a new page), always ask the user whether it needs to be linked from `index.html` or from any other navigation before considering the task done.

**Why:** The booked-hotels and booked-trains pages were implemented without being added to the home page nav, requiring a separate cleanup step.

**How to apply:** At the end of any task that introduces a new route, include a prompt like: "Should I add a nav card for this on `index.html`?" before closing out.
