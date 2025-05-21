package com.guildwars.gui;

import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import com.guildwars.guild.PerkManager;
import com.guildwars.guild.perks.GuildPerkType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GuildPerksGui {

    private final GuildManager guildManager; // Contains PerkManager
    public static final String TITLE = ChatColor.DARK_PURPLE + "Guild Perks";

    public GuildPerksGui(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    public void open(Player player, Guild guild) {
        if (guild == null) {
            player.sendMessage(ChatColor.RED + "You are not in a guild or guild data is missing.");
            return;
        }
        PerkManager perkManager = guildManager.getPerkManager();
        if (perkManager == null) {
            player.sendMessage(ChatColor.RED + "Perk system is not available.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, TITLE + ": " + guild.getName());
        int slot = 0;

        for (GuildPerkType perkType : GuildPerkType.values()) {
            if (slot >= 45) break; // Max 5 rows for perks, last row for controls

            ItemStack perkItem = new ItemStack(getMaterialForPerkType(perkType));
            ItemMeta meta = perkItem.getItemMeta();
            if (meta == null) continue;

            List<String> lore = new ArrayList<>();
            boolean isUnlocked = perkManager.hasPerk(guild.getLevel(), perkType);

            meta.setDisplayName((isUnlocked ? ChatColor.GREEN : ChatColor.GRAY) + formatPerkTypeName(perkType));
            
            lore.add(getPerkDescription(perkType));
            lore.add(""); // Spacer

            if (isUnlocked) {
                lore.add(ChatColor.GREEN + "Status: Unlocked");
                String effect = getCurrentEffect(guild, perkType, perkManager);
                if (effect != null && !effect.isEmpty()) {
                    lore.add(ChatColor.AQUA + "Current Effect: " + ChatColor.WHITE + effect);
                }
            } else {
                int levelRequired = perkManager.getLevelRequiredForPerk(perkType);
                lore.add(ChatColor.RED + "Status: Locked");
                if (levelRequired > 0) {
                    lore.add(ChatColor.YELLOW + "Unlocks at Guild Level: " + ChatColor.AQUA + levelRequired);
                } else {
                    lore.add(ChatColor.GRAY + "This perk is currently not unlockable.");
                }
            }

            meta.setLore(lore);
            perkItem.setItemMeta(meta);
            gui.setItem(slot++, perkItem);
        }

        // Back Button
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Back to Guild Info");
            backButton.setItemMeta(backMeta);
        }
        gui.setItem(49, backButton); // Bottom center

        player.openInventory(gui);
    }

    private String formatPerkTypeName(GuildPerkType perkType) {
        String[] words = perkType.name().toLowerCase().split("_");
        StringBuilder formattedName = new StringBuilder();
        for (String word : words) {
            formattedName.append(Character.toUpperCase(word.charAt(0)))
                         .append(word.substring(1))
                         .append(" ");
        }
        return formattedName.toString().trim();
    }

    private Material getMaterialForPerkType(GuildPerkType perkType) {
        switch (perkType) {
            case MAX_MEMBERS_INCREASE:
                return Material.PLAYER_HEAD;
            case ALLOW_GUILD_SETHOME:
                return Material.BEACON;
            case GUILD_HOME_PARTICLE:
                return Material.REDSTONE_TORCH;
            case PASSIVE_HASTE_AURA:
                return Material.GOLDEN_PICKAXE;
            case MAX_CLAIMED_CHUNKS_INCREASE:
                return Material.GRASS_BLOCK;
            default:
                return Material.PAPER; // Default icon
        }
    }

    private String getPerkDescription(GuildPerkType perkType) {
        switch (perkType) {
            case MAX_MEMBERS_INCREASE:
                return ChatColor.GRAY + "Increases the maximum number of members your guild can have.";
            case ALLOW_GUILD_SETHOME:
                return ChatColor.GRAY + "Allows guild officers to set a guild home teleport point.";
            case GUILD_HOME_PARTICLE: // Example description
                return ChatColor.GRAY + "Adds a particle effect to the guild home teleport (if set).";
            case PASSIVE_HASTE_AURA: // Example description
                return ChatColor.GRAY + "Grants a passive Haste effect to members near claimed land (Not Implemented).";
            case MAX_CLAIMED_CHUNKS_INCREASE:
                return ChatColor.GRAY + "Increases the maximum number of land chunks your guild can claim.";
            default:
                return ChatColor.GRAY + "An unknown guild perk.";
        }
    }

    private String getCurrentEffect(Guild guild, GuildPerkType perkType, PerkManager perkManager) {
        switch (perkType) {
            case MAX_MEMBERS_INCREASE:
                int bonus = perkManager.getAccumulatedIntValue(guild.getLevel(), perkType);
                return "+ " + bonus + " members (Total: " + guildManager.getEffectiveMaxMembers(guild) + ")";
            case ALLOW_GUILD_SETHOME:
                return guildManager.canGuildSetHome(guild) ? "Enabled" : "Disabled";
            case GUILD_HOME_PARTICLE:
                String particle = perkManager.getStringPerkValue(guild.getLevel(), perkType);
                return particle != null ? "Effect: " + particle : "No particle effect set.";
            case PASSIVE_HASTE_AURA:
                int amplifier = perkManager.getIntPerkValue(guild.getLevel(), perkType);
                return "Haste " + (amplifier + 1) + " (Not Implemented)";
            case MAX_CLAIMED_CHUNKS_INCREASE:
                 int bonusChunks = perkManager.getAccumulatedIntValue(guild.getLevel(), perkType);
                 int baseChunks = GuildManager.BASE_MAX_CLAIMS; // Assuming BASE_MAX_CLAIMS is accessible or known
                 return "+ " + bonusChunks + " chunks (Total: " + (baseChunks + bonusChunks) + ")";
            default:
                return "N/A";
        }
    }
}
