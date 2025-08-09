package io.github.mcengine.extension.addon.essential.home.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Utility methods for handling countdowns and visual effects
 * for the HomeCommand teleport process.
 */
public final class HomeCommandUtil {

    /**
     * Starts a countdown timer (5 seconds) and teleports the player to the given coordinates.
     * Displays a floating numeric particle countdown in front of the player each second.
     *
     * @param plugin the owning plugin, used for scheduling
     * @param player the player to teleport
     * @param name   the name of the home
     * @param coords the X/Y/Z destination vector
     */
    public static void startTeleportCountdown(Plugin plugin, Player player, String name, Vector coords) {
        player.sendMessage("§7Teleporting to §b" + name + "§7 in §b5§7 seconds...");

        final int taskId = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            /** Remaining seconds in the countdown (5→1). */
            private int seconds = 5;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    Bukkit.getScheduler().cancelTask(taskId);
                    return;
                }

                renderDigitParticles(player, seconds);

                if (seconds <= 1) {
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
                    Bukkit.getScheduler().cancelTask(taskId);
                }
                seconds--;
            }
        }, 0L, 20L).getTaskId();
    }

    /**
     * Renders a single digit (0–9) as a small grid of particles in front of the player's eyes.
     *
     * @param player the player to render in front of
     * @param digit  the digit to render
     */
    private static void renderDigitParticles(Player player, int digit) {
        if (digit < 0 || digit > 9) return;

        // Simple 5x3 bitmap fonts for digits 0–9 (true = draw particle).
        final boolean[][][] DIGITS = new boolean[][][] {
            { // 0
                {true, true, true},
                {true, false, true},
                {true, false, true},
                {true, false, true},
                {true, true, true}
            },
            { // 1
                {false, true, false},
                {true,  true,  false},
                {false, true, false},
                {false, true, false},
                {true,  true,  true}
            },
            { // 2
                {true,  true,  true},
                {false, false, true},
                {true,  true,  true},
                {true,  false, false},
                {true,  true,  true}
            },
            { // 3
                {true,  true,  true},
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

        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Vector up = new Vector(0, 1, 0);
        Vector right = forward.clone().crossProduct(up).normalize();
        up = right.clone().crossProduct(forward).normalize(); // ensure orthogonal up

        double distance = 1.6;      // meters in front of eyes
        double spacing  = 0.12;     // grid spacing
        double scale    = 1.0;      // overall size multiplier
        Vector base = eye.toVector().add(forward.multiply(distance));

        // Center the 3-column digit horizontally and 5 rows vertically.
        double width  = 3 * spacing * scale;
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
                // Use a ubiquitous particle to avoid version-specific enum issues.
                player.getWorld().spawnParticle(
                    Particle.CRIT,
                    pos.getX(), pos.getY(), pos.getZ(),
                    6,   // count
                    0.01, 0.01, 0.01, // small spread
                    0.0  // extra speed
                );
            }
        }
    }
}
