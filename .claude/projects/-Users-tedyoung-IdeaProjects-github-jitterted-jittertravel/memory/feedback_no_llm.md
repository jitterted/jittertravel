---
name: feedback-no-llm
description: User explicitly refuses any LLM usage in the application code — never suggest it
metadata:
  type: feedback
---

Never suggest using an LLM (Claude API, GPT, Gemini, etc.) as part of the application's logic, data entry, parsing, or processing. This is an explicit, non-negotiable constraint.

**Why:** User preference — "never ever."

**How to apply:** When suggesting solutions for parsing, extraction, classification, or any other task that an LLM could handle, propose only deterministic alternatives: APIs, libraries, regex, rules. Do not mention LLM as an option even as a fallback.
