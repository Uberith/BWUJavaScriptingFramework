package com.botwithus.bot.cli.command;

import com.botwithus.bot.cli.CliContext;

import java.util.List;

public interface Command {

    String name();

    default List<String> aliases() {
        return List.of();
    }

    String description();

    default String usage() {
        return name();
    }

    void execute(ParsedCommand parsed, CliContext ctx);
}
