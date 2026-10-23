# Base Scanner — Fabric mod for Minecraft 1.21.4

A client-side mod that notices storage blocks as you explore, scans a configurable
20×20 area around them, lists everything it finds, and estimates a heuristic
**Base Likelihood** score with the evidence behind it.

Everything comes from data the server has already sent to your client. Unloaded
chunks are reported as "not examined" (they lower the confidence rating) and are
never guessed at.

---

## 1. Requirements

| Thing | Version |
|---|---|
| Minecraft Java Edition | 1.21.4 |
| Fabric Loader | 0.16.10 or newer (built against 0.16.14) |
| Fabric API | 0.119.x+1.21.4 (built against 0.119.4+1.21.4) |
| Java (JDK) to build | 21 |

No other mods are required. The settings screen is built in (no Cloth Config / Mod Menu).

## 2. Build

1. Install a **JDK 21** (e.g. Eclipse Temurin 21) and make sure `java -version` shows 21.
2. Open a terminal in this folder and run:
   - Windows: `gradlew.bat build`
   - macOS / Linux: `./gradlew build` (run `chmod +x gradlew` first if needed)
3. The first build downloads Gradle 8.12.1, Minecraft 1.21.4, the mappings and Fabric API — this takes a few minutes.
4. The mod jar is created at `build/libs/base-scanner-1.0.0.jar`
   (ignore `-sources.jar`).

**IntelliJ IDEA:** *File → Open* → choose this folder → *Trust project*. IntelliJ imports the Gradle
project automatically. Use the Gradle tool window → *Tasks → build → build*, or *fabric → runClient*.

**VS Code:** install the *Extension Pack for Java* and *Gradle for Java*, open the folder, then run
`./gradlew build` or `./gradlew runClient` from the terminal. `./gradlew vscode` generates launch configs.

## 3. Install

1. Install Fabric Loader for 1.21.4 from <https://fabricmc.net/use/installer/>.
2. Put **Fabric API** for 1.21.4 into your `.minecraft/mods` folder
   (download from Modrinth or CurseForge).
3. Copy `build/libs/base-scanner-1.0.0.jar` into `.minecraft/mods`.
4. Start the game with the *fabric-loader-1.21.4* profile.

Settings are saved to `.minecraft/config/basescanner.json` and persist across restarts.

## 4. Test it quickly

The fastest path is a dev client: `./gradlew runClient` (starts Minecraft with the mod and Fabric API).

1. Create a **Creative** world.
2. Place a chest. Within about a second a green 20×20 outline appears around it and the HUD
   (top-left) shows `AREA SCANNER — 20×20` with `Very unlikely` (a single chest scores ≈0%).
3. Add more evidence inside the outline — a few more chests and barrels, a shulker box, a bed, a crafting
   table, furnaces, hoppers, torches, signs with text, farmland and crops. Watch the score climb to
   *Likely* / *Very likely* and the evidence list grow.
4. Place a chest about 15 blocks away from the first: the two areas merge into one outline instead
   of overlapping.
5. Press **B** to open the detail screen. Click a category to expand it, click a coordinate to
   navigate (a tracer line and pulsing box appear; the HUD shows distance and direction), and
   shift-click a category to toggle its ESP outlines.
6. Open a chest, put items in, close it — the HUD shows *Known contents* for the area.
7. Press **N** for settings. Change the area size, outline color, HUD position, category toggles and
   weights; leave and rejoin the world to confirm they were saved.
8. Walk 150+ blocks away: the area disappears; return and it is rediscovered automatically.
9. Spawn a villager and a tamed wolf inside the area to see entity detection and highlighting.

## 5. Controls (all rebindable in *Options → Controls → Key Binds → Base Scanner*)

| Key | Action |
|---|---|
| **B** | Open area details for the focused area |
| **N** | Open scanner settings |
| **H** | Hide / show the whole interface (scanner keeps running) |
| **J** | Cycle the HUD focus through areas (nearest first), then back to automatic |
| **G** | Clear the navigation target |
| **V** | Rescan now (also works when automatic scanning is off) |

**Which area does the HUD show?** The area of the storage block under your crosshair (for 10 s),
otherwise a pinned area (J or *Pin to HUD*), otherwise the area you are standing in, otherwise the nearest.

## 6. Features

