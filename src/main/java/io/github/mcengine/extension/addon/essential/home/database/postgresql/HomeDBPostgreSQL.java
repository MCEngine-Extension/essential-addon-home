package io.github.mcengine.extension.addon.essential.home.database.postgresql;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.home.database.HomeDB;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link HomeDB}.
 * Uses {@code BIGSERIAL} for primary keys and standard FK constraints.
 */
public class HomeDBPostgreSQL implements HomeDB {

    /** Logger for reporting status and problems. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs the DB helper and ensures the tables exist.
     *
     * @param logger Logger wrapper.
     */
    public HomeDBPostgreSQL(MCEngineExtensionLogger logger) {
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

        try {
            db().executeQuery(createHome);
            db().executeQuery(createHomeData);
            logger.info("Home tables (PostgreSQL) created or already exist.");
        } catch (Exception e) {
            logger.warning("Failed to create home tables (PostgreSQL): " + e.getMessage());
        }
    }

    @Override
    public void ensurePlayerRow(UUID playerUuid) {
        final String upsert = """
            INSERT INTO home (player_uuid, home_limit)
            VALUES (%s, 3)
            ON CONFLICT (player_uuid) DO NOTHING
            """.formatted(q(playerUuid.toString()));
        try {
            db().executeQuery(upsert);
        } catch (Exception e) {
            logger.warning("Failed to ensure player row (PostgreSQL) for " + playerUuid + ": " + e.getMessage());
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
            logger.warning("Failed to read home_limit (PostgreSQL) for " + playerUuid + ": " + e.getMessage());
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
            logger.warning("Failed to update home_limit (PostgreSQL) for " + playerUuid + ": " + e.getMessage());
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
            logger.warning("Failed to count homes (PostgreSQL) for " + playerUuid + ": " + e.getMessage());
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
            logger.warning("Failed to check existing home (PostgreSQL) '" + name + "' for " + playerUuid + ": " + e.getMessage());
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
            logger.warning("Failed to insert home (PostgreSQL) '" + name + "' for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public Vector getHome(UUID playerUuid, String name) {
        // Pack vector using Postgres concat_ws
        final String query = "SELECT concat_ws(',', loc_x, loc_y, loc_z) " +
                "FROM home_data WHERE player_uuid = " + q(playerUuid.toString()) +
                " AND home_data_name = " + q(name);
        try {
            String packed = db().getValue(query, String.class);
            if (packed == null || packed.isBlank()) return null;
            String[] parts = packed.split(",", 3);
            if (parts.length < 3) return null;
            return new Vector(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        } catch (Exception e) {
            logger.warning("Failed to read home (PostgreSQL) '" + name + "' for " + playerUuid + ": " + e.getMessage());
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
            logger.warning("Failed to delete home (PostgreSQL) '" + name + "' for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> listHomeNames(UUID playerUuid) {
        // Use string_agg to return a comma-separated list
        final String query = "SELECT string_agg(home_data_name, ',' ORDER BY home_data_name) " +
                "FROM home_data WHERE player_uuid = " + q(playerUuid.toString());
        List<String> names = new ArrayList<>();
        try {
            String packed = db().getValue(query, String.class);
            if (packed == null || packed.isBlank()) return names;
            for (String s : packed.split(",")) {
                if (!s.isEmpty()) names.add(s);
            }
        } catch (Exception e) {
            logger.warning("Failed to list homes (PostgreSQL) for " + playerUuid + ": " + e.getMessage());
        }
        return names;
    }
}
