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
 *   <li>{@code /home} or {@code /home tp} — Teleports to the saved home (current world)</li>
 *   <li>{@code /home set} — Saves your current X/Y/Z as home</li>
 *   <li>{@code /home delete} — Deletes your saved home</li>
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
     * @param args    Arguments: {@code set|tp|delete}
     * @return {@code true} if handled.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /home.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        String sub = args.length == 0 ? "tp" : args[0].toLowerCase();
        switch (sub) {
            case "set" -> {
                Location loc = player.getLocation();
                boolean ok = homeDB.setHome(uuid, loc.getX(), loc.getY(), loc.getZ());
                if (ok) {
                    player.sendMessage(String.format("§aHome set at X: %.2f, Y: %.2f, Z: %.2f.", loc.getX(), loc.getY(), loc.getZ()));
                } else {
                    player.sendMessage("§cFailed to save your home. Check console for details.");
                }
            }
            case "delete" -> {
                boolean ok = homeDB.deleteHome(uuid);
                if (ok) {
                    player.sendMessage("§aYour home has been deleted.");
                } else {
                    player.sendMessage("§eYou don't have a saved home.");
                }
            }
            case "tp" -> {
                HomeDB.HomeRecord record = homeDB.getHome(uuid);
                if (record == null) {
                    player.sendMessage("§eYou don't have a home yet. Use §b/home set§e first.");
                    return true;
                }
                Location dest = player.getLocation().clone();
                dest.setX(record.locX());
                dest.setY(record.locY());
                dest.setZ(record.locZ());
                boolean ok = player.teleport(dest);
                if (ok) {
                    player.sendMessage("§aTeleported to your home.");
                } else {
                    player.sendMessage("§cTeleport failed.");
                }
            }
            default -> player.sendMessage("§7Usage: §b/home [set|tp|delete]");
        }

        return true;
    }
}
