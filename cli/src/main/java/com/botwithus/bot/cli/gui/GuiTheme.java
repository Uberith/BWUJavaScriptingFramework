package com.botwithus.bot.cli.gui;

import java.awt.*;

public final class GuiTheme {

    // Background & surface
    public static final Color BG = new Color(0x1a, 0x1a, 0x2e);
    public static final Color INPUT_BG = new Color(0x22, 0x22, 0x38);
    public static final Color SELECTION = new Color(0x3a, 0x3a, 0x55);

    // Text
    public static final Color TEXT = new Color(0xe0, 0xdc, 0xd0);
    public static final Color DIM_TEXT = new Color(0x88, 0x85, 0x7a);

    // Accent
    public static final Color ACCENT = new Color(0xc1, 0x5f, 0x3c);

    // ANSI colors
    public static final Color RED = new Color(0xf8, 0x71, 0x71);
    public static final Color GREEN = new Color(0x4a, 0xde, 0x80);
    public static final Color YELLOW = new Color(0xfb, 0xbf, 0x24);
    public static final Color BLUE = new Color(0x60, 0xa5, 0xfa);
    public static final Color MAGENTA = new Color(0xc0, 0x84, 0xfc);
    public static final Color CYAN = new Color(0x67, 0xe8, 0xf9);
    public static final Color WHITE = TEXT;

    private GuiTheme() {}

    /** Map SGR color code (30-37) to Color. */
    public static Color ansiColor(int code) {
        return switch (code) {
            case 30 -> BG;          // black → background
            case 31 -> RED;
            case 32 -> GREEN;
            case 33 -> YELLOW;
            case 34 -> BLUE;
            case 35 -> MAGENTA;
            case 36 -> CYAN;
            case 37 -> WHITE;
            default -> TEXT;
        };
    }

    /** Resolve the best available monospace font. */
    public static Font monoFont(int size) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] available = ge.getAvailableFontFamilyNames();
        for (String preferred : new String[]{"JetBrains Mono", "Consolas", "Courier New"}) {
            for (String name : available) {
                if (name.equalsIgnoreCase(preferred)) {
                    return new Font(name, Font.PLAIN, size);
                }
            }
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }
}
