# Decisions

Choices made while building this project without stopping to ask, with the reasoning behind each.

## Platform and build

* **Paper 1.21.11** is the target. It is the last 1.21.x release; Minecraft moved to year-based versions (26.x)
  afterwards. `api-version: '1.21'` keeps the plugins loadable on any 1.21 patch.
* **Four Bukkit-style plugins (`plugin.yml`) rather than Paper plugins (`paper-plugin.yml`).** Classes shared
  through `depend` are simple and well understood. Commands still use Paper's modern Brigadier API
  (`LifecycleEvents.COMMANDS` + `BasicCommand`), so every command gets proper tab completion and is hidden from
  players without permission.
* **Only HikariCP is shaded** (relocated to `net.pvpserver.core.libs.hikari`). Paper already ships the SQLite and
  MySQL drivers, Gson and SnakeYAML, so the jars stay small (core ~560 KB).
* **The `dist` module copies jars to `server/plugins/`** so `mvn clean package` produces a ready-to-run folder.
* **MockBukkit smoke tests live in a Maven profile** (`-Psmoke`) instead of the default build. MockBukkit
  lags behind Paper releases, so a MockBukkit gap should not break a normal build.

## Architecture

* **Core owns everything shared; gamemodes never depend on each other.** Cross-gamemode features (the lobby
  hotbar opening the duel queue, `/party fight`, spectating from a report alert) go through small *bridge*
  interfaces in core (`QueueBridge`, `MatchBridge`, `SpectateBridge`, `FfaBridge`, `LobbyBridge`,
  `KitEditorBridge`). If a plugin is missing, the feature says "not available" instead of failing.
* **One authoritative player state machine** (`LOBBY, QUEUE, MATCH, SPECTATING, FFA, EDITING`) in core. Duplicate
  queueing, dueling busy players, "party member is in a match" and most other edge cases reduce to one state check.
* **Fake deaths.** Lethal damage to players in MATCH or FFA is cancelled and a `PracticeDeathEvent` is fired.
  There is no death screen, no item drops and no respawn packets, which is what practice servers do. FFA
  respawns instantly and matches end cleanly.
* **Knockback uses Paper's `EntityKnockbackByEntityEvent`**, not NMS or packets. The formula mirrors 1.8's
  (friction divisor, base horizontal/vertical, sprint/enchant extra, vertical cap). The first ENTITY_ATTACK
  knockback in a tick is the base hit and later ones in the same tick are the extra, which matches the order
  vanilla calls them in.
* **1.8-style combat** is kit-driven: `old-combat` sets the attack-speed attribute to 1024 (no cooldown) and
  cancels sweep damage. `hit-delay` sets `maximumNoDamageTicks`. `regen: LEGACY` reimplements 1.8 food regen,
  and `misc.disable-relative-projectile-velocity` in Paper's config gives 1.8-feeling pots. Damage values are
  vanilla. A full 1.8 damage table is listed in TODO.

## Arenas

* **Custom template format instead of WorldEdit/FAWE schematics.** The format is a gzip palette plus one short per
  block. It removes a heavy dependency, lets pastes be fully budgeted (blocks and milliseconds per tick), and
  makes placeholder arenas trivial to generate in code.
* **Pooled instances in a disposable void world.** Copies sit on a grid 512 blocks apart. A match borrows an
  idle copy (or a new one is pasted). Every block change is journaled (place, break, fluids, explosions, fire,
  forms, falling blocks) and rolled back when the copy returns to the pool. The world is deleted and recreated on
  every start, which also handles crash cleanup. Chunk tickets keep in-use copies loaded to avoid load/unload churn.
* **Kit ↔ arena matching** uses arena tags (`standard`, `build`, `sumo`, `bridge`) with an optional per-kit
  whitelist, so new arenas work for all matching kits without editing kits.
* **Four placeholder arenas** (classic, nether, sumo, bridge) are generated when the template folder is empty,
  instead of shipping binary files.
* **FFA pastes templates once** into its own disposable world. Player-placed blocks decay after a delay
  instead of being journaled.

