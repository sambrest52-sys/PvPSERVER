# PvP Practice Network

A Minemen Club / PvP.land style practice server for **Paper 1.21.11** (Java 21), split into four plugins built
from one Maven project:

| Plugin | What it does |
|--------|--------------|
| **PvPCore** | Shared services: async SQLite/MySQL storage, profiles and settings, ranks (+ LuckPerms), kits and per-player layouts, knockback profiles, combat rules, parties, arena templates with pooled instancing, sidebars and tab list, chat, moderation, cosmetics, CPS/reach alerts, ELO, stats, leaderboards |
| **PvPLobby** | Spawn hub: protection, double jump, hotbar, queue/stats/settings/cosmetics/leaderboard menus, kit editor, leaderboard holograms |
| **PvPDuels** | 1v1/2v2 ranked and unranked queues with a widening ELO window, matches with countdown, rounds, kit rules, results and inventory viewer, `/duel` requests, rematches, party fights, spectating |
| **PvPFFA** | Ranked and unranked FFA arenas with instant respawn, killstreaks, kill rewards, safe zones and combat-log punishment |

The architecture is described in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), design choices in
[DECISIONS.md](DECISIONS.md), and ideas for later in [TODO.md](TODO.md).

---

## Quick start

Requirements: **Java 21+**, **Maven 3.9+**. Internet access to `repo.papermc.io` (build) and `fill.papermc.io` (start script).

```bash
mvn clean package          # builds the plugins and copies them to server/plugins/
cd server
./start.sh                 # Windows: start.bat   (optional argument: heap size, e.g. ./start.sh 10G)
```

