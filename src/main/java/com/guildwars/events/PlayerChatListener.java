package com.guildwars.events;

import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import org.bukkit.ChatColor; // For potential coloring of the prefix
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent; // Using Paper's Async event

public class PlayerChatListener implements Listener {

    private GuildManager guildManager;

    public PlayerChatListener(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Guild playerGuild = guildManager.getGuildByPlayer(player.getUniqueId());

        if (playerGuild != null) {
            String prefix = playerGuild.getChatPrefix(); // e.g., "[GuildName]"
            
            // Add color to prefix if desired, e.g., light purple
            String coloredPrefix = ChatColor.LIGHT_PURPLE + prefix + ChatColor.RESET;

            // event.setFormat(coloredPrefix + " %1$s: %2$s"); 
            // %1$s is player's display name, %2$s is the message.
            // This explicitly sets the format.
            // A potentially more compatible way (with other plugins modifying format) is:
            event.setFormat(coloredPrefix + " " + event.getFormat());
            // Test which one works best with your server setup and other plugins.
            // For now, the latter is often safer.
        }
        // If player is not in a guild, do nothing, let default chat format apply.
    }
}
