# TODO / nice-to-have

## Scaling out

- [ ] **Velocity multi-instance network.** Run one lobby server and several match servers behind Velocity. Move
      queue state to Redis and let match servers claim pairings, then send players with plugin messaging.
      The bridge interfaces in core (`QueueBridge`, `MatchBridge`) are the seams to implement network-aware versions.
- [ ] **Redis pub/sub** for cross-server staff chat, alerts, reports, party membership and punishments (instant
      kick/mute on every instance).
- [ ] Shared MySQL is already supported; add a read replica option for leaderboard queries.
- [ ] Per-instance arena worlds sized from `max-instances`, and pre-pasting arenas at startup across several ticks
      with a warm-up progress bar.

## Web and data

- [ ] Web stats site / REST API (player profiles, per-kit ELO graphs, match history). The `matches` table already
      stores winners, losers, kit, ELO change and duration.
- [ ] Discord bot or webhooks for reports, punishments and top-10 changes.
- [ ] ELO seasons with resets and end-of-season rewards; ELO decay for inactive top players.
- [ ] Per-division ranks (Bronze → Champion) derived from ELO, shown in the tab list.

## Gameplay

- [ ] More kits: Axe, Pearl Fight, MLG Rush, Battle Rush, Stick Fight, HCF/Diamond, Vanilla (1.21 crits + shields), Mace.
- [ ] Arena voting and per-arena weights; more generator themes (city, desert temple, end island).
- [ ] Bridge: pre-round glass cages and a goal portal effect.
- [ ] Full 1.8 damage/armor table option (sword damage, armor toughness removal, 1.8 critical rules).
- [ ] Tournaments / events (Sumo event, brackets, KOTH-style events) with a `/host` command and rewards.
- [ ] Bot fights (practice against an NPC), e.g. via a Citizens or packet-based NPC integration.
- [ ] Duel request queue for "anyone": `/duel random <kit>`.
- [ ] Kit editor: allow adding optional items from a per-kit "extras" chest (extra pots, soups).
- [ ] 3v3 / 4v4 ranked queues; clan/team ELO.
- [ ] Map voting for duels, and arena rotation weights.
- [ ] Replays of the last N matches for staff review.

## Lobby

- [ ] Lobby pets (the cosmetics framework has room for a `pets` type; they would be client-side display entities).
- [ ] Store parkour times and found eggs in SQL so several lobby servers share them.
- [ ] More hub themes for the generator (winter, desert, nether) selectable with `world.theme`, and seasonal
      decorations.
- [ ] A lobby selector (several lobby instances) once the Velocity network exists.
- [ ] Per-zone ambient music via note block songs or a resource pack.
- [ ] A second parkour difficulty and a daily parkour challenge with rewards.

## Presentation

- [ ] Nametag prefixes (rank colours above heads) using per-viewer scoreboard teams alongside the sidebar.
- [ ] Animated sidebar titles and tab header.
- [ ] Hologram leaderboards with player heads (ItemDisplay) and rotating kits.
- [ ] Translations: per-player locale selection for `messages.yml`.
- [ ] Custom kill-effect shop with coins.

## Operations

- [ ] bStats / Prometheus metrics (players per state, queue times, arena pool usage, DB latency).
- [ ] A real anticheat integration adapter (e.g. forward `CheckAlertEvent` from Grim/Vulcan into the alert pipeline).
- [ ] Automated in-game integration tests on a real Paper server in CI (e.g. a headless bot client).
- [ ] Config validation command that reports unknown materials/enchants/effects in `kits.yml` with line numbers.
