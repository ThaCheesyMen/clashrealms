package com.guildwars.services;

import com.guildwars.GuildWarsPlugin;
import com.guildwars.guild.Guild;
import com.guildwars.guild.outposts.OutpostType;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class HologramManager {

    private final GuildWarsPlugin plugin;
    private boolean decentHologramsEnabled = false;

    public HologramManager(GuildWarsPlugin plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") != null && Bukkit.getPluginManager().getPlugin("DecentHolograms").isEnabled()) {
            this.decentHologramsEnabled = true;
            plugin.getLogger().info("DecentHolograms found and enabled. Holograms will use static timers.");
        } else {
            plugin.getLogger().warning("DecentHolograms not found or not enabled. Outpost holograms will be disabled.");
        }
    }

    // Called by GuildWarsPlugin after all plugins have loaded
    public void initializeAfterServerLoad() {
        if (!this.decentHologramsEnabled && Bukkit.getPluginManager().getPlugin("DecentHolograms") != null) { // If it was only marked false because it wasn't enabled yet
            if (Bukkit.getPluginManager().getPlugin("DecentHolograms").isEnabled()) {
                this.decentHologramsEnabled = true;
                plugin.getLogger().info("DecentHolograms is now confirmed enabled. Holograms will be used.");
                // Placeholder registration would go here if re-enabled in the future
            } else {
                plugin.getLogger().warning("DecentHolograms found but still not enabled after server load. Holograms disabled.");
                this.decentHologramsEnabled = false;
            }
        } else if (this.decentHologramsEnabled) {
            plugin.getLogger().info("DecentHolograms was already confirmed enabled (or present and assumed so). Final check passed.");
        }
        // If decentHologramsPresent was false from constructor (plugin not found), this method won't change that.
    }

    private String getHologramName(Guild guild, OutpostType outpostType) {
        String sanitizedGuildName = guild.getName().toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (sanitizedGuildName.isEmpty()) sanitizedGuildName = "invalidguild";
        return "gwoutpost_" + sanitizedGuildName + "__" + outpostType.name().toLowerCase(); 
    }

    public void createOutpostHologram(Location location, Guild guild, OutpostType outpostType, long nextTickTimestamp) {
        if (!decentHologramsEnabled || location == null || guild == null || outpostType == null) {
            return;
        }
        String hologramName = getHologramName(guild, outpostType);
        DHAPI.removeHologram(hologramName); 

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.translateAlternateColorCodes('&', "&6&l--=[ &b&l" + guild.getName() + " &6&l]=--"));
        lines.add(ChatColor.translateAlternateColorCodes('&', "&e&l" + outpostType.getDisplayName()));
        
        String staticTimeRemaining = formatTimeRemaining(nextTickTimestamp);

        switch (outpostType) {
            case XP_SIPHON:
                lines.add(ChatColor.translateAlternateColorCodes('&', "&7Generates: &a" + plugin.getConfig().getLong("outposts.XP_SIPHON.generation-xp", 100) + " XP"));
                lines.add(ChatColor.translateAlternateColorCodes('&', "&7Interval: &f" + plugin.getConfig().getLong("outposts.XP_SIPHON.generation-interval-hours", 6) + "h"));
                lines.add(ChatColor.translateAlternateColorCodes('&', "&7Next Tick: &e" + staticTimeRemaining));
                break;
            case BARRACKS:
                lines.add(ChatColor.translateAlternateColorCodes('&', "&7Generates: &a" + plugin.getConfig().getLong("outposts.BARRACKS.generation-xp", 25) + " XP"));
                lines.add(ChatColor.translateAlternateColorCodes('&', "&7Interval: &f" + plugin.getConfig().getLong("outposts.BARRACKS.generation-interval-hours", 8) + "h"));
                lines.add(ChatColor.translateAlternateColorCodes('&', "&7Next Tick: &e" + staticTimeRemaining));
                break;
            case RESOURCE_SILO:
                lines.add(ChatColor.translateAlternateColorCodes('&', "&7Status: &aOperational"));
                lines.add(ChatColor.translateAlternateColorCodes('&', "&7Chance: &f" + plugin.getConfig().getInt("outposts.RESOURCE_SILO.generation-chance-percent", 20) + "% / " + plugin.getConfig().getLong("outposts.RESOURCE_SILO.generation-interval-hours", 12) + "h"));
                lines.add(ChatColor.translateAlternateColorCodes('&', "&7Next Attempt: &e" + staticTimeRemaining));
                ConfigurationSection resourceConfig = plugin.getConfig().getConfigurationSection("outposts.RESOURCE_SILO.generated-resources");
                if (resourceConfig != null && !resourceConfig.getKeys(false).isEmpty()) {
                    List<String> resourceSamples = new ArrayList<>(resourceConfig.getKeys(false));
                    String sampleText = resourceSamples.stream().limit(2).collect(Collectors.joining(", "));
                    if (resourceSamples.size() > 2) sampleText += ", etc.";
                    lines.add(ChatColor.translateAlternateColorCodes('&', "&7Produces: &f" + sampleText));
                }
                break;
        }
        Location hologramLocation = location; 
        try {
            Hologram hologram = DHAPI.createHologram(hologramName, hologramLocation, lines);
            if (hologram == null) {
                plugin.getLogger().warning("DHAPI.createHologram returned null for: " + hologramName);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating/updating hologram " + hologramName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void deleteOutpostHologram(Location location, Guild guild, OutpostType outpostType) {
        if (!decentHologramsEnabled || guild == null || outpostType == null) return;
        String hologramName = getHologramName(guild, outpostType);
        if (DHAPI.getHologram(hologramName) != null) {
            DHAPI.removeHologram(hologramName);
        }
    }
    
    public void deleteAllOutpostHologramsForGuild(Guild guild) {
        if (!decentHologramsEnabled || guild == null) return;
        for (OutpostType type : OutpostType.values()) {
            String hologramName = getHologramName(guild, type);
            if (DHAPI.getHologram(hologramName) != null) {
                DHAPI.removeHologram(hologramName);
            }
        }
    }

    public boolean isEnabled() {
        return decentHologramsEnabled;
    }

    private String formatTimeRemaining(long nextTickTimestamp) {
        if (nextTickTimestamp <= 0) return "Calculating...";
        long remainingMillis = nextTickTimestamp - System.currentTimeMillis();
        if (remainingMillis <= 0) return "Processing!";

        long hours = TimeUnit.MILLISECONDS.toHours(remainingMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60;

        if (hours > 0) return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        if (minutes > 0) return String.format("%dm %02ds", minutes, seconds);
        return String.format("%ds", seconds);
    }
}
