package com.botwithus.bot.cli.command.impl;

import com.botwithus.bot.cli.CliContext;
import com.botwithus.bot.cli.Connection;
import com.botwithus.bot.cli.command.Command;
import com.botwithus.bot.cli.command.ParsedCommand;
import com.botwithus.bot.cli.output.AnsiCodes;
import com.botwithus.bot.cli.output.TableFormatter;
import com.botwithus.bot.core.pipe.PipeClient;

import java.util.List;

public class ConnectCommand implements Command {

    @Override public String name() { return "connect"; }
    @Override public List<String> aliases() { return List.of("conn"); }
    @Override public String description() { return "Manage pipe connections"; }
    @Override public String usage() { return "connect [<pipe>|scan [filter]|disconnect [name|--all]|list|use <name>|status]"; }

    @Override
    public void execute(ParsedCommand parsed, CliContext ctx) {
        String sub = parsed.arg(0);
        if (sub == null) {
            autoConnect(ctx);
        } else {
            switch (sub.toLowerCase()) {
                case "scan" -> scan(parsed.arg(1), ctx);
                case "disconnect", "dc" -> {
                    if (parsed.hasFlag("all")) {
                        ctx.disconnectAll();
                    } else {
                        ctx.disconnect(parsed.arg(1));
                    }
                }
                case "reconnect", "rc" -> {
                    String name = ctx.getActiveConnectionName();
                    ctx.disconnect(null);
                    ctx.connect(name);
                }
                case "list", "ls" -> listConnections(ctx);
                case "use" -> useConnection(parsed.arg(1), ctx);
                case "status" -> status(ctx);
                default -> ctx.connect(sub);
            }
        }
    }

    private void autoConnect(CliContext ctx) {
        List<String> pipes = PipeClient.scanPipes("BotWithUs");
        if (pipes.isEmpty()) {
            ctx.out().println("No BotWithUs pipes found. Is the client running?");
            ctx.out().println("Use 'connect scan <filter>' to search with a different filter.");
            return;
        }
        if (pipes.size() == 1) {
            ctx.connect(pipes.getFirst());
        } else {
            ctx.out().println("Found " + pipes.size() + " BotWithUs pipes:");
            TableFormatter table = new TableFormatter().headers("#", "Pipe Name");
            for (int i = 0; i < pipes.size(); i++) {
                table.row(String.valueOf(i + 1), pipes.get(i));
            }
            ctx.out().print(table.build());
            ctx.out().println("Use 'connect <pipe name>' to connect to a specific pipe.");
            ctx.out().println("Or 'connect --all' to connect to all of them.");
        }
    }

    private void scan(String filter, CliContext ctx) {
        String prefix = filter != null ? filter : "BotWithUs";
        ctx.out().println("Scanning for pipes matching '" + prefix + "'...");
        List<String> pipes = PipeClient.scanPipes(prefix);
        if (pipes.isEmpty()) {
            ctx.out().println("No matching pipes found.");
            return;
        }
        TableFormatter table = new TableFormatter().headers("#", "Pipe Name");
        for (int i = 0; i < pipes.size(); i++) {
            table.row(String.valueOf(i + 1), pipes.get(i));
        }
        ctx.out().print(table.build());
        ctx.out().println("Use 'connect <pipe name>' to connect.");
    }

    private void listConnections(CliContext ctx) {
        if (!ctx.hasConnections()) {
            ctx.out().println("No active connections.");
            return;
        }
        String active = ctx.getActiveConnectionName();
        TableFormatter table = new TableFormatter().headers("#", "Name", "Pipe", "Active");
        int i = 1;
        for (Connection conn : ctx.getConnections()) {
            String marker = conn.getName().equals(active)
                    ? AnsiCodes.colorize("*", AnsiCodes.GREEN) : "";
            table.row(String.valueOf(i++), conn.getName(), conn.getPipe().getPipePath(), marker);
        }
        ctx.out().print(table.build());
    }

    private void useConnection(String name, CliContext ctx) {
        if (name == null) {
            ctx.out().println("Usage: connect use <name>");
            return;
        }
        if (ctx.setActive(name)) {
            ctx.out().println("Active connection: " + name);
        } else {
            ctx.out().println("Connection not found: " + name);
        }
    }

    private void status(CliContext ctx) {
        if (!ctx.hasConnections()) {
            ctx.out().println("No connections.");
            return;
        }
        ctx.out().println("Connections: " + ctx.getConnections().size());
        ctx.out().println("Active: " + (ctx.getActiveConnectionName() != null ? ctx.getActiveConnectionName() : "none"));
    }
}
