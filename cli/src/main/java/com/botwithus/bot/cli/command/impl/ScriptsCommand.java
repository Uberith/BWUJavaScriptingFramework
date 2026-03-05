package com.botwithus.bot.cli.command.impl;

import com.botwithus.bot.api.ScriptManifest;
import com.botwithus.bot.cli.CliContext;
import com.botwithus.bot.cli.command.Command;
import com.botwithus.bot.cli.command.ParsedCommand;
import com.botwithus.bot.cli.output.AnsiCodes;
import com.botwithus.bot.cli.output.TableFormatter;
import com.botwithus.bot.core.runtime.ScriptRunner;
import com.botwithus.bot.core.runtime.ScriptRuntime;

import java.util.List;

public class ScriptsCommand implements Command {

    @Override public String name() { return "scripts"; }
    @Override public List<String> aliases() { return List.of("s"); }
    @Override public String description() { return "Manage scripts on active connection"; }
    @Override public String usage() { return "scripts [list|start <name>|stop <name>|restart <name>|info <name>]"; }

    @Override
    public void execute(ParsedCommand parsed, CliContext ctx) {
        ScriptRuntime runtime = ctx.getRuntime();
        if (runtime == null) {
            ctx.out().println("No active connection. Use 'connect' first.");
            return;
        }

        String sub = parsed.arg(0);
        if (sub == null || sub.equals("list")) {
            listScripts(runtime, ctx);
        } else if (sub.equals("start")) {
            startScript(parsed.arg(1), runtime, ctx);
        } else if (sub.equals("stop")) {
            stopScript(parsed.arg(1), runtime, ctx);
        } else if (sub.equals("restart")) {
            restartScript(parsed.arg(1), runtime, ctx);
        } else if (sub.equals("info")) {
            infoScript(parsed.arg(1), runtime, ctx);
        } else {
            ctx.out().println("Unknown subcommand: " + sub + ". Use: list, start, stop, restart, info");
        }
    }

    private void listScripts(ScriptRuntime runtime, CliContext ctx) {
        List<ScriptRunner> runners = runtime.getRunners();
        if (runners.isEmpty()) {
            ctx.out().println("No scripts loaded. Use 'reload' to discover scripts.");
            return;
        }

        TableFormatter table = new TableFormatter().headers("#", "Name", "Version", "Status");
        int i = 1;
        for (ScriptRunner runner : runners) {
            ScriptManifest m = runner.getManifest();
            String version = m != null ? m.version() : "?";
            String status = runner.isRunning()
                    ? AnsiCodes.colorize("RUNNING", AnsiCodes.GREEN)
                    : AnsiCodes.colorize("STOPPED", AnsiCodes.RED);
            table.row(String.valueOf(i++), runner.getScriptName(), version, status);
        }
        ctx.out().print(table.build());
    }

    private void startScript(String name, ScriptRuntime runtime, CliContext ctx) {
        if (name == null) {
            ctx.out().println("Usage: scripts start <name>");
            return;
        }
        ScriptRunner runner = runtime.findRunner(name);
        if (runner == null) {
            ctx.out().println("Script not found: " + name);
            return;
        }
        if (runner.isRunning()) {
            ctx.out().println("Script already running: " + name);
            return;
        }
        runner.start();
        ctx.out().println("Started: " + runner.getScriptName());
    }

    private void stopScript(String name, ScriptRuntime runtime, CliContext ctx) {
        if (name == null) {
            ctx.out().println("Usage: scripts stop <name>");
            return;
        }
        if (runtime.stopScript(name)) {
            ctx.out().println("Stopped: " + name);
        } else {
            ctx.out().println("Script not found or not running: " + name);
        }
    }

    private void restartScript(String name, ScriptRuntime runtime, CliContext ctx) {
        if (name == null) {
            ctx.out().println("Usage: scripts restart <name>");
            return;
        }
        ScriptRunner runner = runtime.findRunner(name);
        if (runner == null) {
            ctx.out().println("Script not found: " + name);
            return;
        }
        if (runner.isRunning()) {
            runner.stop();
        }
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        runner.start();
        ctx.out().println("Restarted: " + runner.getScriptName());
    }

    private void infoScript(String name, ScriptRuntime runtime, CliContext ctx) {
        if (name == null) {
            ctx.out().println("Usage: scripts info <name>");
            return;
        }
        ScriptRunner runner = runtime.findRunner(name);
        if (runner == null) {
            ctx.out().println("Script not found: " + name);
            return;
        }
        ScriptManifest m = runner.getManifest();
        ctx.out().println("  Name:        " + runner.getScriptName());
        ctx.out().println("  Version:     " + (m != null ? m.version() : "?"));
        ctx.out().println("  Author:      " + (m != null && !m.author().isEmpty() ? m.author() : "unknown"));
        ctx.out().println("  Description: " + (m != null && !m.description().isEmpty() ? m.description() : "none"));
        ctx.out().println("  Status:      " + (runner.isRunning() ? "RUNNING" : "STOPPED"));
        ctx.out().println("  Class:       " + runner.getScript().getClass().getName());
        ctx.out().println("  Connection:  " + ctx.getActiveConnectionName());
    }
}
