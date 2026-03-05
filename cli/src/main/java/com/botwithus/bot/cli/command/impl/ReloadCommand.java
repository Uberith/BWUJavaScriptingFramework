package com.botwithus.bot.cli.command.impl;

import com.botwithus.bot.api.BotScript;
import com.botwithus.bot.cli.CliContext;
import com.botwithus.bot.cli.command.Command;
import com.botwithus.bot.cli.command.ParsedCommand;
import com.botwithus.bot.core.runtime.ScriptRuntime;

import java.util.List;

public class ReloadCommand implements Command {

    @Override public String name() { return "reload"; }
    @Override public List<String> aliases() { return List.of("rl"); }
    @Override public String description() { return "Hot-reload scripts on active connection"; }
    @Override public String usage() { return "reload [--start]"; }

    @Override
    public void execute(ParsedCommand parsed, CliContext ctx) {
        ScriptRuntime runtime = ctx.getRuntime();
        if (runtime == null) {
            ctx.out().println("No active connection. Use 'connect' first.");
            return;
        }

        boolean autoStart = parsed.hasFlag("start");

        ctx.out().println("Stopping current scripts...");
        runtime.stopAll();

        ctx.out().println("Reloading scripts from scripts/ directory...");
        List<BotScript> scripts = ctx.loadScripts();
        ctx.out().println("Discovered " + scripts.size() + " script(s).");

        if (autoStart && !scripts.isEmpty()) {
            runtime.startAll(scripts);
            ctx.out().println("Started " + scripts.size() + " script(s).");
        } else if (!scripts.isEmpty()) {
            for (BotScript script : scripts) {
                runtime.startScript(script);
            }
            runtime.stopAll();
            ctx.out().println("Scripts loaded but not started. Use 'scripts start <name>' or 'reload --start'.");
        }
    }
}
