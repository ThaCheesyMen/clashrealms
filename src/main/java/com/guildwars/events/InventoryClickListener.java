package com.guildwars.events;

// We might need to pass this or have a way to identify its inventories
import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import com.guildwars.gui.GuildContributeGui;
import com.guildwars.gui.GuildMainGui;
import com.guildwars.gui.GuildMembersGui;
import com.guildwars.gui.GuildPerksGui;
import com.guildwars.gui.GuildBankGui; // Added import
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent; // Added for Guild Bank saving
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList; // Ensure ArrayList is imported
import java.util.List;    // Ensure List is imported

public class InventoryClickListener implements Listener {

    private GuildManager guildManager;
    private GuildMainGui guildMainGui; // Added field

    public InventoryClickListener(GuildManager guildManager, GuildMainGui guildMainGui) { // Updated constructor
        this.guildManager = guildManager;
        this.guildMainGui = guildMainGui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        Inventory topInventory = event.getView().getTopInventory(); 
        Inventory clickedInventory = event.getClickedInventory(); 

        // If the clicked inventory is null (e.g. clicking outside GUI), or if no item was clicked in that inventory
        if (clickedInventory == null || clickedItem == null || clickedItem.getType() == Material.AIR) {
            // Check if it's a click on the control panel of the contribute GUI (which should be cancelled)
            if (clickedInventory == topInventory && 
                event.getView().getTitle().startsWith(GuildContributeGui.TITLE) &&
                event.getSlot() >= GuildContributeGui.getContributionSlotsCount() && 
                event.getSlot() < topInventory.getSize()) {
                event.setCancelled(true);
            }
            // For any other case where clickedItem is null or AIR, just return.
            // This should prevent NullPointerExceptions on clickedItem.getType() later.
            return; 
        }

        // From this point onwards, clickedItem is guaranteed to be non-null and not AIR.
        String inventoryTitle = event.getView().getTitle();
        String mainGuiTitlePrefix = ChatColor.DARK_AQUA + "Guild Info"; // From GuildMainGui
        String membersGuiTitlePrefix = ChatColor.DARK_AQUA + "Guild Members - Page"; // From GuildMembersGui
        String contributeGuiTitlePrefix = GuildContributeGui.TITLE; // From GuildContributeGui
        String perksGuiTitlePrefix = GuildPerksGui.TITLE; // From GuildPerksGui
        String bankGuiTitlePrefix = GuildBankGui.TITLE_PREFIX; // From GuildBankGui

        if (inventoryTitle.startsWith(mainGuiTitlePrefix)) {
            event.setCancelled(true); // Prevent taking items from our GUIs

            Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
            if (guild == null && !inventoryTitle.equals(mainGuiTitlePrefix)) { // If title implies they are in a guild but they are not
                 player.closeInventory();
                 player.sendMessage(ChatColor.RED + "You are not in a guild or the guild data is missing.");
                 return;
            }

            // Slot-based or item display name based actions
            if (clickedItem.getType() == Material.PLAYER_HEAD && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("View Members")) {
                if (guild != null) {
                    guildMainGui.getGuildMembersGui().open(player, guild, 1);
                } else {
                    player.sendMessage(ChatColor.RED + "You must be in a guild to view members.");
                    player.closeInventory();
                }
            } else if (clickedItem.getType() == Material.EMERALD && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("Contribute Items")) {
                if (guild != null) {
                    guildMainGui.getGuildContributeGui().open(player);
                } else {
                    player.sendMessage(ChatColor.RED + "You must be in a guild to contribute.");
                    player.closeInventory();
                }
            } else if (clickedItem.getType() == Material.COMPASS && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("Guild Home")) {
                player.closeInventory();
                if (guild != null && guildManager.canGuildSetHome(guild)) {
                    Location homeLoc = guild.getGuildHomeLocation();
                    if (homeLoc != null) {
                        player.teleport(homeLoc);
                        player.sendMessage(ChatColor.GREEN + "Teleporting to guild home...");
                    } else {
                        player.sendMessage(ChatColor.RED + "Guild home is not set. Officers use /guild sethome.");
                    }
                } else if (guild != null) {
                     player.sendMessage(ChatColor.RED + "Your guild has not unlocked the Guild Home perk yet.");
                } else {
                    // This case should ideally not happen if the home button isn't shown or is different for non-guild members
                    // but as a fallback if the GUI was opened some other way for a non-guild member.
                    player.sendMessage(ChatColor.RED + "You are not in a guild."); 
                }
            } else if (clickedItem.getType() == Material.NETHER_STAR && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("View Guild Perks")) {
                if (guild != null) {
                    guildMainGui.getGuildPerksGui().open(player, guild);
                } else {
                    player.sendMessage(ChatColor.RED + "You must be in a guild to view perks.");
                    player.closeInventory();
                }
            } else if (clickedItem.getType() == Material.CHEST && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("Guild Bank")) {
                if (guild != null) {
                    guildMainGui.getGuildBankGui().open(player);
                } else {
                    player.sendMessage(ChatColor.RED + "You must be in a guild to use the bank.");
                    player.closeInventory();
                }
            } else if (clickedItem.getType() == Material.BARRIER && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("You are not in a guild")){
                player.closeInventory(); // Just close if they click the 'not in guild' item
            }
            // Add more button handlers here as GUI evolves
        } else if (inventoryTitle.startsWith(membersGuiTitlePrefix)) {
            event.setCancelled(true);
            Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
            if (guild == null) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "Could not retrieve guild information.");
                return;
            }

