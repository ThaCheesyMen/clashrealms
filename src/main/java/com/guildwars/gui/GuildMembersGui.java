package com.guildwars.gui;

import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta; // Added ItemMeta import
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class GuildMembersGui {

    private GuildManager guildManager;

    public GuildMembersGui(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    public void open(Player player, Guild guild, int page) {
        if (guild == null) {
            player.sendMessage(ChatColor.RED + "Could not retrieve guild information.");
            return;
        }

        List<UUID> memberUuids = new ArrayList<>(guild.getMembers());
        int membersPerPage = 45; // 5 rows for members, 9 slots per row
        int totalPages = (int) Math.ceil((double) memberUuids.size() / membersPerPage);
        if (page < 1) page = 1;
        if (page > totalPages && totalPages > 0) page = totalPages;

        String guiTitle = ChatColor.DARK_AQUA + "Guild Members - Page " + page + "/" + totalPages;
        Inventory gui = Bukkit.createInventory(null, 54, guiTitle); // 6 rows

        int startIndex = (page - 1) * membersPerPage;
        int endIndex = Math.min(startIndex + membersPerPage, memberUuids.size());

        for (int i = startIndex; i < endIndex; i++) {
            UUID memberUuid = memberUuids.get(i);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(memberUuid);
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : memberUuid.toString();

            ItemStack memberHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta headMeta = (SkullMeta) memberHead.getItemMeta();

            if (headMeta != null) {
                headMeta.setOwningPlayer(offlinePlayer);
                headMeta.setDisplayName(ChatColor.GREEN + playerName);
                List<String> lore = new ArrayList<>();
                // You can add more info here, e.g., rank, last online
                if (guild.getLeader().equals(memberUuid)) { // Changed getOwner() to getLeader()
                    lore.add(ChatColor.GOLD + "Guild Leader");
                } else if (guild.getOfficers().contains(memberUuid)) {
                    lore.add(ChatColor.YELLOW + "Officer");
                } else {
                    lore.add(ChatColor.AQUA + "Member");
                }
                // Example: lore.add(ChatColor.GRAY + "Last Online: " + (offlinePlayer.isOnline() ? ChatColor.GREEN + "Online" : "Offline - TODO"));
                headMeta.setLore(lore);
                memberHead.setItemMeta(headMeta);
            }
            gui.addItem(memberHead); // Add to next available slot, up to 45
        }

        // Navigation buttons
        if (page > 1) {
            ItemStack previousButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = previousButton.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
                prevMeta.setLore(List.of(ChatColor.GRAY + "Click to go to page " + (page - 1)));
                previousButton.setItemMeta(prevMeta);
            }
            gui.setItem(48, previousButton); // Bottom row, middle-left
        }

        if (page < totalPages) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
                nextMeta.setLore(List.of(ChatColor.GRAY + "Click to go to page " + (page + 1)));
                nextButton.setItemMeta(nextMeta);
            }
            gui.setItem(50, nextButton); // Bottom row, middle-right
        }
        
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Back to Guild Info");
            backButton.setItemMeta(backMeta);
        }
        gui.setItem(49, backButton); // Bottom center

        player.openInventory(gui);
    }
}
