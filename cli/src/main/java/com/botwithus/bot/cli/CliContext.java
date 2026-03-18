package com.botwithus.bot.cli;

import com.botwithus.bot.api.BotScript;
import com.botwithus.bot.api.ScriptManifest;
import com.botwithus.bot.api.blueprint.BlueprintGraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.botwithus.bot.cli.log.LogBuffer;
import com.botwithus.bot.cli.log.LogCapture;
import com.botwithus.bot.cli.stream.StreamManager;
import com.botwithus.bot.core.blueprint.execution.BlueprintBotScript;
import com.botwithus.bot.core.blueprint.serialization.BlueprintSerializer;
import com.botwithus.bot.core.impl.ClientImpl;
import com.botwithus.bot.core.impl.ClientProviderImpl;
import com.botwithus.bot.core.impl.EventBusImpl;
import com.botwithus.bot.core.impl.GameAPIImpl;
import com.botwithus.bot.core.impl.MessageBusImpl;
import com.botwithus.bot.core.impl.ScriptContextImpl;
import com.botwithus.bot.core.pipe.PipeClient;
import com.botwithus.bot.core.rpc.RpcClient;
import com.botwithus.bot.core.config.ScriptProfileStore;
import com.botwithus.bot.core.runtime.SDNScriptLoader;
import com.botwithus.bot.core.runtime.ScriptRuntime;

