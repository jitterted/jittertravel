# Railway: migrate `railway.json` to Infrastructure as Code

**Status:** `open`, with a **hard deadline of 2026-12-01**. Planned 2026-11-14; nothing applied yet.
An untracked `.railway/railway.ts` that the Railway CLI generated sits in the working tree. It is a
draft and must not be committed as it stands (see "What the draft gets wrong").

**The pre-push hook enforces the deadline** (Ted, 2026-11-14: "don't let me push after the deadline
without ensuring the migration worked"). From 2026-12-01 on, `.githooks/pre-push` refuses any push
whose tip still contains `railway.json`, or does not contain `.railway/railway.ts`. There is no
environment-variable escape hatch, on purpose. The migration's own commit satisfies the check,
because that commit deletes the one file and adds the other. See "The guard" below.

## What happened, and why it matters

On 2026-11-14 the updated Railway CLI printed this during a database backup:

> Config as Code (railway.json / railway.toml) is deprecated. Prefer Infrastructure as Code
> (.railway/railway.ts). Run `railway config migrate` …

Railway's docs (<https://docs.railway.com/infrastructure-as-code#migrating-from-config-as-code>,
read 2026-11-14) say:

- **Existing Config as Code files stop being read on 2026-12-01, and that is a hard cutoff.** Until
  then a legacy service still reads `railway.json` on every deploy, and it overrides the dashboard.
- **Infrastructure as Code describes the project, not one service.** That covers services, variables,
  domains, replicas and limits. Railway does **not** read `.railway/` during a deploy. The CLI reads
  it when someone runs `railway config plan` and `railway config apply`.
- **`railway config migrate`** previews a file. **`--apply`** writes it **and clears the service's
  Config File setting**, which is a change to the live service, not just a local file.
  `--delete-files` removes the old file. A service cannot be managed by both systems: `config plan`
  stops while `railway.json` still manages it.
- **Planning and applying need the SDK:** `npm install railway` for the TypeScript form.
- **Destructive changes are flagged before you confirm.** A non-interactive apply needs
  `--confirm-destructive` as well as `--yes`. A file "partial" deletes only the resources it owns.

**Why it matters here:** the deploy settings that keep production safe all live in `railway.json`.
They are the `/actuator/health` check that gates a rollout, its 120 s timeout, and `ON_FAILURE`
restart with 3 retries. DEPLOYMENT.md relies on the first and last when it says "a container that
can't boot fails the rollout rather than replacing a healthy instance." If the cutoff passes with
nothing migrated, those settings stop applying, and nothing in the build or the hook notices. The
first sign would be a broken build replacing a healthy instance.

## What the draft gets wrong

Here is the CLI's draft, compared with `railway.json` line by line:

| Setting | `railway.json` | Draft `railway.ts` | Verdict |
|---|---|---|---|
| Builder | `DOCKERFILE`, `Dockerfile` | `builder: "DOCKERFILE"`, `dockerfilePath: "Dockerfile"`, plus `buildEnvironment: "V3"` | Same; V3 is presumably today's value, so confirm in the plan output |
| Health check | `/actuator/health` | `healthcheck: "/actuator/health"` | Same |
| Health check timeout | `120` | `healthcheckTimeout: 120` | Same |
| Restart policy | `ON_FAILURE`, 3 retries | **absent** | **Lost.** See Q1 |
| Variables | (dashboard) | `variables: { managed: true }`, every current variable listed with `preserve()` | **New behaviour.** See Q2 |
| Domain | (dashboard) | `domains: ["jittertravel.com"]` | Now owned by the file. See Q3 |
| Replicas / region | (dashboard) | `replicas: { "europe-west4-drams3a": 1 }` | Now owned by the file. See Q3 |
| CPU / memory | (dashboard) | 1 CPU, 500 MB | Now owned by the file. See Q3 |
| Source | (dashboard) | `github("jitterted/jittertravel", { branch: "main", checkSuites: false })` | `checkSuites: false` means a deploy does not wait for GitHub checks. That is presumably today's behaviour, so confirm it |
| Postgres service | (dashboard) | not in the file | Must **not** show as a deletion in the plan. See Q4 |

## Questions to answer before applying

**Q1. Can IaC express the restart policy?** The migration doc does not mention it. Look it up in
the IaC reference. If IaC can express it, add `ON_FAILURE` with 3 retries to the file. If it cannot,
find out whether the setting survives in the dashboard once the Config File is cleared, and record
which. Do not apply until this is answered: losing restart-on-failure silently is the outcome this
plan exists to prevent.

