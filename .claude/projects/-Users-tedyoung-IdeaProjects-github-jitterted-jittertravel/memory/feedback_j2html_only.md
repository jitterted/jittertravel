---
name: feedback_j2html_only
description: All views use j2html, not Thymeleaf — never suggest Thymeleaf for new or existing views
metadata:
  type: feedback
---

All views use j2html. The project has moved away from Thymeleaf completely.

**Why:** The team migrated to j2html for type-safe, Java-native HTML rendering.

**How to apply:** Never suggest Thymeleaf templates or Thymeleaf-specific solutions (th:each, th:if, etc.) for any view work. Option C (template-level conditional rendering) is always off the table.
