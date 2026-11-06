package br.com.craftpanel.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Closes what the server left open on a plugin jar. A plugin that fails before it is registered (bad main class,
 * exception in its constructor, missing dependency) never appears in the plugin manager, yet Paper keeps its
 * {@code JarFile} and class loader alive for the rest of the run, which on Windows makes the file undeletable.
 *
 * <p>Everything here reaches into server internals that change between versions, so each lookup may find nothing.
 * The caller must still handle a jar that stays locked.
 */
final class PluginJarReleaser {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginJarReleaser.class);

    private PluginJarReleaser() {}

    static void releaseIfPresent(File jar) {
        if (jar.exists()) {
            release(jar);
        }
    }

    static void release(File jar) {
        Path target = jar.toPath().toAbsolutePath().normalize();
        closePaperProviders(target);
        closeClassLoaders(target);
        System.gc();
    }

    // LaunchEntryPointHandler keeps every provider it discovered at startup, and each provider holds its JarFile.
    private static void closePaperProviders(Path target) {
        try {
            Class<?> handlerClass = Class.forName("io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler");
            Object handler = handlerClass.getField("INSTANCE").get(null);
            Map<?, ?> storage = (Map<?, ?>) handlerClass.getMethod("getStorage").invoke(handler);
            for (Object providerStorage : storage.values()) {
                Method registered = providerStorage.getClass().getMethod("getRegisteredProviders");
                registered.setAccessible(true);
                for (Object provider : (Iterable<?>) registered.invoke(providerStorage)) {
                    closeProviderJar(provider, target);
                }
            }
        } catch (ClassNotFoundException notPaper) {
            // Spigot/CraftBukkit have no provider storage; their class loaders are handled below.
        } catch (Throwable t) {
            LOGGER.debug("Could not inspect Paper's plugin providers", t);
        }
    }

    private static void closeProviderJar(Object provider, Path target) {
        try {
            Method getSource = provider.getClass().getMethod("getSource");
            getSource.setAccessible(true);
            Path source = (Path) getSource.invoke(provider);
            if (source != null && source.toAbsolutePath().normalize().equals(target)) {
                Method file = provider.getClass().getMethod("file");
                file.setAccessible(true);
                if (file.invoke(provider) instanceof Closeable jar) {
                    jar.close();
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Could not close a plugin provider's jar", t);
        }
    }

    private static void closeClassLoaders(Path target) {
        List<Object> loaders = new ArrayList<>();
        addPaperGlobalLoaders(loaders);
        addBukkitLoaders(loaders);
        for (Object loader : loaders) {
            try {
                Field fileField = loader.getClass().getDeclaredField("file");
                fileField.setAccessible(true);
                if (fileField.get(loader) instanceof File file
                        && file.toPath().toAbsolutePath().normalize().equals(target)
                        && loader instanceof Closeable closeable) {
                    closeable.close();
                }
            } catch (Throwable t) {
                LOGGER.debug("Could not close a plugin class loader", t);
            }
        }
    }

    private static void addPaperGlobalLoaders(List<Object> loaders) {
        try {
            Class<?> storageClass = Class.forName("io.papermc.paper.plugin.provider.classloader.PaperClassLoaderStorage");
            Object storage = storageClass.getMethod("instance").invoke(null);
            Object group = storage.getClass().getMethod("getGlobalGroup").invoke(storage);
            Method getLoaders = group.getClass().getMethod("getClassLoaders");
            getLoaders.setAccessible(true);
            loaders.addAll((List<?>) getLoaders.invoke(group));
        } catch (Throwable ignored) {
            // Not Paper, or a version with a different layout.
        }
    }

    private static void addBukkitLoaders(List<Object> loaders) {
        try {
            Object server = Class.forName("org.bukkit.Bukkit").getMethod("getServer").invoke(null);
            Object manager = server.getClass().getMethod("getPluginManager").invoke(server);
            Field fileAssociations = manager.getClass().getDeclaredField("fileAssociations");
            fileAssociations.setAccessible(true);
            for (Object pluginLoader : ((Map<?, ?>) fileAssociations.get(manager)).values()) {
                Field loadersField = pluginLoader.getClass().getDeclaredField("loaders");
                loadersField.setAccessible(true);
                loaders.addAll((List<?>) loadersField.get(pluginLoader));
            }
        } catch (Throwable ignored) {
            // Field layout differs between Bukkit forks.
        }
    }
}
