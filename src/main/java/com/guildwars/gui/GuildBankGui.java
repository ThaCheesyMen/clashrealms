package com.guildwars.gui;

import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GuildBankGui {

    private final GuildManager guildManager;
    public static final String TITLE_PREFIX = ChatColor.DARK_GREEN + "Guild Bank";

    public GuildBankGui(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    public void open(Player player) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(ChatColor.RED + "You are not in a guild or guild data is missing.");
            return;
        }

        // Check permissions (e.g., only members can open, specific ranks for withdrawal - handled in InventoryClickListener)
        // For now, any member can open and view.

        Inventory bankGui = Bukkit.createInventory(player, Guild.BANK_SIZE, TITLE_PREFIX + ": " + guild.getName());
        bankGui.setContents(guild.getBankContents()); // Load the items into the GUI

        player.openInventory(bankGui);
    }

    // Method to be called when the GUI is closed to save its contents.
    // The InventoryCloseEvent will be handled in InventoryClickListener or a dedicated listener.
    // The caller (InventoryClickListener) is responsible for ensuring this is the correct inventory.
    public void saveBankFromInventory(Guild guild, Inventory inventory) {
        if (guild == null || inventory == null) { // Removed title check here
            return; 
        }
        if (inventory.getSize() != Guild.BANK_SIZE) {
             // Log error or handle mismatch
            System.err.println("GuildBankGui: Inventory size mismatch for guild " + guild.getName() + ". Expected " + Guild.BANK_SIZE + " got " + inventory.getSize());
            return;
        }
        guild.setBankContents(inventory.getContents());
        guildManager.saveGuildBank(guild);
    }
}
