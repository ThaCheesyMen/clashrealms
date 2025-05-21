package com.guildwars;

import com.guildwars.commands.GuildChatCommand;
import com.guildwars.commands.GuildCommand;
import com.guildwars.events.PlayerChatListener;
import com.guildwars.events.GuildProtectionListener;
import com.guildwars.events.InventoryClickListener;
import com.guildwars.events.PlayerTerritoryListener;
import com.guildwars.events.GuildWarListener;
import com.guildwars.gui.GuildMainGui;
import com.guildwars.guild.GuildManager;
import com.guildwars.guild.PerkManager;
import com.guildwars.services.HologramManager; // Added for explicit access
import com.guildwars.storage.DatabaseManager;
import org.bukkit.Bukkit; // For Bukkit.getScheduler()
import org.bukkit.plugin.java.JavaPlugin;

public class GuildWarsPlugin extends JavaPlugin {

    private GuildManager guildManager;
    private DatabaseManager databaseManager;
    private PerkManager perkManager;
    private GuildMainGui guildMainGui;
    private HologramManager hologramManager; // Keep a reference if needed by other parts of plugin

    private long upkeepXpPerChunk;
    private long upkeepIntervalHours;
    private long xpSiphonCreationCost, xpSiphonXpGeneration, xpSiphonIntervalHours;
    private long barracksCreationCost, barracksXpGeneration, barracksIntervalHours;
    private long resourceSiloCreationCost, resourceSiloIntervalHours;
    private int resourceSiloGenerationChance;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveDefaultConfig();
        loadConfiguration();

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initializeDatabase();

        this.perkManager = new PerkManager(); 
        // GuildManager constructor now takes the plugin instance itself for config access if needed by sub-managers
        this.guildManager = new GuildManager(this, this.databaseManager, this.perkManager,
                this.upkeepXpPerChunk,
                this.xpSiphonCreationCost, this.xpSiphonXpGeneration, this.xpSiphonIntervalHours,
                this.barracksCreationCost, this.barracksXpGeneration, this.barracksIntervalHours,
                this.resourceSiloCreationCost, this.resourceSiloIntervalHours, this.resourceSiloGenerationChance);
        
        // HologramManager is initialized within GuildManager, but if we need direct access here:
        // this.hologramManager = guildManager.getHologramManager(); // Assuming GuildManager has a getter
        // Or, if HologramManager needs to be a plugin-level service like others:
        this.hologramManager = new HologramManager(this); // This is how it's currently structured from GuildManager's needs
                                                        // But GuildManager ALSO creates one. This leads to two instances.
                                                        // Let GuildManager own its HologramManager instance.
                                                        // We access it via guildManager.getHologramManager() if GuildWarsPlugin needs it.

        this.guildMainGui = new GuildMainGui(this.guildManager);

        getCommand("gc").setExecutor(new GuildChatCommand(this.guildManager));
        getCommand("guild").setExecutor(new GuildCommand(this.guildManager, this.guildMainGui));
        getCommand("guild").setTabCompleter(new GuildCommand(this.guildManager, this.guildMainGui));

