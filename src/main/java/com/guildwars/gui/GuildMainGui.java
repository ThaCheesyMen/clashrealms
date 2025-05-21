package com.guildwars.gui;

import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GuildMainGui {

    private GuildManager guildManager;
    private GuildMembersGui guildMembersGui;
    private GuildContributeGui guildContributeGui;
    private GuildPerksGui guildPerksGui;
    private GuildBankGui guildBankGui; // Added

    public GuildMainGui(GuildManager guildManager) {
        this.guildManager = guildManager;
        this.guildMembersGui = new GuildMembersGui(guildManager);
        this.guildContributeGui = new GuildContributeGui(guildManager);
        this.guildPerksGui = new GuildPerksGui(guildManager);
        this.guildBankGui = new GuildBankGui(guildManager); // Added
    }

    // Added getter for InventoryClickListener
    public GuildMembersGui getGuildMembersGui() {
        return guildMembersGui;
    }

    // Added getter for InventoryClickListener
    public GuildContributeGui getGuildContributeGui() {
        return guildContributeGui;
    }

    // Added getter for InventoryClickListener
    public GuildPerksGui getGuildPerksGui() {
        return guildPerksGui;
    }

    // Added getter for InventoryClickListener
    public GuildBankGui getGuildBankGui() {
        return guildBankGui;
    }

    public void open(Player player) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        String guiTitle = ChatColor.DARK_AQUA + "Guild Info";
        Inventory gui;

        if (guild != null) {
            guiTitle += ": " + ChatColor.AQUA + guild.getName();
            gui = Bukkit.createInventory(null, 27, guiTitle); // 3 rows

            // Guild Name & Level Info Item
            ItemStack infoItem = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = infoItem.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName(ChatColor.GOLD + guild.getName());
                List<String> infoLore = new ArrayList<>();
                infoLore.add(ChatColor.YELLOW + "Level: " + ChatColor.AQUA + guild.getLevel());
                int effectiveMax = guildManager.getEffectiveMaxMembers(guild);
                infoLore.add(ChatColor.YELLOW + "Members: " + ChatColor.WHITE + guild.getMembers().size() + ChatColor.GRAY + "/" + ChatColor.WHITE + effectiveMax);
                infoLore.add(ChatColor.YELLOW + "XP: " + ChatColor.GREEN + guild.getCurrentXp() + ChatColor.GRAY + " / " + ChatColor.WHITE + guild.getXpForNextLevel());
                infoMeta.setLore(infoLore);
                infoItem.setItemMeta(infoMeta);
            }
            gui.setItem(4, infoItem); // Center of first row

            // Members List Button
            ItemStack membersButton = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta membersMeta = membersButton.getItemMeta();
            if (membersMeta != null) {
                membersMeta.setDisplayName(ChatColor.GREEN + "View Members");
                membersMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to see all guild members."));
                membersButton.setItemMeta(membersMeta);
            }
            gui.setItem(11, membersButton);

            // Contribute Button
            ItemStack contributeButton = new ItemStack(Material.EMERALD);
            ItemMeta contributeMeta = contributeButton.getItemMeta();
            if (contributeMeta != null) {
                contributeMeta.setDisplayName(ChatColor.GREEN + "Contribute Items");
                contributeMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to contribute items for XP."));
                contributeButton.setItemMeta(contributeMeta);
            }
            gui.setItem(13, contributeButton); 

            // Guild Home Button
            ItemStack homeButton = new ItemStack(Material.COMPASS);
            ItemMeta homeMeta = homeButton.getItemMeta();
            if (homeMeta != null) {
                homeMeta.setDisplayName(ChatColor.AQUA + "Guild Home");
                if (guildManager.canGuildSetHome(guild)) {
                    if (guild.getGuildHomeLocation() != null) {
                        homeMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to teleport to your guild home."));
                    } else {
                        homeMeta.setLore(Arrays.asList(ChatColor.YELLOW + "Home not set. Officers use /guild sethome."));
                    }
                } else {
                    homeMeta.setLore(Arrays.asList(ChatColor.RED + "Guild Home perk not unlocked."));
                }
                homeButton.setItemMeta(homeMeta);
            }
            gui.setItem(15, homeButton); 

            // Guild Perks Button
            ItemStack perksButton = new ItemStack(Material.NETHER_STAR);
            ItemMeta perksMeta = perksButton.getItemMeta();
            if (perksMeta != null) {
                perksMeta.setDisplayName(ChatColor.DARK_PURPLE + "View Guild Perks");
                perksMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to see all available guild perks."));
                perksButton.setItemMeta(perksMeta);
            }
            // Let's find an empty slot, e.g. slot 22 (center of last row)
            // Current items: Info (4), Members (11), Contribute (13), Home (15)
            // Slots in a 27-inv: 0-8, 9-17, 18-26
            // Let's try to place it aesthetically. Maybe slot 22 or one of the corners like 18 or 26.
            // Or re-arrange. For now, slot 19 (start of last row, left of potential center items if any)
            // Or, let's put it next to Members/Contribute/Home, e.g., slot 12 or 14.
            // Current slots taken are 4, 11, 13, 15.
            // Let's place it at slot 22 (center of last row) to balance things out or an earlier free one like 12.
            gui.setItem(22, perksButton); // Center of the third row

            // Guild Bank Button
            ItemStack bankButton = new ItemStack(Material.CHEST);
            ItemMeta bankMeta = bankButton.getItemMeta();
            if (bankMeta != null) {
                bankMeta.setDisplayName(ChatColor.DARK_GREEN + "Guild Bank");
                bankMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to open the guild bank."));
                bankButton.setItemMeta(bankMeta);
            }
            // Let's place this next to Perks, maybe slot 20 or 24, or if 22 is center, then 21 or 23.
            // Slots used: 4, 11, 13, 15, 22. Try slot 20.
            gui.setItem(20, bankButton); // Third row, towards left from center

        } else {
            gui = Bukkit.createInventory(null, 9, guiTitle);
            ItemStack noGuildItem = new ItemStack(Material.BARRIER);
            ItemMeta noGuildMeta = noGuildItem.getItemMeta();
            if (noGuildMeta != null) {
                noGuildMeta.setDisplayName(ChatColor.RED + "You are not in a guild.");
                noGuildItem.setItemMeta(noGuildMeta);
            }
            gui.setItem(4, noGuildItem); 
        }
        player.openInventory(gui);
    }
}
