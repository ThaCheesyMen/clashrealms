package com.guildwars.commands;

import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import org.bukkit.Bukkit; // Uncommented
import org.bukkit.ChatColor; // Uncommented
import org.bukkit.command.Command; // Uncommented
import org.bukkit.command.CommandExecutor; // Uncommented
import org.bukkit.command.CommandSender; // Uncommented
import org.bukkit.entity.Player; // Uncommented

import java.util.UUID;

// Note: We don't need to import the main plugin class (GuildWarsPlugin) here
// if we are passing GuildManager directly in the constructor, which we are.

public class GuildChatCommand implements CommandExecutor { // Uncommented implements

    private GuildManager guildManager;

    public GuildChatCommand(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    @Override // Uncommented @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use guild chat.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUuid = player.getUniqueId();

        Guild playerGuild = guildManager.getGuildByPlayer(playerUuid);

        if (playerGuild == null) {
            player.sendMessage(ChatColor.RED + "You are not in a guild.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /gc <message>"); // Added some color
            return true;
        }

        String messageContent = String.join(" ", args);
        // Using player.getDisplayName() includes nicknames from other plugins and is generally preferred.
        // We'll also add a configurable guild chat color, e.g., green.
        String guildChatFormat = playerGuild.getChatPrefix() + " " + ChatColor.GRAY + player.getDisplayName() + ChatColor.WHITE + ": " + messageContent;
        String finalMessage = ChatColor.GREEN + guildChatFormat; // Overall guild chat color

        // Optionally, you could allow '&' color codes in the message content itself:
        // messageContent = ChatColor.translateAlternateColorCodes('&', messageContent);
        // String finalMessage = ChatColor.GREEN + playerGuild.getChatPrefix() + " " + ChatColor.GRAY + player.getDisplayName() + ChatColor.WHITE + ": " + messageContent;


        for (UUID memberUuid : playerGuild.getMembers()) {
            Player guildMember = Bukkit.getPlayer(memberUuid);
            if (guildMember != null && guildMember.isOnline()) {
                guildMember.sendMessage(finalMessage);
            }
        }
        
        // Also, print to console for logging if desired
        // Bukkit.getLogger().info("[GuildChat] " + finalMessage);


        return true;
    }
}
