package io.github.mcengine.extension.addon.essential.home.util.gui;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.home.command.HomeCommandUtil;
import io.github.mcengine.extension.addon.essential.home.util.db.HomeDB;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builder/open helper for the Home GUI.
 *
 * <p>Creates an inventory listing the player's homes as clickable items
 * that teleport the player to the selected home. Includes simple pagination
 * when the number of homes exceeds one page.</p>
 */
public final class HomeGUI {

    /**
     * GUI title displayed on the inventory.
     */
    private static final String TITLE = "§9Your Homes";

    /**
     * Maximum number of home entries per page (slots excluding navigation).
     */
    private static final int PAGE_CAPACITY = 45; // Use a 54-slot inventory: 45 items + 9 controls/footer

    /**
     * Hidden constructor; utility class.
     */
    private HomeGUI() {
    }

    /**
     * Opens the Home GUI for the given player.
     *
     * @param player the player.
     * @param homeDB database accessor to read home names.
     * @param logger logger for diagnostics.
     * @param page   zero-based page index.
     */
    public static void open(Player player, HomeDB homeDB, MCEngineExtensionLogger logger, int page) {
        List<String> names = homeDB.listHomeNames(player.getUniqueId());
        int total = names.size();

        // Determine number of pages.
        int pageCount = Math.max(1, (int) Math.ceil(total / (double) PAGE_CAPACITY));
        int current = Math.max(0, Math.min(page, pageCount - 1));

        // Create a 54-slot (6 rows) inventory with our custom holder.
        Inventory inv = Bukkit.createInventory(new HomeGUIHolder(player.getUniqueId(), current), 54, TITLE);

        // Determine slice of names for this page.
        int start = current * PAGE_CAPACITY;
        int end = Math.min(start + PAGE_CAPACITY, total);
        List<String> slice = start >= end ? new ArrayList<>() : names.subList(start, end);

        // Fill entries.
        int slot = 0;
        for (String name : slice) {
            inv.setItem(slot++, createHomeItem(name));
        }

        // Footer: page indicator and navigation
        inv.setItem(49, createInfoItem("§bPage §f" + (current + 1) + "§7/§f" + pageCount, List.of("§7Click an item to teleport.")));
        if (current > 0) {
            inv.setItem(45, createNavItem("§a« Previous", List.of("§7Go to page " + current)));
        }
        if (current < pageCount - 1) {
            inv.setItem(53, createNavItem("§aNext »", List.of("§7Go to page " + (current + 2))));
        }

        player.openInventory(inv);

        if (total == 0) {
            player.sendMessage("§eYou have no homes yet. Use §b/home set <name>§e to create one.");
        }
    }

    /**
     * Attempts a teleport for the player to the given home name.
     *
     * @param player player to teleport.
     * @param name   home name.
     * @param homeDB DB accessor.
     */
    public static void teleport(Player player, String name, HomeDB homeDB) {
        HomeCommandUtil.handleTeleport(player, player.getUniqueId(), name, homeDB);
    }

    private static ItemStack createHomeItem(String name) {
        ItemStack it = new ItemStack(Material.RECOVERY_COMPASS); // visually distinct & thematic
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§a" + name);
        meta.setLore(List.of("§7Click to teleport to §f" + name));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack createNavItem(String title, List<String> lore) {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack createInfoItem(String title, List<String> lore) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }
}
