package com.botwithus.bot.core.runtime;

import com.botwithus.bot.api.BotScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Discovers BotScript implementations from local JAR files in a scripts directory.
 * Each JAR is a Java module that {@code provides BotScript with ...}.
 * Loaded into child module layers so ServiceLoader can find them.
 */
public final class LocalScriptLoader {

    private static final Logger log = LoggerFactory.getLogger(LocalScriptLoader.class);
    private static final String SCRIPTS_DIR_NAME = "scripts";
    private static final String SCRIPTS_DIR_PROPERTY = "botwithus.scripts.dir";

    /** Track parent classloaders from previous loads so we can close them on reload. */
    private static final List<URLClassLoader> previousLoaders = new ArrayList<>();

    private LocalScriptLoader() {}

    /**
     * Loads all BotScript providers from JARs in the default {@code scripts/} directory.
     */
    public static List<BotScript> loadScripts() {
        return loadScripts(resolveScriptsDir());
    }

    /**
     * Resolves the scripts directory. Checks (in order):
     * 1. System property {@code botwithus.scripts.dir}
     * 2. {@code scripts/} relative to the working directory or its parents
     */
    static Path resolveScriptsDir() {
        String override = System.getProperty(SCRIPTS_DIR_PROPERTY);
        if (override != null) {
            return Path.of(override);
        }

        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 3; i++) {
            Path candidate = dir.resolve(SCRIPTS_DIR_NAME);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }

        return Path.of(SCRIPTS_DIR_NAME);
    }

    /**
     * Loads all BotScript providers from JARs in the given directory.
     */
    public static List<BotScript> loadScripts(Path scriptsDir) {
        if (!Files.isDirectory(scriptsDir)) {
            log.info("Scripts directory not found: {}", scriptsDir.toAbsolutePath());
            log.info("Creating it - drop script JARs there and restart.");
            try {
                Files.createDirectories(scriptsDir);
            } catch (IOException e) {
                log.error("Failed to create scripts directory: {}", e.getMessage());
            }
            return List.of();
        }

        closePreviousLoaders();

        List<Path> jars;
        try (var stream = Files.list(scriptsDir)) {
            jars = stream
                    .filter(path -> path.toString().endsWith(".jar"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error("Failed to scan scripts directory: {}", e.getMessage());
            return List.of();
        }

        if (jars.isEmpty()) {
            log.info("No JARs found in {}", scriptsDir.toAbsolutePath());
            return List.of();
        }

        log.info("Found {} JAR(s) in {}", jars.size(), scriptsDir.toAbsolutePath());

        ModuleFinder finder = ModuleFinder.of(jars.toArray(Path[]::new));
        List<ModuleReference> scriptModules = finder.findAll().stream()
                .filter(LocalScriptLoader::providesBotScript)
                .sorted(Comparator.comparing(ref -> ref.descriptor().name()))
                .toList();

        if (scriptModules.isEmpty()) {
            log.info("No BotScript provider modules found in {}", scriptsDir.toAbsolutePath());
            return List.of();
        }

        Module coreModule = LocalScriptLoader.class.getModule();
        if (coreModule.isNamed()) {
            boolean declaresUses = coreModule.getDescriptor().uses()
                    .contains(BotScript.class.getName());
            if (!declaresUses) {
                log.error("FATAL: Module '{}' is missing 'uses com.botwithus.bot.api.BotScript;' in module-info.java", coreModule.getName());
                log.error("ServiceLoader will not discover any scripts without it.");
                return List.of();
            }
        }

        ModuleLayer bootLayer = ModuleLayer.boot();
        URL[] jarUrls = jars.stream()
                .map(LocalScriptLoader::toUrl)
                .filter(Objects::nonNull)
                .toArray(URL[]::new);
        List<BotScript> allScripts = new ArrayList<>();

        for (ModuleReference ref : scriptModules) {
            String moduleName = ref.descriptor().name();

            try {
                Configuration cfg = bootLayer.configuration().resolve(
                        finder, ModuleFinder.of(), Collections.singleton(moduleName));
                URLClassLoader parentLoader = new URLClassLoader(jarUrls, ClassLoader.getSystemClassLoader());
                previousLoaders.add(parentLoader);
                ModuleLayer layer = bootLayer.defineModulesWithOneLoader(cfg, parentLoader);

                ServiceLoader<BotScript> loader = ServiceLoader.load(layer, BotScript.class);
                int countBefore = allScripts.size();
                for (BotScript script : loader) {
                    allScripts.add(script);
                    log.info("Loaded: {}", script.getClass().getName());
                }

                if (allScripts.size() == countBefore) {
                    log.warn("Module '{}' declares 'provides BotScript' but ServiceLoader found 0 implementations.", moduleName);
                }
            } catch (FindException | ResolutionException e) {
                log.error("Failed to load script module '{}': {}", moduleName, e.getMessage());
            } catch (Exception e) {
                log.error("Failed to load script module '{}': {}", moduleName, e.getMessage(), e);
            }
        }

        return allScripts;
    }

    private static boolean providesBotScript(ModuleReference ref) {
        return ref.descriptor().provides().stream()
                .anyMatch(provides -> provides.service().equals(BotScript.class.getName()));
    }

    private static URL toUrl(Path jar) {
        try {
            return jar.toUri().toURL();
        } catch (IOException e) {
            log.warn("Failed to convert JAR path to URL: {}", jar, e);
            return null;
        }
    }

    /**
     * Closes all classloaders from previous loads, releasing JAR file handles.
     * This is necessary on Windows where open file handles prevent re-reading JARs.
     */
    private static void closePreviousLoaders() {
        for (URLClassLoader loader : previousLoaders) {
            try {
                loader.close();
            } catch (IOException e) {
                log.error("Failed to close previous classloader: {}", e.getMessage());
            }
        }
        previousLoaders.clear();
    }
}
