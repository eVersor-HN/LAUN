# What's New in LAUNCHER

Built forward. Tracked clearly. Newest first.

---

## 0.4.0 — 2026-08-25

- Free Position Mode: drop a tile anywhere on screen, no grid snapping and no edge
  margin — tiles still push apart from each other automatically. Independent of the
  existing Free Tile Placement, and takes over from it while on.
- Per-tile size: a SIZE slider (and Reset Size) in the tile color menu lets any tile
  be scaled 50–200% on its own, with its label/icon text scaling to match.
- Long-press an app in the search picker to rename it (24 characters, shown on the
  tile and in folders) — Reset clears the override back to its real name.
- Color menu now also opens on its own after you hold a tile for a set time, with
  the delay (1–60s) configurable in Settings, instead of only firing on release.
- ALWAYS SHOW GRID: skip the empty tap-to-open screen entirely and always show the
  tiles — pair with Tile Reveal set to None for tiles that are just always there.
- The real Android system wallpaper is now automatically set to solid black
  whenever no wallpaper is shown in-app, so switching to another app never flashes
  the phone's actual wallpaper behind LAUNCHER. OLED BLACK background option added
  alongside the existing animated backgrounds, and Neuro Links' brightness/effect
  size are now adjustable instead of capped too dim.
- Settings reorganized: a new TILE INTERACTION section groups Free Tile Placement,
  Free Position Mode and Color Menu Delay, separate from pure grid geometry.
- Several real bugs fixed: dragging a tile could silently duplicate it; sliders
  could jump to a new value just from touching them while scrolling past; a tile
  moved off the hex grid in Free Position Mode could render clipped behind
  neighboring empty tiles; Reset to Defaults missed two of the newer settings;
  the WhatsApp-style color picker silently did nothing for tiles hidden beyond
  the current tile count; apps assigned to a hidden pinned tile could become
  permanently unreachable from the app picker.

## 0.3.0 — 2026-08-25

- App default language is now English (was hardcoded German throughout).
- Tile reveal animations run smoothly at any tile count — animation state is
  now read only at draw time instead of forcing full recomposition every
  frame, which had been the actual source of jank, not the animations
  themselves.
- Folders: assign more than one app to a tile to group them.
- Optional real Android wallpaper behind the grid, or one of 8 new native
  animated backgrounds (Neuro Links, Circuit Trace, Warp Tunnel, Server
  Grid, Threat Ping Map, Shard Drift, Cipher Scroll, Starfield Drift),
  each with its own opacity control.
- Tiles can be picked up (long-press, then drag) and dropped onto another
  tile — including empty ones, and spots that don't have a tile yet but
  have room for one, which grows the grid to include them.
- Status bar redesigned: clock left, status centered, battery/signal/WiFi/
  Bluetooth flush right, optional millisecond clock and battery percentage,
  animated charging indicator.
- About screen, in-app FAQ, and first-run hints (set as default launcher,
  long-press for Settings) — long-press on any empty spot now always opens
  Settings immediately, whether the grid is open or closed.
- Optional app icons on tiles, with an adjustable size that's always kept
  inset from the tile edge.
- Tile count is no longer restricted to symmetric ring totals — pick any
  exact count that fits, one tile at a time.
- Settings reorganized into GRID / APPEARANCE / ANIMATION / BACKGROUND /
  STATUS BAR / SYSTEM, with dedicated picker screens (live preview using
  your real tiles and colors) replacing the old horizontal scroll strips.
- Reset-to-defaults, and several real bugs fixed: a color-picker popup that
  could get stuck open, tiles clipping past the screen edge on small/narrow
  displays, the Android status bar and the grid both reacting to the same
  swipe-down gesture, and Settings not appearing until the finger lifted.

## 0.2.0 — 2026-08-24

- First real Android app build, alongside the existing HTML prototype.
- Real installed-app list with real icons, launched directly from the grid.
- Registers as a selectable/default Home app.
- Settings (tile size, tile count, HUD visibility, immersive mode) now
  persist across restarts.
- Immersive mode hides the system status and navigation bars independently
  of the app's own status readout.
- Tile count is now restricted to whole hex rings (1, 7, 19, 37, 61, 91) so
  the honeycomb is always a complete, symmetric shape.

## 0.1.0 — 2026-08-24

- First public prototype: click-to-reveal, full-screen hexagonal launcher grid.
- Honeycomb layout grows outward from the center as tiles are added.
- Outside-in reveal motion for the grid opening.
- Long-press a tile to tag it with one of 20 accent colors, without losing the label.
- Live controls for tile size and tile count.
