package com.botwithus.bot.core.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLogManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void wrapStdout_writesToDelegateAndLogFile() throws IOException {
        Path baseDir = tempDir.resolve(".botwithus");

        try (LocalLogManager manager = LocalLogManager.create(baseDir, "test-session")) {
            ByteArrayOutputStream delegateBytes = new ByteArrayOutputStream();
            PrintStream delegate = new PrintStream(delegateBytes, true, StandardCharsets.UTF_8);
            PrintStream wrapped = manager.wrapStdout(delegate);

            wrapped.println("hello stdout");
            wrapped.flush();

            assertTrue(Files.isDirectory(baseDir.resolve("logs")));
            assertTrue(Files.exists(manager.getStdoutFile()));
            assertEquals("hello stdout" + System.lineSeparator(), delegateBytes.toString(StandardCharsets.UTF_8));
            assertEquals("hello stdout" + System.lineSeparator(),
                    Files.readString(manager.getStdoutFile(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void wrapStderr_writesToDelegateAndLogFile() throws IOException {
        Path baseDir = tempDir.resolve(".botwithus");

        try (LocalLogManager manager = LocalLogManager.create(baseDir, "test-session")) {
            ByteArrayOutputStream delegateBytes = new ByteArrayOutputStream();
            PrintStream delegate = new PrintStream(delegateBytes, true, StandardCharsets.UTF_8);
            PrintStream wrapped = manager.wrapStderr(delegate);

            wrapped.println("hello stderr");
            wrapped.flush();

            assertTrue(Files.exists(manager.getStderrFile()));
            assertEquals("hello stderr" + System.lineSeparator(), delegateBytes.toString(StandardCharsets.UTF_8));
            assertEquals("hello stderr" + System.lineSeparator(),
                    Files.readString(manager.getStderrFile(), StandardCharsets.UTF_8));
        }
    }
}
