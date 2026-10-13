package br.com.craftpanel.plugins;

import br.com.craftpanel.CraftPanelPlugin;
import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.servers.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PluginManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);
    private final PanelConfig config;

    public PluginManager(PanelConfig config) {
        this.config = config;
    }

    public List<InstalledPlugin> listPlugins(MinecraftServer server) {
        File pluginsDir = getPluginsDir(server);
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            return List.of();
        }

        File[] files = pluginsDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".jar.disabled"));

        if (files == null) {
            return List.of();
        }

        Object bukkitPm = null;
        Method getPluginMethod = null;
        Method isEnabledMethod = null;
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            bukkitPm = bukkitClass.getMethod("getPluginManager").invoke(null);
            if (bukkitPm != null) {
                getPluginMethod = bukkitPm.getClass().getMethod("getPlugin", String.class);
                Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin");
                isEnabledMethod = pluginClass.getMethod("isEnabled");
            }
        } catch (Throwable ignored) {}

        List<InstalledPlugin> result = new ArrayList<>();
        for (File file : files) {
            InstalledPlugin raw = JarMetadataExtractor.extract(file);
            boolean loadedInServer = false;
            boolean enabledInServer = false;

            if (bukkitPm != null && getPluginMethod != null && isEnabledMethod != null) {
                try {
                    Object bukkitPlugin = getPluginMethod.invoke(bukkitPm, raw.name());
                    if (bukkitPlugin != null) {
                        loadedInServer = true;
                        enabledInServer = (boolean) isEnabledMethod.invoke(bukkitPlugin);
                    }
                } catch (Throwable ignored) {}
            }

            boolean isFileDisabled = file.getName().toLowerCase().endsWith(".disabled");
            boolean pendingRestart = !isFileDisabled && bukkitPm != null && !loadedInServer;
            boolean effectiveEnabled = loadedInServer ? enabledInServer : !isFileDisabled;

            result.add(new InstalledPlugin(
                    raw.filename(),
                    raw.name(),
                    raw.version(),
                    raw.author(),
                    raw.description(),
                    raw.mainClass(),
                    raw.apiVersion(),
                    raw.foliaSupported(),
                    effectiveEnabled,
                    loadedInServer,
                    pendingRestart,
                    raw.sizeBytes(),
                    raw.dependencies(),
                    raw.softDependencies()
            ));
        }

        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    public void togglePlugin(MinecraftServer server, String filename) throws IOException {
        File pluginsDir = getPluginsDir(server);
        File file = new File(pluginsDir, filename);

        if (!file.exists()) {
            throw new IllegalArgumentException("Plugin file not found: " + filename);
        }

        InstalledPlugin meta = JarMetadataExtractor.extract(file);

        File target;
        if (filename.endsWith(".disabled")) {
            target = new File(pluginsDir, filename.substring(0, filename.length() - ".disabled".length()));
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            loadPluginAtRuntime(target);
        } else {
            target = new File(pluginsDir, filename + ".disabled");
            disablePluginAtRuntime(meta.name());
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean loadPlugin(MinecraftServer server, String filename) {
        File pluginsDir = getPluginsDir(server);
        File file = new File(pluginsDir, filename);
        if (!file.exists()) {
            return false;
        }
        return loadPluginAtRuntime(file);
    }

    public void deletePlugin(MinecraftServer server, String filename) throws IOException {
        deletePlugin(server, filename, false);
    }

    public void deletePlugin(MinecraftServer server, String filename, boolean deleteConfig) throws IOException {
        File pluginsDir = getPluginsDir(server);
        File file = new File(pluginsDir, filename);

        if (!file.exists()) {
            return;
        }

        InstalledPlugin meta = JarMetadataExtractor.extract(file);
        String pluginName = meta != null ? meta.name() : filename.replace(".jar", "");

        unloadPluginAtRuntime(pluginName);

        Path backupDir = Paths.get(config.storage().backupDirectory(), "plugins", server.id());
        Files.createDirectories(backupDir);
        Path backupFile = backupDir.resolve(filename + "." + System.currentTimeMillis() + ".bak");
        try {
            Files.copy(file.toPath(), backupFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {}

        boolean deleted = false;
        for (int i = 0; i < 5; i++) {
            try {
                Files.delete(file.toPath());
                deleted = true;
                break;
            } catch (IOException e) {
                System.gc();
                try {
                    Thread.sleep(120);
                } catch (InterruptedException ignored) {}
            }
        }

        if (!deleted && file.exists()) {
            if (!file.delete()) {
                File deleteMe = new File(file.getParentFile(), filename + ".deleteme." + System.currentTimeMillis());
                if (file.renameTo(deleteMe)) {
                    deleteMe.deleteOnExit();
                } else {
                    file.deleteOnExit();
                    throw new IOException("O arquivo já está sendo usado por outro processo. O plugin foi descarregado e agendado para remoção.");
                }
            }
        }

        if (deleteConfig) {
            deletePluginConfigDirectory(server, pluginName);
        }
    }

    public void deletePluginConfigDirectory(MinecraftServer server, String pluginName) {
        if (pluginName == null || pluginName.isBlank()) {
            return;
        }
        File pluginsDir = getPluginsDir(server);
        File configDir = new File(pluginsDir, pluginName);
        if (configDir.exists() && configDir.isDirectory()) {
            deleteDirectoryRecursively(configDir);
        }
    }

    private void deleteDirectoryRecursively(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectoryRecursively(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    public InstalledPlugin installPlugin(MinecraftServer server, String filename, InputStream in) throws IOException {
        File pluginsDir = getPluginsDir(server);
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
        }

        String safeName = Paths.get(filename).getFileName().toString();
        if (!safeName.toLowerCase().endsWith(".jar")) {
            safeName += ".jar";
        }

        File target = new File(pluginsDir, safeName);
        Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);

        loadPluginAtRuntime(target);
        return JarMetadataExtractor.extract(target);
    }

    public boolean loadPluginAtRuntime(File file) {
        if (file == null || !file.exists() || !file.getName().toLowerCase().endsWith(".jar")) {
            return false;
        }

        try {
            CraftPanelPlugin pluginInstance = CraftPanelPlugin.getInstance();
            if (pluginInstance == null || !pluginInstance.isEnabled()) {
                return false;
            }

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Runnable task = () -> {
                try {
                    Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
                    Object pm = bukkitClass.getMethod("getPluginManager").invoke(null);
                    Method loadPluginMethod = pm.getClass().getMethod("loadPlugin", File.class);
                    Object loaded = loadPluginMethod.invoke(pm, file);
                    if (loaded != null) {
                        try {
                            Method onLoad = loaded.getClass().getMethod("onLoad");
                            onLoad.invoke(loaded);
                        } catch (Throwable ignored) {}

                        registerPluginCommands(loaded);

                        Method enablePlugin = pm.getClass().getMethod("enablePlugin", Class.forName("org.bukkit.plugin.Plugin"));
                        enablePlugin.invoke(pm, loaded);

                        registerPluginCommands(loaded);
                        syncServerCommands();
                        future.complete(true);
                    } else {
                        future.complete(false);
                    }
                } catch (Throwable t) {
                    LOGGER.error("Failed to dynamically load plugin: " + file.getName(), t);
                    future.complete(false);
                }
            };

            boolean isMain = false;
            try {
                Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
                Method isPrimary = bukkitClass.getMethod("isPrimaryThread");
                isMain = (boolean) isPrimary.invoke(null);
            } catch (Throwable ignored) {}

            if (isMain) {
                task.run();
                return future.getNow(false);
            }

            dispatchServerTask(pluginInstance, task);
            try {
                return future.get(45, TimeUnit.SECONDS);
            } catch (Throwable t) {
                return false;
            }
        } catch (Throwable t) {
            LOGGER.error("Error dispatching load task for " + file.getName(), t);
            return false;
        }
    }

    public boolean disablePluginAtRuntime(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        try {
            CraftPanelPlugin pluginInstance = CraftPanelPlugin.getInstance();
            if (pluginInstance == null || !pluginInstance.isEnabled()) {
                return false;
            }

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Runnable task = () -> {
                try {
                    Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
                    Object pm = bukkitClass.getMethod("getPluginManager").invoke(null);
                    Method getPlugin = pm.getClass().getMethod("getPlugin", String.class);
                    Object target = getPlugin.invoke(pm, name);
                    if (target != null) {
                        Method disablePlugin = pm.getClass().getMethod("disablePlugin", Class.forName("org.bukkit.plugin.Plugin"));
                        disablePlugin.invoke(pm, target);
                    }
                    future.complete(true);
                } catch (Throwable t) {
                    LOGGER.error("Failed to dynamically disable plugin: " + name, t);
                    future.complete(false);
                }
            };

            boolean isMain = false;
            try {
                Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
                Method isPrimary = bukkitClass.getMethod("isPrimaryThread");
                isMain = (boolean) isPrimary.invoke(null);
            } catch (Throwable ignored) {}

            if (isMain) {
                task.run();
                return future.getNow(false);
            }

            dispatchServerTask(pluginInstance, task);
            try {
                return future.get(15, TimeUnit.SECONDS);
            } catch (Throwable t) {
                return false;
            }
        } catch (Throwable t) {
            LOGGER.error("Error dispatching disable task for " + name, t);
            return false;
        }
    }

    public boolean unloadPluginAtRuntime(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        try {
            CraftPanelPlugin pluginInstance = CraftPanelPlugin.getInstance();
            if (pluginInstance == null || !pluginInstance.isEnabled()) {
                return false;
            }

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Runnable task = () -> {
                try {
                    Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
                    Object pm = bukkitClass.getMethod("getPluginManager").invoke(null);
                    Method getPlugin = pm.getClass().getMethod("getPlugin", String.class);
                    Object target = getPlugin.invoke(pm, name);
                    if (target == null) {
                        future.complete(true);
                        return;
                    }

                    Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin");

                    Method isEnabled = target.getClass().getMethod("isEnabled");
                    if ((boolean) isEnabled.invoke(target)) {
                        Method disablePlugin = pm.getClass().getMethod("disablePlugin", pluginClass);
                        disablePlugin.invoke(pm, target);
                    }

                    try {
                        Class<?> handlerListClass = Class.forName("org.bukkit.event.HandlerList");
                        Method unregisterAll = handlerListClass.getMethod("unregisterAll", pluginClass);
                        unregisterAll.invoke(null, target);
                    } catch (Throwable ignored) {}

                    try {
                        Object scheduler = bukkitClass.getMethod("getScheduler").invoke(null);
                        Method cancelTasks = scheduler.getClass().getMethod("cancelTasks", pluginClass);
                        cancelTasks.invoke(scheduler, target);
                    } catch (Throwable ignored) {}

                    try {
                        Field pluginsField = pm.getClass().getDeclaredField("plugins");
                        pluginsField.setAccessible(true);
                        List<?> plugins = (List<?>) pluginsField.get(pm);
                        plugins.remove(target);
                    } catch (Throwable ignored) {}

                    try {
                        Field lookupNamesField = pm.getClass().getDeclaredField("lookupNames");
                        lookupNamesField.setAccessible(true);
                        Map<?, ?> lookupNames = (Map<?, ?>) lookupNamesField.get(pm);
                        lookupNames.remove(name);
                        lookupNames.remove(name.toLowerCase());
                    } catch (Throwable ignored) {}

                    try {
                        Object serverObj = bukkitClass.getMethod("getServer").invoke(null);
                        Method getCommandMap = serverObj.getClass().getMethod("getCommandMap");
                        Object commandMap = getCommandMap.invoke(serverObj);
                        if (commandMap != null) {
                            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
                            knownCommandsField.setAccessible(true);
                            Map<?, ?> knownCommands = (Map<?, ?>) knownCommandsField.get(commandMap);
                            ClassLoader cl = target.getClass().getClassLoader();
                            Class<?> picClass = null;
                            try {
                                picClass = Class.forName("org.bukkit.command.PluginIdentifiableCommand");
                            } catch (Throwable ignored) {}
                            final Class<?> finalPicClass = picClass;
                            knownCommands.values().removeIf(cmd -> {
                                try {
                                    if (cmd == null) return false;
                                    if (cmd.getClass().getClassLoader() == cl) return true;
                                    if (finalPicClass != null && finalPicClass.isInstance(cmd)) {
                                        Method getPluginMethod = cmd.getClass().getMethod("getPlugin");
                                        return getPluginMethod.invoke(cmd) == target;
                                    }
                                    try {
                                        Method getPluginMethod = cmd.getClass().getMethod("getPlugin");
                                        return getPluginMethod.invoke(cmd) == target;
                                    } catch (Throwable ignored) {
                                        return false;
                                    }
                                } catch (Throwable t) {
                                    return false;
                                }
                            });
                            syncServerCommands();
                        }
                    } catch (Throwable ignored) {}

                    ClassLoader classLoader = target.getClass().getClassLoader();
                    if (classLoader instanceof URLClassLoader urlCl) {
                        try {
                            urlCl.close();
                        } catch (Throwable ignored) {}
                    } else if (classLoader instanceof AutoCloseable ac) {
                        try {
                            ac.close();
                        } catch (Throwable ignored) {}
                    }

                    System.gc();
                    System.runFinalization();
                    future.complete(true);
                } catch (Throwable t) {
                    LOGGER.error("Failed to unload plugin: " + name, t);
                    future.complete(false);
                }
            };

            boolean isMain = false;
            try {
                Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
                Method isPrimary = bukkitClass.getMethod("isPrimaryThread");
                isMain = (boolean) isPrimary.invoke(null);
            } catch (Throwable ignored) {}

            if (isMain) {
                task.run();
                return future.getNow(false);
            }

            dispatchServerTask(pluginInstance, task);
            try {
                return future.get(15, TimeUnit.SECONDS);
            } catch (Throwable t) {
                return false;
            }
        } catch (Throwable t) {
            LOGGER.error("Error dispatching unload task for " + name, t);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void registerPluginCommands(Object pluginObj) {
        if (pluginObj == null) {
            return;
        }
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object serverObj = bukkitClass.getMethod("getServer").invoke(null);
            Method getCommandMap = serverObj.getClass().getMethod("getCommandMap");
            Object commandMap = getCommandMap.invoke(serverObj);
            if (commandMap == null) {
                return;
            }

            Method getDesc = pluginObj.getClass().getMethod("getDescription");
            Object desc = getDesc.invoke(pluginObj);
            Method getCommands = desc.getClass().getMethod("getCommands");
            Map<String, Map<String, Object>> commands = (Map<String, Map<String, Object>>) getCommands.invoke(desc);
            if (commands == null || commands.isEmpty()) {
                return;
            }

            Method getName = desc.getClass().getMethod("getName");
            String pluginName = (String) getName.invoke(desc);

            Method getCommand = commandMap.getClass().getMethod("getCommand", String.class);
            Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin");
            Class<?> pluginCommandClass = Class.forName("org.bukkit.command.PluginCommand");
            java.lang.reflect.Constructor<?> ctor = pluginCommandClass.getDeclaredConstructor(String.class, pluginClass);
            ctor.setAccessible(true);

            Method registerMethod = commandMap.getClass().getMethod("register", String.class, Class.forName("org.bukkit.command.Command"));

            for (Map.Entry<String, Map<String, Object>> entry : commands.entrySet()) {
                String cmdName = entry.getKey();
                if (cmdName == null || cmdName.isBlank()) {
                    continue;
                }

                Object existing = getCommand.invoke(commandMap, cmdName);
                if (existing == null) {
                    Object pluginCmd = ctor.newInstance(cmdName, pluginObj);
                    Map<String, Object> details = entry.getValue();
                    if (details != null) {
                        Object description = details.get("description");
                        if (description != null) {
                            pluginCmd.getClass().getMethod("setDescription", String.class).invoke(pluginCmd, description.toString());
                        }
                        Object usage = details.get("usage");
                        if (usage != null) {
                            pluginCmd.getClass().getMethod("setUsage", String.class).invoke(pluginCmd, usage.toString());
                        }
                        Object permission = details.get("permission");
                        if (permission != null) {
                            pluginCmd.getClass().getMethod("setPermission", String.class).invoke(pluginCmd, permission.toString());
                        }
                        Object permissionMsg = details.get("permission-message");
                        if (permissionMsg != null) {
                            pluginCmd.getClass().getMethod("setPermissionMessage", String.class).invoke(pluginCmd, permissionMsg.toString());
                        }
                        Object aliases = details.get("aliases");
                        if (aliases instanceof List<?> aliasList) {
                            List<String> strAliases = new ArrayList<>();
                            for (Object a : aliasList) {
                                if (a != null) strAliases.add(a.toString());
                            }
                            pluginCmd.getClass().getMethod("setAliases", List.class).invoke(pluginCmd, strAliases);
                        } else if (aliases instanceof String singleAlias) {
                            pluginCmd.getClass().getMethod("setAliases", List.class).invoke(pluginCmd, List.of(singleAlias));
                        }
                    }
                    registerMethod.invoke(commandMap, pluginName, pluginCmd);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to register plugin commands", t);
        }
    }

    private void syncServerCommands() {
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object serverObj = bukkitClass.getMethod("getServer").invoke(null);
            try {
                Method syncCommands = serverObj.getClass().getMethod("syncCommands");
                syncCommands.invoke(serverObj);
            } catch (Throwable ignored) {}

            try {
                Method getOnlinePlayers = bukkitClass.getMethod("getOnlinePlayers");
                java.util.Collection<?> players = (java.util.Collection<?>) getOnlinePlayers.invoke(null);
                if (players != null) {
                    for (Object player : players) {
                        try {
                            Method updateCommands = player.getClass().getMethod("updateCommands");
                            updateCommands.invoke(player);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private void dispatchServerTask(CraftPanelPlugin plugin, Runnable task) {
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Method getGlobal = bukkitClass.getMethod("getGlobalRegionScheduler");
            Object scheduler = getGlobal.invoke(null);
            Method execute = scheduler.getClass().getMethod("execute", org.bukkit.plugin.Plugin.class, Runnable.class);
            execute.invoke(scheduler, plugin, task);
        } catch (Throwable t) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    public File getPluginsDir(MinecraftServer server) {
        return new File(server.serverDir(), "plugins");
    }
}
