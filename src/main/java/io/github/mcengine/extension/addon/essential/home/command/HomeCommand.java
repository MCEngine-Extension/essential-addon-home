package io.github.mcengine.extension.addon.essential.home.command;

import io.github.mcengine.extension.addon.essential.home.util.db.HomeDB;
import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Handles logic for the {@code /home} command.
 * <ul>
 *   <li>{@code /home <name>} — Teleports to the named home (current world)</li>
 *   <li>{@code /home tp <name>} — Teleports to the named home</li>
 *   <li>{@code /home set <name>} — Saves your current X/Y/Z as a named home (respects per-player limit)</li>
 *   <li>{@code /home delete <name>} — Deletes the named home</li>
 *   <li>{@code /home limit add &lt;int&gt;} — Increases your home limit (perm: {@code mcengine.essential.home.limit.add})</li>
 *   <li>{@code /home limit minus &lt;int&gt;} — Decreases your home limit (perm: {@code mcengine.essential.home.limit.minus})</li>
 * </ul>
 * <p>
 * Note: The database structure stores only X/Y/Z, so the teleport uses the
 * player's current world.
 */
public class HomeCommand implements CommandExecutor {

    /** Database utility for persisting and reading home locations and limits. */
    private final HomeDB homeDB;

    /** Logger for user-facing and diagnostic messages. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs a new handler for {@code /home}.
     *
     * @param homeDB shared DB helper.
     * @param logger add-on logger.
     */
    public HomeCommand(HomeDB homeDB, MCEngineExtensionLogger logger) {
        this.homeDB = homeDB;
        this.logger = logger;
    }

    /**
     * Executes the {@code /home} command.
     *
     * @param sender  Command sender.
     * @param command The command object.
     * @param label   Alias used.
     * @param args    Arguments.
     * @return {@code true} if handled.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /home.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§7Usage: §b/home <name>§7, §b/home set <name>§7, §b/home tp <name>§7, §b/home delete <name>§7, §b/home limit <add|minus> <int>");
            return true;
        }

        UUID uuid = player.getUniqueId();
        String sub = args[0].toLowerCase();

        // New: /home limit <add|minus> <int>
        if (sub.equals("limit")) {
            return handleLimit(player, uuid, args);
        }

        // Support "/home <name>" as teleport when not one of the known subcommands.
        if (!sub.equals("set") && !sub.equals("tp") && !sub.equals("delete")) {
            return handleTeleport(player, uuid, args[0]);
        }

        if (args.length < 2) {
            sender.sendMessage("§cPlease provide a home name. Example: §b/home " + sub + " mybase");
            return true;
        }

        String name = args[1];

        switch (sub) {
            case "set" -> {
                if (!isValidName(name)) {
                    player.sendMessage("§cInvalid home name. Use 1–32 letters, numbers, underscores, or dashes.");
                    return true;
                }
                // Prevent overwriting; enforce per-player limit (default 3, -1 = unlimited)
                if (!homeDB.homeExists(uuid, name)) {
                    if (!homeDB.canCreateMoreHomes(uuid)) {
                        int limit = homeDB.getHomeLimit(uuid);
                        player.sendMessage(limit < 0
                                ? "§cYou cannot create more homes right now."
                                : "§cYou have reached your home limit (" + limit + ").");
                        return true;
                    }
                } else {
                    player.sendMessage("§cThis name is already in use. Please use another name or delete it first.");
                    return true;
                }

                Location loc = player.getLocation();
                boolean ok = homeDB.setHome(uuid, name, loc.getX(), loc.getY(), loc.getZ());
                if (ok) {
                    player.sendMessage(String.format("§aHome '%s' set at X: %.2f, Y: %.2f, Z: %.2f.", name, loc.getX(), loc.getY(), loc.getZ()));
                } else {
                    player.sendMessage("§cFailed to save your home. Check console for details.");
                }
            }
            case "delete" -> {
                boolean ok = homeDB.deleteHome(uuid, name);
                if (ok) {
                    player.sendMessage("§aHome '" + name + "' has been deleted.");
                } else {
                    player.sendMessage("§eNo such home: '" + name + "'.");
                }
            }
            case "tp" -> handleTeleport(player, uuid, name);
            default -> player.sendMessage("§7Usage: §b/home <name>§7, §b/home set <name>§7, §b/home tp <name>§7, §b/home delete <name>§7, §b/home limit <add|minus> <int>");
        }

        return true;
    }

    /**
     * Handles {@code /home limit <add|minus> <int>} operations.
     *
     * @param player the player executing the command.
     * @param uuid   player's UUID.
     * @param args   full argument array.
     * @return {@code true} once processed.
     */
    private boolean handleLimit(Player player, UUID uuid, String[] args) {
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

    /**
     * Teleports a player to a named home if it exists.
     *
     * @param player the player to teleport.
     * @param uuid   player's UUID.
     * @param name   home name.
     * @return {@code true} once processed.
     */
    private boolean handleTeleport(Player player, java.util.UUID uuid, String name) {
        Vector coords = homeDB.getHome(uuid, name);
        if (coords == null) {
            player.sendMessage("§eNo such home: '" + name + "'.");
            return true;
        }
        // Use player's current world (only X/Y/Z stored in DB).
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
     * Validates home names to reduce SQL/UX issues.
     *
     * @param name candidate name.
     * @return true if valid.
     */
    private boolean isValidName(String name) {
        return name != null && name.length() >= 1 && name.length() <= 32 && name.matches("^[A-Za-z0-9_-]+$");
    }
}
