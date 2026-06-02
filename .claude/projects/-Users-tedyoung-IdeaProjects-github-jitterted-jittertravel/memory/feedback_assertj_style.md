---
name: feedback_assertj_style
description: AssertJ assertion code style rules — chained calls on new lines, boolean assertions must have .as() description
metadata:
  type: feedback
---

Two rules for all AssertJ assertions:

1. All chained method calls go on a new line (even single chains):
```java
assertThat(event.hotelBookingId())
        .isEqualTo(command.hotelBookingId());
```

2. All boolean assertions (`.isTrue()`, `.isFalse()`) must include `.as("description")` immediately after `assertThat(...)`, before the terminal assertion. The description must be useful and readable when the test fails:
```java
assertThat(bindingResult.hasFieldErrors("checkIn"))
        .as("Binding result must have a field error for checkIn")
        .isTrue();
```

**Why:** Chaining on new lines improves readability in diffs and reviews. The `.as()` description makes boolean failures self-explanatory without needing to read the test setup.

**How to apply:** Apply to all test code written or modified. Retroactively update boolean assertions in any test file being touched for other reasons.
