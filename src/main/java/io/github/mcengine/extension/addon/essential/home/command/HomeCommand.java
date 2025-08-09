package io.github.mcengine.extension.addon.essential.home.command;

import io.github.mcengine.extension.addon.essential.home.util.HomeDB;
import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Handles logic for the {@code /home} command.
 * <ul>
 *   <li>{@code /home <name>} — Teleports to the named home (current world) with a 5s countdown</li>
 *   <li>{@code /home tp <name>} — Teleports to the named home with a 5s countdown</li>
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

    /** Owning plugin, used for scheduling the countdown task. */
    private final Plugin plugin;

    /**
     * Constructs a new handler for {@code /home}.
     *
     * @param homeDB shared DB helper
     * @param logger add-on logger
     * @param plugin Bukkit plugin used for scheduling
     */
    public HomeCommand(HomeDB homeDB, MCEngineExtensionLogger logger, Plugin plugin) {
        this.homeDB = homeDB;
        this.logger = logger;
        this.plugin = plugin;
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
                // Prevent overwriting an existing home with the same name.
                if (homeDB.homeExists(uuid, name)) {
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
            default -> player.sendMessage("§7Usage: §b/home <name>§7, §b/home set <name>§7, §b/home tp <name>§7, §b/home delete <name>");
        }

        return true;
    }

    /**
     * Starts a 5-second visual countdown and then teleports the player
     * to the requested home if it exists.
     *
     * @param player the player to teleport
     * @param uuid   player's UUID
     * @param name   home name
     * @return {@code true} once processed
     */
    private boolean handleTeleport(Player player, UUID uuid, String name) {
        Vector coords = homeDB.getHome(uuid, name);
        if (coords == null) {
            player.sendMessage("§eNo such home: '" + name + "'.");
            return true;
        }

        player.sendMessage("§7Teleporting to §b" + name + "§7 in §b5§7 seconds...");
        final int taskId = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            /** Remaining seconds in the countdown (5→1). */
            private int seconds = 5;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    Bukkit.getScheduler().cancelTask(thisTask());
                    return;
                }
                // Show the current number as particles in front of the player's view.
                renderDigitParticles(player, seconds);

                if (seconds <= 1) {
                    // Time to teleport.
                    Location dest = player.getLocation().clone();
                    dest.setX(coords.getX());
                    dest.setY(coords.getY());
                    dest.setZ(coords.getZ());
                    boolean ok = player.teleport(dest);
                    if (ok) {
                        player.sendMessage("§aTeleported to home '" + name + "'.");
                    } else {
                        player.sendMessage("§cTeleport failed.");
                    }
                    Bukkit.getScheduler().cancelTask(thisTask());
                }
                seconds--;
            }

            /** Helper to get this scheduled task id safely. */
            private int thisTask() { return taskId; }
        }, 0L, 20L).getTaskId();

        return true;
    }

    /**
     * Renders a single digit (1–9, 0 supported as well) as a small grid of particles
     * in front of the player's eyes, aligned to their view direction.
     *
     * @param player the player to render in front of
     * @param digit  number to render (0–9)
     */
    private void renderDigitParticles(Player player, int digit) {
        if (digit < 0 || digit > 9) return;

        // Simple 5x3 bitmap fonts for digits 0–9 (true = draw particle).
        // Rows: top→bottom, Cols: left→right.
        final boolean[][][] DIGITS = new boolean[][][]{
            { // 0
                {true, true, true},
                {true, false, true},
                {true, false, true},
                {true, false, true},
                {true, true, true}
            },
            { // 1
                {false, true, false},
                {true,  true, false},
                {false, true, false},
                {false, true, false},
                {true,  true, true}
            },
            { // 2
                {true,  true, true},
                {false, false, true},
                {true,  true, true},
                {true,  false, false},
                {true,  true,  true}
            },
            { // 3
                {true,  true, true},
                {false, false, true},
                {true,  true,  true},
                {false, false, true},
                {true,  true,  true}
            },
            { // 4
                {true, false, true},
                {true, false, true},
                {true, true,  true},
                {false, false, true},
                {false, false, true}
            },
            { // 5
                {true,  true,  true},
                {true,  false, false},
                {true,  true,  true},
                {false, false, true},
                {true,  true,  true}
            },
            { // 6
                {true,  true,  true},
                {true,  false, false},
                {true,  true,  true},
                {true,  false,  true},
                {true,  true,  true}
            },
            { // 7
                {true,  true,  true},
                {false, false, true},
                {false, true,  false},
                {false, true,  false},
                {false, true,  false}
            },
            { // 8
                {true,  true,  true},
                {true,  false,  true},
                {true,  true,  true},
                {true,  false,  true},
                {true,  true,  true}
            },
            { // 9
                {true,  true,  true},
                {true,  false,  true},
                {true,  true,  true},
                {false, false,  true},
                {true,  true,  true}
            }
        };

        boolean[][] map = DIGITS[digit];

        // Geometry relative to player's view.
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Vector up = new Vector(0, 1, 0);
        Vector right = forward.clone().crossProduct(up).normalize();
        up = right.clone().crossProduct(forward).normalize(); // ensure orthogonal up

        double distance = 1.6;      // meters in front of eyes
        double spacing = 0.12;      // grid spacing
        double scale = 1.0;         // overall size multiplier
        Vector base = eye.toVector().add(forward.multiply(distance));

        // Center the 3-column digit horizontally and 5 rows vertically.
        double width = 3 * spacing * scale;
        double height = 5 * spacing * scale;
        Vector topLeft = base.clone()
                .subtract(right.clone().multiply(width / 2.0))
                .add(up.clone().multiply(height / 2.0));

        // Render particles for each "pixel".
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                if (!map[row][col]) continue;
                Vector offset = right.clone().multiply(col * spacing * scale)
                        .subtract(up.clone().multiply(row * spacing * scale));
                Vector pos = topLeft.clone().add(offset);
                player.getWorld().spawnParticle(
                        Particle.REDSTONE,
                        pos.getX(), pos.getY(), pos.getZ(),
                        8, // count
                        0.01, 0.01, 0.01, // small spread
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 255), 1.3f) // white dots
                );
            }
        }
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
