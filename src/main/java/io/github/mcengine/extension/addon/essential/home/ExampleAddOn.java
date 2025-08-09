package io.github.mcengine.extension.addon.example;

import io.github.mcengine.api.core.MCEngineCoreApi;
import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.api.artificialintelligence.extension.addon.IMCEngineArtificialIntelligenceAddOn;

import io.github.mcengine.extension.addon.example.AddOnCommand;
import io.github.mcengine.extension.addon.example.AddOnListener;
import io.github.mcengine.extension.addon.example.AddOnTabCompleter;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Main class for the ExampleAddOn.
 * <p>
 * Registers the /aiaddonexample command and event listeners.
 */
public class ExampleAddOn implements IMCEngineArtificialIntelligenceAddOn {

    /**
     * Initializes the ExampleAddOn.
     * Called automatically by the MCEngine core plugin.
     *
     * @param plugin The Bukkit plugin instance.
     */
    @Override
    public void onLoad(Plugin plugin) {
        /**
         * Logger instance for the AddOn.
         */
        MCEngineExtensionLogger logger = new MCEngineExtensionLogger(plugin, "AddOn", "ArtificialIntelligenceExampleAddon");

        try {
            // Register event listener
            PluginManager pluginManager = Bukkit.getPluginManager();
            pluginManager.registerEvents(new AddOnListener(plugin), plugin);

            // Reflectively access Bukkit's CommandMap
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Define the /aiaddonexample command
            Command aiAddonExampleCommand = new Command("aiaddonexample") {

                /**
                 * Handles command execution for /aiaddonexample.
                 */
                private final AddOnCommand handler = new AddOnCommand();

                /**
                 * Handles tab-completion for /aiaddonexample.
                 */
                private final AddOnTabCompleter completer = new AddOnTabCompleter();

                /**
                 * Executes the /aiaddonexample command.
                 *
                 * @param sender The command sender.
                 * @param label  The command label.
                 * @param args   The command arguments.
                 * @return true if successful.
                 */
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return handler.onCommand(sender, this, label, args);
                }

                /**
                 * Handles tab-completion for the /aiaddonexample command.
                 *
                 * @param sender The command sender.
                 * @param alias  The alias used.
                 * @param args   The current arguments.
                 * @return A list of possible completions.
                 */
                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return completer.onTabComplete(sender, this, alias, args);
                }
            };

            aiAddonExampleCommand.setDescription("AI AddOn example command.");
            aiAddonExampleCommand.setUsage("/aiaddonexample");

            // Dynamically register the /aiaddonexample command
            commandMap.register(plugin.getName().toLowerCase(), aiAddonExampleCommand);

            logger.info("Enabled successfully.");
        } catch (Exception e) {
            logger.warning("Failed to initialize ExampleAddOn: " + e.getMessage());
            e.printStackTrace();
        }

        // Check for updates
        MCEngineCoreApi.checkUpdate(plugin, logger.getLogger(),
            "github", "MCEngine-Extension", "artificialintelligence-addon-example",
            plugin.getConfig().getString("github.token", "null"));
    }

    @Override
    public void onDisload(Plugin plugin) {
        // No specific unload logic
    }

    @Override
    public void setId(String id) {
        MCEngineCoreApi.setId("mcengine-artificialintelligence-addon-example");
    }
}
