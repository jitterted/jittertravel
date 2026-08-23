#!/usr/bin/env python3
"""
Pick the next thing to work on, by adaptive pairwise comparison.

Reads docs/Backlog.md, pulls every item whose status token is not `done`, and
writes a self-contained HTML page that asks

    "If you could only implement one of these next, which would you choose?"

one pair at a time. The page fits a Bradley-Terry model to the answers so far
and picks the pair whose outcome it can predict least well, so each question
buys as much ordering as it can. Stop whenever you like: the ranking on the
right is always current, with a confidence band on every score.

This script is standalone -- Python 3 stdlib only, no build, no server. It
touches nothing in the project; it only reads Backlog.md.

    ./scripts/pick-next-feature.py                # write + open in browser
    ./scripts/pick-next-feature.py --no-open      # just write the file
    ./scripts/pick-next-feature.py --out /tmp/x.html
    ./scripts/pick-next-feature.py --list         # dump the parsed items, no HTML

Answers live in the browser's localStorage, keyed per item, so re-running the
script after editing Backlog.md resumes where you left off: items that are gone
drop out of the history, items that are new arrive unranked and get asked about
first.
"""

import argparse
import html
import json
import os
import re
import sys
import webbrowser

# Sections of Backlog.md to harvest, and how each one's table is shaped.
#
#   title_col / doc_col / body_col are 0-based indices into the row's cells.
#   A doc_col of None means the section's table has no owning-doc column.
#   default_status is used when the body cell opens with no `status` token.
SECTIONS = [
    {
        "heading": "Open features",
        "label": "Feature",
        "title_col": 0, "doc_col": 1, "body_col": 2,
        "default_status": "open",
    },
    {
        "heading": "Open cleanups",
        "label": "Cleanup",
        "title_col": 0, "doc_col": None, "body_col": 1,
        "default_status": "open",
    },
    {
        "heading": "Decisions waiting on Ted",
        "label": "Decision",
        "title_col": 0, "doc_col": 1, "body_col": 0,
        "default_status": "decision",
    },
    {
        # This table's first column IS the doc, so it is the title; repeating
        # it in the footer line would say the same thing twice.
        "heading": "Explorations",
        "label": "Exploration",
        "title_col": 0, "doc_col": None, "body_col": 1,
        "default_status": "exploration",
    },
]

TEASER_CHARS = 340


# ---------------------------------------------------------------- markdown ---

def md_inline(text):
    """Escape HTML, then honour the inline markdown Backlog.md actually uses."""
    parts = text.split("`")
    out = []
    for i, part in enumerate(parts):
        if i % 2 == 1:                     # inside a code span
            out.append("<code>" + html.escape(part) + "</code>")
            continue
        esc = html.escape(part)
        esc = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', esc)
        esc = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", esc)
        esc = re.sub(r"(?<!\*)\*([^*]+?)\*(?!\*)", r"<em>\1</em>", esc)
        out.append(esc)
    return "".join(out)


def strip_md(text):
    """Plain text, for measuring a teaser and for --list."""
    text = re.sub(r"`([^`]*)`", r"\1", text)
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"(?<!\*)\*([^*]+?)\*(?!\*)", r"\1", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    return text.strip()


def teaser_of(body):
    """The opening of a note, cut at a sentence boundary near TEASER_CHARS."""
    plain = strip_md(body)
    if len(plain) <= TEASER_CHARS:
        return body, False
    window = plain[:TEASER_CHARS]
    cut = max(window.rfind(". "), window.rfind("; "))
    if cut < TEASER_CHARS // 2:
        cut = window.rfind(chr(32))
    return plain[:cut + 1].strip(), True


# ------------------------------------------------------------------ parsing ---

def split_row(line):
    """Cells of a markdown table row, minus the leading/trailing pipe."""
    line = line.strip()
    if not line.startswith("|"):
        return None
    cells = line.split("|")
    return [c.strip() for c in cells[1:-1]] if len(cells) >= 3 else None


def status_of(body, default):
    """The leading `status` token of a body cell, minus its date and qualifier.

    Backlog.md writes these as `done 2026-08-21`, `exploration 2026-08-20`,
    `unblocked -- ready to build`, `design 2026-08-19, nothing built` -- the
    date and anything past a comma or dash are prose. But `in progress` is two
    words and both of them are the status, so this cannot just take the first.
    """
    match = re.match(r"^\s*`([^`]+)`", body)
    if not match:
        return default
    token = re.split(r"[,—–]|\s-\s", match.group(1))[0]
    token = re.sub(r"\s*\(?\d{4}-\d{2}-\d{2}\)?.*$", "", token)
    return token.strip().lower() or default


