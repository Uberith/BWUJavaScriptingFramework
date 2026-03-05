package com.botwithus.bot.cli.gui;

import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * A PrintStream that parses ANSI escape codes and renders styled text
 * into a TerminalOutputPane.
 */
public class AnsiStyledPrintStream extends PrintStream {

    public AnsiStyledPrintStream(TerminalOutputPane outputPane) {
        super(new AnsiParsingOutputStream(outputPane), true, StandardCharsets.UTF_8);
    }

    private static class AnsiParsingOutputStream extends OutputStream {

        private enum State { NORMAL, ESCAPE, CSI }

        private final TerminalOutputPane outputPane;
        private final ByteArrayOutputStream textBuffer = new ByteArrayOutputStream();
        private final StringBuilder csiParams = new StringBuilder();

        private State state = State.NORMAL;
        private Color foreground = GuiTheme.TEXT;
        private boolean bold = false;
        private boolean dim = false;

        AnsiParsingOutputStream(TerminalOutputPane outputPane) {
            this.outputPane = outputPane;
        }

        @Override
        public void write(int b) {
            switch (state) {
                case NORMAL -> {
                    if (b == 0x1B) {
                        flushText();
                        state = State.ESCAPE;
                    } else {
                        textBuffer.write(b);
                    }
                }
                case ESCAPE -> {
                    if (b == '[') {
                        state = State.CSI;
                        csiParams.setLength(0);
                    } else {
                        // Not a CSI sequence, ignore the escape
                        state = State.NORMAL;
                    }
                }
                case CSI -> {
                    if (b >= 0x30 && b <= 0x3F) {
                        // Parameter bytes: 0-9, ;, etc.
                        csiParams.append((char) b);
                    } else if (b >= 0x40 && b <= 0x7E) {
                        // Final byte — process the sequence
                        processCsi((char) b);
                        state = State.NORMAL;
                    } else {
                        // Unexpected byte, abort sequence
                        state = State.NORMAL;
                    }
                }
            }
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            for (int i = off; i < off + len; i++) {
                write(buf[i] & 0xFF);
            }
        }

        @Override
        public void flush() {
            flushText();
        }

        private void flushText() {
            if (textBuffer.size() == 0) return;
            String text = textBuffer.toString(StandardCharsets.UTF_8);
            textBuffer.reset();

            SimpleAttributeSet attrs = new SimpleAttributeSet();
            Color fg = foreground;
            if (dim) {
                fg = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 150);
            }
            StyleConstants.setForeground(attrs, fg);
            if (bold) {
                StyleConstants.setBold(attrs, true);
            }
            StyleConstants.setFontFamily(attrs, GuiTheme.monoFont(14).getFamily());
            StyleConstants.setFontSize(attrs, 14);

            outputPane.append(text, attrs);
        }

        private void processCsi(char finalByte) {
            switch (finalByte) {
                case 'm' -> processSgr();
                case 'J' -> {
                    // Clear screen: ESC[2J
                    String param = csiParams.toString();
                    if ("2".equals(param)) {
                        outputPane.clear();
                    }
                }
                case 'H' -> {
                    // Cursor home — ignore in our terminal
                }
                default -> {
                    // Ignore other CSI sequences
                }
            }
        }

        private void processSgr() {
            String params = csiParams.toString();
            if (params.isEmpty()) {
                resetAttributes();
                return;
            }

            String[] codes = params.split(";");
            for (String code : codes) {
                int n;
                try {
                    n = Integer.parseInt(code);
                } catch (NumberFormatException e) {
                    continue;
                }

                switch (n) {
                    case 0 -> resetAttributes();
                    case 1 -> bold = true;
                    case 2 -> dim = true;
                    case 22 -> { bold = false; dim = false; }
                    default -> {
                        if (n >= 30 && n <= 37) {
                            foreground = GuiTheme.ansiColor(n);
                        } else if (n >= 90 && n <= 97) {
                            // Bright colors — map to same as normal
                            foreground = GuiTheme.ansiColor(n - 60);
                        } else if (n == 39) {
                            foreground = GuiTheme.TEXT;
                        }
                    }
                }
            }
        }

        private void resetAttributes() {
            foreground = GuiTheme.TEXT;
            bold = false;
            dim = false;
        }
    }
}