### Automatic storage detection
Detected from block entities in loaded chunks: chests, trapped chests, ender chests, double chests
(counted once), barrels, all 17 shulker box colors, furnaces, blast furnaces, smokers, hoppers,
dispensers, droppers, crafters, chiseled bookshelves, decorated pots, and **any other block entity
that holds items** (including containers from other mods). Discovery runs every refresh interval
and immediately when block entities or chunks load.

### The scan area
* Default 20×20 horizontally, centered on the storage block; configurable 4–64.
* Vertical: **Limited** (storage Y ± range, default ±16) or **Full height** (whole column).
* Overlapping areas are merged (up to a configurable maximum span, default 80 blocks). With merging
  off, a storage block that is already inside an area joins it rather than creating a duplicate.
* Areas are scanned incrementally with a per-tick block budget (default 30,000 reads, all-air chunk
  sections are skipped in one step), so there is no frame spike.
* Rescanned when the server changes a block inside the area (mixin on `ClientWorld#handleBlockUpdate`),
  and periodically (default every 3 s). Entities refresh every 0.5 s.
* Unloaded columns are counted, not guessed. They reduce the *Confidence* rating.

### What is listed (each category toggles independently for detection, HUD and ESP)
* **Storage:** chests, shulker boxes (by color), barrels, other storage (furnaces, hoppers, dispensers, droppers…).
* **Infrastructure:** utility blocks (crafting tables, anvils, enchanting tables, brewing stands, beacons,
  respawn anchors, grindstones, smithing tables, lecterns…), beds, signs (text is read and shown),
  doors / trapdoors / fences / walls, light sources, redstone, valuable mineral blocks, ores,
  obsidian & other defensive blocks, farming, nether portals & End blocks, crafted building materials,
  decoration (banners, carpets, pots, heads, bookshelves), rails.
* **Entities:** players (when the server sends them), villagers & wandering traders, golems,
  tamed or name-tagged animals, other animals, hostile mobs, armor stands, item frames (with their item).
* **Other:** dropped items (with item totals), minecarts & boats, other entities.

For every category: total count, breakdown by type, coordinates and distance (detail screen),
click-to-navigate, and optional ESP outlines in the category's color.

### Storage content summaries
Minecraft only sends a container's contents when you open it. The mod remembers what was inside
containers **you opened** and summarizes them per area (HUD and detail screen). It cannot see inside
containers you have not opened.

### HUD and visuals
Compact panel with section headers, collapsible sections (click the section header in the detail
screen or use the HUD tab), a highlighted score with a progress bar, distance and direction arrow,
object totals, and the navigation target. Position (any corner + offset), scale, width, opacity and
compact mode are configurable. Outlines are thin translucent lines (no fills), drawn through walls
by default; thickness, opacity and through-wall mode are configurable separately for area outlines
and ESP.

### Alerts
Optional sound plus action-bar (or chat) messages for: a new storage area, an area crossing a Base
Likelihood threshold (default 60%), and another player inside an area. Rate-limited.

## 7. How the Base Likelihood score works

The score is a **heuristic estimate**, not a measured probability. It is always shown with the
evidence that produced it and a confidence rating.

**Step 1 — de-duplicated counts.** Every block or entity lands in exactly one category. Double chests,
beds (head only) and doors (lower half only) count once. Furnaces and hoppers count as smelting and
automation evidence and are excluded from the storage-cluster count, so they are not scored twice.

**Step 2 — saturating evidence.** Each evidence type contributes

```
points = weight × (1 − e^(−n / k))
```

where `n` is the count and `k` a saturation constant. The first few items matter most; 200 torches
are worth barely more than 30.

| Evidence | Default weight | k | Notes |
|---|---|---|---|
| Storage clustered together | 22 | 4 | uses `storage − 1`, so **one chest gives 0** |
| Different storage types | 10 | 3 | uses `types − 1` |
| Shulker boxes / ender chests | 14 | 1.5 | never generated naturally |
| Beds | 12 | 1.5 | |
| Crafting tables | 8 | 1 | combined with smelting when both exist |
| Furnaces / smokers / blast furnaces | 6 | 2 | |
| Advanced utility (distinct types) | 14 | 2 | enchanting table, anvil, brewing stand, beacon, respawn anchor, grindstone, smithing table, jukebox, conduit, lodestone |
| Hoppers + redstone | 10 | 8 | |
| Signs | 6 | 3 | |
| Signs with writing | 8 | 2 | |
| Item frames, armor stands, decoration | 8 | 5 | |
| Farming | 10 | 16 | |
| Villagers & golems | 6 | 3 | |
| Tamed / name-tagged animals | 12 | 1.5 | |
| Players present | 10 | 1 | |
| Obsidian / defensive blocks | 8 | 12 | |
| Valuable mineral blocks | 10 | 3 | ores are listed but **not** scored |
| Light sources | 7 | 10 | |
| Crafted building materials | 8 | 48 | |
| Lit nether portal | 8 | — | |