        getServer().getPluginManager().registerEvents(new PlayerChatListener(this.guildManager), this);
        getServer().getPluginManager().registerEvents(new GuildProtectionListener(this.guildManager), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this.guildManager, this.guildMainGui), this);
        getServer().getPluginManager().registerEvents(new PlayerTerritoryListener(this.guildManager), this);
        getServer().getPluginManager().registerEvents(new GuildWarListener(this.guildManager), this);

        scheduleUpkeepTask();
        scheduleXpSiphonTask();
        scheduleBarracksTask();
        scheduleResourceSiloTask();

        // Delayed task to initialize hologram manager after other plugins (like DecentHolograms) are fully loaded
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (guildManager != null && guildManager.getHologramManager() != null) {
                guildManager.getHologramManager().initializeAfterServerLoad();
                // Reload holograms AFTER placeholders might have been registered
                guildManager.reloadAllOutpostHolograms(); 
            } else if (this.hologramManager != null) { // Fallback if guildManager did not init its own
                 this.hologramManager.initializeAfterServerLoad();
                 // If holograms are managed at plugin level, then GuildManager needs access to this instance
                 // And GuildManager should not create its own.
                 // For now, assuming GuildManager owns its HologramManager instance passed from its constructor implicitly by this plugin instance.
            }
        }, 1L); // Run 1 tick after server finishes loading normal plugins

        getLogger().info("GuildWarsPlugin has been enabled!");
    }

    private void loadConfiguration() {
        this.upkeepXpPerChunk = getConfig().getLong("upkeep-xp-per-chunk", 50L);
        this.upkeepIntervalHours = getConfig().getLong("upkeep-interval-hours", 24L);
        this.xpSiphonCreationCost = getConfig().getLong("outposts.XP_SIPHON.creation-cost-xp", 1000L);
        this.xpSiphonXpGeneration = getConfig().getLong("outposts.XP_SIPHON.generation-xp", 100L);
        this.xpSiphonIntervalHours = getConfig().getLong("outposts.XP_SIPHON.generation-interval-hours", 6L);
        this.barracksCreationCost = getConfig().getLong("outposts.BARRACKS.creation-cost-xp", 750L);
        this.barracksXpGeneration = getConfig().getLong("outposts.BARRACKS.generation-xp", 25L);
        this.barracksIntervalHours = getConfig().getLong("outposts.BARRACKS.generation-interval-hours", 8L);
        this.resourceSiloCreationCost = getConfig().getLong("outposts.RESOURCE_SILO.creation-cost-xp", 1200L);
        this.resourceSiloIntervalHours = getConfig().getLong("outposts.RESOURCE_SILO.generation-interval-hours", 12L);
        this.resourceSiloGenerationChance = getConfig().getInt("outposts.RESOURCE_SILO.generation-chance-percent", 20);
        getLogger().info("Configuration loaded.");
    }

    private void scheduleUpkeepTask() {
        long periodTicks = this.upkeepIntervalHours * 3600L * 20L;
        this.getServer().getScheduler().runTaskTimerAsynchronously(this, guildManager::processAllGuildUpkeep, 1200L, periodTicks);
        getLogger().info("Guild upkeep task scheduled (Interval: " + upkeepIntervalHours + "h).");
    }

    private void scheduleXpSiphonTask() {
        long periodTicks = this.xpSiphonIntervalHours * 3600L * 20L;
        this.getServer().getScheduler().runTaskTimerAsynchronously(this, guildManager::processXpSiphons, 2400L, periodTicks);
        getLogger().info("XP Siphon task scheduled (Interval: " + xpSiphonIntervalHours + "h).");
    }

    private void scheduleBarracksTask() {
        long periodTicks = this.barracksIntervalHours * 3600L * 20L;
        this.getServer().getScheduler().runTaskTimerAsynchronously(this, guildManager::processBarracks, 3600L, periodTicks);
        getLogger().info("Barracks task scheduled (Interval: " + barracksIntervalHours + "h).");
    }

    private void scheduleResourceSiloTask() {
        long periodTicks = this.resourceSiloIntervalHours * 3600L * 20L;
        this.getServer().getScheduler().runTaskTimerAsynchronously(this, () -> guildManager.processResourceSilos(this), 4800L, periodTicks);
        getLogger().info("Resource Silo task scheduled (Interval: " + resourceSiloIntervalHours + "h).");
    }

    @Override
    public void onDisable() {
        if (this.hologramManager != null && this.hologramManager.isEnabled()) {
            // Clean up all managed holograms if your manager keeps track of them all
            // Or iterate through guilds and delete their outpost holograms via GuildManager
            // For now, DecentHolograms usually handles removal of its own holograms on shutdown/reload if persistent.
            // If we created temporary holograms, they should be explicitly removed.
        }
        if (this.databaseManager != null) {
            this.databaseManager.closeConnection();
        }
        getLogger().info("GuildWarsPlugin has been disabled!");
    }

    public GuildManager getGuildManager() {
        return guildManager;
    }
}
