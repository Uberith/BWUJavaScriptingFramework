package com.botwithus.bot.cli.command.impl;

import com.botwithus.bot.cli.CliContext;
import com.botwithus.bot.cli.command.Command;
import com.botwithus.bot.cli.command.ParsedCommand;
import com.botwithus.bot.cli.log.LogEntry;
import com.botwithus.bot.cli.output.AnsiCodes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

public class LogsCommand implements Command {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override public String name() { return "logs"; }
    @Override public List<String> aliases() { return List.of("log"); }
    @Override public String description() { return "View captured logs"; }
    @Override public String usage() { return "logs [--lines=N] [--filter=source] [--follow]"; }

    @Override
    public void execute(ParsedCommand parsed, CliContext ctx) {
        int lines = parsed.intFlag("lines", 50);
        String filter = parsed.flag("filter");
        boolean follow = parsed.hasFlag("follow");

        List<LogEntry> entries = ctx.getLogBuffer().tail(lines);
        if (filter != null) {
            entries = entries.stream()
                    .filter(e -> e.source().equalsIgnoreCase(filter) || e.message().contains(filter))
                    .toList();
        }

        for (LogEntry entry : entries) {
            ctx.out().println(formatEntry(entry));
        }

        if (follow) {
            ctx.out().println(AnsiCodes.dim("-- Follow mode. Press Enter to exit --"));
            Instant cursor = Instant.now();
            Thread follower = Thread.ofVirtual().start(() -> {
                try {
                    Instant[] lastCheck = {cursor};
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(500);
                        List<LogEntry> newEntries = ctx.getLogBuffer().since(lastCheck[0]);
                        String f = filter;
                        if (f != null) {
                            newEntries = newEntries.stream()
                                    .filter(e -> e.source().equalsIgnoreCase(f) || e.message().contains(f))
                                    .toList();
                        }
                        for (LogEntry entry : newEntries) {
                            ctx.out().println(formatEntry(entry));
                        }
                        if (!newEntries.isEmpty()) {
                            lastCheck[0] = Instant.now();
                        }
                    }
                } catch (InterruptedException ignored) {}
            });

            // Block until user presses Enter
            new Scanner(System.in).nextLine();
            follower.interrupt();
        }
    }

    private String formatEntry(LogEntry entry) {
        String time = TIME_FMT.format(entry.timestamp());
        String levelColor = "ERROR".equals(entry.level()) ? AnsiCodes.RED : AnsiCodes.CYAN;
        return AnsiCodes.dim(time) + " "
                + AnsiCodes.colorize(entry.level(), levelColor) + " "
                + AnsiCodes.colorize("[" + entry.source() + "]", AnsiCodes.YELLOW) + " "
                + entry.message();
    }
}
