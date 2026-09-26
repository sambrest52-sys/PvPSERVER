# Changelog

## 1.1.0 - Kits and arenas overhaul

### Kits
- Rebuilt every kit to match 1.8-style practice servers (Minemen, Hypixel Duels, PvP Land):
  - **NoDebuff**: Diamond Prot IV / Unbreaking III, Sharpness III / Unbreaking III, 16 pearls, 30 splash
    Instant Health II, three Speed II (8:00) potions, food. No hunger, no natural regen.
  - **Debuff**: NoDebuff plus two splash Slowness and two splash Poison, with fewer health potions.
  - **Gapple**: Diamond Prot IV, Sharpness V, 64 god apples, Speed/Strength II, spare armour, no natural regen.
  - **Combo**: no hit delay, a new juggling knockback profile (more vertical, less horizontal), god apples.
  - **Sumo**: empty hands, no damage, no hunger; lose by leaving the platform or touching water.
  - **Boxing**: fists only, no armour, Speed II, first to 100 hits, no damage or health display.
  - **BuildUHC**: iron/diamond Prot II mix, bow, rod, buckets, blocks, golden apples and heads; only placed
    blocks break and the map resets after the match.
  - **Classic**: iron armour, iron sword, rod and bow with 1.8 regen.
  - **Bridge**: first to 5 goals, respawn at base on death, team-coloured blocks, the arrow regenerates 3.5 s
    after each shot, and building is limited to the arena's build area.
- Added **Soup** (instant-heal stew), **Archer** and **Spleef** (only snow can be dug).
- Every kit has a unique icon and a description shown in the queue, duel and kit editor menus.
- New kit keys: `description`, `arena-blacklist`, custom potion `effects` on items. New rules: `soup`,
  `soup-heal`, `breakable`, `arrow-regen`. Unknown rule keys are reported when kits load.
- Kit editor layouts are tied to the kit's contents and reset when a kit's items change.
- `kits.yml` and `kb.yml` are versioned. Upgrading keeps your old file as `<name>.v1.bak`.
- `/pvpadmin kit <kit> [player]` hands out a kit for inspection.

### Arenas
- A procedural generator builds 21 themed arenas from code:
  - 5 open maps: Colosseum, Mossy Ruins, Frozen Lake, Nether Keep, Sky Temple
  - 4 sumo platforms: Dojo, Lotus Pond, Sky Ring, Islet
  - 3 boxing rings: Championship, Old Gym, Rooftop
  - 3 BuildUHC terrains: Plains, Taiga, Mesa
  - 3 bridge maps: The Bridge, Sunken Ruins, Nether Crossing
  - 3 spleef maps: Snow Bowl, Snow Layers, Lava Pit
- Built-in arenas are installed once and tracked in `arenas/generated.yml`, so deleted ones stay deleted.
  `/arena generate <name|all>` rebuilds them. The four old placeholder arenas are disabled (not deleted) on
  upgrade.
- **Importing**: `/arena import` reads Sponge `.schem` (v1-3), legacy MCEdit `.schematic` and `.arena` files
  from `plugins/PvPCore/imports/`. It uses `[A]`/`[B]`/`[Spectator]`/`[Goal A]`/`[Goal B]` sign markers, or
  guesses spawns from the terrain. `/arena importworld` loads a world folder for capturing.
- Arenas can have a build area (`/arena buildarea`). Kits can blacklist arenas.
- Random arena selection is now uniform across the arenas a kit allows.
- Performance:
  - pool pre-warming pastes in a background queue, so match, FFA and editor pastes always go first
  - one pre-warmed copy per arena by default
  - templates larger than a pool slot are rejected
  - solid-block indexing no longer boxes integers
- `/arena edit` gives each arena a stable editor slot and clears the previous paste, so edits no longer overlap.
- FFA falls back to an arena the kit allows when its configured template is missing.

### Fixes
- The ghost-block resync ignores synthetic place events without a clicked block.
