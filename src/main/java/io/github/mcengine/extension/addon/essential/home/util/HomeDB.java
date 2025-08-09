package io.github.mcengine.extension.addon.essential.home.util;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Database helper for the {@code home} table.
 * <p>
 * Schema (as requested):
 * <pre>
 * table home
 *   home_id AUTOINCREMENT,
 *   player_uuid VARCHAR(36) NOT NULL,
 *   loc_x NOT NULL, loc_y NOT NULL, loc_z NOT NULL
 * </pre>
 * <p>
 * Implementation notes:
 * <ul>
 *   <li>Creates a UNIQUE index on {@code player_uuid} so we can upsert a single home per player.</li>
 *   <li>Only X/Y/Z are stored, so teleport uses the player's current world.</li>
 * </ul>
 */
public class HomeDB {

    /** Active JDBC connection supplied by the Essential module. */
    private final Connection conn;

    /** Logger for reporting status and problems. */
    private final MCEngineExtensionLogger logger;

    /**
     * Immutable record for a stored home.
     *
     * @param locX X coordinate
     * @param locY Y coordinate
     * @param locZ Z coordinate
     */
    public record HomeRecord(double locX, double locY, double locZ) {}

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
     * Adds a UNIQUE constraint on {@code player_uuid} for idempotent updates.
     */
    public void createTable() {
        final String sql = """
            CREATE TABLE IF NOT EXISTS home (
                home_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid VARCHAR(36) NOT NULL UNIQUE,
                loc_x       REAL NOT NULL,
                loc_y       REAL NOT NULL,
                loc_z       REAL NOT NULL
            );
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
            logger.info("Home table created or already exists.");
        } catch (SQLException e) {
            logger.warning("Failed to create 'home' table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Upserts a player's home coordinates.
     *
     * @param playerUuid player UUID string
     * @param x          X coordinate
     * @param y          Y coordinate
     * @param z          Z coordinate
     * @return {@code true} if changed without error
     */
    public boolean setHome(java.util.UUID playerUuid, double x, double y, double z) {
        final String upsert = """
            INSERT INTO home (player_uuid, loc_x, loc_y, loc_z)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
              loc_x = excluded.loc_x,
              loc_y = excluded.loc_y,
              loc_z = excluded.loc_z;
            """;
        try (PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, playerUuid.toString());
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warning("Failed to upsert home for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Looks up a player's saved home.
     *
     * @param playerUuid player UUID
     * @return {@link HomeRecord} or {@code null} if none
     */
    public HomeRecord getHome(java.util.UUID playerUuid) {
        final String query = "SELECT loc_x, loc_y, loc_z FROM home WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new HomeRecord(
                        rs.getDouble("loc_x"),
                        rs.getDouble("loc_y"),
                        rs.getDouble("loc_z")
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to read home for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Deletes a player's saved home.
     *
     * @param playerUuid player UUID
     * @return {@code true} if a row was deleted
     */
    public boolean deleteHome(java.util.UUID playerUuid) {
        final String delete = "DELETE FROM home WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(delete)) {
            ps.setString(1, playerUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Failed to delete home for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