**Q2. What does `variables: { managed: true }` do to a variable that is not in the file?** Our
deploy habit (`Pre-Push-Tasks.md`, DEPLOYMENT.md) is to **set a new variable in the dashboard before
the push**. If "managed" means the file owns the variable list, a dashboard-only variable could be
deleted at the next apply. That would break the boot of a fail-fast variable such as
`REMEMBER_ME_KEY`, or quietly switch off an empty-default one such as `CALENDAR_FEED_TOKEN`. Find
out which it means, then choose one of these:
  - **(a)** Keep `managed: true`, and change the rule so a new variable is added to `railway.ts`
    (as `preserve()`) in the same change. Pre-Push-Tasks.md and DEPLOYMENT.md then say so.
  - **(b)** Drop `managed`, so variables stay dashboard-only as they are today. That is the smallest
    change to how things work, and probably the right default.

**Q3. Do the domain, region and limits belong in the file?** They are dashboard-only today. In the
file they become reviewable and versioned, but a careless edit can then change production on the
next apply. One possibility is to keep only what `railway.json` held (build, health check, restart)
plus the bare minimum IaC requires. Whether IaC allows omitting the rest is unknown, so check the
reference.

**Q4. Is Postgres safe?** It is not in the file. The docs say a partial deletes only resources it
owns, and the draft's partial is `"jittertravel"`. The plan output is what proves Postgres is not
owned. **Any line in `railway config plan` that deletes a service, a variable or a volume is a
stop.**

**Q5. Where does the `railway` npm package live?** Planning needs it, and this is a Maven repo with
no `package.json`. Adding one is a new dependency, which per standing practice needs Ted's
agreement. Options: a `package.json` under `.railway/` only, or a one-off `npx` with nothing
committed. The deploy itself never needs it.

## Steps

1. **Answer Q1–Q5** and edit the draft `railway.ts` to match. Delete the CLI's boilerplate comment
   if it no longer applies.
2. **Take a fresh production backup** first (`railway` backup, as on 2026-11-14). Nothing here
   should touch data, but Q4 is the one way it could.
3. **`railway config migrate`** with no flags, then **`railway config plan`**. Paste the plan
   output into this doc, under "Plan output" below. Read it for deletions (Q4). It should show no
   change to the running deployment beyond the Config File setting.
4. **Apply:** `railway config migrate --apply`, then `railway config apply`. Do it before the
   cutoff, at a quiet moment, and not on the same day as an app change. If something goes wrong,
   the cause should be unambiguous.
5. **Verify on the live service.** These are the "ensuring the migration worked" checks. Each one
   needs a recorded expected/actual result:
   - **A deploy is gated by the health check.** Push a trivial docs-only commit (or redeploy from
     the dashboard), then watch for the health check in the deploy log and
     `Replayed N events from persistent store`.
   - **Restart policy:** the dashboard's service settings show `ON_FAILURE` with 3 retries, or
     whatever Q1 decided.
   - **Variables intact:** the dashboard lists the same 15 variables as before. Settings in the app
     shows the Brevo key's last four characters. Login works, which proves `TED_PASSWORD` and
     `REMEMBER_ME_KEY` are still set.
   - **Domain:** `https://jittertravel.com/` loads and `/calendar` renders for an incognito viewer.
   - **Postgres:** still present, and the event count on `/admin/eventlog` matches the backup.
6. **Only after every check in step 5 passes:** delete `railway.json`, commit `.railway/railway.ts`
   (plus `package.json` if Q5 chose one), and update the docs that name `railway.json`:
   DEPLOYMENT.md (lines 4, 19, 241, 368 and 375 as of 2026-11-14) and
   `SpringBoot41UpgradePlan.md` S9. If Q2 chose (a), also update the variable rule in
   `Pre-Push-Tasks.md` and DEPLOYMENT.md. That commit is what satisfies the guard.
7. Remove the guard from `.githooks/pre-push` once the migration has shipped. A permanent check
   that a file is absent records nothing. Then archive this plan.

## The guard

`.githooks/pre-push` reads the pushed tip (not the working tree). Its behaviour depends on the date:

- **Before 2026-12-01**, while the tip still has `railway.json`, it prints how many days are left
  and lets the push through.
- **From 2026-12-01**, it **blocks** if the tip has `railway.json` or lacks `.railway/railway.ts`.

The file check stands in for step 5. It cannot see the live service, so step 6 is ordered to make
it true only once step 5 has been done. **Do not delete `railway.json` just to clear the hook.**
That would satisfy the guard without satisfying the plan, which is exactly what Ted asked to be
stopped from doing. A hook in a repo you own can always be edited, so the guard defends against
forgetting, not against intent.

## Plan output

_Not yet run._
