package br.com.craftpanel;

import br.com.craftpanel.backups.BackupService;
import br.com.craftpanel.config.ConfigLoader;
import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.monitoring.TickMonitor;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CraftPanelPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, org.bukkit.event.Listener {
    private static CraftPanelPlugin instance;
    private Thread serverThread;

    public static CraftPanelPlugin getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        // Runs before any world is loaded: the only safe moment to apply a restore staged from the panel.
        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.exists()) {
            try {
                BackupService.applyPendingRestore(ConfigLoader.load(configFile.getAbsolutePath()), Paths.get("").toAbsolutePath());
            } catch (Throwable t) {
                getLogger().severe("Failed to apply the pending backup restore: " + t);
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();

        File configFile = new File(getDataFolder(), "config.yml");
        PanelConfig panelConfig = ConfigLoader.load(configFile.getAbsolutePath());

        serverThread = new Thread(() -> {
            try {
                CraftPanelServer.getInstance().start(panelConfig);
            } catch (Exception e) {
                getLogger().severe("Failed to start CraftPanel Web server: " + e.getMessage());
            }
        }, "craftpanel-web-thread");
        serverThread.setDaemon(true);
        serverThread.start();

        PluginCommand cmd = getCommand("craftpanel");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        getServer().getPluginManager().registerEvents(this, this);
        TickMonitor.getInstance().start(this);

        getLogger().info("CraftPanel v" + getDescription().getVersion() + " enabled successfully. Author: lHawk_");
        getLogger().info("Web Panel running at http://" + panelConfig.server().host() + ":" + panelConfig.server().port());
    }

    @org.bukkit.event.EventHandler
    public void onServerLoad(org.bukkit.event.server.ServerLoadEvent event) {
        TickMonitor.getInstance().start(this);
    }

    @Override
    public void onDisable() {
        br.com.craftpanel.console.ConsoleCapture.uninstall();
        TickMonitor.getInstance().stop();
        CraftPanelServer.getInstance().stop();
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
        }
        instance = null;
        getLogger().info("CraftPanel Web disabled cleanly.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> handleStatus(sender);
            case "url" -> handleUrl(sender);
            case "reload" -> handleReload(sender);
            case "createuser" -> handleCreateUser(sender, args);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + args[0] + ". Type /" + label + " help for commands.");
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "=== CraftPanel Web Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " status " + ChatColor.GRAY + "- Show panel server status");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " url " + ChatColor.GRAY + "- Show web panel URL");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload " + ChatColor.GRAY + "- Reload panel configuration");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " createuser <user> <pass> [role] " + ChatColor.GRAY + "- Create panel user");
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("craftpanel.use") && !sender.hasPermission("craftpanel.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }

        CraftPanelServer server = CraftPanelServer.getInstance();
        boolean running = server.isRunning();
        PanelConfig cfg = server.getConfig();

        sender.sendMessage(ChatColor.GOLD + "=== CraftPanel Status ===");
        sender.sendMessage(ChatColor.GRAY + "Status: " + (running ? ChatColor.GREEN + "Running" : ChatColor.RED + "Stopped"));
        if (cfg != null) {
            sender.sendMessage(ChatColor.GRAY + "Address: " + ChatColor.AQUA + "http://" + cfg.server().host() + ":" + cfg.server().port());
            sender.sendMessage(ChatColor.GRAY + "Remote Access: " + (cfg.security().remoteAccessEnabled() ? ChatColor.YELLOW + "Enabled" : ChatColor.RED + "Disabled (Local only)"));
        }
        sender.sendMessage(ChatColor.GRAY + "Host Instance: " + ChatColor.WHITE + getServer().getName() + " " + getServer().getBukkitVersion());
    }

    private void handleUrl(CommandSender sender) {
        if (!sender.hasPermission("craftpanel.use") && !sender.hasPermission("craftpanel.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }

        PanelConfig cfg = CraftPanelServer.getInstance().getConfig();
        String host = (cfg != null) ? cfg.server().host() : "127.0.0.1";
        int port = (cfg != null) ? cfg.server().port() : 8080;
        sender.sendMessage(ChatColor.GREEN + "CraftPanel Web URL: " + ChatColor.AQUA + "http://" + host + ":" + port);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("craftpanel.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to execute this command.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Reloading CraftPanel configuration...");
        File configFile = new File(getDataFolder(), "config.yml");
        PanelConfig panelConfig = ConfigLoader.load(configFile.getAbsolutePath());

        CraftPanelServer.getInstance().stop();
        serverThread = new Thread(() -> {
            try {
                CraftPanelServer.getInstance().start(panelConfig);
            } catch (Exception e) {
                getLogger().severe("Failed to restart CraftPanel after reload: " + e.getMessage());
            }
        }, "craftpanel-web-thread");
        serverThread.setDaemon(true);
        serverThread.start();

        sender.sendMessage(ChatColor.GREEN + "CraftPanel reloaded successfully on http://" + panelConfig.server().host() + ":" + panelConfig.server().port());
    }

    private void handleCreateUser(CommandSender sender, String[] args) {
        if (!sender.hasPermission("craftpanel.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to execute this command.");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /craftpanel createuser <username> <password> [admin|moderator|viewer]");
            return;
        }

        String username = args[1].trim();
        String password = args[2];
        String role = (args.length >= 4) ? args[3].toLowerCase() : "admin";

        UserService userService = CraftPanelServer.getInstance().getUserService();
        if (userService == null) {
            sender.sendMessage(ChatColor.RED + "CraftPanel UserService is not currently initialized.");
            return;
        }

        try {
            User created = userService.createUser(username, password, username, role);
            sender.sendMessage(ChatColor.GREEN + "User '" + created.username() + "' created successfully with role '" + created.roleId() + "'.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to create user: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("status", "url", "reload", "createuser", "help");
            List<String> result = new ArrayList<>();
            for (String s : subs) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }
            return result;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("createuser")) {
            List<String> roles = Arrays.asList("admin", "moderator", "viewer");
            List<String> result = new ArrayList<>();
            for (String r : roles) {
                if (r.toLowerCase().startsWith(args[3].toLowerCase())) {
                    result.add(r);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
