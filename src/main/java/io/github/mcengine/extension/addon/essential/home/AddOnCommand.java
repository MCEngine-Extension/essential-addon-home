package io.github.mcengine.extension.addon.example;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Handles /aiaddonexample command logic.
 */
public class AddOnCommand implements CommandExecutor {

    /**
     * Executes the /aiaddonexample command.
     *
     * @param sender The source of the command.
     * @param command The command which was executed.
     * @param label The alias used.
     * @param args The command arguments.
     * @return true if command executed successfully.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§aExampleAddOn command executed!");
        return true;
    }
}
