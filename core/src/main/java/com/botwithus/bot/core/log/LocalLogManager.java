package com.botwithus.bot.core.log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Mirrors stdout/stderr into log files under {@code ~/.botwithus/logs/}.
 */
public final class LocalLogManager implements AutoCloseable {

    private static final DateTimeFormatter SESSION_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static volatile LocalLogManager installed;

    private final Path baseDir;
    private final Path logDir;
    private final Path stdoutFile;
    private final Path stderrFile;
    private final OutputStream stdoutSink;
    private final OutputStream stderrSink;
    private final Object stdoutLock = new Object();
    private final Object stderrLock = new Object();
    private volatile boolean closed;

    private LocalLogManager(Path baseDir, String sessionPrefix, String sessionId) throws IOException {
        this.baseDir = baseDir;
        this.logDir = baseDir.resolve("logs");
        Files.createDirectories(logDir);

        String safePrefix = sanitize(sessionPrefix);
        this.stdoutFile = logDir.resolve(safePrefix + "-" + sessionId + ".out.log");
        this.stderrFile = logDir.resolve(safePrefix + "-" + sessionId + ".err.log");
        this.stdoutSink = openLogFile(stdoutFile);
        this.stderrSink = openLogFile(stderrFile);
    }

    public static synchronized LocalLogManager install(String sessionPrefix) {
        if (installed != null) {
            return installed;
        }

        try {
            LocalLogManager manager = create(defaultBaseDir(), sessionPrefix);
            System.setOut(manager.wrapStdout(System.out));
            System.setErr(manager.wrapStderr(System.err));
            Runtime.getRuntime().addShutdownHook(new Thread(manager::closeQuietly, "jbot-local-log-shutdown"));
            installed = manager;
            return manager;
        } catch (IOException e) {
            System.err.println("[LocalLogManager] Failed to initialize local logs: " + e.getMessage());
            return null;
        }
    }

    public static LocalLogManager getInstalled() {
        return installed;
    }

    public static LocalLogManager create(Path baseDir, String sessionPrefix) throws IOException {
        String sessionId = SESSION_FORMAT.format(LocalDateTime.now());
        return new LocalLogManager(baseDir, sessionPrefix, sessionId);
    }

    public static Path defaultBaseDir() {
        return Path.of(System.getProperty("user.home"), ".botwithus");
    }

    public PrintStream wrapStdout(PrintStream delegate) {
        return wrap(delegate, stdoutSink, stdoutLock);
    }

    public PrintStream wrapStderr(PrintStream delegate) {
        return wrap(delegate, stderrSink, stderrLock);
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public Path getLogDir() {
        return logDir;
    }

    public Path getStdoutFile() {
        return stdoutFile;
    }

    public Path getStderrFile() {
        return stderrFile;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeSink(stdoutSink);
        closeSink(stderrSink);
    }

    private void closeQuietly() {
        try {
            close();
        } catch (Exception ignored) {
        }
    }

    private static OutputStream openLogFile(Path file) throws IOException {
        return Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static PrintStream wrap(PrintStream delegate, OutputStream sink, Object lock) {
        Objects.requireNonNull(delegate, "delegate");
        return new PrintStream(new TeeOutputStream(delegate, sink, lock), true, StandardCharsets.UTF_8);
    }

    private static String sanitize(String sessionPrefix) {
        String safe = sessionPrefix == null ? "" : sessionPrefix.replaceAll("[^a-zA-Z0-9_\\-.]", "-");
        return safe.isBlank() ? "jbot" : safe;
    }

    private static void closeSink(OutputStream sink) {
        try {
            sink.flush();
            sink.close();
        } catch (IOException ignored) {
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final PrintStream delegate;
        private final OutputStream sink;
        private final Object lock;

        private TeeOutputStream(PrintStream delegate, OutputStream sink, Object lock) {
            this.delegate = delegate;
            this.sink = sink;
            this.lock = lock;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            synchronized (lock) {
                sink.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            synchronized (lock) {
                sink.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
            synchronized (lock) {
                sink.flush();
            }
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
