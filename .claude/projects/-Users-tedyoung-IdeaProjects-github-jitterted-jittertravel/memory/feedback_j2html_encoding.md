---
name: feedback-j2html-encoding
description: j2html direct-response controllers must set charset=UTF-8 in Content-Type; non-ASCII characters in renderer source must use HTML entities via rawHtml()
metadata:
  type: feedback
---

When returning j2html-rendered HTML directly from a controller (`ResponseEntity<String>`), always set the charset explicitly:

```java
return ResponseEntity.ok()
        .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
        .body(html);
```

In renderer source, never use non-ASCII literal characters (e.g. `·`, `—`, `→`). Use `rawHtml("&middot;")`, `rawHtml("&mdash;")`, `rawHtml("&rarr;")` etc. Also add `<meta charset="UTF-8">` to the HTML head.

**Why:** Without an explicit charset in Content-Type, browsers may default to ISO-8859-1. UTF-8 multi-byte characters (e.g. U+00B7 = 0xC2 0xB7) then render as two Latin-1 characters (e.g. "Â·").

**How to apply:** Applies to every j2html renderer + controller pair. Check both the controller return statement and any non-ASCII characters in the renderer source.
