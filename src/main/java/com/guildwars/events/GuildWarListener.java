package com.guildwars.events;

import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class GuildWarListener implements Listener {

    private final GuildManager guildManager;

    public GuildWarListener(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    @EventHandler(priority = EventPriority.NORMAL) // Could be higher if needed to override other PvP plugins
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return; // Only interested in Player vs Player combat
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        Guild victimGuild = guildManager.getGuildByPlayer(victim.getUniqueId());
        Guild attackerGuild = guildManager.getGuildByPlayer(attacker.getUniqueId());

        // Case 1: Both players are in the same guild - prevent friendly fire
        if (victimGuild != null && victimGuild.equals(attackerGuild)) {
            // Bukkit's default is usually no friendly fire within a team, but this is an explicit check for guilds.
            // If your server has global friendly fire enabled, you might need to cancel this.
            // For now, let's assume default Bukkit behavior handles intra-guild FF, or we add config for it.
            // If we want to explicitly PREVENT guild FF even if server allows it:
            // event.setCancelled(true);
            // attacker.sendMessage(ChatColor.RED + "You cannot harm members of your own guild!");
            return; // Or let server default handle it.
        }

        // Case 2: Players are in different guilds
        if (victimGuild != null && attackerGuild != null && !victimGuild.equals(attackerGuild)) {
            // Check if their guilds are at war
            if (guildManager.isAtWarWith(victimGuild.getName(), attackerGuild.getName())) {
                // They are at war, allow the damage.
                // Check if the damage will be fatal for war score
                if (victim.getHealth() - event.getFinalDamage() <= 0) {
                    // Victim is about to die from this attack
                    guildManager.incrementWarScore(attackerGuild.getName(), victimGuild.getName(), GuildManager.WAR_POINTS_PER_KILL, "a kill");
                    // attacker.sendMessage(ChatColor.GREEN + "+ " + GuildManager.WAR_POINTS_PER_KILL + " point(s) for your guild!"); // Message handled by incrementWarScore
                }
                return; // Allow damage
            } else {
                // Not at war (and not same guild), this is where you might enforce peace or ally rules.
                // For now, if not at war, let other plugins/server settings decide PvP.
                // If we wanted to PREVENT PvP between non-warring guilds with this plugin:
                // event.setCancelled(true);
                // attacker.sendMessage(ChatColor.RED + "You cannot attack members of " + victimGuild.getName() + " as you are not at war.");
                return;
            }
        }

        // Case 3: One player in a guild, the other not, or both not in guilds.
        // Let other plugins/server settings handle this. Our war listener doesn't intervene here.
    }
}
