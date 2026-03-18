package com.botwithus.bot.cli.gui;

import com.botwithus.bot.api.BotScript;
import com.botwithus.bot.api.ScriptManifest;
import com.botwithus.bot.cli.CliContext;
import com.botwithus.bot.cli.Connection;
import com.botwithus.bot.core.runtime.ScriptProfiler;
import com.botwithus.bot.core.runtime.ScriptRunner;
import com.botwithus.bot.core.runtime.ScriptRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Scripts management panel — list scripts, start/stop/restart, configure, reload.
 */
public class ScriptsPanel implements GuiPanel {

    private static final Logger log = LoggerFactory.getLogger(ScriptsPanel.class);
    private final ExecutorService executor;
    private final ImBoolean autoStartOnReload = new ImBoolean(false);
    private final ImInt selectedConnection = new ImInt(0);
    private final Set<String> attachInFlight = ConcurrentHashMap.newKeySet();

    public ScriptsPanel(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public String title() {
        return "Scripts";
    }

    @Override
    public void render(CliContext ctx) {
        // Top controls
        if (ImGui.button("Reload Scripts")) {
            boolean startAfter = autoStartOnReload.get();
            executor.submit(() -> reloadScripts(ctx, startAfter));
        }
        ImGui.sameLine();
        ImGui.checkbox("Auto-start", autoStartOnReload);

        ImGui.sameLine(0, 20);
        boolean watcherRunning = ctx.isWatcherRunning();
        if (watcherRunning) {
            if (ImGui.button("Stop Watcher")) {
                ctx.stopScriptWatcher();
            }
            ImGui.sameLine();
            ImGui.textColored(ImGuiTheme.YELLOW_R, ImGuiTheme.YELLOW_G, ImGuiTheme.YELLOW_B, 1f, "Watching...");
        } else {
            if (ImGui.button("Start Watcher")) {
                ctx.startScriptWatcher();
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Connection selector if multiple connections
        var connections = new ArrayList<>(ctx.getConnections());
        List<CliContext.DiscoveredScript> discoveredScripts = ctx.getAllDiscoveredScripts();
        if (connections.isEmpty()) {
            if (discoveredScripts.isEmpty()) {
                ImGui.textColored(ImGuiTheme.DIM_TEXT_R, ImGuiTheme.DIM_TEXT_G, ImGuiTheme.DIM_TEXT_B, 1f,
                        "No active connections. Reload scripts to discover them, then connect to register them.");
            } else {
                ImGui.textColored(ImGuiTheme.DIM_TEXT_R, ImGuiTheme.DIM_TEXT_G, ImGuiTheme.DIM_TEXT_B, 1f,
                        "No active connections. These scripts were discovered from disk but are not attached yet.");
                ImGui.spacing();
                renderDiscoveredScriptsTable(discoveredScripts);
            }
            return;
        }

        if (connections.size() > 1) {
            String[] connNames = connections.stream().map(Connection::getName).toArray(String[]::new);
            ImGui.text("Connection:");
            ImGui.sameLine();
            ImGui.pushItemWidth(200);
            if (selectedConnection.get() >= connNames.length) {
                selectedConnection.set(0);
            }
            ImGui.combo("##connSelector", selectedConnection, connNames);
            ImGui.popItemWidth();
            ImGui.spacing();
        } else {
            selectedConnection.set(0);
        }

        Connection conn = connections.get(selectedConnection.get());
        ScriptRuntime runtime = conn.getRuntime();
        List<ScriptRunner> runners = new ArrayList<>(runtime.getRunners());
        runners.sort(Comparator.comparing(ScriptRunner::getScriptName, String.CASE_INSENSITIVE_ORDER));

        if (conn.isAlive() && !discoveredScripts.isEmpty() && runners.isEmpty()) {
            String connName = conn.getName();
            if (attachInFlight.add(connName)) {
                log.info("ScriptsPanel scheduling auto-attach for '{}'. DiscoveredScripts={}, currentRunners=0",
                        connName, discoveredScripts.stream().map(CliContext.DiscoveredScript::name).toList());
                executor.submit(() -> {
                    try {
                        int attached = ctx.registerAvailableScripts(conn);
                        log.info("ScriptsPanel auto-attach finished for '{}'. AttachedNow={}, runtimeRunnersAfterAttach={}",
                                connName, attached, conn.getRuntime().getRunners().stream().map(ScriptRunner::getScriptName).toList());
                    } finally {
                        attachInFlight.remove(connName);
                    }
                });
            }
        }

        if (runners.isEmpty()) {
            if (discoveredScripts.isEmpty()) {
                ImGui.textColored(ImGuiTheme.DIM_TEXT_R, ImGuiTheme.DIM_TEXT_G, ImGuiTheme.DIM_TEXT_B, 1f,
                        "No scripts loaded on " + conn.getName() + ". Click 'Reload Scripts' to discover scripts.");
            } else {
                ImGui.textColored(ImGuiTheme.DIM_TEXT_R, ImGuiTheme.DIM_TEXT_G, ImGuiTheme.DIM_TEXT_B, 1f,
                        "No scripts are currently registered on " + conn.getName() + ".");
                ImGui.textColored(ImGuiTheme.DIM_TEXT_R, ImGuiTheme.DIM_TEXT_G, ImGuiTheme.DIM_TEXT_B, 1f,
                        "Discovered scripts are listed below. Attaching them to this connection now.");
                ImGui.spacing();
                renderDiscoveredScriptsTable(discoveredScripts);
            }
            return;
        }

        // Scripts table
        int flags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp;
        if (ImGui.beginTable("scriptsTable", 7, flags)) {
            ImGui.tableSetupColumn("#", 0, 0.3f);
            ImGui.tableSetupColumn("Name", 0, 1.5f);
            ImGui.tableSetupColumn("Author", 0, 0.8f);
            ImGui.tableSetupColumn("Version", 0, 0.5f);
            ImGui.tableSetupColumn("Status", 0, 0.6f);
            ImGui.tableSetupColumn("Avg Loop", 0, 0.6f);
            ImGui.tableSetupColumn("Actions", 0, 1.5f);
            ImGui.tableHeadersRow();

            for (int i = 0; i < runners.size(); i++) {
                ScriptRunner runner = runners.get(i);
                ScriptManifest manifest = runner.getManifest();
                ScriptProfiler profiler = runner.getProfiler();

                ImGui.tableNextRow();

                ImGui.tableSetColumnIndex(0);
                ImGui.text(String.valueOf(i + 1));

                ImGui.tableSetColumnIndex(1);
                ImGui.text(runner.getScriptName());

                ImGui.tableSetColumnIndex(2);
                ImGui.text(manifest != null && !manifest.author().isEmpty() ? manifest.author() : "-");

                ImGui.tableSetColumnIndex(3);
                ImGui.text(manifest != null ? manifest.version() : "?");

                ImGui.tableSetColumnIndex(4);
                if (runner.isRunning()) {
                    ImGui.textColored(ImGuiTheme.GREEN_R, ImGuiTheme.GREEN_G, ImGuiTheme.GREEN_B, 1f, "RUNNING");
                } else {
                    ImGui.textColored(ImGuiTheme.RED_R, ImGuiTheme.RED_G, ImGuiTheme.RED_B, 1f, "STOPPED");
                }

                ImGui.tableSetColumnIndex(5);
                if (profiler.getLoopCount() > 0) {
                    ImGui.text(String.format("%.1fms", profiler.avgLoopMs()));
                } else {
                    ImGui.textColored(ImGuiTheme.DIM_TEXT_R, ImGuiTheme.DIM_TEXT_G, ImGuiTheme.DIM_TEXT_B, 1f, "-");
                }

                ImGui.tableSetColumnIndex(6);
                ImGui.pushID("script_actions_" + i);

                if (runner.isRunning()) {
                    if (ImGui.smallButton("Stop")) {
                        runner.stop();
                    }
                    ImGui.sameLine();
                    if (ImGui.smallButton("Restart")) {
                        executor.submit(() -> {
                            runner.stop();
                            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                            runner.start();
                        });
                    }
                } else {
                    if (ImGui.smallButton("Start")) {
                        runner.start();
                    }
                }

                // Config button — show for ConfigField-based or custom ImGui UI scripts
                var configFields = runner.getConfigFields();
                boolean hasConfig = (configFields != null && !configFields.isEmpty()) || runner.getScript().getUI() != null;
                if (hasConfig) {
                    ImGui.sameLine();
                    if (ImGui.smallButton("Config")) {
                        ctx.openConfigPanel(runner);
                    }
                }

                ImGui.popID();
            }

            ImGui.endTable();
        }
    }

    private void reloadScripts(CliContext ctx, boolean autoStart) {
        try {
            log.info("ScriptsPanel reload requested. autoStart={}, connections={}",
                    autoStart, ctx.getConnections().stream().map(Connection::getName).toList());
            List<BotScript> scripts = ctx.loadScripts();
            List<BotScript> blueprints = ctx.loadBlueprints();
            int discoveredCount = scripts.size() + blueprints.size();
            log.info("ScriptsPanel reload discovered {} script(s): local={}, blueprints={}",
                    discoveredCount, scripts.size(), blueprints.size());

            List<Connection> liveConnections = ctx.getConnections().stream()
                    .filter(Connection::isAlive)
                    .toList();

            if (liveConnections.isEmpty()) {
                log.warn("ScriptsPanel reload found no live connections. Discovered {} script(s) but attached none.",
                        discoveredCount);
                ctx.out().println("Discovered " + discoveredCount
                        + " script(s), but there is no live connection to register them on yet.");
                return;
            }

            for (Connection conn : liveConnections) {
                ScriptRuntime runtime = conn.getRuntime();
                log.info("Reloading scripts on '{}'. RunnersBeforeStop={}",
                        conn.getName(), runtime.getRunners().stream().map(ScriptRunner::getScriptName).toList());
                runtime.stopAll();
                log.info("Runtime '{}' cleared. RunnersAfterStop={}", conn.getName(), runtime.getRunners().size());
                for (BotScript script : scripts) {
                    log.info("Reload attaching script '{}' ({}) to '{}'.",
                            getScriptName(script), script.getClass().getName(), conn.getName());
                    runtime.registerScript(script);
                }
                for (BotScript bp : blueprints) {
                    log.info("Reload attaching blueprint '{}' ({}) to '{}'.",
                            getScriptName(bp), bp.getClass().getName(), conn.getName());
                    runtime.registerScript(bp);
                }
                log.info("Reload complete on '{}'. Runtime runners now={}",
                        conn.getName(), runtime.getRunners().stream().map(ScriptRunner::getScriptName).toList());
                ctx.out().println("Reloaded " + discoveredCount + " script(s) on " + conn.getName());

                if (autoStart) {
                    for (ScriptRunner runner : runtime.getRunners()) {
                        runner.start();
                    }
                    ctx.out().println("Auto-started all scripts on " + conn.getName());
                }
            }
        } catch (Exception e) {
            log.error("ScriptsPanel reload failed: {}", e.getMessage(), e);
            ctx.err().println("Script reload failed: " + e.getMessage());
        }
    }

    private void renderDiscoveredScriptsTable(List<CliContext.DiscoveredScript> scripts) {
        int flags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp;
        if (ImGui.beginTable("discoveredScriptsTable", 5, flags)) {
            ImGui.tableSetupColumn("Name", 0, 1.5f);
            ImGui.tableSetupColumn("Author", 0, 0.8f);
            ImGui.tableSetupColumn("Version", 0, 0.5f);
            ImGui.tableSetupColumn("Source", 0, 0.6f);
            ImGui.tableSetupColumn("Class", 0, 2.2f);
            ImGui.tableHeadersRow();

            for (CliContext.DiscoveredScript script : scripts) {
                ImGui.tableNextRow();

                ImGui.tableSetColumnIndex(0);
                ImGui.text(script.name());

                ImGui.tableSetColumnIndex(1);
                ImGui.text(script.author());

                ImGui.tableSetColumnIndex(2);
                ImGui.text(script.version());

                ImGui.tableSetColumnIndex(3);
                ImGui.text(script.source());

                ImGui.tableSetColumnIndex(4);
                ImGui.text(script.className());
            }

            ImGui.endTable();
        }
    }

    private static String getScriptName(BotScript script) {
        ScriptManifest manifest = script.getClass().getAnnotation(ScriptManifest.class);
        return manifest != null ? manifest.name() : script.getClass().getSimpleName();
    }
}