## Kits (1.1)

* **Kits follow the brief first, then Minemen / Hypixel / PvP Land conventions.** For example, NoDebuff has no
  natural regen and no hunger as requested, while Feather Falling IV boots and 16 pearls follow Minemen. "Gapple"
  uses god apples (enchanted golden apples), as practice servers do. Boxing is fought with fists and Speed II.
* **Speed II for 8 minutes is a custom potion effect.** No vanilla potion has that combination, so kit items
  gained an `effects` key.
* **Versioned kit files instead of merging.** Merging defaults key by key would silently resurrect deleted kits and
  items, and would mix old and new loadouts on upgrade. `kits.yml` and `kb.yml` carry a `config-version`; an
  outdated file is backed up and replaced.
* **Layouts carry a kit fingerprint** (material and amount per slot). A layout saved for an older version of a kit
  would move items to nonsense slots, so stale layouts silently fall back to the default. Enchantment changes do
  not invalidate layouts.
* **Soup, spleef and bridge behaviour are kit rules** (`soup`, `breakable`, `arrow-regen`), not special cases in
  the duels plugin, so they also work for new kits.

## Arenas (1.1)

* **Arenas are generated by code, not shipped as files.** The generator is deterministic (seeded noise), so
  `/arena generate` always recreates the same map, and new versions can add arenas without binary blobs in the
  repository. Unit tests check every arena:
  * spawns stand on solid ground with free space above
  * liquids cannot flow
  * block ids and state properties are valid for 1.21.11
  * sizes fit the pool grid
  * bridge goals are open pits
  * every kit has at least 3 arenas
* **Fences, panes, bars and walls get their connection states from a post-pass** over the template. Pastes skip
  physics for speed, so shapes would otherwise stay as lone posts.
* **Barriers make the borders.** Walls stay low enough to look good, and invisible barriers above them stop
  climbing or pearling out. Build kits also get a build area and build limit, enforced when blocks are placed.
* **`generated.yml` records installed built-ins.** This lets an update add new arenas while arenas the admin
  deleted stay deleted. The four v1 placeholders are only disabled on upgrade, never deleted.
* **Uniform random selection.** Picking any arena with an idle copy made arenas that were used less often come up
  more often. Now the arena is chosen first, and a copy is pasted if none is idle; a 100x100 map pastes in a few
  ticks.
* **Background pre-warming.** Startup pastes (21 arenas) run in a separate queue that only proceeds when no match,
  FFA, editor or reset job is waiting.
* **Import through the editor.** Imported maps open as a normal edit session so an admin can check spawns and tags
  before saving. There is also a one-step `save` flag. Marker signs reuse what map builders already place. Legacy
  `.schematic` files use Paper's own legacy tables instead of a hand-made id table.

## Lobby (1.2)

* **The lobby gets its own void world (`pvp_lobby`).** Pasting a hub into the main world could destroy someone's
  build, and a dedicated world makes the border, fixed time, peaceful difficulty and "no mobs" rules safe to apply.
  A marker file (`pvplobby.yml`) in the world folder records what built it. A world that exists without the
  marker is used as it is, with a warning, and never pasted over. `world.mode: custom` keeps any world (for example
  the main world) untouched.
* **The hub is generated, not shipped as a schematic.** It reuses the arena generator's canvas: the same
  deterministic seed, block-state validation and preview renderer. So it is unit-tested without a server (safe
  spawn, NPC and hologram spots, walkable portals, contained water, valid block states, pads that land, parkour
  jumps within sprint-jump reach) and adds nothing binary to the repository.
* **Positions live in `layout.yml`, looks and behaviour in `npcs.yml` and `config.yml`.** The generator and the
  importer write `layout.yml` (backing up the old one), admins edit it or use `/lobby set`, and `/lobby reload`
  applies it. The same layout model serves the generated hub, schematic imports and world imports.
