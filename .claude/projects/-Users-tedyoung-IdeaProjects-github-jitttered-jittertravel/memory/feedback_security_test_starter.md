---
name: feedback_security_test_starter
description: Always add the testing support starter for any new starter added to the project
metadata:
  type: feedback
---

When adding a new `spring-boot-starter-X` dependency, always also add `spring-boot-starter-X-test` in test scope if it exists.

**Why:** The user had to manually add `spring-boot-starter-security-test` after I added `spring-boot-starter-security` without it.

**How to apply:** As part of adding any new starter, check if a `-test` variant exists and add it in test scope in the same commit.