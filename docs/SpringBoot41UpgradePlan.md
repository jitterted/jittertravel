# Spring Boot 4.1.1 Upgrade Plan

Status: **open, agreed, HELD** — the version bump has not happened, and does not happen until Ted
says go. Written 2026-09-06; reviewed against the tree 2026-09-07.

**S1's precondition is now met** (2026-09-07): Cancel Train and overlapping legs landed in `0e0a0c7`
and `846ee9d`, and the tree is clean. That was always a *precondition*, never the trigger — **the
hold stands**.

Questions 1 and 4 were answered by Ted on 2026-09-06 (Modulith **2.1.1** confirmed, the Dockerfile
`maven.test.skip` change **taken**). Question 2 was answered on 2026-09-07 and **acted on ahead of
the bump** — `EventJsonMapperEquivalenceTest` is retired (§2). Question 3 is the only one left, and
it genuinely is not answerable before the suite runs.

Primary source: the [Spring Boot 4.1 Release
Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) and the
[4.1.0 Configuration
Changelog](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1.0-Configuration-Changelog),
plus the [4.2.0-M2 Release
Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.2.0-M2-Release-Notes) read
ahead so this upgrade does not paint us into a corner.

---

## Where we stand

| | Now | Target |
|---|---|---|
| `spring-boot-starter-parent` | 4.0.7 | **4.1.1** |
| `spring-modulith.version` | 2.0.6 | **2.1.1** |
| Java | 26 | 26 (unchanged) |
| Maven | 3.9.15 | unchanged |

4.1.1 is the newest GA in the 4.1 line. 4.2 exists only as milestones (4.2.0-M1 on Central, M2 notes
published) and is not a candidate — see "Looking ahead to 4.2" below for why waiting for it buys us
nothing either.

**The Modulith bump is not optional and not cosmetic.** `spring-modulith-starter-core:2.1.1` declares
`spring-boot-starter:4.1.1`; the 2.0.x line declares 4.0.x. Our parent's dependency management would
paper over the version mismatch, but 2.0.x is *built and tested* against Boot 4.0, so staying there
means running Modulith outside the combination its own CI covers. 2.0.6 is also two patches stale
inside its own line (2.0.8 is out), so we are behind either way.

---

## What actually changes for this app

The 4.1 notes are long; almost none of it is ours. Sorted by whether it touches us.

### 1. Spring Security 7.0.6 → 7.1.1 — the single biggest risk

A whole Security *minor* rides inside this Boot *minor*. `SecurityConfig` is the app's threat model in
one file, so this is where to spend the attention.

What we use, and what 7.1 says about it: `authorizeHttpRequests` / `requestMatchers`,
`CookieCsrfTokenRepository` with a cookie customizer, `formLogin` with a custom `loginPage` and
success handler, a custom `accessDeniedHandler`, `InMemoryUserDetailsManager`, `BCryptPasswordEncoder`
— the 7.1 "what's new" page documents **no breaking change to any of them**. What 7.1 adds is
additive and optional: `InetAddressMatcher`, `ConditionalAuthorizationManager`, MFA conditions,
`RestClientOpaqueTokenIntrospector`, a `charset` on `WWW-Authenticate`. We adopt none of it.

That is a documentation claim, not a proof. The proof is the test tier, and it already exists:
`AuthorizationMatrixTest` (the policy matrix), `SecurityAuthorizationTest` (including
`staleCsrfTokenReturnsToLogin`), and `CalendarRedactionSecurityTest` (anonymous body through the real
chain). **If any of those goes red, stop and read it as a security finding, not a test-maintenance
chore.** Do not adjust a matcher to make a test pass.

### 2. Jackson — and the canary that was retired before the bump rather than read during it

