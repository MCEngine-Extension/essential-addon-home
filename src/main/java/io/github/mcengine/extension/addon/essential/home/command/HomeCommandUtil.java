package io.github.mcengine.extension.addon.essential.home.command;

import io.github.mcengine.extension.addon.essential.home.database.HomeDB;
import org.bukkit.Bukkit;
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
     * Handles {@code /home limit <add|minus> <player> <int>} operations for online players only.
     *
     * <p>Permissions:</p>
     * <ul>
     *   <li>{@code mcengine.essential.home.limit.add} for {@code add}</li>
     *   <li>{@code mcengine.essential.home.limit.minus} for {@code minus}</li>
     * </ul>
     *
     * @param executor the player executing the command.
     * @param executorUuid executor's UUID (unused but kept for parity).
     * @param args   full argument array.
     * @param homeDB database accessor.
     * @return {@code true} once processed.
     */
    public static boolean handleLimit(Player executor, UUID executorUuid, String[] args, HomeDB homeDB) {
        if (args.length < 4) {
            executor.sendMessage("§cUsage: §b/home limit <add|minus> <player> <int>");
            return true;
        }

        String action = args[1].toLowerCase();
        String targetName = args[2];
        String amountStr = args[3];

        // Permission checks
        if (action.equals("add") && !executor.hasPermission("mcengine.essential.home.limit.add")) {
            executor.sendMessage("§cYou lack permission: §7mcengine.essential.home.limit.add");
            return true;
        }
        if (action.equals("minus") && !executor.hasPermission("mcengine.essential.home.limit.minus")) {
            executor.sendMessage("§cYou lack permission: §7mcengine.essential.home.limit.minus");
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            executor.sendMessage("§cPlayer §e" + targetName + "§c is not online.");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException nfe) {
            executor.sendMessage("§cAmount must be an integer. Example: §b/home limit " + action + " " + targetName + " 1");
            return true;
        }
        if (amount <= 0) {
            executor.sendMessage("§cAmount must be greater than zero.");
            return true;
        }

        UUID targetUuid = target.getUniqueId();
        int current = homeDB.getHomeLimit(targetUuid);

        // Unlimited handling
        if (current < 0) {
            executor.sendMessage("§e" + target.getName() + "§e currently has §6unlimited§e home limit; this command has no effect.");
            return true;
        }

        int newLimit;
        switch (action) {
            case "add" -> newLimit = current + amount;
            case "minus" -> newLimit = Math.max(0, current - amount);
            default -> {
                executor.sendMessage("§cInvalid action. Use §badd§c or §bminus§c.");
                return true;
            }
        }

        boolean ok = homeDB.setHomeLimit(targetUuid, newLimit);
        if (!ok) {
            executor.sendMessage("§cFailed to update home limit for §e" + target.getName() + "§c. Check console for details.");
            return true;
        }

        int count = homeDB.getHomeCount(targetUuid);
        if (newLimit >= 0 && count > newLimit) {
            executor.sendMessage("§eNew limit for §6" + target.getName() + "§e is §6" + newLimit + "§e, but they currently have §6" + count + "§e homes.");
        }

        executor.sendMessage("§aHome limit updated for §b" + target.getName() + "§a: §f" + current + " §7→ §b" + newLimit + "§a.");
        if (!executor.getUniqueId().equals(targetUuid)) {
            target.sendMessage("§eYour home limit was updated by §6" + executor.getName() + "§e: §f" + current + " §7→ §b" + newLimit + "§e.");
        }
        return true;
    }
}
