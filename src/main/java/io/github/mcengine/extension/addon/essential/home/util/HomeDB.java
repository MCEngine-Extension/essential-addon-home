package io.github.mcengine.extension.addon.essential.home.util;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.util.Vector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Database helper for the Home system using two tables:
 *
 * <pre>
 * CREATE TABLE IF NOT EXISTS home (
 *   home_id      INTEGER PRIMARY KEY AUTOINCREMENT,
 *   player_uuid  VARCHAR(36) NOT NULL UNIQUE,
 *   home_limit   INTEGER NOT NULL DEFAULT 3   -- -1 = unlimited
 * );
 *
 * CREATE TABLE IF NOT EXISTS home_data (
 *   home_data_id   INTEGER PRIMARY KEY AUTOINCREMENT,
 *   home_data_name TEXT NOT NULL,
 *   loc_x          REAL NOT NULL,
 *   loc_y          REAL NOT NULL,
 *   loc_z          REAL NOT NULL,
 *   player_uuid    VARCHAR(36) NOT NULL,
 *   UNIQUE(player_uuid, home_data_name),
 *   FOREIGN KEY (player_uuid) REFERENCES home(player_uuid) ON DELETE CASCADE
 * );
 * </pre>
 *
 * <p>Only X/Y/Z are stored, so teleports use the player's current world.</p>
 */
public class HomeDB {

    /** Active JDBC connection supplied by the Essential module. */
    private final Connection conn;

    /** Logger for reporting status and problems. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs the DB helper and ensures the tables exist.
     *
     * @param conn   JDBC connection.
     * @param logger Logger wrapper.
     */
    public HomeDB(Connection conn, MCEngineExtensionLogger logger) {
        this.conn = conn;
        this.logger = logger;
        createTable(); // kept method name for compatibility; now creates both tables.
    }