Boot 4.1 changes the auto-configured Jackson mapper: new `spring.jackson.read.*` / `spring.jackson.write.*`
general features, `spring.jackson.factory.*` (including `factory.constraints.read.max-string-length`,
`max-document-length`, `max-nesting-depth`, `max-token-count`), a `HandlerInstantiator` that builds
handlers from application beans, and new `JsonFactoryBuilderCustomizer` / `CborFactoryBuilderCustomizer`
/ `XmlFactoryBuilderCustomizer` callbacks. Jackson itself moves 3.1.4 → 3.1.5.

Our stored format is **not** auto-configured — `EventSourcingConfig` pins the `JsonMapper` bean to
`EventJsonMapperFactory.create()` precisely so a framework default cannot rewrite the event log's
contract. So production serialization is insulated by construction. Good.

`EventJsonMapperEquivalenceTest` asserted the pinned mapper serializes **byte-for-byte identically
to the mapper Boot auto-configures**, and Boot 4.1 changes exactly that mapper. **It is retired, in
its own commit, ahead of the bump** (Ted, 2026-09-07). The reasoning:

- Its job was the *one-time* proof that swapping the auto-configured bean for the pin changed
  nothing. That swap shipped long ago, so the test now asserts a counterfactual about a completed
  migration — its own javadoc said as much ("As long as this passes, replacing the auto-configured
  bean with the pinned factory cannot change the on-the-wire format").
- A red there would never have meant the stored format changed. It would have meant Boot's default
  moved away from our pin — **which is the outcome pinning was for**, i.e. the test was built to go
  red on success.
- Retiring it *before* the bump rather than deciding mid-upgrade means S4 has one fewer red that
  has to be talked out of being a failure. A canary that has to be explained every Boot minor is a
  cost, not a signal.
- Nothing is lost: its five samples (`FlightBooked`, `HotelBooked`, `TrainBooked`,
  `GatheringPlanned`, `ConferencePlanned`) are all covered by `GoldenEventDeserializationTest`,
  which carries 39 cases over 22 event types **and** uses a stricter mapper
  (`FAIL_ON_UNKNOWN_PROPERTIES=true`).

**The rule the test existed to protect outlives it, and it is the one to keep in mind during the
upgrade: never edit `EventJsonMapperFactory` to chase a framework default.** That would change what
production writes to `event_log` — the exact failure the pin prevents. The durable contract is
`GoldenEventDeserializationTest`, `RestoreSafetyTest` and `BackupRestoreRoundTripTest` plus the S7
replay preflight; those must stay green, and they are what proves the upgrade is safe. Both javadocs
(`EventJsonMapperFactory`, `EventSourcingConfig.jsonMapper`) now say this in place of pointing at
the retired test.

The new read constraints (`max-string-length` and friends) apply to the auto-configured mapper, so
`/admin/restore` — which reads whole backup files through the **pinned** mapper — is unaffected. Worth
knowing rather than acting on.

### 3. Devtools LiveReload deprecated

`spring.devtools.livereload.enabled` and `.port` are deprecated with no replacement, and the LiveReload
feature itself is deprecated ("decreased popularity"). We ship `spring-boot-devtools` at `runtime`
scope and set neither property, so **nothing to change** — but LiveReload is now a standing removal
candidate. If we want to stop relying on it, now is cheaper than at the 4.2 upgrade.

### 4. Maven AOT and `-DskipTests`

AOT processing no longer honours `-DskipTests`; it wants `maven.test.skip`. Our `Dockerfile` runs
`./mvnw -B -ntp -DskipTests` twice (the `dependency:go-offline` layer and the `package` layer), and we
run **no AOT** (no native profile, no `process-aot` execution), so this is a no-op today. It becomes a
silent trap the day AOT is switched on.

**Decided (Ted, 2026-09-06): take it.** Both invocations move to `-Dmaven.test.skip=true`, in the same
commit as the version bumps — it is inert today, so it cannot be the cause of anything that goes red,
and keeping it with the upgrade is what makes the whole thing one revertable commit. Note the two
flags are not synonyms in general: `-DskipTests` compiles tests and skips running them,
`-Dmaven.test.skip=true` skips compiling them too. For a container image that never runs tests that
is strictly faster, and it is the flag the Boot plugin now reads.

### 5. Layertools removed

Removed in favour of `tools`. We build a plain executable jar (`COPY --from=build /app/target/*.jar`)
and never invoke a jarmode, so this does not reach us.

### 6. `spring.sql.init` — the one Boot auto-configuration that writes to our database

`application.properties` sets `spring.sql.init.mode=always`, so Boot's datasource initializer runs
`src/main/resources/schema.sql` — **DDL, against production** — on **every** boot. That is a
Boot-managed feature on the upgrade's path, and it is the only one that touches the database, so it
is named here rather than left to be assumed safe.

It *is* safe, and here is why rather than an assertion: the script is written to be re-run
(`CREATE TABLE IF NOT EXISTS`, and its column additions are explicitly commented "idempotent
upgrades for existing databases (schema.sql runs on every startup)"), `spring.sql.init.*` is not on
the 4.1.0 removed-properties list, and the 4.1 notes change nothing in datasource initialization.

Two consequences worth carrying forward: the **first boot on 4.1.1 runs this script** (S9 watches
it), and so does the **first boot after a revert** — which is the one thing the Rollback section
below has to be true about, and is.

### 7. Other version moves worth naming

From the managed-dependency diff between 4.0.7 and 4.1.1, restricted to what we actually use:

| Library | 4.0.7 | 4.1.1 | Note |
|---|---|---|---|
| Spring Framework | 7.0.8 | 7.0.9 | patch |
| Spring Security | 7.0.6 | **7.1.1** | minor — see §1 |
| Jackson BOM | 3.1.4 | 3.1.5 | patch — see §2 |
| Micrometer | 1.16.6 | **1.17.1** | minor — see below |
| Micrometer Tracing | 1.6.6 | 1.7.1 | unused |
| PostgreSQL driver | 42.7.11 | 42.7.13 | patch |
| Tomcat | 11.0.22 | 11.0.24 | patch |
| Logback | 1.5.34 | 1.5.38 | patch; we have no `logback-spring.xml` |
| Mockito | 5.20.0 | 5.23.0 | test only |
| Thymeleaf layout dialect | 3.4.0 | 4.0.1 | **unused** — no `layout:` attribute in any template |

Unchanged and therefore not a risk: Testcontainers, JUnit, AssertJ, HikariCP, core Thymeleaf.

**Micrometer is the second minor in this bump and needs a step of its own.** `EventStore` takes a
`MeterRegistry` and registers three meters — `eventstore.subscriber.failures` (counter),
`eventstore.subscriber.duration` and `eventstore.notification.duration` (timers) — and
`/actuator/metrics` is one of only two endpoints we expose
(`management.endpoints.web.exposure.include=health,metrics`). A minor can change meter naming or
`Timer` conventions, and nothing in the test suite asserts a meter name, so a silently renamed meter
would surface only as a metric that stopped existing. **Named in S8 as an explicit check** rather
than left as a parenthetical.

### 8. Dependencies Boot does *not* manage — the ones nobody upgrades for us

`spring-test-profiler:0.1.0`, `strictland:0.3.0` and `playwright:1.49.0` are pinned by us. The first
two bind to Spring/Boot internals and are the most likely things to break on a Framework bump, and
both are declared spikes: Strictland's adoption is already paused
(`docs/Event_Serialization_Contract_Tests.md`), and it is used by exactly two test files
(`ConferenceCancelledContractTest`, `JsonMapperMessageSerializer`). Playwright drives a browser, not
Spring, so it is insulated.

### 9. Everything in the 4.1 notes that is not ours

Recorded so nobody re-reads the notes wondering: Derby deprecation, Spring gRPC, JPA bootstrap-mode
changes, Reactor `HttpClient` defaults, RabbitMQ streams/SSL, Kafka and JMS conventions,
`@RedisListener`, Spring Batch MongoDB, Log4j rotation, embedded LDAP SSL, jOOQ's Java 21 floor,
OAuth2 resource-server authority expressions, `spring.datasource.connection-fetch`, Spock/Groovy 5,
every Gradle-plugin change, and the OpenTelemetry/OTLP surface. None are on our dependency graph.

The `/actuator/info` `process.*` fields (uptime, startTime, timezone, locale, workingDirectory) are
also moot: `management.endpoints.web.exposure.include=health,metrics` does not expose `info`.

**Removed properties in 4.1.0** — `logging.file.clean-history-on-start`, `logging.file.max-history`,
`logging.file.max-size`, `logging.file.total-size-cap`, `logging.pattern.rolling-file-name`,
`spring.data.redis.lettuce.cluster.refresh.adaptive`, `spring.datasource.oracleucp.role-name`. Grepped
`src/`: **we set none of them.**

### 10. Compile risk: what the deprecation scan does and does not tell us

A `-Dmaven.compiler.showDeprecation=true` build against the *current* 4.0.7 reports four warnings,
all in tests and none from Spring Boot:

- `BackupServiceTest:43,45,57` — `JsonNode.asText()` (Jackson)
- `AddressParseControllerTest:48` — `HttpStatus.UNPROCESSABLE_ENTITY` (Spring Framework)

Main sources compile clean. Worth clearing the four anyway while we are in here — but as a separate
commit, not folded into the upgrade.

**What this does not prove** (corrected 2026-09-07 — the earlier draft read this as evidence that
"4.1's removal sweep has nothing to remove from us", and it is not). `showDeprecation` reports
deprecated APIs *we call at compile time*. It says nothing about a class that moved package, an API
removed without ever being deprecated in a line we compiled against, or a configuration property
(not a compile-time construct at all). A clean report is evidence about one failure mode, not about
the class of change most likely to bite.

**The actual compile surface is small enough to name.** The tree has thirteen
`org.springframework.boot` imports, and five sit in the per-module autoconfigure packages Boot 4.0
itself churned:

```
org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
org.springframework.boot.jdbc.test.autoconfigure.{AutoConfigureTestDatabase, JdbcTest}
org.springframework.boot.webmvc.test.autoconfigure.{AutoConfigureMockMvc, WebMvcTest}
org.springframework.boot.testcontainers.service.connection.ServiceConnection
```

The other eight (`SpringApplication`, `SpringBootApplication`, `ImportAutoConfiguration`,
`ApplicationReadyEvent`, `BuildProperties`, `SpringBootTest`, `TestConfiguration`) are stable
surface. So S4 step 1 expects no compile errors because **this list is short and these packages are
one release old**, not because a deprecation scan was clean. Checking costs one `test-compile`.

(The first of the five was the import in `EventJsonMapperEquivalenceTest`, which is now retired — so
a relocation there can no longer be mistaken for the §2 decision. That is a small side benefit of
retiring it early, not the reason.)

---

## Looking ahead to 4.2 (from the 4.2.0-M2 notes)

Read so this upgrade is not a step toward a wall. It is not:

- **Embedded LDAP now requires an SSL bundle when SSL is enabled** — we have no LDAP.
- **Janino dependency management removed** (Logback dropped Janino support) — we have no
  `logback-spring.xml` and no Janino anywhere in the tree, so nothing to take over managing.
- New in 4.2: LDAP SSL bundles, common OTLP fallback config under `management.opentelemetry.otlp`,
  configurable tracing MDC keys (`management.tracing.mdc.trace-id-key` / `.span-id-key`),
  `ResourceBasedMessageSourceConfigurer`, and an `sbom` command in the tools jarmode. **None of it is
  load-bearing here.**

**Conclusion: 4.2 costs this app nothing today, and offers it nothing today.** So there is no reason
to hold 4.1.1 back waiting for 4.2, and no reason to jump onto 4.2.0-M1 — that would mean adding the
Spring milestone repository and moving to Modulith 2.2.0-M1, i.e. two pre-release lines under an
event-sourced production database, to gain features we do not use.

The one thing 4.2 *does* change about this plan is §3: LiveReload's deprecation is the item most
likely to become a removal, so decide about devtools now rather than under time pressure later.

---

## Steps

**S1 — clean baseline. Done, 2026-09-07.** This was the blocker as written on 2026-09-06: the tree
then carried a large staged Cancel Train / overlapping-legs feature (`CancelTrain`, `TrainCancelled`,
`TravelLeg`, `CancelTrainController`, the `ProblemRef` → `ProblemKey` rename, ~70 files) touching
`SecurityConfig`, `AuthorizationMatrixTest`, `CalendarRedactionSecurityTest` and
`GoldenEventDeserializationTest` — the exact tests S4 judges the upgrade by, so a red one in a mixed
tree could not have been attributed to either change. **It landed in `0e0a0c7` and `846ee9d` and the
tree is clean.** The standing requirement survives: the upgrade must be its own commit, and a bisect
must be able to name it.

Note this cleared a *precondition* only. **The hold is Ted's and is separate** — see Status.

**S2 — the change itself.** Three edits, one commit:

- `pom.xml`: parent `4.0.7` → `4.1.1`;
- `pom.xml`: `<spring-modulith.version>` `2.0.6` → `2.1.1` (**confirmed by Ted 2026-09-06**);
- `Dockerfile`: both `-DskipTests` → `-Dmaven.test.skip=true` (**agreed 2026-09-06**, §4 above).

Nothing else — in particular the four deprecation warnings from §10 stay out; they are a separate
commit so they cannot muddy the attribution of a failure. The equivalence-test retirement (§2) is
likewise its own commit and is **already done**, ahead of the bump, for the same reason.

**S3 — dependency-tree diff.** Capture `./mvnw -B dependency:tree` before and after and diff them.
Eyeball for anything that moved unexpectedly, especially in the jackson, security and micrometer
subtrees, and for any new transitive arrival.

**S4 — `./mvnw test`** (237 test classes as of 2026-09-07). Triage strictly in this order, because
the order encodes what a failure *means*:

1. compile errors — expected to be none, and the reason is the thirteen Boot imports enumerated in
   §10, not the deprecation scan;
2. the security tier — `AuthorizationMatrixTest`, `SecurityAuthorizationTest`,
   `CalendarRedactionSecurityTest`. A red here is a finding;
3. the serialization contract — `GoldenEventDeserializationTest`, `RestoreSafetyTest`,
   `BackupRestoreRoundTripTest`. A red here **blocks the upgrade outright**;
4. everything else.

There is deliberately no "expected red" step any more: retiring the equivalence test (§2) removed the
one failure that had to be argued out of being a failure. **If something goes red here, it is a
problem.**

**S5 — `./mvnw test -Pjs-tests`.** The Playwright tier is excluded from the default build and the
pre-push gate requires it. A green default build is not proof.

**S6 — read the architecture guards' output, not just their colour.** `DomainIsPureTest`,
`NoAmbientClockReadsTest`, `ApplicationServicesUseCommandExecutorTest`,
`ProjectorsDependOnEventsAloneTest`, `HoverIsNeverTheAffordanceTest`, `TrimmedTypedTextConventionTest`,
`ProblemContextFragmentConventionTest`, `TimeFilterToggleConventionTest`, `CalendarDayMenuTest`. These
are source scans, so a framework bump cannot legitimately break them — which is exactly why a red one
means someone reached for the wrong fix upstream.

**S7 — replay preflight against a real production backup.** This is the step that matters for an
event-sourced app, and it is the one the default suite does not cover:

```sh
./mvnw test -Preplay-preflight -Dpreflight.dump=/path/to/jittertravel-backup-production-*.json
```

It proves the stored log still deserializes and upcasts under the new Jackson. **Do not skip it
because S4 was green** — the golden samples are a handful of shapes; the dump is the whole log.

**The dump to use is `backups/jittertravel-backup-production-2026-09-07T090805Z.json`** (taken after
the 2026-09-07 deploy; `backups/` is gitignored, so it is on Ted's machine and not in the repo).
v3, **117 events across 19 types, including one `TrainCancelled`** — which is why this one and not
an older one: a dump that predates an event type cannot exercise it, and that is precisely the
coverage this step adds over the goldens. It **replays green on 4.0.7** (`Tests run: 1, Skipped: 0`,
2026-09-07), so S7 is a before/after comparison on identical data rather than a first look — a red
here under 4.1 is the bump, with nothing else to rule out.

Take a newer one if prod has moved on and the new events are of a kind this dump does not carry;
otherwise this is the input.

**S8 — Ted runs it locally** against the real database and checks, at minimum: `/calendar` in a normal
window and in **incognito** (anonymous redaction — never "log out", there is no logout affordance),
`/admin/eventlog`, and `/admin/restore/validate` against the latest backup (a dry run, writes nothing).

Plus `/actuator/metrics`, for the Micrometer minor in §7: confirm `eventstore.subscriber.failures`,
`eventstore.subscriber.duration` and `eventstore.notification.duration` are all still listed. No test
asserts a meter name, so this page is the only place a rename shows up.

**S9 — deploy.** `railway.json` needs no change and the healthcheck stays `/actuator/health`; the image
already targets `eclipse-temurin:26`. The Dockerfile's only change is the flag from S2, so **watch the
first build**: `-Dmaven.test.skip=true` skips test *compilation* as well as execution, which is what
we want in an image but is a different code path through the build than the one that has been running.
Then watch the first boot for the event-schema-version preflight, and for `schema.sql` (§6).

**S10 — docs.** Move this file to `docs/archived/` once it owns no remaining work, cut its row in
`docs/Backlog.md` down to one Done line, and lift anything surviving (the deprecation cleanup, the
devtools decision) into `docs/Cleanup_Tasks.md`. The pre-push docs gate will block the push otherwise.

---

## Rollback

One commit, two version numbers. `git revert` it and Railway redeploys the previous image. Nothing in
this upgrade writes to the database, changes an event's shape, or migrates a row — so a rollback is a
rollback, with no data to unwind. That is what makes S7 the gate rather than the deploy.

**The one thing that does run DDL on the way back is `schema.sql`** (§6): `spring.sql.init.mode=always`
means the reverted 4.0.7 image runs it at boot exactly as the 4.1.1 image did. It is idempotent and
4.1 changes nothing about datasource initialization, so the round trip is clean — but it is named
here because "nothing writes to the database" is otherwise a claim with a counter-example one
property file away.

---

## Questions

**Answered (Ted, 2026-09-06):**

1. **Modulith 2.1.1** — yes. It is the pair for Boot 4.1.1 (its starter declares
   `spring-boot-starter:4.1.1`).
2. **Dockerfile `-DskipTests` → `-Dmaven.test.skip=true`** — yes, take it, in the upgrade commit.

**Answered (Ted, 2026-09-07):**

2. **`EventJsonMapperEquivalenceTest`** — **retire it, and do it before the bump**, not conditionally
   on it going red. The original framing ("if it goes red, decide then") turned out to be the wrong
   question: what it did or did not do under 4.1 was never going to change the answer, because the
   *policy* — a one-time migration proof whose migration is done — is decidable without the outcome.
   Deciding it in advance also removes a stop-and-ask from the middle of an upgrade, which is the
   worst moment to be re-litigating whether a red is a red. Done, §2.

**Still open, and genuinely not answerable before the suite runs:**

3. **The two spike test deps** (`strictland`, `spring-test-profiler`) — if either breaks on the bump,
   drop it or fix it? Strictland is two files and already adoption-paused, so dropping is cheap.