**Step 3 — synergy.** An evidence type that reaches at least 25% of its weight is a *strong indicator*.
Raw points are multiplied by `min(1, 0.50 + 0.13 × indicators)`, so several different kinds of evidence
together score much higher than a lot of one kind.

**Step 4 — percentage.** `percent = 100 × (1 − e^(−points / 45))`. If the only evidence is one storage
block, the result is capped at 15%.

**Bands:** 0–19 Very unlikely · 20–39 Unlikely · 40–59 Possible · 60–79 Likely · 80–100 Very likely.

**Confidence** reflects how much of the requested area was loaded and examined: High ≥ 95%,
Medium ≥ 60%, Low below that.

Sample results with default weights (computed by the mod's scorer):

| Scenario | Score |
|---|---|
| A single chest | 0% — Very unlikely |
| Mineshaft-style chest with a few torches and rails | 3% — Very unlikely |
| 2 chests, a crafting table and a furnace | 17% — Very unlikely |
| 16 storage blocks incl. 5 shulkers, furnaces, hoppers, 2 beds, crafting, torches, signs, farmland, villagers | 86% — Very likely |
| Natural village (beds, crops, villagers, planks) | 57% — Possible |

Villages are the main source of false positives (they naturally contain beds, crops, villagers and
crafted blocks). Lower the *Villagers & golems*, *Beds* and *Farming* weights if that matters to you.
All weights, the scale, the synergy parameters and the single-storage cap are editable in
*Settings → Scoring*.

## 8. Settings overview

* **General:** automatic scanning, hide entire interface, area outline, HUD, scoring, storage content
  summaries, merge overlapping areas, open details on storage look (optionally only while sneaking),
  reset everything.
* **Scanning:** area size, vertical mode, vertical range, refresh interval, maximum scan distance,
  maximum merged span, maximum tracked areas, blocks per tick, rescan now.
* **Outlines:** area outline color / focused color / opacity / thickness / through walls; block ESP,
  entity highlighting, ESP in every area, ESP through walls / thickness / opacity / box limit.
* **Categories:** per category Scan / HUD / ESP toggles and hex color.
* **HUD:** corner, offsets, scale, opacity, width, compact mode, evidence lines, coordinates,
  distance, which sections start expanded.
* **Scoring:** all weights and model parameters.
* **Alerts:** on/off, sound and volume, chat vs action bar, new area, high score + threshold, players.

## 9. Performance notes

* Discovery only reads block-entity maps of loaded chunks within the max scan distance.
* Block scans are time-sliced; lower *Blocks per tick* if you ever see a hitch.
* *Full height* mode scans up to 384 blocks per column; prefer *Limited* on weaker PCs.
* ESP boxes are capped per frame (default 400).

## 10. Troubleshooting

* **Build fails resolving a version:** check the current 1.21.4 versions at
  <https://fabricmc.net/develop> and update `gradle.properties`.
* **"Unsupported class file major version" / Java errors:** Gradle must run on JDK 21.
* **Game says Fabric API is missing:** install Fabric API for 1.21.4 in the mods folder.
* **No outline appears:** check that *Automatic scanning* and *Show area outline* are on, the chest
  category is enabled, and the interface is not hidden (H).

## 11. Using it on servers

The mod only reads what the server sends to your client, but ESP-style outlines and base detection are
treated as unfair advantages on many multiplayer servers. Check a server's rules before using it there.

## 12. Project layout

```
src/main/java/com/basescanner/
  BaseScannerClient.java      entrypoint, key bindings, event registration
  config/                     ModConfig, CategorySettings, ScoreWeights, ConfigManager (JSON)
  scan/                       ScanManager (discovery, clustering, scheduling), ScanJob (incremental
                              block scan), classifiers, results, ContainerMemory, Navigation, Alerts
  score/                      BaseScorer and ScoreResult
  render/                     world outlines, ESP and navigation tracer
  hud/                        HUD panel
  gui/                        settings screen, area detail screen, slider widget
  mixin/                      block update hook
src/main/resources/           fabric.mod.json, mixin config, language file, icon
```

License: MIT.