* **Rebuilds clear exactly what was pasted.** The pasted template is saved next to the marker, and a regenerate or
  import clears those blocks with the tick-budgeted paster before pasting the new ones. Clearing a whole 200x100x200
  box would check four million blocks. The first build is pasted synchronously on startup, before anyone can join
  (about 146k blocks, roughly half a second).
* **NPCs are Paper Mannequins**, player-shaped entities with profiles, equipment and poses, plus a text display.
  This avoids Citizens and packet-level fake players, survives restarts by respawning (entities are
  non-persistent and tagged), and costs two entities per NPC. Clicks come from `PlayerInteractEntityEvent` and
  `PrePlayerAttackEntityEvent`, so both mouse buttons work.
* **Sign tags for imports** (`[npc ranked]`, `[portal ranked]`, `[parkour start]`...) because builders already
  place signs, and WorldEdit keeps them in schematics. Arena marker signs (`[A]`, `[B]`) keep their meaning for
  arena imports; the lobby importer ignores signs the schematic reader already removed.
* **Launch pad velocities are solved, not tuned by hand.** `LaunchMath` simulates the player's airborne drag and
  gravity to find the lowest arc that reaches the target, and a unit test flies every pad to check it lands on
  solid ground inside the barrier. Imported pads (`[pad 3]`) use a simple forward-and-up push instead.
* **Parkour times and found eggs are kept in `data.yml`**, not the SQL database: they are small and lobby-only,
  and a YAML file keeps the storage schema unchanged. It is written in the background at most every 5 s and on
  shutdown. A network running several lobbies would move this to SQL (see TODO).
* **Live holograms: snapshot on the main thread, render off it.** Queue and match counts come from bridges that
  are not thread-safe, so they are read once per interval on the main thread (a few map lookups). MiniMessage
  parsing and text building happen asynchronously. Only changed texts are queued and applied a few per tick.
* **Hidden eggs are dragon and turtle eggs.** Sniffer eggs schedule a hatch tick when placed, turtle eggs only
  hatch on sand, and dragon eggs only teleport when clicked (clicks are cancelled). The eggs cannot disappear.
* **`/lobby` became the lobby admin command.** For players it still teleports to spawn, so muscle memory keeps
  working; `/hub` and `/l` stay as aliases of `/spawn`.
* **A config.yml from 1.1 reads the new sections from the bundled defaults.** Bukkit getters that take an explicit
  fallback ignore the defaults of a merged file. The lobby settings reader avoids them, and a unit test loads an old
  file on top of the defaults.

## Data

* **Profiles load in `AsyncPlayerPreLoginEvent`**, which Paper already runs off the main thread. This is the only
  place storage is used synchronously. Everything else returns `CompletableFuture`s on a dedicated executor and
  hops back to the main thread with `Tasks.sync`.
* **SQLite uses one pooled connection** in WAL mode. SQLite allows a single writer, and a single connection avoids
  `SQLITE_BUSY` and keeps writes ordered. MySQL uses a normal Hikari pool.
* **Settings, cosmetics and ignore lists are JSON columns** on the players table. They are always loaded together
  and never queried, so extra tables would only add joins.
* **Kit layouts are stored as a slot permutation** (`from:to,...`), not serialized items. Changing a kit's items
  never corrupts saved layouts, and unknown or new items fall back to their default slot.
* **Leaderboards are cached** and refreshed every 5 minutes by sequential async queries. Menus and holograms
  never touch the database.
* **Only queue and duel matches record stats.** Party fights are for fun. Ranked ELO uses team averages for 2v2.
  Cancelled matches (shutdown, admin cancel, arena failure) record nothing.

## Gameplay rules chosen

* Ranked matching requires the rating gap to fit in *both* players' search windows, which grow over time.
  This stops a long-waiting high-rated player from pulling in a brand-new low-rated one.
* A 2v2 queue accepts parties of 2 and solos. Solos are combined into teams by closest rating.
* Disconnecting mid-match is a forfeit and counts as a loss in queue/duel matches. Leaving while combat-tagged in
  FFA is a death credited to the last attacker, with an extra rating penalty in ranked FFA.
