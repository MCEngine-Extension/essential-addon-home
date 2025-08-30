package io.github.mcengine.extension.addon.essential.home.tabcompleter;

import io.github.mcengine.extension.addon.essential.home.util.db.HomeDB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tab-completion for the {@code /home} command.
 * <p>
 * Offers base subcommands: {@code set}, {@code tp}, and {@code delete},
 * and dynamically suggests the player's saved home names.
 */
public class HomeTabCompleter implements TabCompleter {

    /** DB accessor to fetch player home names for completion. */
    private final HomeDB homeDB;

    /**
     * Constructs a new tab completer for {@code /home}.
     *
     * @param homeDB database helper to list names.
     */
    public HomeTabCompleter(HomeDB homeDB) {
        this.homeDB = homeDB;
    }

    /**
     * Provides subcommand and name completions for {@code /home}.
     *
     * @param sender  Command sender.
     * @param command Command object.
     * @param alias   Alias used.
     * @param args    Current arguments.
     * @return Matching completions or empty list.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // If not a player, only suggest static subcommands.
        List<String> names = Collections.emptyList();
        if (sender instanceof Player p) {
            names = homeDB.listHomeNames(p.getUniqueId());
        }

        if (args.length == 1) {
            // Blend subcommands + existing names; filter by prefix.
            List<String> base = new ArrayList<>();
            base.addAll(Arrays.asList("set", "tp", "delete"));
            base.addAll(names);
            String prefix = args[0].toLowerCase();
            base.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            return base;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("tp") || sub.equals("delete")) {
                String prefix = args[1].toLowerCase();
                List<String> filtered = new ArrayList<>(names);
                filtered.removeIf(s -> !s.toLowerCase().startsWith(prefix));
                return filtered;
            }
        }

        return Collections.emptyList();
    }
}
