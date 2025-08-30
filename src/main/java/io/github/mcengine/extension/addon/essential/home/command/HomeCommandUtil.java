package io.github.mcengine.extension.addon.essential.home.command;

import io.github.mcengine.extension.addon.essential.home.util.db.HomeDB;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Utility helpers for the {@code /home} command to reduce duplication in the executor.
 *
 * <p>Contains static methods for:
 * <ul>
 *   <li>Validating home names</li>
 *   <li>Teleporting to a saved home</li>
 *   <li>Adjusting per-player home limits</li>
 * </ul>
 * </p>
 */
public final class HomeCommandUtil {

    /**
     * Hidden constructor to prevent instantiation.
     */
    private HomeCommandUtil() {
        // Utility class; no instances.
    }

    /**
     * Validates home names to reduce SQL/UX issues.
     *
     * @param name candidate name.
     * @return true if valid.
     */
    public static boolean isValidName(String name) {
        return name != null && name.length() >= 1 && name.length() <= 32 && name.matches("^[A-Za-z0-9_-]+$");
    }

    /**
     * Teleports a player to a named home if it exists.
     *
     * @param player the player to teleport.
     * @param uuid   player's UUID.
     * @param name   home name.
     * @param homeDB database accessor.
     * @return {@code true} once processed.
     */
    public static boolean handleTeleport(Player player, UUID uuid, String name, HomeDB homeDB) {
        Vector coords = homeDB.getHome(uuid, name);
        if (coords == null) {
            player.sendMessage("§eNo such home: '" + name + "'.");
            return true;
        }
        var dest = player.getLocation().clone();
        dest.setX(coords.getX());
        dest.setY(coords.getY());
        dest.setZ(coords.getZ());
        boolean ok = player.teleport(dest);
        if (ok) {
            player.sendMessage("§aTeleported to home '" + name + "'.");
        } else {
            player.sendMessage("§cTeleport failed.");
        }
        return true;
    }

    /**
     * Handles {@code /home limit <add|minus> <int>} operations.
     *
     * @param player the player executing the command.
     * @param uuid   player's UUID.
     * @param args   full argument array.
     * @param homeDB database accessor.
     * @return {@code true} once processed.
     */
    public static boolean handleLimit(Player player, UUID uuid, String[] args, HomeDB homeDB) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §b/home limit <add|minus> <int>");
            return true;
        }

        String action = args[1].toLowerCase();
        String amountStr = args[2];

        // Permission checks
        if (action.equals("add") && !player.hasPermission("mcengine.essential.home.limit.add")) {
            player.sendMessage("§cYou lack permission: §7mcengine.essential.home.limit.add");
            return true;
        }
        if (action.equals("minus") && !player.hasPermission("mcengine.essential.home.limit.minus")) {
            player.sendMessage("§cYou lack permission: §7mcengine.essential.home.limit.minus");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException nfe) {
            player.sendMessage("§cAmount must be an integer. Example: §b/home limit " + action + " 1");
            return true;
        }
        if (amount <= 0) {
            player.sendMessage("§cAmount must be greater than zero.");
            return true;
        }

        int current = homeDB.getHomeLimit(uuid);

        // Unlimited handling
        if (current < 0) {
            player.sendMessage("§eYour home limit is currently §6unlimited§e; this command has no effect.");
            return true;
        }

        int newLimit;
        switch (action) {
            case "add" -> newLimit = current + amount;
            case "minus" -> newLimit = Math.max(0, current - amount);
            default -> {
                player.sendMessage("§cInvalid action. Use §badd§c or §bminus§c.");
                return true;
            }
        }

        boolean ok = homeDB.setHomeLimit(uuid, newLimit);
        if (!ok) {
            player.sendMessage("§cFailed to update your home limit. Check console for details.");
            return true;
        }

        int count = homeDB.getHomeCount(uuid);
        if (newLimit >= 0 && count > newLimit) {
            player.sendMessage("§eYour new limit is §6" + newLimit + "§e, but you currently have §6" + count + "§e homes. You won't be able to set new homes until you delete some.");
        }

        player.sendMessage("§aHome limit updated: §f" + current + " §7→ §b" + newLimit + "§a.");
        return true;
    }
}
