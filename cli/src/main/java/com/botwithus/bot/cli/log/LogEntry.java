package com.botwithus.bot.cli.log;

import java.time.Instant;

public record LogEntry(Instant timestamp, String source, String level, String message) {

    public LogEntry(String source, String level, String message) {
        this(Instant.now(), source, level, message);
    }
}