def strip_status(body):
    """The body cell with its leading status token and dash removed."""
    return re.sub(r"^\s*`[^`]+`\s*(\((`[^`]+`|[^)]*)\)\s*)?[—–-]*\s*",
                  "", body).strip()


def slugify(title):
    slug = re.sub(r"[^a-z0-9]+", "-", strip_md(title).lower()).strip("-")
    return slug[:64] or "item"


def parse_backlog(path):
    with open(path, encoding="utf-8") as handle:
        lines = handle.read().splitlines()

    items, seen_slugs = [], {}
    section, spec = None, None

    for line in lines:
        if line.startswith("## "):
            name = strip_md(line[3:]).strip()
            spec = next((s for s in SECTIONS if name.startswith(s["heading"])), None)
            section = name if spec else None
            continue
        if line.startswith("### "):
            # A subheading ends the parent section's table ("Deferred until
            # needed", "Loose follow-ups") -- neither is queued work.
            spec, section = None, None
            continue
        if spec is None:
            continue

        cells = split_row(line)
        if not cells or len(cells) <= max(spec["title_col"], spec["body_col"]):
            continue
        if set("".join(cells)) <= set("-:" + chr(32)):     # the |---|---| separator
            continue
        title_raw = cells[spec["title_col"]]
        if title_raw in ("Item", "Decision", "Doc"):
            continue

        body_raw = cells[spec["body_col"]]
        status = status_of(body_raw, spec["default_status"])
        if status == "done":
            continue

        # A Decisions row keeps title and body in one cell: the leading bold
        # run is the decision, the rest is the note.
        if spec["body_col"] == spec["title_col"]:
            bold = re.match(r"^\*\*(.+?)\*\*", title_raw)
            if bold:
                title_raw, body_raw = bold.group(1), title_raw[bold.end():]
            else:
                body_raw = ""

        doc_raw = cells[spec["doc_col"]] if spec["doc_col"] is not None else ""
        body = strip_status(body_raw)
        teaser, truncated = teaser_of(body)

        slug = slugify(title_raw)
        seen_slugs[slug] = seen_slugs.get(slug, 0) + 1
        if seen_slugs[slug] > 1:
            slug = f"{slug}-{seen_slugs[slug]}"

        items.append({
            "slug": slug,
            "title": md_inline(strip_md(title_raw)),
            "titleText": strip_md(title_raw),
            "doc": strip_md(doc_raw),
            "status": status,
            "kind": spec["label"],
            "section": section,
            "teaser": md_inline(teaser),
            "full": md_inline(body),
            "truncated": truncated,
        })

    return items


# --------------------------------------------------------------------- html ---

PAGE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Pick the next thing &mdash; JitterTravel backlog</title>
<style>
:root {
  --bg: #f6f7f9;      --panel: #ffffff;   --ink: #16181d;   --muted: #5f6672;
  --line: #dfe3e9;    --accent: #1f6feb;  --accent-ink: #ffffff;
  --chip: #eef1f5;    --win: #12805c;     --shadow: 0 1px 2px rgba(16,20,28,.06), 0 8px 24px rgba(16,20,28,.06);
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #14161a;    --panel: #1c1f25;   --ink: #e8eaee;   --muted: #98a1b0;
    --line: #2c313a;  --accent: #4b8ffb;  --accent-ink: #0b1220;
    --chip: #262b33;  --win: #3ecf9a;     --shadow: 0 1px 2px rgba(0,0,0,.4), 0 8px 24px rgba(0,0,0,.3);
  }
}
* { box-sizing: border-box; }
html, body { max-width: 100%; overflow-x: hidden; }
body {
  margin: 0; background: var(--bg); color: var(--ink);
  font: 15px/1.55 -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
}
code { font: 0.88em/1.4 ui-monospace, SFMono-Regular, Menlo, monospace;
       background: var(--chip); padding: .1em .32em; border-radius: 4px; overflow-wrap: anywhere; }
a { color: var(--accent); }

.wrap { max-width: 1180px; margin: 0 auto; padding: 24px 20px 64px; }
header h1 { font-size: 20px; margin: 0 0 4px; letter-spacing: -.01em; }
header p  { margin: 0 0 18px; color: var(--muted); font-size: 13.5px; }

