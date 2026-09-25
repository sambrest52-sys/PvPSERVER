package net.pvpserver.core.rank;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import org.bukkit.entity.Player;

/**
 * Thin LuckPerms adapter. Only loaded when the LuckPerms plugin is present so the class never resolves otherwise.
 */
final class LuckPermsHook {

    private final LuckPerms api;

    LuckPermsHook() {
        this.api = LuckPermsProvider.get();
    }

    String prefix(Player player) {
        CachedMetaData meta = api.getPlayerAdapter(Player.class).getMetaData(player);
        return meta.getPrefix();
    }

    String suffix(Player player) {
        CachedMetaData meta = api.getPlayerAdapter(Player.class).getMetaData(player);
        return meta.getSuffix();
    }

    String primaryGroup(Player player) {
        return api.getPlayerAdapter(Player.class).getUser(player).getPrimaryGroup();
    }

    int weight(Player player) {
        Group group = api.getGroupManager().getGroup(primaryGroup(player));
        return group == null ? 0 : group.getWeight().orElse(0);
    }
}
