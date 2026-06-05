---
name: feedback-renderer-testing
description: Test rendering code with direct unit tests on the renderer; web integration tests only for Spring-required concerns (URL mapping, status codes, type conversion)
metadata:
  type: feedback
---

Test rendering code as close to the renderer as possible — call `Renderer.render(...)` directly in a unit test and assert on the HTML string. Do not test HTML content through a web integration test.

Web integration tests must use `@WebMvcTest` slice testing (not `@SpringBootTest`) unless explicitly told otherwise. Use `@MockitoBean` for dependencies. Only test things that require Spring: URL mapping, HTTP status codes, content-type headers, request parameter binding/conversion.

**Why:** Separation keeps renderer tests fast and independent of Spring infrastructure; web tests that assert on HTML content are slow, fragile, and obscure where the responsibility lies.

**How to apply:** Any time a renderer class exists, create `FooRendererTest` as a plain unit test. Create `FooControllerTest` with `@WebMvcTest(FooController.class)` for the mapping/status assertions only.
