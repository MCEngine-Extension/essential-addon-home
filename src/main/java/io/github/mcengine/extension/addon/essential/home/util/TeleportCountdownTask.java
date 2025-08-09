package io.github.mcengine.extension.addon.essential.home.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Top-level countdown task used by {@link HomeCommandUtil} to avoid inner-class
 * packaging issues. Counts down from 5 and teleports the player.
 */
public final class TeleportCountdownTask extends BukkitRunnable {

    /** Player that will be teleported. */
    private final Player player;

    /** Target home display name (for chat messages). */
    private final String name;

    /** Destination coordinates (X/Y/Z only). */
    private final Vector coords;

    /** Seconds remaining in the countdown (starts at 5). */
    private int seconds = 5;

    /**
     * Constructs the countdown task.
     *
     * @param player target player
     * @param name   home name
     * @param coords destination coordinates
     */
    public TeleportCountdownTask(Player player, String name, Vector coords) {
        this.player = player;
        this.name = name;
        this.coords = coords;
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }

        HomeCommandUtil.renderDigitParticles(player, seconds);

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
            cancel();
            return;
        }
        seconds--;
    }
}
