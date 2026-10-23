package br.com.craftpanel.plugins;

import br.com.craftpanel.CraftPanelPlugin;
import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.monitoring.PluginCpuProfiler;
import br.com.craftpanel.servers.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
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

    /** Bare file name of an uploaded or downloaded plugin jar; rejects anything that is not a plain .jar name. */
    public static String sanitizeJarName(String filename) {
        String name = filename == null ? "" : filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1).trim();
        if (name.length() <= 4 || !name.toLowerCase().endsWith(".jar") || name.matches(".*[<>:\"|?*\\x00-\\x1F].*")) {
            throw new IllegalArgumentException("Only .jar plugin files can be installed");
        }
        return name;
    }

    // Request file names must name a jar directly inside the plugins folder, never "../server.properties".
    private File resolvePluginFile(MinecraftServer server, String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        boolean plainName = filename != null && !filename.isBlank() && !filename.matches(".*[/\\\\:\\x00-\\x1F].*");
        if (!plainName || !(lower.endsWith(".jar") || lower.endsWith(".jar.disabled"))) {
            throw new IllegalArgumentException("Invalid plugin file name: " + filename);
        }
        return new File(getPluginsDir(server), filename);
    }

    public void togglePlugin(MinecraftServer server, String filename) throws IOException {
        File pluginsDir = getPluginsDir(server);
        File file = resolvePluginFile(server, filename);

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
        File file = resolvePluginFile(server, filename);
        if (!file.exists()) {
            return false;
        }
        return loadPluginAtRuntime(file);
    }

    public void deletePlugin(MinecraftServer server, String filename) throws IOException {
        deletePlugin(server, filename, false);
    }

    public void deletePlugin(MinecraftServer server, String filename, boolean deleteConfig) throws IOException {
        if (filename != null && (filename.toLowerCase().contains("craftpanel") || filename.equalsIgnoreCase("craftpanel.jar"))) {
            throw new IllegalArgumentException("CraftPanel is the core system plugin and cannot be deleted.");
        }

        File file = resolvePluginFile(server, filename);

        if (!file.exists()) {
            return;
        }

        InstalledPlugin meta = JarMetadataExtractor.extract(file);
        String pluginName = meta != null ? meta.name() : filename.replace(".jar", "");
        if ("craftpanel".equalsIgnoreCase(pluginName)) {
            throw new IllegalArgumentException("CraftPanel is the core system plugin and cannot be deleted.");
        }

        unloadPluginAtRuntime(pluginName);

        try {
            backupPluginFile(server, file.toPath());
        } catch (Throwable t) {
            LOGGER.warn("Could not back up {} before deleting it: {}", filename, t.toString());
        }

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
        // The name comes from the jar's plugin.yml: "name: .." would otherwise wipe the whole server folder.
        try {
            if (!configDir.getCanonicalFile().getParentFile().equals(pluginsDir.getCanonicalFile())) {
                LOGGER.warn("Refusing to delete config folder outside plugins/: {}", pluginName);
                return;
            }
        } catch (IOException e) {
            return;
        }
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
        Path pluginsDir = getPluginsDir(server).toPath();
        Files.createDirectories(pluginsDir);

        String safeName = sanitizeJarName(filename);
        Path target = pluginsDir.resolve(safeName);
        Path staged = pluginsDir.resolve(safeName + ".part");
        try {
            try (in) {
                Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
            }
            replacePluginJar(server, staged, target);
        } finally {
            Files.deleteIfExists(staged);
        }

        loadPluginAtRuntime(target.toFile());
        return JarMetadataExtractor.extract(target.toFile());
    }

    public boolean loadPluginAtRuntime(File file) {
        if (file == null || !file.exists() || !file.getName().toLowerCase().endsWith(".jar")) {
            return false;
        }

        InstalledPlugin meta = JarMetadataExtractor.extract(file);
        String pluginName = meta != null ? meta.name() : null;

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

                    if (pluginName != null && !pluginName.isBlank()) {
                        Method getPlugin = pm.getClass().getMethod("getPlugin", String.class);
                        Object existing = getPlugin.invoke(pm, pluginName);
                        if (existing != null) {
                            unloadPluginInternal(existing, pluginName);
                        }
                    }

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

                        rebindPluginCommands(loaded);
                        PluginCpuProfiler.getInstance().registerPlugin((org.bukkit.plugin.Plugin) loaded);
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

                    unloadPluginInternal(target, name);
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

    private void unloadPluginInternal(Object target, String name) {
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object pm = bukkitClass.getMethod("getPluginManager").invoke(null);
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

            unregisterFromPaper(target);

            try {
                Object serverObj = bukkitClass.getMethod("getServer").invoke(null);
                Method getCommandMap = serverObj.getClass().getMethod("getCommandMap");
                Object commandMap = getCommandMap.invoke(serverObj);
                if (commandMap != null) {
                    Map<String, Object> knownCommands = getKnownCommandsMap(commandMap);
                    if (knownCommands != null) {
                        ClassLoader cl = target.getClass().getClassLoader();
                    Class<?> picClass = null;
                    try {
                        picClass = Class.forName("org.bukkit.command.PluginIdentifiableCommand");
                    } catch (Throwable ignored) {}
                    final Class<?> finalPicClass = picClass;

                    List<Object> removedCmds = new ArrayList<>();
                    Iterator<? extends Map.Entry<?, ?>> it = knownCommands.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<?, ?> entry = it.next();
                        Object cmd = entry.getValue();
                        if (cmd == null) continue;

                        boolean match = false;
                        if (cmd.getClass().getClassLoader() == cl) {
                            match = true;
                        } else if (finalPicClass != null && finalPicClass.isInstance(cmd)) {
                            try {
                                Method getPluginMethod = cmd.getClass().getMethod("getPlugin");
                                Object owning = getPluginMethod.invoke(cmd);
                                if (owning == target || (owning != null && name.equalsIgnoreCase(owning.getClass().getMethod("getName").invoke(owning).toString()))) {
                                    match = true;
                                }
                            } catch (Throwable ignored) {}
                        } else {
                            try {
                                Method getPluginMethod = cmd.getClass().getMethod("getPlugin");
                                Object owning = getPluginMethod.invoke(cmd);
                                if (owning == target) {
                                    match = true;
                                }
                            } catch (Throwable ignored) {}
                        }

                        if (match) {
                            removedCmds.add(cmd);
                            it.remove();
                        }
                    }

                    Class<?> cmdMapClass = Class.forName("org.bukkit.command.CommandMap");
                    for (Object cmd : removedCmds) {
                        try {
                            cmd.getClass().getMethod("unregister", cmdMapClass).invoke(cmd, commandMap);
                        } catch (Throwable ignored) {}
                    }
                }
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
            syncServerCommands();
        } catch (Throwable t) {
            LOGGER.error("Error during internal unload of " + name, t);
        }
    }

    // Paper keeps its own plugin registry (PaperPluginInstanceManager). Leaving the old instance there makes Paper
    // refuse the new jar as a duplicate, and the failed load keeps that jar locked on Windows.
    private void unregisterFromPaper(Object target) {
        try {
            Class<?> managerClass = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            Field instanceField = managerClass.getDeclaredField("instanceManager");
            instanceField.setAccessible(true);
            Object instanceManager = instanceField.get(manager);

            Field pluginsField = instanceManager.getClass().getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            ((List<?>) pluginsField.get(instanceManager)).remove(target);

            Field namesField = instanceManager.getClass().getDeclaredField("lookupNames");
            namesField.setAccessible(true);
            ((Map<?, ?>) namesField.get(instanceManager)).values().removeIf(p -> p == target);

            Field treeField = instanceManager.getClass().getDeclaredField("dependencyTree");
            treeField.setAccessible(true);
            Object tree = treeField.get(instanceManager);
            Object meta = target.getClass().getMethod("getPluginMeta").invoke(target);
            for (Method m : tree.getClass().getMethods()) {
                if (m.getName().equals("remove") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(meta)) {
                    m.invoke(tree, meta);
                    break;
                }
            }
        } catch (ClassNotFoundException notPaper) {
            // Spigot/CraftBukkit: the SimplePluginManager fields cleared above are the only registry.
        } catch (Throwable t) {
            LOGGER.warn("Could not fully unregister plugin from Paper's plugin manager: {}", t.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getKnownCommandsMap(Object commandMap) {
        if (commandMap == null) {
            return null;
        }
        try {
            Method m = commandMap.getClass().getMethod("getKnownCommands");
            m.setAccessible(true);
            Object res = m.invoke(commandMap);
            if (res instanceof Map) {
                return (Map<String, Object>) res;
            }
        } catch (Throwable ignored) {}

        Class<?> curr = commandMap.getClass();
        while (curr != null && curr != Object.class) {
            try {
                Field f = curr.getDeclaredField("knownCommands");
                f.setAccessible(true);
                Object res = f.get(commandMap);
                if (res instanceof Map) {
                    return (Map<String, Object>) res;
                }
            } catch (Throwable ignored) {
                curr = curr.getSuperclass();
            }
        }
        return null;
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

            Map<String, Object> knownCommands = getKnownCommandsMap(commandMap);
            if (knownCommands == null) {
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

            Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin");
            Class<?> pluginCommandClass = Class.forName("org.bukkit.command.PluginCommand");
            Class<?> executorClass = Class.forName("org.bukkit.command.CommandExecutor");
            Class<?> tabCompleterClass = Class.forName("org.bukkit.command.TabCompleter");
            Class<?> cmdMapClass = Class.forName("org.bukkit.command.CommandMap");

            Constructor<?> ctor = pluginCommandClass.getDeclaredConstructor(String.class, pluginClass);
            ctor.setAccessible(true);

            Method registerMethod = commandMap.getClass().getMethod("register", String.class, Class.forName("org.bukkit.command.Command"));

            for (Map.Entry<String, Map<String, Object>> entry : commands.entrySet()) {
                String cmdName = entry.getKey();
                if (cmdName == null || cmdName.isBlank()) {
                    continue;
                }

                String lower = cmdName.toLowerCase();
                String namespaced = pluginName.toLowerCase() + ":" + lower;

                Object existing = knownCommands.remove(lower);
                if (existing != null) {
                    try {
                        existing.getClass().getMethod("unregister", cmdMapClass).invoke(existing, commandMap);
                    } catch (Throwable ignored) {}
                }
                Object existingNs = knownCommands.remove(namespaced);
                if (existingNs != null) {
                    try {
                        existingNs.getClass().getMethod("unregister", cmdMapClass).invoke(existingNs, commandMap);
                    } catch (Throwable ignored) {}
                }

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
                            if (a != null) {
                                String aliasStr = a.toString();
                                strAliases.add(aliasStr);
                                knownCommands.remove(aliasStr.toLowerCase());
                                knownCommands.remove(pluginName.toLowerCase() + ":" + aliasStr.toLowerCase());
                            }
                        }
                        pluginCmd.getClass().getMethod("setAliases", List.class).invoke(pluginCmd, strAliases);
                    } else if (aliases instanceof String singleAlias) {
                        pluginCmd.getClass().getMethod("setAliases", List.class).invoke(pluginCmd, List.of(singleAlias));
                        knownCommands.remove(singleAlias.toLowerCase());
                        knownCommands.remove(pluginName.toLowerCase() + ":" + singleAlias.toLowerCase());
                    }
                }

                if (executorClass.isInstance(pluginObj)) {
                    try {
                        pluginCmd.getClass().getMethod("setExecutor", executorClass).invoke(pluginCmd, pluginObj);
                    } catch (Throwable ignored) {}
                }
                if (tabCompleterClass.isInstance(pluginObj)) {
                    try {
                        pluginCmd.getClass().getMethod("setTabCompleter", tabCompleterClass).invoke(pluginCmd, pluginObj);
                    } catch (Throwable ignored) {}
                }

                registerMethod.invoke(commandMap, pluginName, pluginCmd);
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to register plugin commands", t);
        }
    }

    @SuppressWarnings("unchecked")
    private void rebindPluginCommands(Object pluginObj) {
        if (pluginObj == null) {
            return;
        }
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object serverObj = bukkitClass.getMethod("getServer").invoke(null);
            Object commandMap = serverObj.getClass().getMethod("getCommandMap").invoke(serverObj);
            if (commandMap == null) return;

            Method getDesc = pluginObj.getClass().getMethod("getDescription");
            Object desc = getDesc.invoke(pluginObj);
            Method getCommands = desc.getClass().getMethod("getCommands");
            Map<String, Map<String, Object>> commands = (Map<String, Map<String, Object>>) getCommands.invoke(desc);
            if (commands == null || commands.isEmpty()) return;

            Method getCommand = commandMap.getClass().getMethod("getCommand", String.class);
            Class<?> executorClass = Class.forName("org.bukkit.command.CommandExecutor");
            Class<?> tabCompleterClass = Class.forName("org.bukkit.command.TabCompleter");

            for (String cmdName : commands.keySet()) {
                if (cmdName == null || cmdName.isBlank()) continue;
                Object cmd = getCommand.invoke(commandMap, cmdName);
                if (cmd != null) {
                    try {
                        Method getExec = cmd.getClass().getMethod("getExecutor");
                        Object exec = getExec.invoke(cmd);
                        if (exec == null && executorClass.isInstance(pluginObj)) {
                            cmd.getClass().getMethod("setExecutor", executorClass).invoke(cmd, pluginObj);
                        }
                    } catch (Throwable ignored) {}
                    try {
                        Method getComp = cmd.getClass().getMethod("getTabCompleter");
                        Object comp = getComp.invoke(cmd);
                        if (comp == null && tabCompleterClass.isInstance(pluginObj)) {
                            cmd.getClass().getMethod("setTabCompleter", tabCompleterClass).invoke(cmd, pluginObj);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
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
        // Inside Bukkit, the folder holding CraftPanel.jar is the real plugins directory (honours --plugins).
        try {
            CraftPanelPlugin plugin = CraftPanelPlugin.getInstance();
            if (plugin != null && plugin.getDataFolder().getParentFile() != null) {
                return plugin.getDataFolder().getAbsoluteFile().getParentFile();
            }
        } catch (Throwable ignored) {}
        return new File(server.serverDir(), "plugins");
    }

    public Path backupPluginFile(MinecraftServer server, Path file) throws IOException {
        Path backupDir = Paths.get(config.storage().backupDirectory(), "plugins", server.id());
        Files.createDirectories(backupDir);
        Path backupFile = backupDir.resolve(file.getFileName().toString() + "." + System.currentTimeMillis() + ".bak");
        Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
        return backupFile;
    }

    /**
     * Moves a downloaded jar into place as {@code target}. Every other jar declaring the same plugin name is
     * backed up and removed (Bukkit refuses to boot two jars with one name), and the running copy is unloaded
     * first because Windows keeps a loaded jar locked. Returns the file names that were removed.
     */
    public List<String> replacePluginJar(MinecraftServer server, Path stagedJar, Path target) throws IOException {
        String pluginName = JarMetadataExtractor.extract(stagedJar.toFile()).name();
        boolean touchesSelf = "craftpanel".equalsIgnoreCase(pluginName)
                || (Files.exists(target) && "craftpanel".equalsIgnoreCase(JarMetadataExtractor.extract(target.toFile()).name()));
        if (touchesSelf) {
            throw new IllegalArgumentException("CraftPanel cannot replace itself while running.");
        }

        unloadPluginAtRuntime(pluginName);

        List<String> removed = new ArrayList<>();
        File[] jars = target.getParent().toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                Path jarPath = jar.toPath();
                boolean isTarget = jarPath.equals(target);
                if (!isTarget && !pluginName.equalsIgnoreCase(JarMetadataExtractor.extract(jar).name())) {
                    continue;
                }
                backupPluginFile(server, jarPath);
                if (!isTarget) {
                    retryIo(() -> Files.deleteIfExists(jarPath));
                    removed.add(jar.getName());
                }
            }
        }

        try {
            retryIo(() -> Files.move(stagedJar, target, StandardCopyOption.REPLACE_EXISTING));
        } catch (IOException locked) {
            // Still held open by the server: hand it to Bukkit's update folder, applied before plugins load on restart.
            Path updateDir = getUpdateFolder(target.getParent());
            Files.createDirectories(updateDir);
            Files.move(stagedJar, updateDir.resolve(target.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("O arquivo " + target.getFileName() + " está em uso pelo servidor. A nova versão foi salva em "
                    + target.getParent().relativize(updateDir) + "/ e será aplicada na próxima reinicialização.", locked);
        }
        return removed;
    }

    private Path getUpdateFolder(Path pluginsDir) {
        try {
            Object folder = Class.forName("org.bukkit.Bukkit").getMethod("getUpdateFolderFile").invoke(null);
            if (folder instanceof File file) {
                return file.getAbsoluteFile().toPath();
            }
        } catch (Throwable ignored) {}
        return pluginsDir.resolve("update");
    }

    private interface IoAction {
        void run() throws IOException;
    }

    // A jar released by an unloaded class loader can stay locked on Windows until the next GC.
    private static void retryIo(IoAction action) throws IOException {
        IOException last = null;
        for (int i = 0; i < 5; i++) {
            try {
                action.run();
                return;
            } catch (IOException e) {
                last = e;
                System.gc();
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last;
    }
}
