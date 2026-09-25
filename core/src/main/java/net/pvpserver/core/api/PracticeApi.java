package net.pvpserver.core.api;

import net.pvpserver.core.anticheat.CheckService;
import net.pvpserver.core.arena.ArenaService;
import net.pvpserver.core.chat.ChatService;
import net.pvpserver.core.combat.CombatService;
import net.pvpserver.core.config.ReloadRegistry;
import net.pvpserver.core.cosmetic.CosmeticService;
import net.pvpserver.core.hotbar.HotbarService;
import net.pvpserver.core.kit.KitService;
import net.pvpserver.core.knockback.KnockbackService;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.moderation.PunishmentService;
import net.pvpserver.core.moderation.StaffService;
import net.pvpserver.core.party.PartyService;
import net.pvpserver.core.profile.ProfileService;
import net.pvpserver.core.rank.RankService;
import net.pvpserver.core.scoreboard.SidebarService;
import net.pvpserver.core.scoreboard.TabService;
import net.pvpserver.core.state.PlayerStateService;
import net.pvpserver.core.stats.LeaderboardService;
import net.pvpserver.core.stats.StatsService;
import net.pvpserver.core.world.WorldService;

/**
 * Entry point for gamemode plugins. Obtain with {@link Practice#api()} after PvPCore enabled.
 */
public interface PracticeApi {

    /** @return core message catalogue (parent of every plugin's messages) */
    MessageService messages();

    /** @return player state machine */
    PlayerStateService states();

    /** @return bridge registry for cross-plugin features */
    BridgeRegistry bridges();

    /** @return reload registry for {@code /pvpadmin reload} */
    ReloadRegistry reloads();

    /** @return profiles */
    ProfileService profiles();

    /** @return ranks */
    RankService ranks();

    /** @return kits */
    KitService kits();

    /** @return knockback profiles */
    KnockbackService knockback();

    /** @return combat rules, cooldowns and tags */
    CombatService combat();

    /** @return parties */
    PartyService parties();

    /** @return arenas */
    ArenaService arenas();

    /** @return sidebars */
    SidebarService sidebars();

    /** @return tab list */
    TabService tab();

    /** @return chat */
    ChatService chat();

    /** @return punishments */
    PunishmentService punishments();

    /** @return staff tools */
    StaffService staff();

    /** @return cosmetics */
    CosmeticService cosmetics();

    /** @return alert-only checks */
    CheckService checks();

    /** @return stats and ELO */
    StatsService stats();

    /** @return leaderboards */
    LeaderboardService leaderboards();

    /** @return hotbar items */
    HotbarService hotbar();

    /** @return world helpers */
    WorldService worlds();

    /** @return server counters */
    Counts counts();
}