import com.botwithus.bot.core.runtime.ScriptRunner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class CliContext {

    private static final Logger log = LoggerFactory.getLogger(CliContext.class);

    public record DiscoveredScript(
            String name,
            String author,
            String version,
            String description,
            String className,
            String source
    ) {}

    @FunctionalInterface
    public interface ImageDisplay {
        void display(BufferedImage image);
    }

/** Progress indicator that can be started and completed with an image or error. */
    public interface ProgressDisplay {
        /** Show an indeterminate progress bar with a label. Returns an opaque handle. */
        Object start(String label);
        /** Replace the progress bar with an inline image. */
        void completeWithImage(Object handle, BufferedImage image);
        /** Replace the progress bar with an error message. */
        void completeWithError(Object handle, String message);
    }

    private final LogBuffer logBuffer;
    private final LogCapture logCapture;
    private final ClientProviderImpl clientProvider = new ClientProviderImpl();
    private final Map<String, Connection> connections = new LinkedHashMap<>();
    private final Map<String, ConnectionGroup> groups = new LinkedHashMap<>();
    private String activeConnectionName;
    private String mountedConnectionName;
    private ImageDisplay imageDisplay;
    private ProgressDisplay progressDisplay;
    private StreamManager streamManager;
    private Consumer<ScriptRunner> configPanelOpener;
    private com.botwithus.bot.cli.watch.ScriptWatcher scriptWatcher;
    private ScriptProfileStore profileStore;
    private AutoStartManager autoStartManager;
    private ClientManager clientManager;
    private com.botwithus.bot.core.runtime.ManagementScriptRuntime managementRuntime;
    private volatile List<DiscoveredScript> discoveredScripts = List.of();
    private volatile List<DiscoveredScript> discoveredBlueprints = List.of();

    public CliContext(LogBuffer logBuffer, LogCapture logCapture) {
        this.logBuffer = logBuffer;
        this.logCapture = logCapture;
        this.clientManager = new ClientManager(this);
    }

    public void setStreamManager(StreamManager sm) { this.streamManager = sm; }
    public StreamManager getStreamManager() { return streamManager; }

    public void setProfileStore(ScriptProfileStore store) { this.profileStore = store; }
    public ScriptProfileStore getProfileStore() { return profileStore; }

    public void setAutoStartManager(AutoStartManager manager) { this.autoStartManager = manager; }
    public AutoStartManager getAutoStartManager() { return autoStartManager; }

    public ClientManager getClientManager() { return clientManager; }

    public com.botwithus.bot.core.runtime.ManagementScriptRuntime getManagementRuntime() {
        return managementRuntime;
    }

    /**
     * Initialises the management script runtime. Call after the ClientManager
     * is ready (i.e. after construction). Uses a global MessageBus and
     * SharedState shared across all management scripts.
     */
    public void initManagementRuntime() {
        if (managementRuntime != null) return;
        var messageBus = new MessageBusImpl();
        var sharedState = new com.botwithus.bot.core.impl.SharedStateImpl();
        var mgmtContext = new com.botwithus.bot.core.impl.ManagementContextImpl(
                clientManager, clientProvider, messageBus, sharedState);
        managementRuntime = new com.botwithus.bot.core.runtime.ManagementScriptRuntime(mgmtContext);
    }

    /**
     * Loads management scripts from {@code scripts/management/} and registers
     * them in the management runtime.
     */
    public List<com.botwithus.bot.api.script.ManagementScript> loadManagementScripts() {
        if (managementRuntime == null) initManagementRuntime();
        return com.botwithus.bot.core.runtime.ManagementScriptLoader.loadScripts();
    }

    public void connect(String pipeName) {
        String connName = pipeName != null ? pipeName : "BotWithUs";
        if (connections.containsKey(connName)) {
            out().println("Already connected to '" + connName + "'. Use 'use " + connName + "' to switch.");
            return;
        }
        log.info("Connecting to '{}'. Existing connections={}, discoveredScripts={}, discoveredBlueprints={}",
                connName, connections.size(), discoveredScripts.size(), discoveredBlueprints.size());
        try {
            PipeClient pipe = pipeName != null ? new PipeClient(pipeName) : new PipeClient();
            RpcClient rpc = new RpcClient(pipe);
            rpc.setConnectionName(connName);
            EventBusImpl eventBus = new EventBusImpl();
            MessageBusImpl messageBus = new MessageBusImpl();
            GameAPIImpl gameAPI = new GameAPIImpl(rpc);
            ClientImpl client = new ClientImpl(connName, gameAPI, eventBus, pipe::isOpen);
            clientProvider.putClient(connName, client);
            ScriptContextImpl context = new ScriptContextImpl(gameAPI, eventBus, messageBus, clientProvider);

            var dispatcher = new com.botwithus.bot.core.impl.EventDispatcher(eventBus);
            dispatcher.bindAutoSubscription(gameAPI);
            rpc.setEventHandler(dispatcher::dispatch);
            rpc.start();

            ScriptRuntime runtime = new ScriptRuntime(context);
            runtime.setConnectionName(connName);

            // Wire up ScriptManager so scripts can manage other scripts
            var scriptManager = new com.botwithus.bot.core.impl.ScriptManagerImpl(runtime);
            context.setScriptManager(scriptManager);

            Connection conn = new Connection(connName, pipe, rpc, runtime);
            conn.setEventBus(eventBus);
            connections.put(connName, conn);
            activeConnectionName = connName;
            log.info("Connection '{}' created. PipeOpen={}, runtimeRunnersBeforeAttach={}",
                    connName, conn.isAlive(), runtime.getRunners().size());
            int attached = registerAvailableScripts(conn);
            log.info("Connection '{}' attach pass completed. AttachedNow={}, runtimeRunnersAfterAttach={}",
                    connName, attached, runtime.getRunners().size());
            out().println("Connected to pipe: " + pipe.getPipePath());
            if (connections.size() > 1) {
                out().println("Active connection set to '" + connName + "'.");
            }
        } catch (Exception e) {
            log.error("Connection failed for '{}': {}", connName, e.getMessage(), e);
            out().println("Connection failed: " + e.getMessage());
        }
    }

    public void disconnect(String name, boolean force) {
        String target = name != null ? name : activeConnectionName;
        if (target == null || !connections.containsKey(target)) {
            out().println(target == null ? "No active connection." : "Connection not found: " + target);
            return;
        }
        Connection conn = connections.get(target);
        if (conn.hasRunningScripts() && !force) {
            out().println("Connection '" + target + "' has running scripts. Use 'disconnect --force' to stop them and disconnect.");
            return;
        }
        // Save auto-start state before disconnecting
        if (autoStartManager != null && conn.getAccountName() != null) {
            autoStartManager.saveState(conn);
        }
        if (target.equals(mountedConnectionName)) {
            unmount();
            out().println("Auto-unmounted — mounted connection was disconnected.");
        }
        connections.remove(target);
        clientProvider.removeClient(target);
        conn.close();
        out().println("Disconnected from '" + target + "'.");

        if (target.equals(activeConnectionName)) {
            activeConnectionName = connections.isEmpty() ? null : connections.keySet().iterator().next();
            if (activeConnectionName != null) {
                out().println("Active connection switched to '" + activeConnectionName + "'.");
            }
        }
    }

    public void disconnect(String name) {
        disconnect(name, false);
    }

    public void disconnectAll(boolean force) {
        if (streamManager != null) {
            streamManager.stopAll(name -> connections.containsKey(name) ? connections.get(name) : null);
        }
        var iter = connections.entrySet().iterator();
        while (iter.hasNext()) {
            Connection conn = iter.next().getValue();
            if (conn.hasRunningScripts() && !force) {
                out().println("Skipping '" + conn.getName() + "' — has running scripts. Use --force to override.");
                continue;
            }
            conn.close();
            clientProvider.removeClient(conn.getName());
            out().println("Disconnected from '" + conn.getName() + "'.");
            iter.remove();
        }
        if (activeConnectionName != null && !connections.containsKey(activeConnectionName)) {
            activeConnectionName = connections.isEmpty() ? null : connections.keySet().iterator().next();
        }
    }

    public void disconnectAll() {
        disconnectAll(false);
    }

    public boolean setActive(String name) {
        if (!connections.containsKey(name)) return false;
        activeConnectionName = name;
        return true;
    }

    public List<BotScript> loadScripts() {
        List<BotScript> scripts = SDNScriptLoader.loadScripts();
        discoveredScripts = describeScripts(scripts, "Script");
        log.info("loadScripts discovered {} local script(s): {}",
                scripts.size(),
                discoveredScripts.stream().map(DiscoveredScript::name).toList());
        return scripts;
    }

    /**
     * Scans the {@code scripts/blueprints/} directory for {@code *.blueprint.json} files,
     * deserializes each into a {@link BlueprintGraph}, and wraps them as {@link BlueprintBotScript} instances.
     */
    public List<BotScript> loadBlueprints() {
        Path dir = Path.of("scripts", "blueprints");
        if (!Files.isDirectory(dir)) {
            discoveredBlueprints = List.of();
            return List.of();
        }
        try {
            List<BlueprintGraph> graphs = BlueprintSerializer.loadAllFromDirectory(dir);
            List<BotScript> scripts = new ArrayList<>();
            for (BlueprintGraph graph : graphs) {
                scripts.add(new BlueprintBotScript(graph));
            }
            discoveredBlueprints = describeScripts(scripts, "Blueprint");
            log.info("loadBlueprints discovered {} blueprint script(s): {}",
                    scripts.size(),
                    discoveredBlueprints.stream().map(DiscoveredScript::name).toList());
            return scripts;
        } catch (Exception e) {
            discoveredBlueprints = List.of();
            log.error("Failed to load blueprints", e);
            return List.of();
        }
    }

    public List<DiscoveredScript> getDiscoveredScripts() {
        return discoveredScripts;
    }

    public List<DiscoveredScript> getDiscoveredBlueprints() {
        return discoveredBlueprints;
    }

    public List<DiscoveredScript> getAllDiscoveredScripts() {
        List<DiscoveredScript> all = new ArrayList<>(discoveredScripts);
        all.addAll(discoveredBlueprints);
        all.sort(Comparator.comparing(DiscoveredScript::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(all);
    }

    /**
     * Called when a connection error is detected (pipe closed, RPC failure, etc.).
     * Removes the dead connection, stops its scripts, and switches to the next
     * available connection (or clears the active view).
     */
    public void handleConnectionError(String connName) {
        if (streamManager != null) {
            streamManager.handleConnectionLost(connName);
        }
        if (connName.equals(mountedConnectionName)) {
            unmount();
            out().println("Auto-unmounted — mounted connection was lost.");
        }
        Connection conn = connections.remove(connName);
        clientProvider.removeClient(connName);
        if (conn != null) {
            conn.close();
            out().println("Connection '" + connName + "' lost — removed.");
        }
        if (connName.equals(activeConnectionName)) {
            activeConnectionName = connections.isEmpty() ? null : connections.keySet().iterator().next();
            if (activeConnectionName != null) {
                out().println("Active connection switched to '" + activeConnectionName + "'.");
            }
        }
    }

    public boolean hasConnections() { return !connections.isEmpty(); }
    public boolean hasActiveConnection() { return activeConnectionName != null; }
    public String getActiveConnectionName() { return activeConnectionName; }
    public Connection getActiveConnection() { return activeConnectionName != null ? connections.get(activeConnectionName) : null; }
    public Collection<Connection> getConnections() { return connections.values(); }

    public ScriptRuntime getRuntime() {
        Connection conn = getActiveConnection();
        return conn != null ? conn.getRuntime() : null;
    }

    public LogBuffer getLogBuffer() { return logBuffer; }
    public PrintStream out() { return logCapture.getOriginalOut(); }
    public PrintStream err() { return logCapture.getOriginalErr(); }

    public void setImageDisplay(ImageDisplay d) { this.imageDisplay = d; }
    public ImageDisplay getImageDisplay() { return imageDisplay; }

    public void setProgressDisplay(ProgressDisplay d) { this.progressDisplay = d; }
    public ProgressDisplay getProgressDisplay() { return progressDisplay; }

    public void setConfigPanelOpener(Consumer<ScriptRunner> opener) { this.configPanelOpener = opener; }
    public void openConfigPanel(ScriptRunner runner) {
        if (configPanelOpener != null) configPanelOpener.accept(runner);
    }

    // --- Connection Group management & persistence ---

    private static final Path GROUPS_FILE = Path.of(System.getProperty("user.home"), ".botwithus", "groups.json");

    /** Simple DTO for JSON serialization of a group. */
    private static class GroupData {
        String description;
        List<String> members;
        GroupData() {}
        GroupData(String description, List<String> members) {
            this.description = description;
            this.members = members;
        }
    }

    /** Loads persisted groups from ~/.botwithus/groups.json. */
    public void loadGroups() {
        if (!Files.exists(GROUPS_FILE)) return;
        try {
            String json = Files.readString(GROUPS_FILE);
            Gson gson = new Gson();
            Map<String, GroupData> data = gson.fromJson(json,
                    new TypeToken<LinkedHashMap<String, GroupData>>() {}.getType());
            if (data != null) {
                groups.clear();
                for (var entry : data.entrySet()) {
                    ConnectionGroup group = new ConnectionGroup(entry.getKey());
                    GroupData gd = entry.getValue();
                    if (gd.description != null) group.setDescription(gd.description);
                    if (gd.members != null) gd.members.forEach(group::add);
                    groups.put(entry.getKey(), group);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load groups", e);
        }
    }

    /** Persists current groups to ~/.botwithus/groups.json. */
    void saveGroups() {
        try {
            Files.createDirectories(GROUPS_FILE.getParent());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Map<String, GroupData> data = new LinkedHashMap<>();
            for (var entry : groups.entrySet()) {
                ConnectionGroup g = entry.getValue();
                data.put(entry.getKey(), new GroupData(g.getDescription(), new ArrayList<>(g.getConnectionNames())));
            }
            Files.writeString(GROUPS_FILE, gson.toJson(data));
        } catch (Exception e) {
            log.error("Failed to save groups", e);
        }
    }

    public void createGroup(String name) {
        groups.put(name, new ConnectionGroup(name));
        saveGroups();
    }

    public boolean deleteGroup(String name) {
        boolean removed = groups.remove(name) != null;
        if (removed) saveGroups();
        return removed;
    }

    public ConnectionGroup getGroup(String name) {
        return groups.get(name);
    }

    public Map<String, ConnectionGroup> getGroups() {
        return Collections.unmodifiableMap(groups);
    }

    public void addToGroup(String groupName, String connectionName) {
        ConnectionGroup group = groups.get(groupName);
        if (group != null) {
            group.add(connectionName);
            saveGroups();
        }
    }

    public void removeFromGroup(String groupName, String connectionName) {
        ConnectionGroup group = groups.get(groupName);
        if (group != null) {
            group.remove(connectionName);
            saveGroups();
        }
    }

    /**
     * Returns the list of active (connected) Connection objects for a group.
     * Connections that are in the group but not currently connected are skipped.
     */
    public List<Connection> getGroupConnections(String groupName) {
        ConnectionGroup group = groups.get(groupName);
        if (group == null) return List.of();
        List<Connection> result = new ArrayList<>();
        for (String connName : group.getConnectionNames()) {
            Connection conn = connections.get(connName);
            if (conn != null && conn.isAlive()) {
                result.add(conn);
            }
        }
        return result;
    }

    public void mount(String connectionName) {
        this.mountedConnectionName = connectionName;
        logCapture.setConnectionFilter(name -> name.equals(connectionName));
    }

    public void unmount() {
        this.mountedConnectionName = null;
        logCapture.setConnectionFilter(null);
    }

    public boolean isMounted() { return mountedConnectionName != null; }
    public String getMountedConnectionName() { return mountedConnectionName; }

    public void startScriptWatcher() {
        if (scriptWatcher != null && scriptWatcher.isRunning()) return;
        java.nio.file.Path scriptsDir = java.nio.file.Path.of("scripts");
        if (!java.nio.file.Files.isDirectory(scriptsDir)) return;
        scriptWatcher = new com.botwithus.bot.cli.watch.ScriptWatcher(scriptsDir, () -> {
            out().println("[ScriptWatcher] Script files changed — reloading...");
            for (Connection conn : connections.values()) {
                if (conn.isAlive()) {
                    conn.getRuntime().stopAll();
                    List<BotScript> scripts = loadScripts();
                    for (BotScript script : scripts) {
                        conn.getRuntime().registerScript(script);
                    }
                    out().println("[ScriptWatcher] Reloaded " + scripts.size() + " script(s) on " + conn.getName());
                }
            }
        });
        scriptWatcher.start();
        out().println("Script file watcher started.");
    }

    public void stopScriptWatcher() {
        if (scriptWatcher != null) {
            scriptWatcher.stop();
            scriptWatcher = null;
            out().println("Script file watcher stopped.");
        }
    }

    public boolean isWatcherRunning() {
        return scriptWatcher != null && scriptWatcher.isRunning();
    }

    public int registerAvailableScripts(Connection conn) {
        if (conn == null || !conn.isAlive()) {
            log.warn("registerAvailableScripts skipped: connection missing or not alive.");
            return 0;
        }

        ScriptRuntime runtime = conn.getRuntime();
        log.info("registerAvailableScripts starting for '{}'. Existing runners={}",
                conn.getName(), runtime.getRunners().stream().map(ScriptRunner::getScriptName).toList());

        try {
            List<BotScript> scripts = loadScripts();
            List<BotScript> blueprints = loadBlueprints();
            Set<String> existingNames = new HashSet<>();
            for (ScriptRunner runner : runtime.getRunners()) {
                existingNames.add(runner.getScriptName());
            }

            int added = 0;
            int discoveredCount = scripts.size() + blueprints.size();
            log.info("registerAvailableScripts '{}' discovered {} script(s) total. Existing names={}",
                    conn.getName(), discoveredCount, existingNames);
            for (BotScript script : scripts) {
                String scriptName = getScriptDisplayName(script);
                if (existingNames.add(scriptName)) {
                    log.info("Attaching script '{}' ({}) to '{}'.",
                            scriptName, script.getClass().getName(), conn.getName());
                    runtime.registerScript(script);
                    added++;
                } else {
                    log.info("Skipping already-registered script '{}' on '{}'.", scriptName, conn.getName());
                }
            }
            for (BotScript blueprint : blueprints) {
                String scriptName = getScriptDisplayName(blueprint);
                if (existingNames.add(scriptName)) {
                    log.info("Attaching blueprint '{}' ({}) to '{}'.",
                            scriptName, blueprint.getClass().getName(), conn.getName());
                    runtime.registerScript(blueprint);
                    added++;
                } else {
                    log.info("Skipping already-registered blueprint '{}' on '{}'.", scriptName, conn.getName());
                }
            }

            if (added > 0) {
                log.info("Registered {} script(s) on '{}'.", added, conn.getName());
                out().println("Registered " + added + " script(s) on '" + conn.getName() + "'.");
            } else {
                log.warn("registerAvailableScripts '{}' attached 0 script(s). Runtime runners now={}",
                        conn.getName(), runtime.getRunners().stream().map(ScriptRunner::getScriptName).toList());
            }
            return added;
        } catch (Exception e) {
            log.error("Failed to register scripts on connect for {}", conn.getName(), e);
            out().println("Failed to register scripts on '" + conn.getName() + "': " + e.getMessage());
            return 0;
        }
    }

    private static List<DiscoveredScript> describeScripts(List<BotScript> scripts, String source) {
        List<DiscoveredScript> discovered = new ArrayList<>();
        for (BotScript script : scripts) {
            String name = getScriptDisplayName(script);
            String author;
            String version;
            String description;

            if (script instanceof BlueprintBotScript blueprint) {
                var metadata = blueprint.getMetadata();
                author = metadata.author() != null && !metadata.author().isBlank() ? metadata.author() : "-";
                version = metadata.version() != null && !metadata.version().isBlank() ? metadata.version() : "?";
                description = metadata.description() != null ? metadata.description() : "";
            } else {
                ScriptManifest manifest = script.getClass().getAnnotation(ScriptManifest.class);
                author = manifest != null && !manifest.author().isBlank() ? manifest.author() : "-";
                version = manifest != null && !manifest.version().isBlank() ? manifest.version() : "?";
                description = manifest != null ? manifest.description() : "";
            }

            discovered.add(new DiscoveredScript(
                    name,
                    author,
                    version,
                    description,
                    script.getClass().getName(),
                    source
            ));
        }
        discovered.sort(Comparator.comparing(DiscoveredScript::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(discovered);
    }

    private static String getScriptDisplayName(BotScript script) {
        if (script instanceof BlueprintBotScript blueprint) {
            return blueprint.getMetadata().name();
        }
        ScriptManifest manifest = script.getClass().getAnnotation(ScriptManifest.class);
        return manifest != null ? manifest.name() : script.getClass().getSimpleName();
    }
}
