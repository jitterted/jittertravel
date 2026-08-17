# JitterTravel

A little app that helps me plan my travel calendar, both tracking conferences, meetups, etc., along with travel logistics down to the train and bus schedule level and hotels.

Developed as an experiment in aggregate-free Event Sourcing, using Event Modeling and Claude Code.

## Development setup

After cloning, enable the tracked git hooks once:

```sh
git config core.hooksPath .githooks
```

This wires up the `pre-push` MUST-PASS gate (`.githooks/pre-push`), which blocks a `git push`
unless **both** test tiers pass:

```sh
./mvnw test              # default suite
./mvnw test -Pjs-tests   # the @Tag("js") Playwright tier (opt-in, excluded from the default build)
```

`core.hooksPath` is local git config, not tracked, so each clone runs the command above once.