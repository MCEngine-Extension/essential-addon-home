package io.github.mcengine.extension.addon.essential.home.database.sqlite;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.home.database.HomeDB;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite implementation of {@link HomeDB}.
 * Uses {@code INTEGER PRIMARY KEY AUTOINCREMENT} and simple FK constraints.
 */
public class HomeDBSQLite implements HomeDB {

    /** Logger for reporting status and problems. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs the DB helper and ensures the tables exist.
     *
     * @param logger Logger wrapper.
     */
    public HomeDBSQLite(MCEngineExtensionLogger logger) {
        this.logger = logger;
        createTable();
    }

    /** Returns the Essential DB facade. */
    private static MCEngineEssentialCommon db() {
        return MCEngineEssentialCommon.getApi();
    }

    /** Escapes a string for safe SQL literal usage. */
    private static String q(String s) {
        if (s == null) return "NULL";
        return "'" + s.replace("'", "''") + "'";
    }

    @Override
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

        try {
            db().executeQuery(createHome);
            db().executeQuery(createHomeData);
            logger.info("Home tables (SQLite) created or already exist.");
        } catch (Exception e) {
            logger.warning("Failed to create home tables (SQLite): " + e.getMessage());
        }
    }

    @Override
    public void ensurePlayerRow(UUID playerUuid) {
        final String upsert = """
            INSERT INTO home (player_uuid, home_limit)
            SELECT %s, 3
            WHERE NOT EXISTS (SELECT 1 FROM home WHERE player_uuid = %s)
            """.formatted(q(playerUuid.toString()), q(playerUuid.toString()));
        try {
            db().executeQuery(upsert);
        } catch (Exception e) {
            logger.warning("Failed to ensure player row (SQLite) for " + playerUuid + ": " + e.getMessage());
        }
    }

    @Override
    public int getHomeLimit(UUID playerUuid) {
        ensurePlayerRow(playerUuid);
        final String q = "SELECT home_limit FROM home WHERE player_uuid = " + q(playerUuid.toString());
        try {
            Integer v = db().getValue(q, Integer.class);
            return v == null ? 3 : v;
        } catch (Exception e) {
            logger.warning("Failed to read home_limit (SQLite) for " + playerUuid + ": " + e.getMessage());
            return 3;
        }
    }

    @Override
    public boolean setHomeLimit(UUID playerUuid, int newLimit) {
        ensurePlayerRow(playerUuid);
        final String upd = "UPDATE home SET home_limit = " + newLimit +
                " WHERE player_uuid = " + q(playerUuid.toString());
        try {
            db().executeQuery(upd);
            return true;
        } catch (Exception e) {
            logger.warning("Failed to update home_limit (SQLite) for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public int getHomeCount(UUID playerUuid) {
        final String q = "SELECT COUNT(1) FROM home_data WHERE player_uuid = " + q(playerUuid.toString());
        try {
            Integer c = db().getValue(q, Integer.class);
            return c == null ? 0 : c;
        } catch (Exception e) {
            logger.warning("Failed to count homes (SQLite) for " + playerUuid + ": " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean homeExists(UUID playerUuid, String name) {
        final String sql = "SELECT 1 FROM home_data WHERE player_uuid = " + q(playerUuid.toString()) +
                " AND home_data_name = " + q(name) + " LIMIT 1";
        try {
            Integer one = db().getValue(sql, Integer.class);
            return one != null;
        } catch (Exception e) {
            logger.warning("Failed to check existing home (SQLite) '" + name + "' for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean setHome(UUID playerUuid, String name, double x, double y, double z) {
        ensurePlayerRow(playerUuid);
        final String insert = "INSERT INTO home_data (home_data_name, loc_x, loc_y, loc_z, player_uuid) VALUES (" +
                q(name) + ", " + x + ", " + y + ", " + z + ", " + q(playerUuid.toString()) + ")";
        try {
            db().executeQuery(insert);
            return true;
        } catch (Exception e) {
            logger.warning("Failed to insert home (SQLite) '" + name + "' for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public Vector getHome(UUID playerUuid, String name) {
        // Pack vector as text using SQLite concatenation
        final String query = "SELECT (CAST(loc_x AS TEXT) || ',' || CAST(loc_y AS TEXT) || ',' || CAST(loc_z AS TEXT)) " +
                "FROM home_data WHERE player_uuid = " + q(playerUuid.toString()) +
                " AND home_data_name = " + q(name);
        try {
            String packed = db().getValue(query, String.class);
            if (packed == null || packed.isBlank()) return null;
            String[] parts = packed.split(",", 3);
            if (parts.length < 3) return null;
            return new Vector(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        } catch (Exception e) {
            logger.warning("Failed to read home (SQLite) '" + name + "' for " + playerUuid + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean deleteHome(UUID playerUuid, String name) {
        final String delete = "DELETE FROM home_data WHERE player_uuid = " + q(playerUuid.toString()) +
                " AND home_data_name = " + q(name);
        try {
            db().executeQuery(delete);
            return true;
        } catch (Exception e) {
            logger.warning("Failed to delete home (SQLite) '" + name + "' for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> listHomeNames(UUID playerUuid) {
        // Use GROUP_CONCAT to return a comma-separated list
        final String query = "SELECT GROUP_CONCAT(home_data_name, ',') " +
                "FROM home_data WHERE player_uuid = " + q(playerUuid.toString()) +
                " ORDER BY home_data_name COLLATE NOCASE ASC";
        List<String> names = new ArrayList<>();
        try {
            String packed = db().getValue(query, String.class);
            if (packed == null || packed.isBlank()) return names;
            for (String s : packed.split(",")) {
                if (!s.isEmpty()) names.add(s);
            }
        } catch (Exception e) {
            logger.warning("Failed to list homes (SQLite) for " + playerUuid + ": " + e.getMessage());
        }
        return names;
    }
}
