package com.guildwars.events;

import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class GuildProtectionListener implements Listener {

    private GuildManager guildManager;

    public GuildProtectionListener(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    @EventHandler(priority = EventPriority.LOW) // Process this before other plugins might un-cancel it
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        Guild ownerGuild = guildManager.getGuildOwningChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());

        if (ownerGuild != null) { // Chunk is claimed
            Guild playerGuild = guildManager.getGuildByPlayer(player.getUniqueId());
            if (playerGuild == null || !playerGuild.getName().equals(ownerGuild.getName())) {
                // Player is not in the owner guild, or not in any guild.
                if (!player.hasPermission("guildwars.admin.bypassclaims")) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot break blocks here. This land is claimed by " + ChatColor.GOLD + ownerGuild.getName() + ChatColor.RED + ".");
                }
            }
            // If player is a member of the owner guild, allow. (Further role-based perms could be added here later)
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        Guild ownerGuild = guildManager.getGuildOwningChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());

        if (ownerGuild != null) { // Chunk is claimed
            Guild playerGuild = guildManager.getGuildByPlayer(player.getUniqueId());
            if (playerGuild == null || !playerGuild.getName().equals(ownerGuild.getName())) {
                // Player is not in the owner guild, or not in any guild.
                 if (!player.hasPermission("guildwars.admin.bypassclaims")) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot place blocks here. This land is claimed by " + ChatColor.GOLD + ownerGuild.getName() + ChatColor.RED + ".");
                }
            }
        }
    }
    // TODO: Add PlayerInteractEvent for chests, doors, etc.
    // TODO: Add protection against explosions, fire spread if desired.
}
