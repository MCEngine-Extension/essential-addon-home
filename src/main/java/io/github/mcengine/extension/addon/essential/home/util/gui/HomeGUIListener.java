package io.github.mcengine.extension.addon.essential.home.util.gui;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.home.util.db.HomeDB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Listener that handles clicks in the Home GUI.
 *
 * <p>Detects the custom {@link HomeGUIHolder}, prevents item grabbing, and
 * either teleports to the clicked home or changes pages via navigation
 * arrows in the footer row.</p>
 */
public final class HomeGUIListener implements Listener {

    /**
     * Database accessor used to fulfill teleports and list names.
     */
    private final HomeDB homeDB;

    /**
     * Logger used for diagnostics and trace messages.
     */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs the listener with required dependencies.
     *
     * @param homeDB database accessor for home data.
     * @param logger logger for diagnostics.
     */
    public HomeGUIListener(HomeDB homeDB, MCEngineExtensionLogger logger) {
        this.homeDB = homeDB;
        this.logger = logger;
    }

    /**
     * Handles all clicks inside the Home GUI inventory.
     *
     * @param e inventory click event.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() == null) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof HomeGUIHolder holder)) return;

        // Cancel default behavior (taking items).
        e.setCancelled(true);

        // Only process clicks in the top inventory.
        if (!e.getClickedInventory().equals(e.getView().getTopInventory())) return;

        // Safety: only owner can interact.
        if (!player.getUniqueId().equals(holder.getOwner())) return;

        var clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;

        String title = clicked.getItemMeta().getDisplayName();

        // Navigation buttons.
        if (title.startsWith("§a« Previous")) {
            int prev = Math.max(0, holder.getPage() - 1);
            HomeGUI.open(player, homeDB, logger, prev);
            return;
        }
        if (title.startsWith("§aNext »")) {
            int next = holder.getPage() + 1;
            HomeGUI.open(player, homeDB, logger, next);
            return;
        }

        // Home items: display name is "§a<name>"
        if (title.startsWith("§a")) {
            String homeName = title.substring(2); // strip '§a'
            HomeGUI.teleport(player, homeName, homeDB);
            player.closeInventory();
        }
    }
}
