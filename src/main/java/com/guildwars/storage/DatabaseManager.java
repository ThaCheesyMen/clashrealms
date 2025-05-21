package com.guildwars.storage;

import com.guildwars.GuildWarsPlugin;
import com.guildwars.guild.Guild;
import com.guildwars.guild.outposts.OutpostType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {

    private final GuildWarsPlugin plugin;
    private Connection connection;
    private final String dbPath;

    public DatabaseManager(GuildWarsPlugin plugin) {
        this.plugin = plugin;
        this.dbPath = "jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + File.separator + "guilds.db";
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection(dbPath);
            } catch (ClassNotFoundException e) {
                plugin.getLogger().severe("SQLite JDBC driver not found! Ensure it's in the classpath.");
                throw new SQLException("SQLite JDBC driver not found", e);
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not connect to SQLite database: " + e.getMessage());
                throw e;
            }
        }
        return connection;
    }

    public void initializeDatabase() {
        String createGuildsTable = "CREATE TABLE IF NOT EXISTS guilds (" +
                "name TEXT PRIMARY KEY NOT NULL, " +
                "leader_uuid TEXT NOT NULL, " +
                "level INTEGER NOT NULL DEFAULT 1, " +
                "current_xp BIGINT NOT NULL DEFAULT 0, " +
                "home_world TEXT DEFAULT NULL, " +
                "home_x REAL DEFAULT NULL, " +
                "home_y REAL DEFAULT NULL, " +
                "home_z REAL DEFAULT NULL, " +
                "home_yaw REAL DEFAULT NULL, " +
                "home_pitch REAL DEFAULT NULL" +
                ");";

        String createGuildMembersTable = "CREATE TABLE IF NOT EXISTS guild_members (" +
                "guild_name TEXT NOT NULL, " +
                "player_uuid TEXT NOT NULL, " +
                "is_officer INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (guild_name, player_uuid), " +
                "FOREIGN KEY (guild_name) REFERENCES guilds(name) ON DELETE CASCADE" +
                ");";
        
        String createGuildInvitesTable = "CREATE TABLE IF NOT EXISTS guild_invites (" +
                "invited_player_uuid TEXT PRIMARY KEY NOT NULL, " +
                "inviting_guild_name TEXT NOT NULL" +
                ");";

        String createGuildClaimsTable = "CREATE TABLE IF NOT EXISTS guild_claims (" +
                "guild_name TEXT NOT NULL, " +
                "world_name TEXT NOT NULL, " +
                "chunk_x INTEGER NOT NULL, " +
                "chunk_z INTEGER NOT NULL, " +
                "PRIMARY KEY (world_name, chunk_x, chunk_z), " +
                "FOREIGN KEY (guild_name) REFERENCES guilds(name) ON DELETE CASCADE" +
                ");";

        String createGuildBankTable = "CREATE TABLE IF NOT EXISTS guild_bank_items (" +
                "guild_name TEXT NOT NULL, " +
                "slot INTEGER NOT NULL, " +
                "item_data TEXT NOT NULL, " +
                "PRIMARY KEY (guild_name, slot), " +
                "FOREIGN KEY (guild_name) REFERENCES guilds(name) ON DELETE CASCADE" +
                ");";

        String createGuildOutpostsPreciseTable = "CREATE TABLE IF NOT EXISTS guild_outposts_precise (" +
                "guild_name TEXT NOT NULL, " +
                "outpost_type TEXT NOT NULL, " +
                "world_name TEXT NOT NULL, " +
                "core_x INTEGER NOT NULL, " +
                "core_y INTEGER NOT NULL, " +
                "core_z INTEGER NOT NULL, " +
                "next_tick_timestamp BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (guild_name, outpost_type), " +
                "FOREIGN KEY (guild_name) REFERENCES guilds(name) ON DELETE CASCADE" +
                ");";

        String createGuildWarsTable = "CREATE TABLE IF NOT EXISTS guild_wars (" +
                "guild1_name TEXT NOT NULL, " +
                "guild2_name TEXT NOT NULL, " +
                "start_date INTEGER NOT NULL, " +
                "guild1_score INTEGER NOT NULL DEFAULT 0, " +
                "guild2_score INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (guild1_name, guild2_name), " +
                "FOREIGN KEY (guild1_name) REFERENCES guilds(name) ON DELETE CASCADE, " +
                "FOREIGN KEY (guild2_name) REFERENCES guilds(name) ON DELETE CASCADE, " +
                "CHECK (guild1_name < guild2_name)" +
                ");";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createGuildsTable);
            stmt.execute(createGuildMembersTable);
            stmt.execute(createGuildInvitesTable);
            stmt.execute(createGuildClaimsTable);
            stmt.execute(createGuildBankTable);
            stmt.execute(createGuildOutpostsPreciseTable);
            stmt.execute(createGuildWarsTable);
            plugin.getLogger().info("Database tables initialized.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error initializing DB tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                 plugin.getLogger().info("SQLite database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing SQLite connection: " + e.getMessage());
        }
    }

    public Map<String, Guild> loadAllGuilds() {
        Map<String, Guild> guildsMap = new HashMap<>();
        String sql = "SELECT name, leader_uuid, level, current_xp, home_world, home_x, home_y, home_z, home_yaw, home_pitch FROM guilds";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String guildName = rs.getString("name");
                Guild guild = new Guild(guildName, UUID.fromString(rs.getString("leader_uuid")), rs.getInt("level"), rs.getLong("current_xp"));
                String homeWorld = rs.getString("home_world");
                if (homeWorld != null) {
                    guild.setRawHomeLocation(homeWorld, rs.getDouble("home_x"), rs.getDouble("home_y"), rs.getDouble("home_z"), rs.getFloat("home_yaw"), rs.getFloat("home_pitch"));
                }
                guildsMap.put(guildName, guild);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading basic guild info: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyMap(); 
        }

        if (!guildsMap.isEmpty()) {
            plugin.getLogger().info("Loading related data for " + guildsMap.size() + " guilds...");
            for (Guild guild : guildsMap.values()) {
                try {
                    loadGuildBank(guild); 
                    loadGuildOutposts(guild);
                    // loadGuildClaims(guild); // Already handled in GuildManager's loadAllData
                    // loadGuildMembersAndOfficers is also handled in GuildManager's loadAllData separately
                } catch (Exception e) { 
                    plugin.getLogger().severe("Failed to load related data (bank/outposts) for guild: " + guild.getName() + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
            plugin.getLogger().info("Finished loading related guild data.");
        }
        return guildsMap;
    }

    public void loadGuildClaims(Guild guild) {
        String sql = "SELECT world_name, chunk_x, chunk_z FROM guild_claims WHERE guild_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guild.getName());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    guild.loadClaimedChunk(rs.getString("world_name") + ":" + rs.getInt("chunk_x") + ":" + rs.getInt("chunk_z"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading claims for guild '" + guild.getName() + "': " + e.getMessage());
        }
    }

    public void loadGuildMembersAndOfficers(Map<String, Guild> guildsMap) {
        String sql = "SELECT guild_name, player_uuid, is_officer FROM guild_members";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Guild guild = guildsMap.get(rs.getString("guild_name"));
                if (guild != null) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    guild.addMember(playerUuid);
                    if (rs.getInt("is_officer") == 1 && !guild.getLeader().equals(playerUuid)) {
                        guild.promoteOfficer(playerUuid);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading guild members/officers: " + e.getMessage());
        }
    }

    public Map<UUID, String> loadPendingInvites() {
        Map<UUID, String> invitesMap = new HashMap<>();
        String sql = "SELECT invited_player_uuid, inviting_guild_name FROM guild_invites";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                invitesMap.put(UUID.fromString(rs.getString("invited_player_uuid")), rs.getString("inviting_guild_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading pending invites: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyMap(); 
        }
        return invitesMap;
    }

    public void saveNewGuild(Guild guild) {
        String sqlGuild = "INSERT INTO guilds(name, leader_uuid, level, current_xp, home_world, home_x, home_y, home_z, home_yaw, home_pitch) VALUES(?,?,?,?,?,?,?,?,?,?)";
        String sqlMember = "INSERT INTO guild_members(guild_name, player_uuid, is_officer) VALUES(?,?,?)";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmtGuild = conn.prepareStatement(sqlGuild); PreparedStatement pstmtMember = conn.prepareStatement(sqlMember)) {
                pstmtGuild.setString(1, guild.getName());
                pstmtGuild.setString(2, guild.getLeader().toString());
                pstmtGuild.setInt(3, guild.getLevel());
                pstmtGuild.setLong(4, guild.getCurrentXp());
                pstmtGuild.setString(5, guild.getHomeWorldName());
                pstmtGuild.setObject(6, guild.getHomeX());
                pstmtGuild.setObject(7, guild.getHomeY());
                pstmtGuild.setObject(8, guild.getHomeZ());
                pstmtGuild.setObject(9, guild.getHomeYaw());
                pstmtGuild.setObject(10, guild.getHomePitch());
                pstmtGuild.executeUpdate();
                pstmtMember.setString(1, guild.getName());
                pstmtMember.setString(2, guild.getLeader().toString());
                pstmtMember.setInt(3, 1);
                pstmtMember.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("Error saving new guild '" + guild.getName() + "': " + e.getMessage());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB error during saveNewGuild: " + e.getMessage());
        }
    }

    public void deleteGuildDB(String guildName) {
        String sqlGuild = "DELETE FROM guilds WHERE name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmtGuild = conn.prepareStatement(sqlGuild)) {
            pstmtGuild.setString(1, guildName);
            pstmtGuild.executeUpdate(); 
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting guild '" + guildName + "': " + e.getMessage());
        }
        clearInvitesFromGuildDB(guildName); 
    }

    public void addGuildMemberDB(String guildName, UUID playerUuid, boolean isOfficer) {
        String sql = "INSERT OR REPLACE INTO guild_members(guild_name, player_uuid, is_officer) VALUES(?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildName);
            pstmt.setString(2, playerUuid.toString());
            pstmt.setInt(3, isOfficer ? 1 : 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding/updating member: " + e.getMessage());
        }
    }

    public void removeGuildMemberDB(String guildName, UUID playerUuid) {
        String sql = "DELETE FROM guild_members WHERE guild_name = ? AND player_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildName);
            pstmt.setString(2, playerUuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing member: " + e.getMessage());
        }
    }

    public void updateGuildMemberRoleDB(String guildName, UUID playerUuid, boolean isOfficer) {
        addGuildMemberDB(guildName, playerUuid, isOfficer);
    }

    public void saveInviteDB(UUID invitedPlayerUuid, String guildName) {
        String sql = "INSERT OR REPLACE INTO guild_invites(invited_player_uuid, inviting_guild_name) VALUES(?,?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, invitedPlayerUuid.toString());
            pstmt.setString(2, guildName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving invite: " + e.getMessage());
        }
    }

    public void removeInviteDB(UUID invitedPlayerUuid) {
        String sql = "DELETE FROM guild_invites WHERE invited_player_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, invitedPlayerUuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing invite: " + e.getMessage());
        }
    }
    
    public void clearInvitesFromGuildDB(String guildName) {
        String sql = "DELETE FROM guild_invites WHERE inviting_guild_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error clearing invites for guild '" + guildName + "': " + e.getMessage());
        }
    }

    public void updateGuildLeaderDB(String guildName, UUID newLeaderUuid) {
        String sql = "UPDATE guilds SET leader_uuid = ? WHERE name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newLeaderUuid.toString());
            pstmt.setString(2, guildName);
            pstmt.executeUpdate();
            addGuildMemberDB(guildName, newLeaderUuid, true); 
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating leader: " + e.getMessage());
        }
    }

    public void updateGuildLevelAndXp(String guildName, int level, long currentXp) {
        String sql = "UPDATE guilds SET level = ?, current_xp = ? WHERE name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, level);
            pstmt.setLong(2, currentXp);
            pstmt.setString(3, guildName);
            if (pstmt.executeUpdate() == 0) {
                plugin.getLogger().warning("Tried to update level/XP for non-existent guild: " + guildName);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating level/XP: " + e.getMessage());
        }
    }

    public void saveGuildClaim(String guildName, String worldName, int chunkX, int chunkZ) {
        String sql = "INSERT OR IGNORE INTO guild_claims(guild_name, world_name, chunk_x, chunk_z) VALUES(?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildName);
            pstmt.setString(2, worldName);
            pstmt.setInt(3, chunkX);
            pstmt.setInt(4, chunkZ);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving claim: " + e.getMessage());
        }
    }

    public void removeGuildClaim(String worldName, int chunkX, int chunkZ) {
        String sql = "DELETE FROM guild_claims WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, worldName);
            pstmt.setInt(2, chunkX);
            pstmt.setInt(3, chunkZ);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing claim: " + e.getMessage());
        }
    }

    public void updateGuildHomeDB(String guildName, Location location) {
        String sql = "UPDATE guilds SET home_world = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ? WHERE name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (location != null) {
                pstmt.setString(1, location.getWorld().getName());
                pstmt.setDouble(2, location.getX());
                pstmt.setDouble(3, location.getY());
                pstmt.setDouble(4, location.getZ());
                pstmt.setFloat(5, location.getYaw());
                pstmt.setFloat(6, location.getPitch());
            } else {
                pstmt.setNull(1, java.sql.Types.VARCHAR);
                pstmt.setNull(2, java.sql.Types.REAL);
                pstmt.setNull(3, java.sql.Types.REAL);
                pstmt.setNull(4, java.sql.Types.REAL);
                pstmt.setNull(5, java.sql.Types.REAL);
                pstmt.setNull(6, java.sql.Types.REAL);
            }
            pstmt.setString(7, guildName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating home: " + e.getMessage());
        }
    }

    public void saveGuildBank(Guild guild) {
        String deleteSql = "DELETE FROM guild_bank_items WHERE guild_name = ?";
        String insertSql = "INSERT INTO guild_bank_items(guild_name, slot, item_data) VALUES(?,?,?)";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmtDelete = conn.prepareStatement(deleteSql); PreparedStatement pstmtInsert = conn.prepareStatement(insertSql)) {
                pstmtDelete.setString(1, guild.getName());
                pstmtDelete.executeUpdate();
                ItemStack[] bankContents = guild.getBankContents();
                for (int i = 0; i < bankContents.length; i++) {
                    if (bankContents[i] != null && bankContents[i].getType() != Material.AIR) {
                        pstmtInsert.setString(1, guild.getName());
                        pstmtInsert.setInt(2, i);
                        try {
                            pstmtInsert.setString(3, Base64.getEncoder().encodeToString(bankContents[i].serializeAsBytes()));
                            pstmtInsert.addBatch();
                        } catch (Exception e) {
                            plugin.getLogger().warning("Could not serialize bank item for " + guild.getName() + " slot " + i + ": " + e.getMessage());
                        }
                    }
                }
                pstmtInsert.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("Error saving guild bank for '" + guild.getName() + "': " + e.getMessage());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB error during saveGuildBank: " + e.getMessage());
        }
    }

    public void loadGuildBank(Guild guild) {
        String sql = "SELECT slot, item_data FROM guild_bank_items WHERE guild_name = ?";
        ItemStack[] bankContents = new ItemStack[Guild.BANK_SIZE];
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guild.getName());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    if (slot >= 0 && slot < Guild.BANK_SIZE) {
                        try {
                            bankContents[slot] = ItemStack.deserializeBytes(Base64.getDecoder().decode(rs.getString("item_data")));
                        } catch (Exception e) {
                            plugin.getLogger().warning("Could not deserialize bank item for " + guild.getName() + " slot " + slot + ": " + e.getMessage());
                        }
                    }
                }
            }
            guild.setBankContents(bankContents);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading guild bank for '" + guild.getName() + "': " + e.getMessage());
            guild.setBankContents(new ItemStack[Guild.BANK_SIZE]);
        }
    }

    public void saveGuildOutposts(Guild guild) {
        String deleteSql = "DELETE FROM guild_outposts_precise WHERE guild_name = ?";
        String insertSql = "INSERT INTO guild_outposts_precise(guild_name, outpost_type, world_name, core_x, core_y, core_z, next_tick_timestamp) VALUES(?,?,?,?,?,?,?)";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmtDelete = conn.prepareStatement(deleteSql); PreparedStatement pstmtInsert = conn.prepareStatement(insertSql)) {
                pstmtDelete.setString(1, guild.getName());
                pstmtDelete.executeUpdate();
                for (Map.Entry<OutpostType, Guild.ActiveOutpostInfo> entry : guild.getAllActiveOutposts().entrySet()) {
                    Guild.LocationData locData = entry.getValue().location();
                    pstmtInsert.setString(1, guild.getName());
                    pstmtInsert.setString(2, entry.getKey().name());
                    pstmtInsert.setString(3, locData.worldName());
                    pstmtInsert.setInt(4, locData.x());
                    pstmtInsert.setInt(5, locData.y());
                    pstmtInsert.setInt(6, locData.z());
                    pstmtInsert.setLong(7, entry.getValue().nextTickTimestamp());
                    pstmtInsert.addBatch();
                }
                pstmtInsert.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("Error saving outposts for guild '" + guild.getName() + "': " + e.getMessage());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB error during saveGuildOutposts: " + e.getMessage());
        }
    }

    public void loadGuildOutposts(Guild guild) {
        String sql = "SELECT outpost_type, world_name, core_x, core_y, core_z, next_tick_timestamp FROM guild_outposts_precise WHERE guild_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guild.getName());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        OutpostType type = OutpostType.valueOf(rs.getString("outpost_type").toUpperCase());
                        Guild.LocationData locData = new Guild.LocationData(
                                rs.getString("world_name"), 
                                rs.getInt("core_x"), 
                                rs.getInt("core_y"), 
                                rs.getInt("core_z"));
                        long nextTick = rs.getLong("next_tick_timestamp");
                        guild.addOutpost(type, locData, nextTick);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Unknown outpost type or bad data for " + guild.getName() + ": " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading outposts for guild '" + guild.getName() + "': " + e.getMessage());
        }
    }
    
    public void saveWar(String guild1Name, String guild2Name, long startDate) {
        String g1 = guild1Name.compareTo(guild2Name) < 0 ? guild1Name : guild2Name;
        String g2 = guild1Name.compareTo(guild2Name) < 0 ? guild2Name : guild1Name;
        String sql = "INSERT OR REPLACE INTO guild_wars (guild1_name, guild2_name, start_date, guild1_score, guild2_score) VALUES (?, ?, ?, 0, 0)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, g1);
            pstmt.setString(2, g2);
            pstmt.setLong(3, startDate);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving war: " + e.getMessage());
        }
    }

    public void deleteWar(String guild1Name, String guild2Name) {
        String g1 = guild1Name.compareTo(guild2Name) < 0 ? guild1Name : guild2Name;
        String g2 = guild1Name.compareTo(guild2Name) < 0 ? guild2Name : guild1Name;
        String sql = "DELETE FROM guild_wars WHERE guild1_name = ? AND guild2_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, g1);
            pstmt.setString(2, g2);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting war: " + e.getMessage());
        }
    }
    
    public void updateWarScore(String guild1Name, String guild2Name, int guild1ScoreDelta, int guild2ScoreDelta) {
        String g1 = guild1Name.compareTo(guild2Name) < 0 ? guild1Name : guild2Name;
        String g2 = guild1Name.compareTo(guild2Name) < 0 ? guild2Name : guild1Name;
        // Determine which score field to update based on who is g1
        String scoreFieldForGuild1Param = g1.equals(guild1Name) ? "guild1_score" : "guild2_score";
        String scoreFieldForGuild2Param = g1.equals(guild1Name) ? "guild2_score" : "guild1_score";
        int actualDeltaForG1Field = g1.equals(guild1Name) ? guild1ScoreDelta : guild2ScoreDelta;
        int actualDeltaForG2Field = g1.equals(guild1Name) ? guild2ScoreDelta : guild1ScoreDelta;

        String sql = "UPDATE guild_wars SET guild1_score = guild1_score + ?, guild2_score = guild2_score + ? WHERE guild1_name = ? AND guild2_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // If acting guild (guild1Name) is g1, its points go to guild1_score. Else its points go to guild2_score.
            if (g1.equals(guild1Name)) { // guild1Name is g1, guild2Name is g2
                 pstmt.setInt(1, guild1ScoreDelta); // delta for g1_score field
                 pstmt.setInt(2, guild2ScoreDelta); // delta for g2_score field
            } else { // guild1Name is g2, guild2Name is g1
                 pstmt.setInt(1, guild2ScoreDelta); // delta for g1_score field (which is opposing guild here)
                 pstmt.setInt(2, guild1ScoreDelta); // delta for g2_score field (which is acting guild here)
            }
            pstmt.setString(3, g1);
            pstmt.setString(4, g2);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating war score between " + g1 + " and " + g2 + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Map<String, Integer> getWarScore(String guild1Name, String guild2Name) {
        String g1_norm = guild1Name.compareTo(guild2Name) < 0 ? guild1Name : guild2Name;
        String g2_norm = guild1Name.compareTo(guild2Name) < 0 ? guild2Name : guild1Name;
        Map<String, Integer> scores = new HashMap<>();
        // Initialize with 0 in case no record or error
        scores.put(guild1Name, 0);
        scores.put(guild2Name, 0);

        String sql = "SELECT guild1_name, guild1_score, guild2_name, guild2_score FROM guild_wars WHERE guild1_name = ? AND guild2_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, g1_norm);
            pstmt.setString(2, g2_norm);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // rs.getString("guild1_name") will be g1_norm
                // rs.getString("guild2_name") will be g2_norm
                scores.put(g1_norm, rs.getInt("guild1_score"));
                scores.put(g2_norm, rs.getInt("guild2_score"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting war score between " + g1_norm + " and " + g2_norm + ": " + e.getMessage());
            e.printStackTrace();
            // Keep scores at 0 if error
        }
        // Ensure the returned map is keyed by the original input names
        Map<String, Integer> resultScores = new HashMap<>();
        resultScores.put(guild1Name, scores.getOrDefault(guild1Name, 0));
        resultScores.put(guild2Name, scores.getOrDefault(guild2Name, 0));
        return resultScores;
    }

    public Map<String, List<String>> loadActiveWars() {
        Map<String, List<String>> warsMap = new HashMap<>();
        String sql = "SELECT guild1_name, guild2_name FROM guild_wars"; 
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String g1_db = rs.getString("guild1_name");
                String g2_db = rs.getString("guild2_name");
                warsMap.computeIfAbsent(g1_db, k -> new ArrayList<>()).add(g2_db);
                warsMap.computeIfAbsent(g2_db, k -> new ArrayList<>()).add(g1_db);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading active war relationships: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyMap();
        }
        return warsMap;
    }
}
