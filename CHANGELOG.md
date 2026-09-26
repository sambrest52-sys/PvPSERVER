# Changelog

## 1.2.0 - The lobby rebuild

### A generated hub
- The lobby now lives in its own void world, `pvp_lobby`, built from code on first start: a floating hub about
  150 blocks across, behind an invisible barrier and a world border, with noon forever, no weather or mobs, and
  peaceful difficulty.
  - **Spawn plaza**: fountain with a quartz spire and water basins, a star mosaic in the zones' colours, a
    crossed-swords medallion at spawn, quartz pillars with banners and lanterns, planters, benches and cherry trees.
  - **Eight themed zone islands** joined by railed bridges:
    - Ranked Hall: quartz rotunda, dome and beacon.
    - Unranked Hall: prismarine temple with pools.
    - FFA Gate: blackstone colosseum.
    - Hall of Fame: leaderboard wall and podium.
    - Cosmetics Shop.
    - Info Pavilion: rules and links.
    - Party Lounge: campfire, string lights, jukebox.
    - Kit Workshop: anvils, forge, chimney.
  - **Sky parkour**: 30 jumps and 3 checkpoints spiralling around a crystal spire, with launch pads up and down.
  - A **skyline** of floating islands and crystal spires.
- Same seed, same lobby: `world.seed` in `config.yml`. `/lobby regenerate [seed]` rebuilds it, clearing exactly
  the blocks pasted before.

### Interactive features
- **NPCs** without any NPC plugin: player-shaped Mannequins with skins, armour, held items, glow and a live
  hologram (queued and fighting counts per mode). Left or right click runs the NPC's action, and their heads follow
  nearby players. Nine by default: Ranked, Unranked, FFA, Kit Editor, Stats, Leaderboards, Cosmetics, Party, Info.
- **Portals**: walk into the Ranked or Unranked portal to open that queue, or into the FFA gate to join FFA
  directly. Portals have particle curtains, a push-back and a cooldown.
- **Launch pads**: velocities are solved against player physics, so each pad lands on its target.
- **Clickable blocks** run actions: anvils and tables open the kit editor, lecterns show the rules, links and
  leaderboards, the enchanting table opens cosmetics, and a bell starts a party fight.
- **Parkour**: start, checkpoint and finish plates; action bar timer; falls return you to the last checkpoint.
  Personal and server records, a best times hologram, reward commands, a parkour hotbar (checkpoint, restart,
  leave) and no flying during a run.
- **Ten hidden eggs**. Finding all of them grants permissions (by default the Emerald trail and the Totem join
  effect) and can run reward commands.
- **Leaderboard wall**: one panel per ranked kit plus a global one, cycling ELO, Wins and Best Win Streak. Panels
  read the cached leaderboards, and kit icons spin above them.
- **Ambience**: particle emitters (fountain spray, blossoms, sparkles, smoke, soul flames, notes), zone names in
  the action bar, a rotating tips boss bar, timed chat announcements, and a welcome title with a sound.
- **Lobby cosmetics**: 8 particle trails and 5 join effects in `/cosmetics` (new `trails` and `join-effects`
  sections in `cosmetics.yml`).
- Sidebar placeholders `<zone>`, `<parkour_time>`, `<parkour_best>`, `<eggs_found>`, `<eggs_total>` and a
  `parkour` flag.

### Custom lobbies
- `/lobby import <file|folder>` loads a Sponge `.schem`, a legacy `.schematic` or a whole world folder from
  `plugins/PvPLobby/imports/`. Signs such as `[spawn]`, `[npc ranked]`, `[portal ranked]`, `[pad]`,
  `[parkour start]`, `[checkpoint 1]`, `[egg]`, `[button kit editor]`, `[zone ranked 15]`, `[particles fountain]`
  and `[wall]` place everything; the signs are removed (plates, pads and eggs replace their own signs). An
  imported lobby overrides the generated one until `/lobby regenerate`.
- Positions live in `layout.yml` (edit and `/lobby reload`, or use `/lobby set spawn|npc|hologram|wall|zone`,
  `/lobby pad`, `/lobby egg`, `/lobby button`, `/lobby parkour ...`, `/lobby remove ...`). Every rebuild keeps
  the previous file as `layout.yml.bak`.
- `world.mode: custom` keeps using an existing world as it is, without pasting anything.

### Configuration and commands
- New files: `layout.yml` (positions), `npcs.yml` (versioned: NPC looks, texts and actions), `data.yml`
  (parkour times and eggs).
- `config.yml` has new sections for the world, pads, portals, NPCs, parkour, eggs, the wall, ambient particles,
  zones, the boss bar, announcements, the welcome title, lobby cosmetics and performance limits. Values are
  validated: a bad value falls back to its default with a warning naming the key.
- `/pvpadmin reload` and `/lobby reload` reload the lobby's settings, NPCs and layout and respawn its entities.
- `/lobby` is now the lobby admin command. Players typing it still go to spawn; `/spawn`, `/hub` and `/l` are
  unchanged.

### Performance and protection
- About 55 lobby entities in the default hub, non-persistent and tagged, with a hard cap.
- Trigger lookups only run when a player crosses into another block.
- Hologram counts are captured once per interval and rendered off the main thread; text updates are applied a
  few per tick.
- Ambient particles are spread across ticks and only sent to nearby players.
- The lobby world blocks natural mob spawns, nether portals, and tampering with item frames, armour stands and
  NPCs, on top of the existing damage, hunger, block and item protection.

### Fixes and internals
- The hotbar no longer fires its item's action when a click is used by something else (an NPC or a clickable
  block).
- The core leaderboard cache never runs two refreshes at once, and keeps the previous boards when a refresh fails.
- The smoke test harness fails, instead of silently skipping, when a plugin hits a MockBukkit gap while booting.

### Upgrading from 1.1.0
- New players spawn in the generated `pvp_lobby` hub. Your old `spawn` setting is only used with
  `world.mode: custom` (set `world.name` to your old lobby world to keep it).
- The `/lobby` alias of `/spawn` became the lobby command; `/lobby` alone still teleports players to spawn.

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