    /**
     * Creates the {@code home} and {@code home_data} tables if needed.
     * Ensures UNIQUE constraints and a foreign key on player_uuid.
     */
    public void createTable() {
        final String createHome = """
            CREATE TABLE IF NOT EXISTS home (
                home_id      INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid  VARCHAR(36) NOT NULL UNIQUE,
                home_limit   INTEGER NOT NULL DEFAULT 3
            );
            """;

        final String createHomeData = """
            CREATE TABLE IF NOT EXISTS home_data (
                home_data_id   INTEGER PRIMARY KEY AUTOINCREMENT,
                home_data_name TEXT NOT NULL,
                loc_x          REAL NOT NULL,
                loc_y          REAL NOT NULL,
                loc_z          REAL NOT NULL,
                player_uuid    VARCHAR(36) NOT NULL,
                UNIQUE(player_uuid, home_data_name),
                FOREIGN KEY (player_uuid) REFERENCES home(player_uuid) ON DELETE CASCADE
            );
            """;

        try (PreparedStatement ps1 = conn.prepareStatement(createHome);
             PreparedStatement ps2 = conn.prepareStatement(createHomeData)) {
            ps1.execute();
            ps2.execute();
            logger.info("Home tables created or already exist (home, home_data).");
        } catch (SQLException e) {
            logger.warning("Failed to create home tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ensures a {@code home} row exists for this player, inserting with default limit 3 if missing.
     *
     * @param playerUuid player UUID.
     */
    public void ensurePlayerRow(UUID playerUuid) {
        final String upsert = """
            INSERT INTO home (player_uuid, home_limit)
            SELECT ?, 3
            WHERE NOT EXISTS (SELECT 1 FROM home WHERE player_uuid = ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(upsert)) {
            final String id = playerUuid.toString();
            ps.setString(1, id);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to ensure player row for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the configured home limit for a player. A value &lt; 0 means unlimited.
     *
     * @param playerUuid player UUID.
     * @return limit value (default 3 if missing).
     */
    public int getHomeLimit(UUID playerUuid) {
        ensurePlayerRow(playerUuid);
        final String q = "SELECT home_limit FROM home WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("home_limit");
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to read home_limit for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return 3;
    }

    /**
     * Updates the player's home limit to an absolute value.
     *
     * @param playerUuid player UUID.
     * @param newLimit   new limit (use -1 for unlimited).
     * @return true if updated or inserted.
     */
    public boolean setHomeLimit(UUID playerUuid, int newLimit) {
        ensurePlayerRow(playerUuid);
        final String upd = "UPDATE home SET home_limit = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(upd)) {
            ps.setInt(1, newLimit);
            ps.setString(2, playerUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Failed to update home_limit for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Counts how many homes a player has saved.
     *
     * @param playerUuid player UUID.
     * @return number of rows in {@code home_data} for the player.
     */
    public int getHomeCount(UUID playerUuid) {
        final String q = "SELECT COUNT(1) AS c FROM home_data WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("c");
            }
        } catch (SQLException e) {
            logger.warning("Failed to count homes for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Determines if the player can create another home given their limit.
     * A limit &lt; 0 is treated as unlimited.
     *
     * @param playerUuid player UUID.
     * @return true if creation is permitted.
     */
    public boolean canCreateMoreHomes(UUID playerUuid) {
        int limit = getHomeLimit(playerUuid);
        if (limit < 0) return true; // unlimited
        return getHomeCount(playerUuid) < limit;
    }

    /**
     * Checks whether a player already has a home with the given name.
     *
     * @param playerUuid player UUID.
     * @param name       home name.
     * @return {@code true} if a row exists; {@code false} otherwise.
     */
    public boolean homeExists(UUID playerUuid, String name) {
        final String sql = "SELECT 1 FROM home_data WHERE player_uuid = ? AND home_data_name = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warning("Failed to check existing home '" + name + "' for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Inserts a new player's named home coordinates into {@code home_data}.
     * <p>This method does not overwrite an existing home with the same name.</p>
     *
     * @param playerUuid player UUID.
     * @param name       home name (unique within player's namespace).
     * @param x          X coordinate.
     * @param y          Y coordinate.
     * @param z          Z coordinate.
     * @return {@code true} if inserted without error; {@code false} otherwise.
     */
    public boolean setHome(UUID playerUuid, String name, double x, double y, double z) {
        ensurePlayerRow(playerUuid);
        final String insert = "INSERT INTO home_data (home_data_name, loc_x, loc_y, loc_z, player_uuid) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, name);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setString(5, playerUuid.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // Could be a UNIQUE constraint violation or some other DB error.
            logger.warning("Failed to insert home '" + name + "' for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Looks up a player's named home and returns its coordinates.
     *
     * @param playerUuid player UUID.
     * @param name       home name.
     * @return {@link Vector} of (x,y,z) or {@code null} if none.
     */
    public Vector getHome(UUID playerUuid, String name) {
        final String query = "SELECT loc_x, loc_y, loc_z FROM home_data WHERE player_uuid = ? AND home_data_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Vector(
                        rs.getDouble("loc_x"),
                        rs.getDouble("loc_y"),
                        rs.getDouble("loc_z")
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to read home '" + name + "' for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Deletes a player's named home from {@code home_data}.
     *
     * @param playerUuid player UUID.
     * @param name       home name.
     * @return {@code true} if a row was deleted.
     */
    public boolean deleteHome(UUID playerUuid, String name) {
        final String delete = "DELETE FROM home_data WHERE player_uuid = ? AND home_data_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(delete)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Failed to delete home '" + name + "' for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lists all home names saved by a player from {@code home_data}.
     *
     * @param playerUuid player UUID.
     * @return list of names (possibly empty).
     */
    public List<String> listHomeNames(UUID playerUuid) {
        final String query = "SELECT home_data_name FROM home_data WHERE player_uuid = ? ORDER BY home_data_name COLLATE NOCASE ASC";
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("home_data_name"));
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to list homes for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return names;
    }
}