            String title = event.getView().getTitle(); // e.g., "Guild Members - Page 1/2"
            // Extract current page from title
            int currentPage = 1;
            try {
                String pagePart = title.substring(title.indexOf("Page ") + 5, title.indexOf("/"));
                currentPage = Integer.parseInt(pagePart.trim());
            } catch (Exception e) {
                // Could not parse page, default to 1 or close
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "Error with GUI title parsing.");
                return;
            }

            if (clickedItem.getType() == Material.ARROW) {
                if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("Next Page")) {
                    guildMainGui.getGuildMembersGui().open(player, guild, currentPage + 1);
                } else if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("Previous Page")) {
                    guildMainGui.getGuildMembersGui().open(player, guild, currentPage - 1);
                }
            } else if (clickedItem.getType() == Material.BARRIER && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("Back to Guild Info")) {
                guildMainGui.open(player);
            }
        } else if (inventoryTitle.startsWith(contributeGuiTitlePrefix)) {
            Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
            if (guild == null) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "Could not retrieve guild information.");
                return;
            }

            int clickedSlot = event.getSlot();
            // Allow players to place items in the contribution slots (0-17)
            // or take items they've placed there from their own inventory.
            // Cancel event only if they click on the control items in the bottom row (18-26)
            // or try to interact with the GUI inventory in a forbidden way (e.g. shift-clicking a GUI item).

            if (event.getClickedInventory() == topInventory && clickedSlot >= GuildContributeGui.getContributionSlotsCount()) {
                 event.setCancelled(true); // Interacting with control items or empty space in control row
            }
            // If event is not cancelled yet, it means they are clicking in the top 2 rows (contribution area)
            // or in their own inventory. Bukkit handles item placement/taking from these slots correctly by default.

            if (clickedItem.getType() == Material.BARRIER && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("Back to Guild Info")) {
                event.setCancelled(true); // Ensure this specific click is cancelled
                guildMainGui.open(player);
            } else if (clickedItem.getType() == Material.GREEN_STAINED_GLASS_PANE && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("Confirm Contribution")) {
                event.setCancelled(true); // Ensure this specific click is cancelled
                List<ItemStack> contributedItems = new ArrayList<>();
                for (int i = 0; i < GuildContributeGui.getContributionSlotsCount(); i++) {
                    ItemStack item = topInventory.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        contributedItems.add(item.clone()); // Clone to prevent issues if manager modifies them
                        topInventory.setItem(i, null); // Clear the item from GUI
                    }
                }

                if (contributedItems.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "You haven't placed any items to contribute!");
                } else {
                    guildManager.contributeItems(player, guild, contributedItems);
                    // Messages to player are handled by contributeItems method.
                    // Items are already cleared from GUI.
                    player.closeInventory(); // Close after successful contribution
                }
            }
            // No specific handling for the info paper or gray glass panes, as setCancelled(true) for that row covers them.
        } else if (inventoryTitle.startsWith(perksGuiTitlePrefix)) {
            event.setCancelled(true); // All items in Perk GUI are for display only, except Back button
            Guild guild = guildManager.getGuildByPlayer(player.getUniqueId()); // Guild might be needed for future actions
            if (guild == null) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "Could not retrieve guild information.");
                return;
            }

            if (clickedItem.getType() == Material.BARRIER && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().contains("Back to Guild Info")) {
                guildMainGui.open(player);
            }
            // Other items in perk GUI are purely informational, no action on click needed yet.
        } else if (inventoryTitle.startsWith(bankGuiTitlePrefix)) {
            // For Guild Bank, we generally DO NOT cancel the event by default, 
            // as players need to be able to move items in and out.
            // Permission checks for withdrawal or specific slot interactions would go here if needed.
            // For now, all interactions are allowed, and saving happens on close.
            // No specific item click handling needed here yet, unless we add buttons inside the bank GUI.
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        Inventory closedInventory = event.getInventory();
        String inventoryTitle = event.getView().getTitle();

        String bankGuiTitlePrefix = GuildBankGui.TITLE_PREFIX;

        if (inventoryTitle.startsWith(bankGuiTitlePrefix)) {
            Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
            if (guild != null) {
                // The GuildBankGui class handles getting contents and calling GuildManager to save.
                guildMainGui.getGuildBankGui().saveBankFromInventory(guild, closedInventory);
                player.sendMessage(ChatColor.GREEN + "Guild bank contents saved.");
            } else {
                // This should ideally not happen if they could open it, but as a safeguard:
                player.sendMessage(ChatColor.RED + "Could not save guild bank: Guild not found.");
            }
        }
        // Could add saving for other GUIs if they allowed direct item manipulation and needed saving on close.
    }
}
