package io.github.mcengine.extension.addon.essential.home.util.db;

import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * Abstraction for Home database operations to support multiple SQL dialects.
 *
 * <p>Implementations must create/ensure the following logical schema:</p>
 * <ul>
 *   <li><strong>home</strong>(home_id PK, player_uuid UNIQUE, home_limit DEFAULT 3; -1 = unlimited)</li>
 *   <li><strong>home_data</strong>(home_data_id PK, player_uuid FK(home), home_data_name UNIQUE per player, x/y/z)</li>
 * </ul>
 */
public interface HomeDB {

    /** Creates required tables if they don't already exist. */
    void createTable();

    /** Ensures a player row exists in {@code home} with default limit if missing. */
    void ensurePlayerRow(UUID playerUuid);

    /** Retrieves the player's home limit (default 3). */
    int getHomeLimit(UUID playerUuid);

    /** Sets (overrides) the player's home limit; -1 = unlimited. */
    boolean setHomeLimit(UUID playerUuid, int newLimit);

    /** Counts homes for a player. */
    int getHomeCount(UUID playerUuid);

    /** Whether a home with the given name exists for the player. */
    boolean homeExists(UUID playerUuid, String name);

    /** Inserts a new named home (no overwrite). */
    boolean setHome(UUID playerUuid, String name, double x, double y, double z);

    /** Reads a named home's coordinates or null. */
    Vector getHome(UUID playerUuid, String name);

    /** Deletes a named home; returns true if a row was deleted. */
    boolean deleteHome(UUID playerUuid, String name);

    /** Lists all home names for a player. */
    List<String> listHomeNames(UUID playerUuid);

    /** True if player can create more homes (based on limit). */
    default boolean canCreateMoreHomes(UUID playerUuid) {
        int limit = getHomeLimit(playerUuid);
        if (limit < 0) return true;
        return getHomeCount(playerUuid) < limit;
    }
}
