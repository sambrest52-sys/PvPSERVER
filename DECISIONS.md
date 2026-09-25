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
  separate class loaders the way Paper does and runs through:
  * joining and the lobby hotbar
  * ranked queue matchmaking into a pasted arena, ending with ELO **persisted to SQLite and checked**
  * a sumo void fall
  * a `/duel` request sent through the GUI, accepted and forfeited
  * FFA fake death and respawn
  * party commands, moderation commands, `/pvpadmin reload`, `/kb set`
  * disabling all plugins mid-match

  The smoke suite found and fixed three real bugs: invalid `plugin.yml` YAML, fragile armor clearing, and a
  deprecated event listener.
