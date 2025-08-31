package io.github.mcengine.extension.addon.essential.home.tabcompleter;

import io.github.mcengine.extension.addon.essential.home.util.db.HomeDB;
import org.bukkit.Bukkit;
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
 * Offers base subcommands: {@code set}, {@code tp}, {@code delete}, {@code limit},
 * and dynamically suggests the player's saved home names. For {@code limit},
 * suggests actions, online players, and common integer amounts.
 */
public class HomeTabCompleter implements TabCompleter {

    /**
     * DB accessor to fetch player home names for completion.
     */
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
            base.addAll(Arrays.asList("set", "tp", "delete", "limit"));
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
            if (sub.equals("limit")) {
                List<String> ops = new ArrayList<>(Arrays.asList("add", "minus"));
                String prefix = args[1].toLowerCase();
                ops.removeIf(s -> !s.toLowerCase().startsWith(prefix));
                return ops;
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("limit")) {
            // Suggest online players
            String prefix = args[2].toLowerCase();
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                String n = p.getName();
                if (n.toLowerCase().startsWith(prefix)) {
                    players.add(n);
                }
            }
            return players;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("limit")) {
            if (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("minus")) {
                // Provide a few sensible integer suggestions.
                List<String> suggestions = new ArrayList<>(Arrays.asList("1", "2", "3", "5", "10"));
                String prefix = args[3].toLowerCase();
                suggestions.removeIf(s -> !s.toLowerCase().startsWith(prefix));
                return suggestions;
            }
        }

        return Collections.emptyList();
    }
}
