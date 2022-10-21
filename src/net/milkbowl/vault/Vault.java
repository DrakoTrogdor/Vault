/* This file is part of Vault.

    Vault is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Vault is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with Vault.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.milkbowl.vault;

import java.util.Collection;
import java.util.logging.Logger;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;


@SuppressWarnings("unused")
public class Vault extends JavaPlugin {

    private static Logger log;
    private Permission perms;
    private ServicesManager sm;
    private Vault plugin;

    @Override
    public void onDisable() {
        // Remove all Service Registrations
        getServer().getServicesManager().unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
    }

    @Override
    public void onEnable() {
        plugin = this;
        log = this.getLogger();
        sm = getServer().getServicesManager();
        // set defaults
        getConfig().addDefault("update-check", true);
        getConfig().options().copyDefaults(true);
        saveConfig();
        // Load Vault Addons
        loadPermission();
        getCommand("vault-info").setExecutor(this);
        getCommand("vault-convert").setExecutor(this);


        // Load up the Plugin metrics

        log.info(String.format("Enabled Version %s", getDescription().getVersion()));
    }


    /**
     * Attempts to load Permission Addons
     */
    private void loadPermission() {
        // Try to load Starburst
        sm.register(Permission.class, perms, this, ServicePriority.Lowest);
        log.info("[Permission] SuperPermissions loaded as backup permission system.");

        this.perms = sm.getRegistration(Permission.class).getProvider();
    }

    private void hookChat(String name, Class<? extends Chat> hookClass, ServicePriority priority, String... packages) {
        try {
            if (packagesExists(packages)) {
                Chat chat = hookClass.getConstructor(Plugin.class, Permission.class).newInstance(this, perms);
                sm.register(Chat.class, chat, this, priority);
                log.info(String.format("[Chat] %s found: %s", name, chat.isEnabled() ? "Loaded" : "Waiting"));
            }
        } catch (Exception e) {
            log.severe(String.format("[Chat] There was an error hooking %s - check to make sure you're using a compatible version!", name));
        }
    }

    private void hookPermission(String name, Class<? extends Permission> hookClass, ServicePriority priority, String... packages) {
        try {
            if (packagesExists(packages)) {
                Permission perms = hookClass.getConstructor(Plugin.class).newInstance(this);
                sm.register(Permission.class, perms, this, priority);
                log.info(String.format("[Permission] %s found: %s", name, perms.isEnabled() ? "Loaded" : "Waiting"));
            }
        } catch (Exception e) {
            log.severe(String.format("[Permission] There was an error hooking %s - check to make sure you're using a compatible version!", name));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if (!sender.hasPermission("vault.admin")) {
            sender.sendMessage("You do not have permission to use that command!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vault-info")) {
            infoCommand(sender);
            return true;
        } else if (command.getName().equalsIgnoreCase("vault-convert")) {
            convertCommand(sender, args);
            return true;
        } else {
            // Show help
            sender.sendMessage("Vault Commands:");
            sender.sendMessage("  /vault-info - Displays information about Vault");
            sender.sendMessage("  /vault-convert [economy1] [economy2] - Converts from one Economy to another");
            return true;
        }
    }

    private void convertCommand(CommandSender sender, String[] args) {
        Collection<RegisteredServiceProvider<Economy>> econs = this.getServer().getServicesManager().getRegistrations(Economy.class);
        if (econs == null || econs.size() < 2) {
            sender.sendMessage("You must have at least 2 economies loaded to convert.");
            return;
        } else if (args.length != 2) {
            sender.sendMessage("You must specify only the economy to convert from and the economy to convert to. (names should not contain spaces)");
            return;
        }
        Economy econ1 = null;
        Economy econ2 = null;
        StringBuilder economies = new StringBuilder();
        for (RegisteredServiceProvider<Economy> econ : econs) {
            String econName = econ.getProvider().getName().replace(" ", "");
            if (econName.equalsIgnoreCase(args[0])) {
                econ1 = econ.getProvider();
            } else if (econName.equalsIgnoreCase(args[1])) {
                econ2 = econ.getProvider();
            }
            if (economies.length() > 0) {
                economies.append(", ");
            }
            economies.append(econName);
        }

        if (econ1 == null) {
            sender.sendMessage("Could not find " + args[0] + " loaded on the server, check your spelling.");
            sender.sendMessage("Valid economies are: " + economies);
            return;
        } else if (econ2 == null) {
            sender.sendMessage("Could not find " + args[1] + " loaded on the server, check your spelling.");
            sender.sendMessage("Valid economies are: " + economies);
            return;
        }

        sender.sendMessage("This may take some time to convert, expect server lag.");
        for (OfflinePlayer op : Bukkit.getServer().getOfflinePlayers()) {
            if (econ1.hasAccount(op)) {
                if (econ2.hasAccount(op)) {
                    continue;
                }
                econ2.createPlayerAccount(op);
                double diff = econ1.getBalance(op) - econ2.getBalance(op);
                if (diff > 0) {
                    econ2.depositPlayer(op, diff);
                } else if (diff < 0) {
                    econ2.withdrawPlayer(op, -diff);
                }

            }
        }
        sender.sendMessage("Converson complete, please verify the data before using it.");
    }

    private void infoCommand(CommandSender sender) {
        // Get String of Registered Economy Services
        StringBuilder registeredEcons = null;
        Collection<RegisteredServiceProvider<Economy>> econs = this.getServer().getServicesManager().getRegistrations(Economy.class);
        for (RegisteredServiceProvider<Economy> econ : econs) {
            Economy e = econ.getProvider();
            if (registeredEcons == null) {
                registeredEcons = new StringBuilder(e.getName());
            } else {
                registeredEcons.append(", ").append(e.getName());
            }
        }

        // Get String of Registered Permission Services
        StringBuilder registeredPerms = null;
        Collection<RegisteredServiceProvider<Permission>> perms = this.getServer().getServicesManager().getRegistrations(Permission.class);
        for (RegisteredServiceProvider<Permission> perm : perms) {
            Permission p = perm.getProvider();
            if (registeredPerms == null) {
                registeredPerms = new StringBuilder(p.getName());
            } else {
                registeredPerms.append(", ").append(p.getName());
            }
        }

        StringBuilder registeredChats = null;
        Collection<RegisteredServiceProvider<Chat>> chats = this.getServer().getServicesManager().getRegistrations(Chat.class);
        for (RegisteredServiceProvider<Chat> chat : chats) {
            Chat c = chat.getProvider();
            if (registeredChats == null) {
                registeredChats = new StringBuilder(c.getName());
            } else {
                registeredChats.append(", ").append(c.getName());
            }
        }

        // Get Economy & Permission primary Services
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        Economy econ = null;
        if (rsp != null) {
            econ = rsp.getProvider();
        }
        Permission perm = null;
        RegisteredServiceProvider<Permission> rspp = getServer().getServicesManager().getRegistration(Permission.class);
        if (rspp != null) {
            perm = rspp.getProvider();
        }
        Chat chat = null;
        RegisteredServiceProvider<Chat> rspc = getServer().getServicesManager().getRegistration(Chat.class);
        if (rspc != null) {
            chat = rspc.getProvider();
        }
        // Send user some info!
        sender.sendMessage(String.format("[%s] Vault v%s Information", getDescription().getName(), getDescription().getVersion()));
        sender.sendMessage(String.format("[%s] Economy: %s [%s]", getDescription().getName(), econ == null ? "None" : econ.getName(), registeredEcons.toString()));
        sender.sendMessage(String.format("[%s] Permission: %s [%s]", getDescription().getName(), perm == null ? "None" : perm.getName(), registeredPerms.toString()));
        sender.sendMessage(String.format("[%s] Chat: %s [%s]", getDescription().getName(), chat == null ? "None" : chat.getName(), registeredChats.toString()));
    }

    /**
     * Determines if all packages in a String array are within the Classpath
     * This is the best way to determine if a specific plugin exists and will be
     * loaded. If the plugin package isn't loaded, we shouldn't bother waiting
     * for it!
     *
     * @param packages String Array of package names to check
     * @return Success or Failure
     */
    private static boolean packagesExists(String... packages) {
        try {
            for (String pkg : packages) {
                Class.forName(pkg);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
