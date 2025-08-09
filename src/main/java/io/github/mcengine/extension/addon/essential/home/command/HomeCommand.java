package io.github.mcengine.extension.addon.essential.home.command;

import io.github.mcengine.extension.addon.essential.home.util.HomeDB;
import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles logic for the {@code /home} command.
 * <ul>
 *   <li>{@code /home <name>} — Teleports to the named home (current world)</li>
 *   <li>{@code /home tp <name>} — Teleports to the named home</li>
 *   <li>{@code /home set <name>} — Saves your current X/Y/Z as a named home</li>
 *   <li>{@code /home delete <name>} — Deletes the named home</li>
 * </ul>
 * <p>
 * Note: The database structure stores only X/Y/Z, so the teleport uses the
 * player's current world.
 */
public class HomeCommand implements CommandExecutor {

    /** Database utility for persisting and reading home locations. */
    private final HomeDB homeDB;

    /** Logger for user-facing and diagnostic messages. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs a new handler for {@code /home}.
     *
     * @param homeDB shared DB helper
     * @param logger add-on logger
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
            sender.sendMessage("§7Usage: §b/home <name>§7, §b/home set <name>§7, §b/home tp <name>§7, §b/home delete <name>");
            return true;
        }

        UUID uuid = player.getUniqueId();

        // Support "/home <name>" as teleport
        String sub = args[0].toLowerCase();
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
            default -> player.sendMessage("§7Usage: §b/home <name>§7, §b/home set <name>§7, §b/home tp <name>§7, §b/home delete <name>");
        }

        return true;
    }

    /**
     * Teleports a player to a named home if it exists.
     *
     * @param player the player to teleport
     * @param uuid   player's UUID
     * @param name   home name
     * @return {@code true} once processed
     */
    private boolean handleTeleport(Player player, UUID uuid, String name) {
        HomeDB.HomeRecord record = homeDB.getHome(uuid, name);
        if (record == null) {
            player.sendMessage("§eNo such home: '" + name + "'.");
            return true;
        }
        // Use player's current world (only X/Y/Z stored in DB).
        var dest = player.getLocation().clone();
        dest.setX(record.locX());
        dest.setY(record.locY());
        dest.setZ(record.locZ());
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
     * @param name candidate name
     * @return true if valid
     */
    private boolean isValidName(String name) {
        return name != null && name.length() >= 1 && name.length() <= 32 && name.matches("^[A-Za-z0-9_-]+$");
    }
}
