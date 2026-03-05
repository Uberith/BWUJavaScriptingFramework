package com.botwithus.bot.cli.gui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class TerminalOutputPane extends JScrollPane {

    private static final int MAX_LINES = 10_000;

    private final JTextPane textPane;
    private final StyledDocument doc;
    private boolean autoScroll = true;

    /** Handle returned by insertProgressBar() — used to replace the bar with final content. */
    public static class ProgressHandle {
        final Position startPos;
        final int length;
        ProgressHandle(Position startPos, int length) {
            this.startPos = startPos;
            this.length = length;
        }
    }

    public TerminalOutputPane() {
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(GuiTheme.BG);
        textPane.setCaretColor(GuiTheme.TEXT);
        textPane.setSelectionColor(GuiTheme.SELECTION);
        textPane.setSelectedTextColor(GuiTheme.TEXT);
        textPane.setFont(GuiTheme.monoFont(14));
        textPane.setMargin(new Insets(8, 12, 8, 12));

        doc = textPane.getStyledDocument();

        setViewportView(textPane);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_AS_NEEDED);
        setBorder(null);
        getViewport().setBackground(GuiTheme.BG);

        // Track whether user has scrolled up
        getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                JScrollBar sb = getVerticalScrollBar();
                autoScroll = sb.getValue() + sb.getVisibleAmount() >= sb.getMaximum() - 20;
            }
        });
    }

    /** Append styled text. Thread-safe — marshals to EDT if needed. */
    public void append(String text, AttributeSet attrs) {
        Runnable task = () -> {
            try {
                doc.insertString(doc.getLength(), text, attrs);
                pruneIfNeeded();
                if (autoScroll) {
                    textPane.setCaretPosition(doc.getLength());
                }
            } catch (BadLocationException ignored) {}
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    /** Append an inline image. Thread-safe — scaling runs on the calling thread to avoid EDT freeze. */
    public void appendImage(BufferedImage img) {
        // Scale on the calling thread (background executor), NOT on EDT
        int maxWidth = 800;
        BufferedImage scaled = img;
        if (img.getWidth() > maxWidth) {
            double ratio = (double) maxWidth / img.getWidth();
            int newW = maxWidth;
            int newH = (int) (img.getHeight() * ratio);
            scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, newW, newH, null);
            g.dispose();
        }

        // Only the lightweight document insertion runs on EDT
        final BufferedImage finalImage = scaled;
        Runnable task = () -> {
            try {
                Style iconStyle = doc.addStyle("icon", null);
                StyleConstants.setIcon(iconStyle, new ImageIcon(finalImage));
                doc.insertString(doc.getLength(), " ", iconStyle);
                doc.insertString(doc.getLength(), "\n", defaultStyle());
                if (autoScroll) {
                    textPane.setCaretPosition(doc.getLength());
                }
            } catch (BadLocationException ignored) {}
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    /**
     * Insert an indeterminate progress bar into the output. Returns a handle
     * that can be passed to replaceProgressWithImage() or replaceProgressWithText().
     * Thread-safe — blocks briefly until the EDT has inserted the component.
     */
    public ProgressHandle insertProgressBar(String label) {
        AtomicReference<ProgressHandle> ref = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Runnable task = () -> {
            try {
                // Label text before the bar
                SimpleAttributeSet labelAttrs = new SimpleAttributeSet();
                StyleConstants.setForeground(labelAttrs, GuiTheme.DIM_TEXT);
                StyleConstants.setFontFamily(labelAttrs, GuiTheme.monoFont(14).getFamily());
                StyleConstants.setFontSize(labelAttrs, 14);

                int startOffset = doc.getLength();
                doc.insertString(doc.getLength(), "  " + label + " ", labelAttrs);

                // Create themed progress bar
                JProgressBar bar = new JProgressBar();
                bar.setIndeterminate(true);
                bar.setPreferredSize(new Dimension(250, 16));
                bar.setMaximumSize(new Dimension(250, 16));
                bar.setBackground(GuiTheme.INPUT_BG);
                bar.setForeground(GuiTheme.ACCENT);
                bar.setBorderPainted(false);

                Style compStyle = doc.addStyle("progress", null);
                StyleConstants.setComponent(compStyle, bar);
                doc.insertString(doc.getLength(), " ", compStyle);
                doc.insertString(doc.getLength(), "\n", defaultStyle());

                Position pos = doc.createPosition(startOffset);
                int length = doc.getLength() - startOffset;
                ref.set(new ProgressHandle(pos, length));

                if (autoScroll) {
                    textPane.setCaretPosition(doc.getLength());
                }
            } catch (BadLocationException ignored) {}
            latch.countDown();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
            try { latch.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        return ref.get();
    }

    /**
     * Replace the progress bar (identified by handle) with an inline image.
     * Image scaling runs on the calling thread; only document ops run on EDT.
     */
    public void replaceProgressWithImage(ProgressHandle handle, BufferedImage img) {
        if (handle == null) {
            appendImage(img);
            return;
        }

        // Scale off-EDT
        int maxWidth = 800;
        BufferedImage scaled = img;
        if (img.getWidth() > maxWidth) {
            double ratio = (double) maxWidth / img.getWidth();
            int newW = maxWidth;
            int newH = (int) (img.getHeight() * ratio);
            scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, newW, newH, null);
            g.dispose();
        }

        final BufferedImage finalImage = scaled;
        Runnable task = () -> {
            try {
                int offset = handle.startPos.getOffset();
                int len = Math.min(handle.length, doc.getLength() - offset);
                if (len > 0) {
                    doc.remove(offset, len);
                }

                Style iconStyle = doc.addStyle("icon", null);
                StyleConstants.setIcon(iconStyle, new ImageIcon(finalImage));
                doc.insertString(offset, " ", iconStyle);
                doc.insertString(offset + 1, "\n", defaultStyle());

                if (autoScroll) {
                    textPane.setCaretPosition(doc.getLength());
                }
            } catch (BadLocationException ignored) {}
        };

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    /** Replace the progress bar with a text message (e.g. on failure). */
    public void replaceProgressWithText(ProgressHandle handle, String text, Color color) {
        if (handle == null) return;
        Runnable task = () -> {
            try {
                int offset = handle.startPos.getOffset();
                int len = Math.min(handle.length, doc.getLength() - offset);
                if (len > 0) {
                    doc.remove(offset, len);
                }

                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setForeground(attrs, color);
                StyleConstants.setFontFamily(attrs, GuiTheme.monoFont(14).getFamily());
                StyleConstants.setFontSize(attrs, 14);
                doc.insertString(offset, text + "\n", attrs);

                if (autoScroll) {
                    textPane.setCaretPosition(doc.getLength());
                }
            } catch (BadLocationException ignored) {}
        };

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    /** Clear all content. Thread-safe. */
    public void clear() {
        Runnable task = () -> {
            try {
                doc.remove(0, doc.getLength());
            } catch (BadLocationException ignored) {}
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void pruneIfNeeded() {
        Element root = doc.getDefaultRootElement();
        int lines = root.getElementCount();
        if (lines > MAX_LINES) {
            int removeUpTo = root.getElement(lines - MAX_LINES).getStartOffset();
            try {
                doc.remove(0, removeUpTo);
            } catch (BadLocationException ignored) {}
        }
    }

    private SimpleAttributeSet defaultStyle() {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, GuiTheme.TEXT);
        return attrs;
    }
}
