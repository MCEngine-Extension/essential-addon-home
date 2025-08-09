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
 * Database helper for the {@code home} table.
 * <p>
 * Updated schema (per request) supports multiple named homes per player:
 * <pre>
 * CREATE TABLE IF NOT EXISTS home (
 *   home_id     INTEGER PRIMARY KEY AUTOINCREMENT,
 *   player_uuid VARCHAR(36) NOT NULL,
 *   home_name   TEXT NOT NULL,
 *   loc_x       REAL NOT NULL,
 *   loc_y       REAL NOT NULL,
 *   loc_z       REAL NOT NULL,
 *   UNIQUE(player_uuid, home_name)
 * );
 * </pre>
 * <p>
 * Only X/Y/Z are stored, so teleports use the player's current world.
 */
public class HomeDB {

    /** Active JDBC connection supplied by the Essential module. */
    private final Connection conn;

    /** Logger for reporting status and problems. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs the DB helper and ensures the table exists.
     *
     * @param conn   JDBC connection
     * @param logger logger wrapper
     */
    public HomeDB(Connection conn, MCEngineExtensionLogger logger) {
        this.conn = conn;
        this.logger = logger;
        createTable();
    }

    /**
     * Creates the {@code home} table if needed.
     * Ensures a UNIQUE constraint on {@code (player_uuid, home_name)}.
     */
    public void createTable() {
        final String sql = """
            CREATE TABLE IF NOT EXISTS home (
                home_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid VARCHAR(36) NOT NULL,
                home_name   TEXT NOT NULL,
                loc_x       REAL NOT NULL,
                loc_y       REAL NOT NULL,
                loc_z       REAL NOT NULL,
                UNIQUE(player_uuid, home_name)
            );
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
            logger.info("Home table created or already exists (with name uniqueness per player).");
        } catch (SQLException e) {
            logger.warning("Failed to create 'home' table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Upserts a player's named home coordinates.
     *
     * @param playerUuid player UUID
     * @param name       home name (unique within player's namespace)
     * @param x          X coordinate
     * @param y          Y coordinate
     * @param z          Z coordinate
     * @return {@code true} if changed without error
     */
    public boolean setHome(UUID playerUuid, String name, double x, double y, double z) {
        final String upsert = """
            INSERT INTO home (player_uuid, home_name, loc_x, loc_y, loc_z)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid, home_name) DO UPDATE SET
              loc_x = excluded.loc_x,
              loc_y = excluded.loc_y,
              loc_z = excluded.loc_z;
            """;
        try (PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, name);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warning("Failed to upsert home '" + name + "' for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Looks up a player's named home and returns its coordinates.
     * <p>
     * Returns a {@link Vector} containing (x, y, z) or {@code null} if not found.
     * Using a Bukkit type here avoids cross-classloader issues with custom nested types.
     *
     * @param playerUuid player UUID
     * @param name       home name
     * @return {@link Vector} of (x,y,z) or {@code null} if none
     */
    public Vector getHome(UUID playerUuid, String name) {
        final String query = "SELECT loc_x, loc_y, loc_z FROM home WHERE player_uuid = ? AND home_name = ?";
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
     * Deletes a player's named home.
     *
     * @param playerUuid player UUID
     * @param name       home name
     * @return {@code true} if a row was deleted
     */
    public boolean deleteHome(UUID playerUuid, String name) {
        final String delete = "DELETE FROM home WHERE player_uuid = ? AND home_name = ?";
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
     * Lists all home names saved by a player.
     *
     * @param playerUuid player UUID
     * @return list of names (possibly empty)
     */
    public List<String> listHomeNames(UUID playerUuid) {
        final String query = "SELECT home_name FROM home WHERE player_uuid = ? ORDER BY home_name COLLATE NOCASE ASC";
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("home_name"));
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to list homes for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return names;
    }
}
