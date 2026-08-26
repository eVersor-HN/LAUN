# What's New in LAUNCHER

Built forward. Tracked clearly. Newest first.

---

## 0.7.3 — 2026-08-26

- Fixed: swiping up from empty background lit up tiles it happened to pass over on the way, as
  if they were being pressed — a swipe that starts on background now never picks up a tile just
  because the finger crossed over it.
- Fixed: on some devices, switching away and back (or even just an edge-swipe back gesture) can
  restart LAUN's process from scratch rather than simply resuming it. When that happens, the
  installed-app list takes a moment to reload, and every tile briefly rendered as empty before
  snapping to its real content once it finished — most visible with HIDE EMPTY TILES on, where it
  looked like every hidden slot flashing on before the real tiles caught up. The grid now stays
  blank for that brief moment instead of showing the wrong content and correcting itself, and the
  HIDE EMPTY TILES reveal-on-change flash no longer fires on that first frame either. Also widened
  the set of configuration changes LAUN handles itself (density, font scale, locale, layout
  direction) so a genuine system-level change is less likely to trigger this kind of restart in
  the first place.

## 0.7.2 — 2026-08-26

- Fixed: swiping up to search only ever counted the straight-line distance between where the
  finger went down and where it lifted — easing back down a little right before lifting (a very
  normal way to end a swipe) could measure short and fall through to launching whatever tile was
  underneath, or opening its picker if it was empty. Now the whole gesture's peak upward travel
  counts, and reaching it early blocks Settings, a tile's color menu, or picking the tile up to
  drag from also firing mid-swipe.
- Fixed: with HIDE EMPTY TILES on, a hidden tile stayed fully tappable — pressing exactly where
  an invisible tile sat still opened its app picker. The toggle's effect wasn't part of the
  grid's touch-handling setup, so switching it never actually took hold there until something
  unrelated happened to refresh it. Hidden tiles now correctly pass taps and swipes straight
  through to the background, while still lighting up as a valid drop target the moment an
  in-progress drag reaches them.
- Fixed: swiping in from the left or right edge could make tiles flash away and snap back — the
  system's predictive-back preview animation, which has nothing meaningful to show behind a
  launcher's own home screen. Turned off; the swipe still closes an open grid instantly, just
  without the animated preview around it.

## 0.7.1 — 2026-08-25

- Settings is now an accordion: each category (GRID, TILE INTERACTION, APPEARANCE, ANIMATION,
  BACKGROUND, STATUS BAR, SYSTEM) collapses to a single line, tap to expand it — only one open
  at a time, so opening Settings shows 7 short headers instead of the full list of every control
  at once.
- A toggle's own description (what it does, and what conflicts with it) is now always shown
  underneath it, not only after switching it on.

## 0.7.0 — 2026-08-25

- FREE TILE PLACEMENT is back as its own setting, alongside Free Position Mode and Snap Mode
  rather than replacing anything: drag a tile onto any open grid cell beyond your current
  COUNT — as much as still fits on screen at the current SIZE — and it pins there without
  swapping or moving any other tile. The empty landing spots only show up as faint markers
  while you're actually dragging.
- SPACING: extra gap between tiles, independent of tile size.
- MARGIN TOP / BOTTOM / LEFT / RIGHT: four independent screen-edge margins, replacing the old
  fixed 32dp on every side.
- HIDE EMPTY TILES: unassigned slots stay invisible until pressed, instead of always showing a
  "+" placeholder — still tappable in place to assign an app. Any layout change (SIZE, COUNT,
  SPACING, a MARGIN) briefly reveals them again so you can see what just changed.
- SETTINGS MENU DELAY: the background long-press that opens the main Settings sheet now has its
  own adjustable delay, matching the existing COLOR MENU DELAY for a tile's own menu.
- Tapping a slider's own label (SIZE, COUNT, SPACING, any MARGIN, either menu delay) now
  collapses Settings down to just that control, so the grid stays visible behind it while you
  drag — previously only SIZE and COUNT had this.
- A swipe up now always opens search, even if it started on a tile — previously an imprecise
  swipe that grazed a tile opened that tile's own app picker instead.
- Fixed: About Launcher's version was hardcoded and stuck at v0.3.0, three releases behind —
  now reads it from the installed app itself.
- Fixed: the small tile preview used by the Tile Reveal and Background Source pickers rendered
  overlapping, illegible text at its fixed miniature size.
- Fixed: with Hide Empty Tiles on, dragging toward an empty slot left it invisible, so you
  couldn't see your own drop target.
- Fixed: an app pinned via Free Tile Placement could fall outside the currently visible
  capacity and get stuck excluded from every app picker with no tile showing it anywhere.
- FAQ updated with swipe-to-search, renaming an app, and Snap Mode / Free Tile Placement's
  effect on moving a tile.

## 0.6.0 — 2026-08-25

- Choose an icon from any icon pack already installed on the phone (Delta, Redline, MBE Style, MaterialOS,
  Skyline, and any other pack that follows the standard icon-pack format) for any tile — same idea as Pie
  Launcher's icon-pack support, no pack-specific code needed. A chosen icon overrides the name/app-icon
  display regardless of the "app icons instead of name" setting, and is fully resettable back to the app's
  own icon.
- SNAP MODE: a new tile-drag mode alongside Free Position Mode — drag a tile anywhere and it snaps to
  whichever slot is closest when released, instead of needing to land precisely on a tile's hitbox.
- Animated wallpapers (Neuro Links, Server Grid, Cipher Scroll, ...) can now be tinted any color from
  Background > Source, not just left white.
- The wallpaper color picker is now a compact popup opened from a single row, instead of a swatch grid
  that required scrolling to reach.

## 0.5.0 — 2026-08-25

- COUNT is now a hard limit: exactly that many tiles are ever on screen, always. Dragging a tile
  can no longer silently grow COUNT, and a tile can no longer stay pinned-but-hidden far off
  beyond it — an app assigned to a slot beyond COUNT simply isn't shown until COUNT is raised or
  the app is reassigned through the picker. FREE TILE PLACEMENT is removed as a setting since its
  entire purpose (dropping beyond COUNT without growing it, or pinning it out of view) no longer
  applies; FREE POSITION MODE stays, now always among exactly COUNT tiles.
- Swipe up anywhere on the home screen (background, not a tile — open or closed) to search and
  launch an app directly, without going through a tile first. Typing enough of a name for only
  one app to still match launches it immediately on its own. The matched letters are highlighted
  in the results list — in this new search and in the existing tile app picker. The list also
  grows to use the freed space once the keyboard is dismissed.

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
