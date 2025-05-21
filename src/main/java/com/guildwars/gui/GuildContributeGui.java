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

import java.util.Arrays;

public class GuildContributeGui {

    private GuildManager guildManager;
    public static final String TITLE = ChatColor.DARK_AQUA + "Contribute Items to Guild";
    private static final int CONTRIBUTION_SLOTS = 18; // First 2 rows for item placement

    public GuildContributeGui(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    public void open(Player player) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(ChatColor.RED + "You are not in a guild.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, TITLE + ": " + guild.getName());

        // Informational Item
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.YELLOW + "Contribute for Guild XP");
            infoMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Place items in the top two rows.",
                ChatColor.GRAY + "Then click \"Confirm Contribution\"."
            ));
            infoItem.setItemMeta(infoMeta);
        }
        gui.setItem(22, infoItem); // Middle of the last row

        // Back Button
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Back to Guild Info");
            backButton.setItemMeta(backMeta);
        }
        gui.setItem(20, backButton); // Last row, left

        // Confirm Contribution Button
        ItemStack confirmButton = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirmButton.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatColor.GREEN + "Confirm Contribution");
            confirmMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to contribute the items above."));
            confirmButton.setItemMeta(confirmMeta);
        }
        gui.setItem(24, confirmButton); // Last row, right
        
        // Fill empty slots in the last row with gray stained glass for aesthetics
        ItemStack fillerPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = fillerPane.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" "); // Empty display name
            fillerPane.setItemMeta(fillerMeta);
        }
        for (int i = 18; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, fillerPane.clone());
            }
        }

        player.openInventory(gui);
    }

    public static int getContributionSlotsCount() {
        return CONTRIBUTION_SLOTS;
    }
}
