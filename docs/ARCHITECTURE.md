# Architecture

Four Paper plugins built from one Maven reactor. `PvPCore` owns every shared system; the three
gamemode plugins depend on it (`depend: [PvPCore]`) and never on each other. Cross-gamemode calls
(for example the lobby hotbar opening the duel queue menu) go through small *bridge* interfaces that
live in core and are registered by whichever plugin implements them.

```
                   ┌──────────────────────────── PvPCore ────────────────────────────┐
                   │ api/  Practice (static accessor), PracticeApi, bridges, events  │
                   │ storage/  Hikari (SQLite | MySQL/MariaDB) + repositories        │
                   │ profile/ rank/ stats/ kit/ knockback/ combat/ party/ arena/     │
                   │ scoreboard/ chat/ moderation/ cosmetic/ anticheat/ gui/ hotbar/ │
                   └───────▲──────────────────────▲───────────────────────▲──────────┘
                           │ bridges              │ bridges               │ bridges
                    PvPLobby                 PvPDuels                  PvPFFA
            spawn, protection,        queues + ELO matcher,     arenas per kit, instant
            hotbar, menus, kit        match lifecycle, rounds,  respawn, killstreaks,
            editor, holograms         requests, party fights,   safe zone, combat log
                                      spectating, snapshots
```

## Module layout

| Module  | Jar          | Responsibility |
|---------|--------------|----------------|
| `core`  | PvPCore.jar  | Config + MiniMessage messages, async storage, profiles/settings, ranks (+LuckPerms), kits + per-player layouts, knockback profiles, combat rules (hit delay, pearl cooldown, fake deaths, combat tag, ghost block fix), parties, arena templates + pooled instancing, flicker-free sidebars + tab, chat/PMs/filter/slow mode, moderation, cosmetics, CPS/reach alerts, stats + leaderboards, GUI + hotbar frameworks, `/pvpadmin` |
| `lobby` | PvPLobby.jar | Spawn hub, void/damage/hunger protection, double jump, lobby hotbar, queue/stats/settings/cosmetics/spectate/leaderboard menus, kit editor, TextDisplay leaderboard holograms |
| `duels` | PvPDuels.jar | 1v1/2v2 ranked+unranked queues with widening ELO range, matches (countdown → freeze → fight → end), best-of-N rounds, kit rule enforcement (sumo, boxing, bridge, build limits), `/duel` requests, party split / party vs party / party FFA, spectators, post-match inventory viewer, rematch |
| `ffa`   | PvPFFA.jar   | Per-kit FFA arenas (ranked and unranked), kit selection, instant respawn, killstreak rewards, safe spawn zone, combat tag + combat-log punishment, decaying placed blocks |
| `dist`  | –            | Copies the four jars into `server/plugins/` during `mvn package` |

## Key design rules

* **Main thread never blocks on IO.** Every repository call returns a `CompletableFuture` running on
  core's storage executor. Results hop back with `Tasks.sync(...)`. The only intentional `join()` is in
  `AsyncPlayerPreLoginEvent`, which Paper already runs off the main thread.
* **Player state machine.** `PlayerStateService` tracks `LOBBY, QUEUE, MATCH, SPECTATING, FFA,
  EDITING`. Every gamemode checks and transitions state through it, which is how duplicate queueing
  and "party member is busy" cases are rejected in one place.
* **Fake deaths.** Players never actually die in managed states. Lethal damage is cancelled and a
  `PracticeDeathEvent` is fired, so there is no death screen, instant FFA respawn and clean match ends.
* **Arenas are disposable.** Templates are stored in a compact custom schematic format
  (`arenas/<name>.arena`, gzip palette + indices). Matches borrow a pasted instance from a per-template
  pool inside a void world. Changed blocks are journaled and rolled back when the instance is returned.
  The arena world is deleted and recreated on every start, which also makes crash cleanup free.
* **Per-tick budgets.** Arena pasting, sidebar refreshes and leaderboard hologram updates are spread
  across ticks with configurable limits.
* **Arena content is code.** `arena/gen` builds the built-in arenas deterministically on a Bukkit-free
  `TemplateBuilder`, and `arena/io` reads NBT schematics (Sponge v1-3, legacy MCEdit). Both are unit-tested without
  a server. `ArenaImporter` and `ArenaEditor` bring imported maps into the pool through the normal editor.
* **Pure logic is Bukkit-free.** ELO (`EloCalculator`), parties (`Party`) and queue matching
  (`QueueMatcher`) have no Bukkit dependencies and are covered by JUnit tests.
