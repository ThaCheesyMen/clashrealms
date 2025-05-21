package com.guildwars.events;

import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerTerritoryListener implements Listener {

    private final GuildManager guildManager;
    private final Map<UUID, String> playerLastTerritoryGuildName = new HashMap<>(); // Player UUID to Guild Name

    public PlayerTerritoryListener(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Check if player moved to a new chunk
        if (from.getChunk().getX() == to.getChunk().getX() && from.getChunk().getZ() == to.getChunk().getZ() && from.getWorld().equals(to.getWorld())) {
            return; // Still in the same chunk
        }

        Chunk toChunk = to.getChunk();
        Guild currentTerritoryGuild = guildManager.getGuildOwningChunk(toChunk.getWorld(), toChunk.getX(), toChunk.getZ());
        String currentTerritoryName = (currentTerritoryGuild != null) ? currentTerritoryGuild.getName() : null;

        String lastTerritoryName = playerLastTerritoryGuildName.get(player.getUniqueId());

        if (currentTerritoryName != null && !currentTerritoryName.equals(lastTerritoryName)) {
            // Entered a new guild's territory
            player.sendTitle(ChatColor.AQUA + currentTerritoryGuild.getName(), 
                             ChatColor.GOLD + "Guild Territory", 10, 70, 20); // fadeIn, stay, fadeOut ticks
            playerLastTerritoryGuildName.put(player.getUniqueId(), currentTerritoryName);
        } else if (currentTerritoryName == null && lastTerritoryName != null) {
            // Left a guild territory into wilderness
            player.sendTitle(ChatColor.GRAY + "Wilderness", 
                             ChatColor.DARK_GREEN + "Unclaimed Land", 10, 70, 20);
            playerLastTerritoryGuildName.remove(player.getUniqueId());
        }
        // If currentTerritoryName is null and lastTerritoryName is null, do nothing (moving within wilderness)
        // If currentTerritoryName is not null and equals lastTerritoryName, do nothing (moving within same guild territory)
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();
        Chunk currentChunk = loc.getChunk();
        Guild currentTerritoryGuild = guildManager.getGuildOwningChunk(currentChunk.getWorld(), currentChunk.getX(), currentChunk.getZ());
        
        if (currentTerritoryGuild != null) {
            playerLastTerritoryGuildName.put(player.getUniqueId(), currentTerritoryGuild.getName());
            // Optionally send title on join if they spawn in a territory
            player.sendTitle(ChatColor.AQUA + currentTerritoryGuild.getName(), ChatColor.GOLD + "Guild Territory", 10, 70, 20);
        } else {
            playerLastTerritoryGuildName.remove(player.getUniqueId());
            // Optionally send title on join if they spawn in wilderness
            player.sendTitle(ChatColor.GRAY + "Wilderness", ChatColor.DARK_GREEN + "Unclaimed Land", 10, 70, 20);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerLastTerritoryGuildName.remove(event.getPlayer().getUniqueId());
    }
}