* Reconnecting players always land in the lobby with a clean state. Stale matches, queue entries and party invites
  were already cleaned up when they quit.
* The kit editor saves when its menu is closed, since players expect that. Cancel is an explicit button.
* Spectators use ADVENTURE mode with flight rather than SPECTATOR mode, so they can use hotbar items. They are
  hidden from fighters with per-plugin `hidePlayer`, and projectiles pass through them.
* The CPS/reach "anticheat" is **alert-only** by design (per the brief): it fires a cancellable `CheckAlertEvent`
  and never punishes.
* Built-in ranks support `*` and `node.*` wildcards by expanding them to registered permissions. Bukkit has no
  native wildcard support.

## Environment notes (how this was built and verified)

* The build sandbox could not reach `repo.papermc.io` or `fill.papermc.io`. To compile against the **real** API,
  the official `paper-api` sources for 1.21.11 (and Mojang's Brigadier) were cloned from GitHub and compiled into
  the local Maven repository. Nothing from that workaround is part of the project: on your machine Maven downloads
  `paper-api` from `repo.papermc.io` as declared in the POM.
* For the same reason a real Paper server could not be started in the sandbox; Paperclip also needs Mojang's server
  jar. The substitute is the MockBukkit smoke suite (`mvn -Psmoke verify`). It loads the four built jars with
  separate class loaders the way Paper does and plays 22 scenarios. The full list is in the README. It covers every
  gamemode, every kit win condition, parties, spectators, the kit editor, arena authoring, holograms, moderation and
  reconnect handling. Across the whole suite the server log has no warnings or errors.
* **Mock gaps are filled in the test harness and must fail loudly.** MockBukkit leaves some Paper methods
  unimplemented, and its `UnimplementedOperationException` makes JUnit report a test as *skipped*. The suite turns
  that into a failure, so a gap is noticed and filled in `PracticeServerMock` (text display billboards, per-plugin
  chunk tickets and so on) instead of hiding a scenario.
* **Where MockBukkit differs from Paper, tests assert on the event.** `simulatePlayerMove` ignores
  `PlayerMoveEvent#setTo`, which freeze, the countdown and the FFA safe zone use to push players back. Tests read the
  event's final destination, which is what Paper applies. The chunk a player stands in is also not "loaded" in the
  mock until requested.
* **MySQL/MariaDB was verified against a real MariaDB 10.11** with the Connector/J 9.2.0 driver that Paper bundles.
  Two runs cover it: the storage contract (the same tests as SQLite), and a full server boot that plays a ranked
  match and reloads the rating on rejoin. Both run for `MYSQL` and `MARIADB`. They are skipped unless
  `-Dpvp.test.mysql.host` is given, so the default build needs no database.
* MockBukkit's block-state parser cannot apply some valid 1.21 properties: lantern `hanging`, chain `axis`, snow
  `layers`, and wall/pane/bar connections. The test harness falls back to the block's default state for block ids
  that exist, and the generator's unit tests validate every property against a schema, so real errors are not
  hidden. Legacy schematic conversion (Paper's `UnsafeValues.fromLegacy`) is not available in MockBukkit. Its NBT
  parsing and palette remapping are unit-tested; the conversion itself is untested here.
* The smoke suite found and fixed four real bugs:
  * invalid `plugin.yml` YAML
  * fragile armor clearing
  * a deprecated event listener
  * the ghost-block fix throwing when another plugin fires a `BlockPlaceEvent` with no clicked block
* New arenas created with `/arena create` start with the `standard` tag, so they work for most kits right away.
  Use `/arena tag remove standard` for special arenas such as sumo rings.
* **The lobby scenarios move players the way Paper does.** MockBukkit places the player at the destination
  *before* firing `PlayerMoveEvent` and ignores `setTo`. The lobby redirects falls with `setTo`, as Paper expects,
  and the harness's `move()` applies the event's final destination, as Paper does. MockBukkit's
  `UnimplementedOperationException` is a JUnit "aborted" exception; the harness now turns it into a failure during
  setup as well, after it silently skipped a whole test class.
