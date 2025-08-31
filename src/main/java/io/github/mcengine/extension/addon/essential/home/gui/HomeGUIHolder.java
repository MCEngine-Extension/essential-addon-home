package io.github.mcengine.extension.addon.essential.home.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Custom {@link InventoryHolder} used to safely identify the Home GUI.
 * Stores the owner UUID and current page for pagination.
 */
public final class HomeGUIHolder implements InventoryHolder {

    /**
     * UUID of the player who owns this GUI instance.
     */
    private final UUID owner;

    /**
     * Zero-based page index for pagination.
     */
    private final int page;

    /**
     * Constructs a holder with owner and page information.
     *
     * @param owner Owner player UUID.
     * @param page  Zero-based page index.
     */
    public HomeGUIHolder(UUID owner, int page) {
        this.owner = owner;
        this.page = page;
    }

    /**
     * Gets the owner UUID.
     *
     * @return UUID of the GUI owner.
     */
    public UUID getOwner() {
        return owner;
    }

    /**
     * Gets the current page index.
     *
     * @return zero-based page index.
     */
    public int getPage() {
        return page;
    }

    /**
     * Required by {@link InventoryHolder}; not used because we create
     * the {@link Inventory} externally and only need the holder marker.
     */
    @Override
    public Inventory getInventory() {
        return null;
    }
}
