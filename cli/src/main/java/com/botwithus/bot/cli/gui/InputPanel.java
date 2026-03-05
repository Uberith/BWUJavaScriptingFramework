package com.botwithus.bot.cli.gui;

import com.botwithus.bot.cli.command.CommandRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class InputPanel extends JPanel {

    private final JLabel promptLabel;
    private final JTextField inputField;
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private String savedInput = "";
    private CommandRegistry registry;

    public InputPanel(Consumer<String> onCommand) {
        setLayout(new BorderLayout(6, 0));
        setBackground(GuiTheme.INPUT_BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, GuiTheme.SELECTION),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        promptLabel = new JLabel();
        promptLabel.setFont(GuiTheme.monoFont(14));
        updatePrompt(null, false, 0, null);

        inputField = new JTextField();
        inputField.setFont(GuiTheme.monoFont(14));
        inputField.setBackground(GuiTheme.INPUT_BG);
        inputField.setForeground(GuiTheme.TEXT);
        inputField.setCaretColor(GuiTheme.ACCENT);
        inputField.setSelectionColor(GuiTheme.SELECTION);
        inputField.setSelectedTextColor(GuiTheme.TEXT);
        inputField.setBorder(BorderFactory.createEmptyBorder());

        // Enter → submit command
        inputField.addActionListener(e -> {
            String text = inputField.getText().trim();
            if (!text.isEmpty()) {
                history.add(text);
                historyIndex = history.size();
                inputField.setText("");
                onCommand.accept(text);
            }
        });

        // Up/Down → history, Tab → autocomplete
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP -> {
                        if (!history.isEmpty() && historyIndex > 0) {
                            if (historyIndex == history.size()) {
                                savedInput = inputField.getText();
                            }
                            historyIndex--;
                            inputField.setText(history.get(historyIndex));
                        }
                        e.consume();
                    }
                    case KeyEvent.VK_DOWN -> {
                        if (historyIndex < history.size()) {
                            historyIndex++;
                            if (historyIndex == history.size()) {
                                inputField.setText(savedInput);
                            } else {
                                inputField.setText(history.get(historyIndex));
                            }
                        }
                        e.consume();
                    }
                    case KeyEvent.VK_TAB -> {
                        e.consume();
                        autoComplete();
                    }
                }
            }
        });

        add(promptLabel, BorderLayout.WEST);
        add(inputField, BorderLayout.CENTER);
    }

    public void setRegistry(CommandRegistry registry) {
        this.registry = registry;
    }

    public void updatePrompt(String connName, boolean connected, int count) {
        updatePrompt(connName, connected, count, null);
    }

    public void updatePrompt(String connName, boolean connected, int count, String mountedConnection) {
        String html;
        if (connected && connName != null) {
            String suffix = count > 1 ? " [" + count + "]" : "";
            String mountIndicator = mountedConnection != null
                    ? " <font color='#c084fc'>[mounted]</font>" : "";
            html = String.format(
                    "<html><font color='#4ade80'>*</font> <b>jbot</b>:<font color='#67e8f9'>%s</font>%s%s&gt; </html>",
                    escapeHtml(connName), suffix, mountIndicator
            );
        } else {
            html = "<html><font color='#f87171'>o</font> <b>jbot</b>&gt; </html>";
        }
        promptLabel.setText(html);
        promptLabel.setForeground(GuiTheme.TEXT);
    }

    public void focusInput() {
        inputField.requestFocusInWindow();
    }

    private void autoComplete() {
        if (registry == null) return;
        String prefix = inputField.getText().toLowerCase();
        if (prefix.isEmpty()) return;

        List<String> matches = new ArrayList<>();
        for (var cmd : registry.all()) {
            if (cmd.name().toLowerCase().startsWith(prefix)) {
                matches.add(cmd.name());
            }
            for (String alias : cmd.aliases()) {
                if (alias.toLowerCase().startsWith(prefix)) {
                    matches.add(alias);
                }
            }
        }

        if (matches.size() == 1) {
            inputField.setText(matches.get(0));
        } else if (matches.size() > 1) {
            // Find common prefix
            String common = matches.get(0);
            for (int i = 1; i < matches.size(); i++) {
                common = commonPrefix(common, matches.get(i));
            }
            if (common.length() > prefix.length()) {
                inputField.setText(common);
            }
        }
    }

    private static String commonPrefix(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && Character.toLowerCase(a.charAt(i)) == Character.toLowerCase(b.charAt(i))) {
            i++;
        }
        return a.substring(0, i);
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
