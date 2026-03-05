package com.botwithus.bot.cli.log;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

public class LogCapture {

    private final PrintStream originalOut;
    private final PrintStream originalErr;
    private final LogBuffer logBuffer;

    public LogCapture(LogBuffer logBuffer) {
        this.logBuffer = logBuffer;
        this.originalOut = System.out;
        this.originalErr = System.err;
    }

    /** Constructor for GUI mode: uses custom PrintStreams instead of System.out/err. */
    public LogCapture(LogBuffer logBuffer, PrintStream customOut, PrintStream customErr) {
        this.logBuffer = logBuffer;
        this.originalOut = customOut;
        this.originalErr = customErr;
    }

    public void install() {
        System.setOut(new CapturingPrintStream(originalOut, logBuffer, "stdout", "INFO"));
        System.setErr(new CapturingPrintStream(originalErr, logBuffer, "stderr", "ERROR"));
    }

    public void restore() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    public PrintStream getOriginalOut() {
        return originalOut;
    }

    public PrintStream getOriginalErr() {
        return originalErr;
    }

    private static class CapturingPrintStream extends PrintStream {
        private final LogBuffer logBuffer;
        private final String source;
        private final String level;

        CapturingPrintStream(PrintStream original, LogBuffer logBuffer, String source, String level) {
            super(new TeeOutputStream(original, logBuffer, source, level));
            this.logBuffer = logBuffer;
            this.source = source;
            this.level = level;
        }
    }

    private static class TeeOutputStream extends OutputStream {
        private final PrintStream original;
        private final LogBuffer logBuffer;
        private final String source;
        private final String level;
        private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

        TeeOutputStream(PrintStream original, LogBuffer logBuffer, String source, String level) {
            this.original = original;
            this.logBuffer = logBuffer;
            this.source = source;
            this.level = level;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                flushLine();
            } else {
                lineBuffer.write(b);
            }
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            for (int i = off; i < off + len; i++) {
                write(buf[i]);
            }
        }

        @Override
        public void flush() {
            original.flush();
        }

        private void flushLine() {
            String line = lineBuffer.toString();
            lineBuffer.reset();
            if (!line.isEmpty()) {
                logBuffer.add(new LogEntry(source, level, line));
            }
            original.println(line);
        }
    }
}