`start.sh` downloads the latest Paper 1.21.11 build and checks its SHA-256. It also **accepts the Minecraft EULA
on your behalf** (running it means you agree to https://aka.ms/MinecraftEULA) and starts Paper with Aikar's G1 flags.
Environment overrides: `MC_VERSION`, `MEMORY`, `UPDATE_PAPER=1`, `JAVA`.

The `server/` folder already contains tuned configs:

* `server.properties`: flat world, view distance 6, simulation distance 4, 250 slots, allow-flight for knockback.
* `bukkit.yml`, `spigot.yml`: no mob spawning, reduced activation and tracking ranges, player data saving off.
* `config/paper-*.yml`: no player collisions, nether off, Alternate Current redstone, pots ignore thrower
  momentum, anti-xray off.

It also has default copies of every plugin config in `plugins/PvP*/`.

On first start PvPCore generates four placeholder arenas (`classic`, `nether`, `sumo`, `bridge`). The server is
playable immediately: join, right-click the swords in your hotbar and queue.

### First steps as an admin

1. `/op <you>` (or `/rank set <you> owner`).
2. Stand where the lobby spawn should be and run `/setspawn`.
3. Optionally place leaderboard holograms: `/lbholo create top-nodebuff nodebuff ELO`.
4. Build your own arenas (see [Adding arenas](#adding-arenas)) and tune knockback with `/kb`.

### Useful build targets

| Command | Result |
|---------|--------|
| `mvn clean package` | Four jars in `*/target/` and copied to `server/plugins/` (unit tests included) |
| `mvn test` | Unit and SQLite integration tests (ELO, parties, queue matching, repositories...) |
| `mvn -Psmoke verify` | Also boots the real jars inside MockBukkit and plays 22 end-to-end scenarios (see below) |
| `... -Dpvp.test.mysql.host=127.0.0.1 -Dpvp.test.mysql.username=u -Dpvp.test.mysql.password=p` | Adds the MySQL/MariaDB runs: the repository contract and a full ranked match on the server (`-Dpvp.test.mysql.database`, default `practice_test`; `-Dpvp.test.mysql.type=MARIADB` to test that setting) |

The smoke scenarios cover:
- Boot, lobby join and hotbar, and clean shutdown mid-match.
- Ranked 1v1 with ELO saved to the database, and the ranked 2v2 queue with team ELO.
- Kit rules: sumo void falls, boxing's 100-hit win, and bridge goals with the build limit and block rollback.
- `/duel` requests, rematch, and the post-match inventory viewer.
- Party split, party FFA and party-vs-party fights.
- Spectators, the kit editor layout carrying into matches, and disconnect forfeits with a clean reconnect.
- Duplicate queueing, FFA death and combat logging, the safe zone, and creating an arena in-game.
- Leaderboard holograms, moderation (freeze, vanish, report, warn, tempban, history, unban) and private messages.

---

## Gameplay overview

* **Lobby**: hotbar with Unranked, Ranked, FFA, Party, Spectate, Leaderboards, Kit Editor, Stats and Settings.
  Players take no damage or hunger and are rescued from the void. Double jump can be toggled per player.
  The sidebar and tab list show live online, fighting and queued counts.
* **Queues**: pick a kit in the queue menu. Ranked pairs players whose ELO difference fits inside *both* players'
  search windows, which start at ±50 and grow by 25 every 5 s (configurable). Party leaders with 2 members
  queue 2v2. Solo players can also queue 2v2 and are paired into teams.
* **Matches**: an arena copy is taken from a pool, players are frozen for a 5 s countdown, then fight.
  Deaths are "fake deaths": no death screen. Results show ELO changes, clickable inventory buttons (final
  inventory, health, potions left, hits, longest combo) and a **[REMATCH]** button, then everyone returns to
  the lobby.
* **Kits** (in `kits.yml`): NoDebuff, Debuff, Gapple, Sumo, Boxing (first to 100 hits), Combo, BuildUHC,
  Classic (1.8 style) and Bridge (score goals). Each kit has its own rules.
* **FFA**: `/ffa` or the hotbar. Instant respawn with 2 s spawn protection, killstreak rewards, a spawn safe zone
  that combat-tagged players can't re-enter, and blocked commands while tagged. Logging out in combat counts as a
  death and credits the last attacker. Ranked FFA arenas change a per-kit FFA rating.
* **Parties**: `/party invite`. The leader can queue 2v2, split the party into two teams, start a party FFA, or
  challenge another party.
* **Spectating**: `/spectate <player>` or the Spectate menu. Spectators are invisible to fighters, can fly, and
  get a teleport compass and a leave item.

---

## Commands

All commands have tab completion. Player commands are granted by default through `default: true` permissions.

### Players

| Command | Aliases | Description | Permission |
|---------|---------|-------------|------------|
| `/spawn` | `/lobby`, `/hub`, `/l` | Return to the lobby (leaves queue/FFA/spectating) | `pvp.command.spawn` |
| `/queue join <kit> <ranked\|unranked> [1v1\|2v2]` | `/q` | Join a queue (also `/queue ranked`, `/queue unranked`, `/queue leave`) | `pvp.command.queue` |
| `/duel <player> [kit]` | `/1v1`, `/fight`, `/challenge` | Challenge a player (kit, arena, rounds menus) | `pvp.command.duel` |
| `/duel accept\|deny <player>` | | Answer a duel request | `pvp.command.duel` |
| `/rematch` | | Rematch your last opponent | `pvp.command.duel` |
| `/leave` | `/forfeit`, `/ff` | Forfeit a match, leave a queue or stop spectating | `pvp.command.leave` |
| `/spectate [player\|leave]` | `/spec`, `/watch` | Spectate a match or open the live match list | `pvp.command.spectate` |
| `/inventory <id>` | `/inv` | Post-match inventory viewer (used by chat buttons) | `pvp.command.inventory` |
| `/ffa [arena\|leave\|list]` | | Join or leave FFA | `pvp.command.ffa` |
| `/party <sub>` | `/p` | `create, invite, accept, join, leave, kick, promote, disband, open, close, info, chat, fight` | `pvp.command.party` |
| `/pc <message>` | `/partychat` | Party chat | `pvp.command.party` |
| `/kiteditor` | `/editkit` | Edit kit layouts | `pvp.command.kiteditor` |
| `/stats [player]` | `/profile` | Per-kit statistics | `pvp.command.stats` |
| `/leaderboard` | `/lb`, `/top` | Leaderboards menu | `pvp.command.leaderboard` |
| `/settings` | `/options`, `/prefs` | Scoreboard, duel requests, PMs, spectators, lobby players, cosmetics, join messages, double jump, time of day | `pvp.command.settings` |
| `/cosmetics` | | Kill effects, death animations, join messages | `pvp.command.cosmetics` |
| `/msg <player> <message>` | `/tell`, `/w`, `/m`, `/pm` | Private message | `pvp.command.msg` |
| `/reply <message>` | `/r` | Reply | `pvp.command.msg` |
| `/ignore <player\|list>`, `/unignore <player>` | | Ignore list (also blocks PMs, duels, invites) | `pvp.command.ignore` |
| `/report <player> <reason>` | | Report to staff (cooldown) | `pvp.command.report` |

### Staff and moderation

Durations: `30s`, `10m`, `12h`, `7d`, `2w`, `1mo`, `1y`, combinations like `1d12h`, or `perm`. Add `-s` to keep a punishment silent.

| Command | Description | Permission |
|---------|-------------|------------|
| `/ban <player> [reason] [-s]` | Permanent ban | `pvp.moderation.ban` |
| `/tempban <player> <duration> [reason] [-s]` | Temporary ban (`/tban`) | `pvp.moderation.tempban` |
| `/unban <player> [reason]` | Remove ban (`/pardon`) | `pvp.moderation.unban` |
| `/mute`, `/tempmute`, `/unmute` | Mutes (same syntax) | `pvp.moderation.mute` / `.tempmute` / `.unmute` |
| `/kick <player> [reason]` | Kick | `pvp.moderation.kick` |
| `/warn <player> <reason>` | Warning (title + record) | `pvp.moderation.warn` |
| `/history <player>` | Punishment history (`/punishments`) | `pvp.moderation.history` |
| `/freeze <player>` | Freeze for a screenshare (`/ss`) | `pvp.staff.freeze` |
| `/vanish` | Vanish (`/v`) | `pvp.staff.vanish` |
| `/staffchat [message]` | Staff chat or toggle (`/sc`) | `pvp.staff.chat` |
| `/reports` | Recent reports | `pvp.staff.reports` |
| `/alerts` | Toggle CPS/reach alerts | `pvp.staff.alerts` |
| `/socialspy` | See private messages (`/spy`) | `pvp.staff.socialspy` |
| `/chat <slow <s>\|clear\|mute\|unmute>` | Chat moderation | `pvp.chat.admin` |

### Administration

| Command | Description | Permission |
|---------|-------------|------------|
| `/pvpadmin reload` | Reload every config of all four plugins | `pvp.admin.reload` |
| `/pvpadmin status` | TPS, memory, player states, arena pool | `pvp.admin` |
| `/pvpadmin setelo <player> <kit> <elo>` / `resetstats <player> [kit]` | Stats admin | `pvp.admin.stats` |
| `/pvpadmin leaderboards` | Refresh leaderboards now | `pvp.admin` |
| `/kb list\|info\|set\|create\|delete\|default\|kit` | Edit knockback profiles live | `pvp.admin.kb` |
| `/rank set <player> <rank>`, `/rank list`, `/rank info <player>` | Built-in ranks (`/setrank`) | `pvp.admin.rank` |
| `/arena ...` | Arena authoring and pool control (see below) | `pvp.admin.arena` |
| `/matches [cancel <player>]` | List or cancel live matches | `pvp.admin.matches` |
| `/setspawn` | Set the lobby spawn | `pvp.admin.lobby` |
| `/lbholo create <id> <kit\|global> <stat>`, `delete`, `list`, `reload` | Leaderboard holograms | `pvp.admin.lobby` |
| `/fly` | Lobby flight | `pvp.lobby.fly` |

### Other permissions

| Permission | Default | Meaning |
|------------|---------|---------|
| `pvp.staff` | op | Staff member: includes chat, alerts, vanish, see-vanished, freeze, reports, socialspy, kick/warn/mute/tempban/history |
| `pvp.staff.vanish.see` | op | See vanished staff |
| `pvp.moderation.exempt` / `pvp.moderation.override` | false / op | Cannot be punished / may punish exempt players |
| `pvp.chat.color` | false | MiniMessage colours in chat |
| `pvp.chat.bypass` | op | Bypass slow mode, filter, chat mute |
| `pvp.party.large` | false | `party.max-size-large` instead of `party.max-size` |
| `pvp.lobby.build` | op | Build in the lobby while in creative |
| `pvp.cosmetic.<type>.<id>` | false | Cosmetics, e.g. `pvp.cosmetic.kill.lightning` (see `cosmetics.yml`) |

---

## Configuration guide

Every file is YAML with commented defaults. Missing keys are added automatically on upgrade, and
**`/pvpadmin reload` reloads everything**. The exceptions are the storage type and arena world settings, which need a restart.
Text uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html). The theme colours from core's
`messages.yml` (`<primary>`, `<secondary>`, `<accent>`, `<success>`, `<error>`, `<muted>`) and `<prefix>` work in
every plugin.

| File | Contents |
|------|----------|
| `PvPCore/config.yml` | Storage (SQLite/MySQL/MariaDB + pool), ELO (starting, K-factor, floor), combat (old-combat attack speed, combat tag, golden heads), party limits, arena pool (slots, max instances, idle pool, prewarm, paste budget), sidebar refresh rate, tab header/footer, chat format and filter, moderation, alert thresholds, leaderboards |
| `PvPCore/messages.yml` | Core messages and theme colours |
| `PvPCore/kits.yml` | Kits (items, armor, effects, rules, arena tags, knockback profile) |
| `PvPCore/kb.yml` | Knockback profiles (also editable with `/kb`) |
| `PvPCore/ranks.yml` | Built-in ranks and the LuckPerms switch |
| `PvPCore/arenas.yml` + `arenas/*.arena` | Arena definitions and block templates |
| `PvPCore/cosmetics.yml` | Kill effects, death animations, join messages and their permissions |
| `PvPLobby/config.yml` | Spawn, void Y, double jump |
| `PvPLobby/hotbar.yml` | Hotbar layouts per situation (lobby, queue, party leader/member) |
| `PvPLobby/menus.yml`, `messages.yml`, `scoreboard.yml`, `holograms.yml` | Menu items, texts, sidebars, holograms |
| `PvPDuels/config.yml` | Countdown/round/end timings, ELO search window, ranked unlock requirement, request expiry, max rounds |
| `PvPDuels/menus.yml`, `messages.yml`, `scoreboard.yml` | Menus, texts, match and spectator sidebars |
| `PvPFFA/config.yml` | FFA arenas, respawn protection, kill rewards, killstreak rewards, combat-log rules, block decay |
| `PvPFFA/messages.yml`, `scoreboard.yml` | Texts and FFA sidebar |

### MySQL / MariaDB

```yaml
storage:
  type: MYSQL          # or MARIADB
  mysql: {host: db.example.com, port: 3306, database: practice, username: practice, password: secret}
```

Paper bundles the MySQL driver (Connector/J 9.2.0), and MariaDB works over it; the MariaDB driver is used if you
install one. Tables are created automatically with the `table-prefix`. Both settings have been tested against
MariaDB 10.11 with that driver.

### LuckPerms

With `provider: auto` in `ranks.yml`, installing LuckPerms switches chat/tab prefixes and tab ordering to
LuckPerms (group prefix and weight), and built-in rank permissions are no longer applied. Use `provider: builtin`
to ignore LuckPerms.

### Sidebars

Each state (lobby, queue, editing, match, spectating, FFA) has its own template. A line starting with `?flag `
only shows when the flag applies, e.g. `"?ranked <gray>Range: <white><queue_min> - <queue_max>"`. The available
flags and placeholders are listed at the top of each `scoreboard.yml`.

---

## Adding kits

Add a section under `kits:` in `PvPCore/kits.yml` and run `/pvpadmin reload`:

```yaml
kits:
  soup:
    display-name: "<green>Soup"
    order: 10
    ranked: true            # available in ranked queues
    unranked: true          # unranked queues and /duel
    ffa: false              # usable by FFA arenas
    editable: true          # kit editor
    knockback: default      # kb.yml profile or "vanilla"
    arena-tags: [standard]  # or: arenas: [classic, nether]
    icon: {material: MUSHROOM_STEW}
    rules: {old-combat: true, regen: NONE, hunger: false}
    armor:
      helmet: {material: IRON_HELMET}
      chestplate: {material: IRON_CHESTPLATE}
      leggings: {material: IRON_LEGGINGS}
      boots: {material: IRON_BOOTS}
    items:
      - {slot: 0, material: DIAMOND_SWORD, enchants: ["sharpness:1"]}
    fill: {material: MUSHROOM_STEW}
    effects: ["speed:0:-1"]
```

Item keys: `material, amount, name, lore, enchants, unbreakable, potion, color, glow, tag`. The full list of rules
(hunger, regen modes, build/break, hit delay, old combat, pearl/gapple cooldowns, potion velocity, no-damage,
sumo, boxing, bridge, rounds, max duration, item drops) is documented at the top of `kits.yml`.

## Adding arenas

Arenas are block templates plus metadata. Matches paste copies of a template into a void world, and changes are
rolled back when the copy returns to the pool.

1. Build the arena anywhere, for example in a creative flat world.
2. `/arena create <name>` starts an edit session and gives you a wand.
3. Select the build: left-click one corner and right-click the opposite corner with the wand (or `/arena pos1`/`pos2`).
4. Stand at the spawns and run `/arena setspawn a` and `/arena setspawn b` (optionally `spectator`).
5. Optional:
   * `/arena buildlimit [y]` (max build height for build kits)
   * `/arena voidy [y]` (falling below = death)
   * `/arena tag add build|sumo|bridge|standard` (which kits can use it). New arenas start tagged `standard`;
     remove it (`/arena tag remove standard`) for sumo rings and other special maps.
   * `/arena setgoal a|b` and `/arena goalradius <r>` for bridge. Goal A is the goal team A defends.
   * `/arena displayname <name>`, `/arena icon` (item in hand)
6. `/arena save` captures the region asynchronously into `arenas/<name>.arena` and enables it.

Edit an existing arena with `/arena edit <name>`, which pastes it into the `pvp_editor` world, then `/arena save`
again. `/arena list`, `/arena info <name>` and `/arena status` (pool statistics) help with management. Kits pick
arenas by `arena-tags` or an explicit `arenas:` whitelist. FFA arenas reference an arena by `template:` in
`PvPFFA/config.yml`.

---

## Project layout

```
pom.xml                  parent (modules: core, lobby, duels, ffa, dist; profile "smoke" adds smoke-test)
core/  lobby/  duels/  ffa/   plugin sources, resources and tests
dist/                    copies the jars into server/plugins during package
smoke-test/              MockBukkit end-to-end smoke tests (mvn -Psmoke verify)
server/                  ready-to-run server folder (start scripts, tuned configs, plugin config defaults)
docs/ARCHITECTURE.md     architecture and module layout
```
