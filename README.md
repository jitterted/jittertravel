# JitterTravel

A little app that helps me plan my travel calendar, both tracking conferences, meetups, etc., along with travel logistics down to the train and bus schedule level and hotels.

Developed as an experiment in aggregate-free Event Sourcing, using Event Modeling and Claude Code.

## Development setup

The tracked git hooks live in `.githooks/` and are wired up by `core.hooksPath`. The first
Maven build does this for you automatically (an `initialize`-phase step runs
`git config core.hooksPath .githooks`), so a plain `./mvnw test` after cloning is enough. To
enable them immediately without a build, run it yourself:

```sh
git config core.hooksPath .githooks
```

This wires up the `pre-push` MUST-PASS gate (`.githooks/pre-push`), which blocks a `git push`
unless **both** test tiers pass:

```sh
./mvnw test              # default suite
./mvnw test -Pjs-tests   # the @Tag("js") Playwright tier (opt-in, excluded from the default build)
```

`core.hooksPath` is local git config, not tracked — so it's per-clone, which is why the Maven
bootstrap (or the command above) sets it.