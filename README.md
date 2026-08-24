# LAUN

Minimal by default. Instant when you need it.

LAUN is a monochrome, cyberpunk-corpo application launcher. The screen stays
completely empty until you act. One click expands a full-screen honeycomb of
your applications, growing outward from the center, with per-tile color
tagging and a fast outside-in reveal built for clarity over clutter.

This repository currently holds the interaction and visual-style prototype
used to lock in the launcher's final direction before implementation. It is a
single self-contained page — no build, no install, no server.

------------------------------------------------------------
STATUS / LICENSE
------------------------------------------------------------

Open-source software licensed under the GNU General Public License v3.0.

Early-stage prototype. Interaction model and visual language only — not yet
the full launcher application.

------------------------------------------------------------
SUPPORT THE PROJECT
------------------------------------------------------------

Ko-fi: https://ko-fi.com/eversorhn
PayPal: https://paypal.me/FAMarco
Bitcoin: `bc1qv92c3eyeqvhgfnez7spfd7v2aytkhpshsl65yv`

------------------------------------------------------------
LOCAL FIRST / PRIVATE BY DESIGN
------------------------------------------------------------

- Runs entirely in your browser. No backend, no server component.
- No account, no telemetry, no analytics.
- No network calls at runtime beyond loading the page's own web font.
- Nothing you do in the prototype leaves your machine.

------------------------------------------------------------
OFFICIAL SOURCE
------------------------------------------------------------

Author: eVersor-HN
Official repository: https://github.com/eVersor-HN/LAUN

------------------------------------------------------------
DOWNLOAD / INSTALL
------------------------------------------------------------

1. Clone or download this repository.
2. Open `demo.html` directly in a modern browser.
3. Click anywhere on the empty screen to expand the launcher grid.

No build step, no dependencies, no configuration.

------------------------------------------------------------
FEATURES
------------------------------------------------------------

CONTROL
- Empty by default — the launcher only appears when you ask for it.
- Adjustable tile size and tile count, live.
- Per-tile color tagging via long-press, without losing the label.

WORKFLOW
- Full-screen honeycomb grid, spaced evenly, growing outward from the center.
- Fast outside-in reveal motion tuned for a single, deliberate open action.
- Click the background (not a tile) to collapse the grid again.

PRIVACY
- Fully local. Nothing is transmitted, stored remotely, or tracked.

------------------------------------------------------------
BUILD FROM SOURCE
------------------------------------------------------------

There is no build step. `demo.html` is a single static HTML file with inline
CSS and JavaScript. Any current desktop browser (Chrome, Edge, Firefox,
Safari) can run it directly from disk.

------------------------------------------------------------
SECURITY / PRIVACY NOTES
------------------------------------------------------------

- The page loads one external resource: the Google Fonts stylesheet for its
  display typefaces. No other network access occurs.
- No user data is collected, stored, or transmitted.
- This is a UI/interaction prototype, not a functioning application launcher.
  It does not read, list, or launch anything on your system.

------------------------------------------------------------
SYSTEM REQUIREMENTS
------------------------------------------------------------

- Any OS with a current browser supporting CSS `clip-path` and custom
  properties (all evergreen browsers).
- No GPU, minimum memory, or runtime requirement.

------------------------------------------------------------
LICENSE
------------------------------------------------------------

GNU General Public License v3.0. See [LICENSE](LICENSE) for the full text.

------------------------------------------------------------
THIRD-PARTY NOTICES
------------------------------------------------------------

- Typefaces "Chakra Petch" and "JetBrains Mono", loaded via Google Fonts,
  each under the SIL Open Font License 1.1.
