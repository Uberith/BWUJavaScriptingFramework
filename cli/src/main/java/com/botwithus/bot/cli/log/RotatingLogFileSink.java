package com.botwithus.bot.cli.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Persistent UTF-8 log sink with simple size-based rotation.
 */
public class RotatingLogFileSink {

    private static final Logger log = LoggerFactory.getLogger(RotatingLogFileSink.class);
    private static final Path DEFAULT_LOG_DIR = Path.of(System.getProperty("user.home"), ".botwithus", "logs");
    private static final String DEFAULT_LOG_FILE = "botwithus.log";
    private static final long DEFAULT_MAX_BYTES = 5L * 1024L * 1024L;
    private static final int DEFAULT_MAX_ARCHIVES = 1;

    private final Path logFile;
    private final long maxBytes;
    private final int maxArchives;
    private BufferedWriter writer;

    public RotatingLogFileSink() {
        this(DEFAULT_LOG_DIR.resolve(DEFAULT_LOG_FILE), DEFAULT_MAX_BYTES, DEFAULT_MAX_ARCHIVES);
    }

    RotatingLogFileSink(Path logFile, long maxBytes, int maxArchives) {
        this.logFile = logFile;
        this.maxBytes = maxBytes;
        this.maxArchives = maxArchives;
    }

    public synchronized void writeLine(String line) {
        try {
            openIfNeeded();
            rotateIfNeeded(line);
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.error("Failed to write log file {}: {}", logFile, e.getMessage());
        }
    }

    public synchronized void close() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException e) {
            log.debug("Failed to close log file {}: {}", logFile, e.getMessage());
        } finally {
            writer = null;
        }
    }

    private void openIfNeeded() throws IOException {
        if (writer != null) return;
        Files.createDirectories(logFile.getParent());
        writer = Files.newBufferedWriter(
                logFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private void rotateIfNeeded(String line) throws IOException {
        long currentSize = Files.exists(logFile) ? Files.size(logFile) : 0L;
        long incomingSize = line.getBytes(StandardCharsets.UTF_8).length + System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
        if (currentSize + incomingSize <= maxBytes) return;

        close();
        deleteIfExists(rotatedFile(maxArchives));
        for (int i = maxArchives - 1; i >= 1; i--) {
            Path source = rotatedFile(i);
            if (Files.exists(source)) {
                Files.move(source, rotatedFile(i + 1), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (Files.exists(logFile)) {
            Files.move(logFile, rotatedFile(1), StandardCopyOption.REPLACE_EXISTING);
        }
        openIfNeeded();
    }

    private Path rotatedFile(int index) {
        return logFile.resolveSibling(logFile.getFileName() + "." + index);
    }

    private static void deleteIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }
}
