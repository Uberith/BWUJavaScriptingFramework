package com.botwithus.bot.cli.gui;

import com.botwithus.bot.cli.CliContext;
import com.botwithus.bot.cli.command.Command;
import com.botwithus.bot.cli.command.CommandParser;
import com.botwithus.bot.cli.command.CommandRegistry;
import com.botwithus.bot.cli.command.ParsedCommand;
import com.botwithus.bot.cli.command.impl.*;
import com.botwithus.bot.cli.log.LogBuffer;
import com.botwithus.bot.cli.log.LogCapture;
import com.botwithus.bot.cli.output.AnsiCodes;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JBotGui extends JFrame {

    private static final String BANNER = """

               _ ____        _      ____ _     ___
              | | __ )  ___ | |_   / ___| |   |_ _|
           _  | |  _ \\ / _ \\| __| | |   | |    | |
          | |_| | |_) | (_) | |_  | |___| |___ | |
           \\___/|____/ \\___/ \\__|  \\____|_____|___|
                  BotWithUs Script Manager

              Type 'help' for available commands.
            """;

    private final TerminalOutputPane outputPane;
    private final InputPanel inputPanel;
    private final CliContext ctx;
    private final CommandRegistry registry;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jbot-cmd");
        t.setDaemon(true);
        return t;
    });

    private final PrintStream guiOut;
    private final PrintStream guiErr;

    public JBotGui() {
        super("JBot — disconnected");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(900, 650);
        setMinimumSize(new Dimension(600, 400));
        setLocationRelativeTo(null);

        // Dark window background
        getContentPane().setBackground(GuiTheme.BG);

        // Output pane
        outputPane = new TerminalOutputPane();

        // ANSI-aware print streams that render into the output pane
        guiOut = new AnsiStyledPrintStream(outputPane);
        guiErr = new AnsiStyledPrintStream(outputPane);

        // Wire log capture with GUI streams
        LogBuffer logBuffer = new LogBuffer();
        LogCapture logCapture = new LogCapture(logBuffer, guiOut, guiErr);
        logCapture.install();

        // CLI context and commands
        ctx = new CliContext(logBuffer, logCapture);
        registry = new CommandRegistry();
        registry.register(new HelpCommand(registry));
        registry.register(new ConnectCommand());
        registry.register(new ScriptsCommand());
        registry.register(new LogsCommand());
        registry.register(new ReloadCommand());
        registry.register(new ScreenshotCommand());
        registry.register(new ClearCommand());
        registry.register(new ExitCommand());

        // Image display hook for inline screenshots
        ctx.setImageDisplay(image -> outputPane.appendImage(image));

        // Progress display hook — embeds a live progress bar, then swaps for image/error
        ctx.setProgressDisplay(new CliContext.ProgressDisplay() {
            @Override
            public Object start(String label) {
                return outputPane.insertProgressBar(label);
            }

            @Override
            public void completeWithImage(Object handle, java.awt.image.BufferedImage image) {
                outputPane.replaceProgressWithImage((TerminalOutputPane.ProgressHandle) handle, image);
            }

            @Override
            public void completeWithError(Object handle, String message) {
                outputPane.replaceProgressWithText(
                        (TerminalOutputPane.ProgressHandle) handle, message, GuiTheme.RED);
            }
        });

        // Input panel
        inputPanel = new InputPanel(this::handleCommand);
        inputPanel.setRegistry(registry);

        // Layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(GuiTheme.BG);
        mainPanel.add(outputPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        // Window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        // Print banner
        guiOut.println(AnsiCodes.colorize(BANNER, AnsiCodes.CYAN));
        updatePromptAndTitle();
    }

    private void handleCommand(String line) {
        // Echo input in accent color
        SimpleAttributeSet echoAttrs = new SimpleAttributeSet();
        StyleConstants.setForeground(echoAttrs, GuiTheme.ACCENT);
        StyleConstants.setFontFamily(echoAttrs, GuiTheme.monoFont(14).getFamily());
        StyleConstants.setFontSize(echoAttrs, 14);
        outputPane.append("> " + line + "\n", echoAttrs);

        executor.submit(() -> {
            ParsedCommand parsed = CommandParser.parse(line);
            Command cmd = registry.resolve(parsed.name());

            if (cmd == null) {
                guiOut.println("Unknown command: " + parsed.name() + ". Type 'help' for available commands.");
                return;
            }

            // Handle exit
            if (cmd instanceof ExitCommand) {
                SwingUtilities.invokeLater(this::shutdown);
                return;
            }

            try {
                cmd.execute(parsed, ctx);
            } catch (Exception e) {
                guiOut.println("Error: " + e.getMessage());
            }

            SwingUtilities.invokeLater(this::updatePromptAndTitle);
        });
    }

    private void updatePromptAndTitle() {
        boolean connected = ctx.hasActiveConnection();
        String connName = ctx.getActiveConnectionName();
        int count = ctx.getConnections().size();

        inputPanel.updatePrompt(connName, connected, count);

        if (connected) {
            String suffix = count > 1 ? " [" + count + "]" : "";
            setTitle("JBot — " + connName + suffix);
        } else {
            setTitle("JBot — disconnected");
        }
    }

    private void shutdown() {
        ctx.disconnectAll();
        executor.shutdownNow();
        dispose();
        System.exit(0);
    }

    public static void main(String[] args) {
        // Set UI defaults for dark theme
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        UIManager.put("Panel.background", GuiTheme.BG);
        UIManager.put("TextPane.background", GuiTheme.BG);
        UIManager.put("ScrollPane.background", GuiTheme.BG);
        UIManager.put("ScrollBar.background", GuiTheme.BG);
        UIManager.put("ScrollBar.thumb", GuiTheme.SELECTION);
        UIManager.put("ScrollBar.track", GuiTheme.BG);

        SwingUtilities.invokeLater(() -> {
            JBotGui gui = new JBotGui();
            gui.setVisible(true);
            gui.inputPanel.focusInput();
        });
    }
}
