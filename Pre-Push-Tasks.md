# Pre-Push Tasks

**Manual setup that unpushed commits are waiting on.** Everything here happens *outside* the repo —
in the Railway dashboard, on a device, or as a one-off admin run against the live app — so nothing
in the build can check it and no test will go red. An unticked box means the code on `main` expects
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

Tick a box only once it is confirmed on the live service — not once it is typed. Then move it to
**Done**, keeping the date and the commit; a ticked box with its reasoning intact is what stops the
same variable being invented twice with two different values.

**A pushed-but-unticked box is the dangerous state**, so prefer doing the manual step *first*: a
variable set before the deploy is inert, while a deploy that arrives before its variable fails its
health check at best and comes up misconfigured at worst.

## Open

- [ ] **Add the Railway variable `REMEMBER_ME_KEY` on the app service, before pushing `8e86ac8`.**
      Generate the value once with `openssl rand -hex 32` (any long, stable random string does).

      **If skipped:** the app **does not start**. `SecurityConfig.rememberMeServices` binds it as
      `@Value("${REMEMBER_ME_KEY}")` with no default, deliberately, so the container fails its
      health check and `restartPolicyType: ON_FAILURE` retries three times — the running instance is
      not replaced, so this fails safe, but it is a wasted deploy.

      **Do not rotate it casually once set:** changing it signs out every remembered device. That is
      the intended revocation control (it needs no logged-in browser), but it is not a free
      cleanup. Note what the key is *not*: it does not sign the cookie — see
      "`REMEMBER_ME_KEY`: stability matters more than secrecy" in `DEPLOYMENT.md`.

      Nothing else is needed on the Railway side. `persistent_logins` is created by `schema.sql`
      under `spring.sql.init.mode=always`, so there is no manual migration.

      Introduced by `8e86ac8` "Stay signed in across a restart, with persistent remember-me tokens".

- [ ] **After that deploy, confirm on `/admin` that production reports cookies as Secure.**
      This is the one claim a local run cannot check, and it is why `SecureCookieProbe` exists.
      Expect **"Cookies on this request are marked Secure"**; the raw lines beneath it distinguish
      *no `X-Forwarded-Proto` arrived* from *one arrived and was ignored*, which have different
      fixes. A wrong answer means `server.forward-headers-strategy=framework` is not taking effect
      and the remember-me cookie — a real credential — is shipping unmarked.

      **If skipped:** nothing breaks visibly; the risk is silent. *After* the rollout, not before.

      Introduced by `8e86ac8`.

## Done

_(Nothing yet. Move a ticked box here with its date and commit rather than deleting it.)_