.bar { height: 6px; background: var(--chip); border-radius: 99px; overflow: hidden; margin-bottom: 8px; }
.bar > div { height: 100%; background: var(--accent); width: 0; transition: width .35s ease; }
.meter { display: flex; flex-wrap: wrap; gap: 4px 14px; font-size: 13px; color: var(--muted); margin-bottom: 22px; }
.meter b { color: var(--ink); font-weight: 600; }

.layout { display: grid; grid-template-columns: minmax(0,1fr) 320px; gap: 24px; align-items: start; }
@media (max-width: 900px) { .layout { grid-template-columns: minmax(0,1fr); } }

.question { font-size: 17px; font-weight: 600; margin: 0 0 14px; }
.pair { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 700px) { .pair { grid-template-columns: minmax(0,1fr); } }

.card {
  background: var(--panel); border: 1px solid var(--line); border-radius: 12px;
  padding: 16px 16px 14px; cursor: pointer; text-align: left; color: inherit;
  font: inherit; box-shadow: var(--shadow); display: flex; flex-direction: column;
  transition: border-color .12s, transform .12s;
}
.card:hover, .card:focus-visible { border-color: var(--accent); transform: translateY(-1px); outline: none; }
.card .key {
  display: inline-block; min-width: 20px; text-align: center; margin-right: 8px;
  border: 1px solid var(--line); border-radius: 5px; font-size: 11px; padding: 1px 5px; color: var(--muted);
}
.card h2 { font-size: 16px; margin: 0 0 8px; line-height: 1.35; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.tag { font-size: 11px; text-transform: uppercase; letter-spacing: .04em;
       background: var(--chip); color: var(--muted); padding: 2px 7px; border-radius: 99px; }
.tag.status-open        { color: #b26b00; }
.tag.status-partial     { color: #b26b00; }
.tag.status-in          { color: var(--win); }
.tag.status-decision    { color: var(--accent); }
.tag.status-exploration { color: var(--muted); }
.note { font-size: 13.5px; color: var(--muted); margin: 0; overflow-wrap: anywhere; }
.note strong { color: var(--ink); font-weight: 600; }
.doc { margin-top: auto; padding-top: 12px; font-size: 12px; color: var(--muted); }
.more { background: none; border: 0; padding: 6px 0 0; font: inherit; font-size: 12.5px;
        color: var(--accent); cursor: pointer; align-self: flex-start; }

.controls { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
button.plain {
  background: var(--panel); border: 1px solid var(--line); border-radius: 8px;
  padding: 7px 13px; font: inherit; font-size: 13px; color: var(--ink); cursor: pointer;
}
button.plain:hover:not(:disabled) { border-color: var(--accent); }
button.plain:disabled { opacity: .45; cursor: default; }
button.plain kbd { color: var(--muted); font: inherit; font-size: 11px; margin-left: 5px; }

aside { background: var(--panel); border: 1px solid var(--line); border-radius: 12px;
        padding: 14px 16px; box-shadow: var(--shadow); }
aside h3 { font-size: 12px; text-transform: uppercase; letter-spacing: .05em;
           color: var(--muted); margin: 0 0 10px; }
ol.rank { list-style: none; margin: 0; padding: 0; }
ol.rank li { display: grid; grid-template-columns: 22px minmax(0,1fr) auto; gap: 8px;
             padding: 6px 0; border-bottom: 1px solid var(--line); font-size: 13px; align-items: baseline; }
ol.rank li:last-child { border-bottom: 0; }
ol.rank .n { color: var(--muted); font-variant-numeric: tabular-nums; }
ol.rank .t { overflow-wrap: anywhere; }
ol.rank .s { color: var(--muted); font-size: 11.5px; font-variant-numeric: tabular-nums; white-space: nowrap; }
ol.rank li.top .t { font-weight: 600; }
ol.rank li.unseen .t { opacity: .5; }

.verdict { background: var(--panel); border: 1px solid var(--win); border-left-width: 4px;
           border-radius: 10px; padding: 14px 16px; margin-bottom: 18px; box-shadow: var(--shadow); }
.verdict h2 { margin: 0 0 4px; font-size: 17px; }
.verdict p  { margin: 0; color: var(--muted); font-size: 13.5px; }
.hidden { display: none !important; }
.export { margin-top: 14px; display: flex; gap: 8px; flex-wrap: wrap; }
footer { margin-top: 28px; color: var(--muted); font-size: 12.5px; }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <h1>Pick the next thing to work on</h1>
    <p>__COUNT__ open items from <code>docs/Backlog.md</code>, generated __STAMP__.
       Answers are kept in this browser &mdash; regenerate the page any time and it picks up where you left off.</p>
    <div class="bar"><div id="bar"></div></div>
    <div class="meter" id="meter"></div>
  </header>

  <div id="verdict" class="verdict hidden">
    <h2 id="verdict-title"></h2>
    <p id="verdict-note"></p>
  </div>

  <div class="layout">
    <main>
      <p class="question">If you could only implement one of these next, which would you choose?</p>
      <div class="pair" id="pair"></div>
      <div class="controls">
        <button class="plain" id="skip">Skip this pair <kbd>s</kbd></button>
        <button class="plain" id="undo">Undo <kbd>u</kbd></button>
        <button class="plain" id="reset">Start over</button>
      </div>
    </main>

    <aside>
      <h3>Standings</h3>
      <ol class="rank" id="rank"></ol>
      <div class="export">
        <button class="plain" id="copy">Copy as Markdown</button>
        <button class="plain" id="download">Download JSON</button>
      </div>
    </aside>
  </div>

  <footer>
    Scores are Bradley-Terry strengths (log-odds) fitted to your answers with a
    weak prior, so an item you have compared once is drawn toward 0 rather than
    off the scale. &plusmn; is one standard error at the mode.
    &ldquo;Settled&rdquo; is a bootstrap: the share of 200 resamplings of your own
    answers that put the same item on top &mdash; it stays at 0 until every item
    has been compared at least once. Pairs are chosen to maximise expected
    information: close scores, wide bands, not asked recently.
  </footer>
</div>

<script>
const ITEMS = __ITEMS__;
const KEY = "jittertravel-pick-next-v1";
const PRIOR_VAR = 4.0;          // theta ~ N(0, 2^2): keeps an unbeaten item finite
const SETTLED_AT = 0.90;
const SLUGS = new Set(ITEMS.map(i => i.slug));

let state = load();
let current = null;             // {a, b} slugs, in display order
let fit = null;

function load() {
  let raw = null;
  try { raw = localStorage.getItem(KEY); } catch (e) { /* private window */ }
  const blank = { comparisons: [], skips: [] };
  if (!raw) return blank;
  try {
    const parsed = JSON.parse(raw);
    return {
      // Drop history that names an item the backlog no longer has, so editing
      // Backlog.md never invalidates the answers about everything else.
      comparisons: (parsed.comparisons || []).filter(c => SLUGS.has(c[0]) && SLUGS.has(c[1])),
      skips: (parsed.skips || []).filter(c => SLUGS.has(c[0]) && SLUGS.has(c[1])),
    };
  } catch (e) { return blank; }
}

function save() {
  try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* ignore */ }
}

const sigmoid = x => 1 / (1 + Math.exp(-x));

const ITEM_INDEX = new Map(ITEMS.map((it, i) => [it.slug, i]));

/* Per-item list of [opponentIndex, wonFlag] for a set of comparisons. */
function buildGames(comparisons) {
  const games = ITEMS.map(() => []);
  for (const [w, l] of comparisons) {
    const wi = ITEM_INDEX.get(w), li = ITEM_INDEX.get(l);
    games[wi].push([li, 1]);
    games[li].push([wi, 0]);
  }
  return games;
}

/* Coordinate-Newton MAP fit of the Bradley-Terry model. `init` warm-starts it,
   which is what makes the bootstrap below affordable. */
function solve(games, init, passes) {
  const n = ITEMS.length;
  const theta = init ? Float64Array.from(init) : new Float64Array(n);
  for (let pass = 0; pass < passes; pass++) {
    let biggest = 0;
    for (let i = 0; i < n; i++) {
      let grad = -theta[i] / PRIOR_VAR, hess = 1 / PRIOR_VAR;
      for (const [j, won] of games[i]) {
        const p = sigmoid(theta[i] - theta[j]);
        grad += won - p;
        hess += p * (1 - p);
      }
      let step = grad / hess;
      if (step > 1) step = 1; else if (step < -1) step = -1;
      theta[i] += step;
      biggest = Math.max(biggest, Math.abs(step));
    }
    if (biggest < 1e-10) break;
  }
  return theta;
}

/* Marginal standard error from the observed information at the mode. It is
   what the ± column shows; it is deliberately NOT used to decide when the
   leader is settled, because it ignores how strongly the scores co-vary. */
function fitModel() {
  const games = buildGames(state.comparisons);
  const theta = solve(games, null, 300);
  const se = new Float64Array(ITEMS.length);
  for (let i = 0; i < ITEMS.length; i++) {
    let info = 1 / PRIOR_VAR;
    for (const [j] of games[i]) {
      const p = sigmoid(theta[i] - theta[j]);
      info += p * (1 - p);
    }
    se[i] = 1 / Math.sqrt(info);
  }
  return { idx: ITEM_INDEX, theta, se, count: games.map(g => g.length) };
}

function leaderOf(theta) {
  let best = 0;
  for (let i = 1; i < theta.length; i++) if (theta[i] > theta[best]) best = i;
  return best;
}

/* How often the same item comes out on top when the answers themselves are
   resampled with replacement. A nonparametric bootstrap rather than a draw
   from the Laplace approximation: with a handful of lopsided comparisons the
   marginal standard errors stay wide even though the ordering is not remotely
   in doubt, so sampling them independently would say "unsettled" forever. */
function settled(f) {
  // No claim until every item has been weighed twice. One comparison each is
  // enough to produce a leader but not enough for the bootstrap to mean
  // anything: resampling a set where most items have a single game just
  // reproduces that thinness and reports false confidence.
  if (f.count.some(c => c < 2)) return 0;
  const comps = state.comparisons, m = comps.length;
  const leader = leaderOf(f.theta);
  const draws = 200;
  let same = 0;
  for (let b = 0; b < draws; b++) {
    const resampled = new Array(m);
    for (let k = 0; k < m; k++) resampled[k] = comps[(Math.random() * m) | 0];
    if (leaderOf(solve(buildGames(resampled), f.theta, 40)) === leader) same++;
  }
  return same / draws;
}

function ranked(f) {
  return ITEMS.map((it, i) => ({ ...it, theta: f.theta[i], se: f.se[i], seen: f.count[i] }))
              .sort((x, y) => y.theta - x.theta || x.titleText.localeCompare(y.titleText));
}

function pairKey(a, b) { return [a, b].slice().sort().join("|"); }

function history() {
  const asked = new Map();
  const bump = (a, b, w) => asked.set(pairKey(a, b), (asked.get(pairKey(a, b)) || 0) + w);
  for (const [w, l] of state.comparisons) bump(w, l, 1);
  for (const [a, b] of state.skips) bump(a, b, 3);   // a skip means "don't ask me that"
  return asked;
}

/* Expected information gain: an outcome we cannot predict (p near .5), about
   items we are unsure of (wide bands), that we have not just asked about --
   weighted toward the top of the list, because the question being answered is
   "what next", not "please totally order the bottom of my backlog". Without
   that weight the chooser spends its questions separating items 30 and 31,
   which are equally informative in the abstract and equally useless here.

   The weight is how plausibly an item could still BE the leader, not how far
   behind it currently sits. That distinction is what stops a rich-get-richer
   trap: an item that won its first comparison is nominally on top, and a
   weight based on position alone would pour every later question into
   confirming it while a genuine contender three rows down is never asked
   about again. Scored against its own uncertainty, an item one point behind
   with a wide band is still a contender and keeps getting asked. */
function choosePair(f) {
  const asked = history();
  const leader = leaderOf(f.theta);
  const contender = new Float64Array(ITEMS.length);
  for (let i = 0; i < ITEMS.length; i++) {
    const spread = Math.sqrt(f.se[i] * f.se[i] + f.se[leader] * f.se[leader]);
    contender[i] = 0.02 + sigmoid(1.702 * (f.theta[i] - f.theta[leader]) / spread);
  }
  const last = state.comparisons.length
    ? pairKey(state.comparisons[state.comparisons.length - 1][0],
              state.comparisons[state.comparisons.length - 1][1])
    : null;
  // Seed phase: until every item has been weighed twice -- the floor `settled`
  // insists on -- every question must involve one of the least-weighed items.
  const scarcest = Math.min(...f.count);
  const seeding = scarcest < 2;

  let best = null, bestScore = -1;
  for (let i = 0; i < ITEMS.length; i++) {
    for (let j = i + 1; j < ITEMS.length; j++) {
      if (seeding && f.count[i] !== scarcest && f.count[j] !== scarcest) continue;
      const key = pairKey(ITEMS[i].slug, ITEMS[j].slug);
      if (key === last && ITEMS.length > 2) continue;
      const p = sigmoid(f.theta[i] - f.theta[j]);
      const score = p * (1 - p)
                  * (f.se[i] * f.se[i] + f.se[j] * f.se[j])
                  * Math.max(contender[i], contender[j])
                  / (1 + (asked.get(key) || 0))
                  * (0.85 + 0.3 * Math.random());   // break ties without bias
      if (score > bestScore) { bestScore = score; best = [ITEMS[i].slug, ITEMS[j].slug]; }
    }
  }
  if (!best) return null;
  return Math.random() < 0.5                        // no left/right position bias
    ? { a: best[0], b: best[1] }
    : { a: best[1], b: best[0] };
}

function bySlug(slug) { return ITEMS.find(i => i.slug === slug); }

function cardHtml(item, key) {
  const statusClass = "status-" + item.status.replace(/[^a-z]/g, "").slice(0, 11);
  return `<button class="card" data-slug="${item.slug}">
      <h2><span class="key">${key}</span>${item.title}</h2>
      <div class="tags">
        <span class="tag ${statusClass}">${item.status}</span>
        <span class="tag">${item.kind}</span>
      </div>
      <p class="note" data-teaser>${item.teaser}</p>
      ${item.truncated ? `<p class="note hidden" data-full>${item.full}</p>
                          <span class="more" data-more>Show the full note</span>` : ""}
      ${item.doc ? `<div class="doc">&rarr; <code>${item.doc}</code></div>` : ""}
    </button>`;
}

function render() {
  fit = fitModel();
  const f = fit, order = ranked(f), conf = settled(f);

  document.getElementById("bar").style.width = Math.min(100, conf / SETTLED_AT * 100) + "%";
  const unseen = f.count.filter(c => c === 0).length;
  document.getElementById("meter").innerHTML =
      `<span><b>${state.comparisons.length}</b> comparisons</span>` +
      `<span>leader <b>${(conf * 100).toFixed(0)}%</b> settled</span>` +
      (unseen ? `<span><b>${unseen}</b> not yet seen</span>` : "") +
      (state.skips.length ? `<span>${state.skips.length} skipped</span>` : "");

  const verdict = document.getElementById("verdict");
  if (conf >= SETTLED_AT) {
    verdict.classList.remove("hidden");
    document.getElementById("verdict-title").innerHTML = "Work on: " + order[0].title;
    document.getElementById("verdict-note").textContent =
      `Settled at ${(conf * 100).toFixed(0)}% after ${state.comparisons.length} comparisons. `
      + `Runner-up: ${order[1].titleText}. Keep going to sharpen the rest of the list.`;
  } else {
    verdict.classList.add("hidden");
  }

  document.getElementById("rank").innerHTML = order.map((it, i) => `
    <li class="${i < 3 ? "top" : ""} ${it.seen === 0 ? "unseen" : ""}">
      <span class="n">${i + 1}</span>
      <span class="t">${it.title}</span>
      <span class="s">${it.seen === 0 ? "&mdash;" : it.theta.toFixed(2) + " ±" + it.se.toFixed(2)}</span>
    </li>`).join("");

  current = choosePair(f);
  const pair = document.getElementById("pair");
  if (!current) { pair.innerHTML = "<p class='note'>Nothing left to compare.</p>"; }
  else { pair.innerHTML = cardHtml(bySlug(current.a), "1") + cardHtml(bySlug(current.b), "2"); }

  document.getElementById("undo").disabled = state.comparisons.length === 0;
}

function choose(winnerSlug) {
  if (!current) return;
  const loser = winnerSlug === current.a ? current.b : current.a;
  state.comparisons.push([winnerSlug, loser]);
  save();
  render();
}

document.getElementById("pair").addEventListener("click", e => {
  const more = e.target.closest("[data-more]");
  if (more) {                                   // expanding a note is not a vote
    e.stopPropagation();
    const card = more.closest(".card");
    card.querySelector("[data-teaser]").classList.add("hidden");
    card.querySelector("[data-full]").classList.remove("hidden");
    more.remove();
    return;
  }
  const card = e.target.closest(".card");
  if (card) choose(card.dataset.slug);
});

document.getElementById("skip").addEventListener("click", () => {
  if (current) { state.skips.push([current.a, current.b]); save(); render(); }
});
document.getElementById("undo").addEventListener("click", () => {
  state.comparisons.pop(); save(); render();
});
document.getElementById("reset").addEventListener("click", () => {
  if (state.comparisons.length && !confirm("Throw away all " + state.comparisons.length + " answers?")) return;
  state = { comparisons: [], skips: [] }; save(); render();
});

document.addEventListener("keydown", e => {
  if (e.metaKey || e.ctrlKey || e.altKey) return;
  if (e.key === "1" && current) choose(current.a);
  else if (e.key === "2" && current) choose(current.b);
  else if (e.key === "s") document.getElementById("skip").click();
  else if (e.key === "u") document.getElementById("undo").click();
});

function asMarkdown() {
  const order = ranked(fit), conf = settled(fit);
  const lines = [
    "# Backlog priority — " + new Date().toISOString().slice(0, 10),
    "",
    `${state.comparisons.length} pairwise comparisons over ${ITEMS.length} open items; `
      + `leader ${(conf * 100).toFixed(0)}% settled.`,
    "",
    "| # | Item | Kind | Status | Score | ± | Comparisons |",
    "|---|---|---|---|---|---|---|",
  ];
  order.forEach((it, i) => lines.push(
    `| ${i + 1} | ${it.titleText} | ${it.kind} | \`${it.status}\` | `
    + `${it.seen ? it.theta.toFixed(2) : "—"} | ${it.seen ? it.se.toFixed(2) : "—"} | ${it.seen} |`));
  return lines.join("\n");
}

document.getElementById("copy").addEventListener("click", async e => {
  try {
    await navigator.clipboard.writeText(asMarkdown());
    e.target.textContent = "Copied";
    setTimeout(() => { e.target.textContent = "Copy as Markdown"; }, 1400);
  } catch (err) {
    // Clipboard is blocked on file:// in some browsers; fall back to a prompt.
    window.prompt("Copy the ranking:", asMarkdown());
  }
});

document.getElementById("download").addEventListener("click", () => {
  const order = ranked(fit);
  const payload = {
    generated: new Date().toISOString(),
    comparisons: state.comparisons,
    skips: state.skips,
    ranking: order.map((it, i) => ({
      rank: i + 1, slug: it.slug, title: it.titleText, kind: it.kind,
      status: it.status, doc: it.doc, score: it.theta, se: it.se, comparisons: it.seen,
    })),
  };
  const url = URL.createObjectURL(new Blob([JSON.stringify(payload, null, 2)],
                                           { type: "application/json" }));
  const a = document.createElement("a");
  a.href = url; a.download = "backlog-priority.json"; a.click();
  URL.revokeObjectURL(url);
});

render();
</script>
</body>
</html>
"""


def build_html(items, stamp):
    return (PAGE
            .replace("__ITEMS__", json.dumps(items, ensure_ascii=False))
            .replace("__COUNT__", str(len(items)))
            .replace("__STAMP__", html.escape(stamp)))


# --------------------------------------------------------------------- main ---

def main():
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)

    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--backlog", default=os.path.join(root, "docs", "Backlog.md"))
    parser.add_argument("--out", default=os.path.join(root, "target", "pick-next-feature.html"),
                        help="where to write the page (default: target/, which is gitignored)")
    parser.add_argument("--no-open", action="store_true", help="write the file, don't open a browser")
    parser.add_argument("--list", action="store_true", help="print the parsed items and exit")
    args = parser.parse_args()

    if not os.path.exists(args.backlog):
        sys.exit(f"no backlog at {args.backlog}")

    items = parse_backlog(args.backlog)
    if not items:
        sys.exit(f"parsed no open items out of {args.backlog} -- has its table shape changed?")

    if args.list:
        width = max(len(i["titleText"]) for i in items)
        for item in items:
            print(f"{item['kind']:<12} {item['status']:<12} {item['titleText']:<{width}}  {item['doc']}")
        print(f"\n{len(items)} open items")
        return

    stamp = f"from {os.path.relpath(args.backlog, root)}"
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as handle:
        handle.write(build_html(items, stamp))

    print(f"{len(items)} open items -> {args.out}")
    for kind in dict.fromkeys(i["kind"] for i in items):
        print(f"  {sum(1 for i in items if i['kind'] == kind):>3}  {kind}")
    if not args.no_open:
        webbrowser.open("file://" + os.path.abspath(args.out))


if __name__ == "__main__":
    main()
