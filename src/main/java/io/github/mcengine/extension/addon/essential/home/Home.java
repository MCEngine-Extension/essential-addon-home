package io.github.mcengine.extension.addon.essential.home;

import io.github.mcengine.api.core.MCEngineCoreApi;
import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.api.essential.extension.addon.IMCEngineEssentialAddOn;
import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.home.command.HomeCommand;
import io.github.mcengine.extension.addon.essential.home.tabcompleter.HomeTabCompleter;
import io.github.mcengine.extension.addon.essential.home.util.HomeDB;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.sql.Connection;

/**
 * Main class for the Essential Home AddOn.
 * <p>
 * Dynamically registers the {@code /home} command and wires the database utility
 * that manages player home coordinates.
 */
public class Home implements IMCEngineEssentialAddOn {

    /** Logger wrapper dedicated to this add-on. */
    private MCEngineExtensionLogger logger;

    /** Shared DB utility for CRUD against the {@code home} table. */
    private HomeDB homeDB;

    /**
     * Initializes the Home AddOn.
     * <p>
     * Registers {@code /home} via the Bukkit {@link CommandMap} and prepares the
     * required database table using {@link HomeDB}.
     *
     * @param plugin The Bukkit plugin instance.
     */
    @Override
    public void onLoad(Plugin plugin) {
        // Initialize logger first so all messages go through a consistent channel.
        this.logger = new MCEngineExtensionLogger(plugin, "AddOn", "EssentialHome");

        try {
            // Obtain DB connection from Essential common API and create the table.
            Connection conn = MCEngineEssentialCommon.getApi().getDBConnection();
            this.homeDB = new HomeDB(conn, logger);

            // Access Bukkit's CommandMap reflectively and register /home.
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Define the /home command shell that forwards to our executor & completer.
            Command homeCommand = new Command("home") {
                /** Command logic handler for {@code /home}. */
                private final HomeCommand handler = new HomeCommand(homeDB, logger);
                /** Tab completer for {@code /home}. */
                private final HomeTabCompleter completer = new HomeTabCompleter();

                /**
                 * Executes the {@code /home} command.
                 *
                 * @param sender The command sender.
                 * @param label  The command label used.
                 * @param args   The command arguments.
                 * @return {@code true} when handled.
                 */
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return handler.onCommand(sender, this, label, args);
                }

                /**
                 * Provides tab-completions for {@code /home}.
                 */
                @Override
                public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return completer.onTabComplete(sender, this, alias, args);
                }
            };

            homeCommand.setDescription("Teleport to or manage your home.");
            homeCommand.setUsage("/home [set|tp|delete]");

            // Register the command under the plugin's fallback prefix.
            commandMap.register(plugin.getName().toLowerCase(), homeCommand);

            logger.info("Home AddOn enabled and /home registered.");
        } catch (Exception e) {
            logger.warning("Failed to initialize Home AddOn: " + e.getMessage());
            e.printStackTrace();
        }

        // Provide a stable identifier for this add-on.
        MCEngineCoreApi.setId("mcengine-essential-home");
    }

    /**
     * Called when this AddOn is unloaded/disabled.
     *
     * @param plugin Bukkit plugin instance.
     */
    @Override
    public void onDisload(Plugin plugin) {
        // No explicit shutdown actions required for this simple add-on.
    }

    /**
     * Sets the identifier string for this AddOn.
     *
     * @param id The identifier (ignored in favor of a stable built-in id).
     */
    @Override
    public void setId(String id) {
        // Keep a stable id; ignore external values to avoid churn.
        MCEngineCoreApi.setId("mcengine-essential-home");
    }
}
