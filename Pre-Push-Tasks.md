# Pre-Push Tasks

**Manual setup that unpushed commits are waiting on.** Everything here happens *outside* the repo —
in the Railway dashboard, on a device, or as a one-off admin run against the live app — so nothing
in the build can check it and no test will go red. An open box means the code on `main` expects
an environment that does not exist yet.

**Read this before every push, and empty it as you go.** `DEPLOYMENT.md` names it as the fourth
pre-push gate, alongside the two automatic ones (both test tiers, docs freshness) and the manual
boot-replay preflight.

## The rule that keeps it filled in

**When a change needs a manual step outside the repo before its deploy is healthy, add a box here
in the same change that introduces the need** — the same way marking a `Cleanup_Tasks.md` item done
is part of "done". A change qualifies if it adds or renames an environment variable, needs a
dashboard/DNS/plan setting, requires a one-off `/admin` run after the rollout, or expects a device
to be re-subscribed or re-paired.

Write each box so it can be executed months later by someone who has forgotten the change:

- **The literal thing to do**, including the exact variable name and, where a value must be
  invented, the command that invents it.
- **Which service or surface** it goes on (this project has two Railway services — the app and
  Postgres — and variables are scoped per service).
- **What happens if it is skipped**, because that decides whether it blocks the push or merely
  follows it. Say so explicitly: *before* the push, or *after* the rollout.
- **The commit that created the need**, so the reasoning is one `git show` away.

**When a box is done, delete it** (Ted, 2026-10-05). Tick it only once it is confirmed on the live
service, not once it is typed; then, as part of that same tidy-up, remove it. There is no "Done"
section: the history is in git (`git log -p Pre-Push-Tasks.md`) and nothing here is an archive. A
file that keeps its finished boxes turns "is anything waiting on me?" into a reading exercise, and
that question is the only thing this file is for.

**Clearing it is regular maintenance, not a one-off.** Whoever notices a ticked box deletes it, and
`DEPLOYMENT.md`'s pre-push check ("empty of boxes") is the prompt. What a *standing* description of a
configured instance needs lives in `DEPLOYMENT.md`'s variable table, which stays true forever; a box
here stops being true the moment it is done, which is why it goes.

**A pushed-but-open box is the dangerous state**, so prefer doing the manual step *first*: a
variable set before the deploy is inert, while a deploy that arrives before its variable fails its
health check at best and comes up misconfigured at worst.

## Open

- [ ] **Location codes: preflight before the push, `MIGRATE` after the rollout**
  (`docs/LocationDataCleanupPlan.md` §5; the commit that adds `LocationCodesUpcaster`).
  1. *Before the push:* take a production backup (`/admin/backup`), then run
     `./mvnw test -Preplay-preflight -Dpreflight.dump=<that file>`. The new rung **fails loud** on a
     country name it cannot map, so a booking typed since 2026-10-08 with an unusual spelling would
     put production into read-only mode at boot; the preflight names the event first. If it fails,
     add the spelling to `LocationCodesUpcaster.ALIASES` and re-run. Plan nothing in the app between
     this backup and the push.
  2. *After the rollout:* `/admin/migrate-legacy-events` on the app service. The preview should show
     about 110 rows to migrate and no errors; type `MIGRATE`. Skipping it is safe for reads (the rung
     converts every row as it is read) but leaves the approved fixes unapplied: Didcot, St. Georg,
     Devnexus's GA, Aschaffenburg, Johannesberg, and the five blank airport ends.
  3. *Then:* a second backup. It is the new floor; the one from step 1 is the rollback artifact.
