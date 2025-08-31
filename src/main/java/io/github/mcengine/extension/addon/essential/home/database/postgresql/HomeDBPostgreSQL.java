package io.github.mcengine.extension.addon.essential.home.database.postgresql;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.home.database.HomeDB;
import org.bukkit.util.Vector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link HomeDB}.
 * Uses {@code BIGSERIAL} for primary keys and standard FK constraints.
 */
public class HomeDBPostgreSQL implements HomeDB {

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
    public HomeDBPostgreSQL(Connection conn, MCEngineExtensionLogger logger) {
        this.conn = conn;
        this.logger = logger;
        createTable();
    }

    @Override
    public void createTable() {
        final String createHome = """
            CREATE TABLE IF NOT EXISTS home (
                home_id      BIGSERIAL PRIMARY KEY,
                player_uuid  VARCHAR(36) UNIQUE NOT NULL,
                home_limit   INTEGER NOT NULL DEFAULT 3
            );
            """;

        final String createHomeData = """
            CREATE TABLE IF NOT EXISTS home_data (
                home_data_id   BIGSERIAL PRIMARY KEY,
                home_data_name TEXT NOT NULL,
                loc_x          DOUBLE PRECISION NOT NULL,
                loc_y          DOUBLE PRECISION NOT NULL,
                loc_z          DOUBLE PRECISION NOT NULL,
                player_uuid    VARCHAR(36) NOT NULL,
                CONSTRAINT uq_player_name UNIQUE (player_uuid, home_data_name),
                CONSTRAINT fk_home_player FOREIGN KEY (player_uuid) REFERENCES home(player_uuid) ON DELETE CASCADE
            );
            """;

        try (PreparedStatement ps1 = conn.prepareStatement(createHome);
             PreparedStatement ps2 = conn.prepareStatement(createHomeData)) {
            ps1.execute();
            ps2.execute();
            logger.info("Home tables (PostgreSQL) created or already exist.");
        } catch (SQLException e) {
            logger.warning("Failed to create home tables (PostgreSQL): " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void ensurePlayerRow(UUID playerUuid) {
        // Upsert via INSERT ... ON CONFLICT for unique (player_uuid)
        final String upsert = """
            INSERT INTO home (player_uuid, home_limit)
            VALUES (?, 3)
            ON CONFLICT (player_uuid) DO NOTHING
            """;
        try (PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to ensure player row (PostgreSQL) for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public int getHomeLimit(UUID playerUuid) {
        ensurePlayerRow(playerUuid);
        final String q = "SELECT home_limit FROM home WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("home_limit");
            }
        } catch (SQLException e) {
            logger.warning("Failed to read home_limit (PostgreSQL) for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return 3;
    }

    @Override
    public boolean setHomeLimit(UUID playerUuid, int newLimit) {
        ensurePlayerRow(playerUuid);
        final String upd = "UPDATE home SET home_limit = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(upd)) {
            ps.setInt(1, newLimit);
            ps.setString(2, playerUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Failed to update home_limit (PostgreSQL) for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public int getHomeCount(UUID playerUuid) {
        final String q = "SELECT COUNT(1) AS c FROM home_data WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("c");
            }
        } catch (SQLException e) {
            logger.warning("Failed to count homes (PostgreSQL) for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public boolean homeExists(UUID playerUuid, String name) {
        final String sql = "SELECT 1 FROM home_data WHERE player_uuid = ? AND home_data_name = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warning("Failed to check existing home (PostgreSQL) '" + name + "' for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
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
            logger.warning("Failed to insert home (PostgreSQL) '" + name + "' for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
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
            logger.warning("Failed to read home (PostgreSQL) '" + name + "' for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean deleteHome(UUID playerUuid, String name) {
        final String delete = "DELETE FROM home_data WHERE player_uuid = ? AND home_data_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(delete)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Failed to delete home (PostgreSQL) '" + name + "' for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<String> listHomeNames(UUID playerUuid) {
        final String query = "SELECT home_data_name FROM home_data WHERE player_uuid = ? ORDER BY home_data_name ASC";
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString("home_data_name"));
            }
        } catch (SQLException e) {
            logger.warning("Failed to list homes (PostgreSQL) for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return names;
    }
}
