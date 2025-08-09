package io.github.mcengine.extension.addon.essential.home.tabcompleter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tab-completion for the {@code /home} command.
 * <p>
 * Offers base subcommands: {@code set}, {@code tp}, and {@code delete}.
 */
public class HomeTabCompleter implements TabCompleter {

    /**
     * Provides simple subcommand completions for {@code /home}.
     *
     * @param sender  Command sender.
     * @param command Command object.
     * @param alias   Alias used.
     * @param args    Current arguments.
     * @return Matching completions or empty list.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("set", "tp", "delete");
        }
        return Collections.emptyList();
        }
}
