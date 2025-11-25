package org.whispercat.recording;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * A collapsible panel that displays pipeline execution history.
 * Shows all previous pipeline results with timestamps and copy buttons.
 */
public class HistoryPanel extends JPanel {
    private final JButton toggleButton;
    private final JPanel contentPanel;
    private final JScrollPane scrollPane;
    private boolean expanded = false;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("h:mm a");
    private static final int MAX_PREVIEW_LENGTH = 100;

    public HistoryPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(5, 0, 5, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        // Toggle button
        toggleButton = new JButton("\u25BC Show Pipeline History (0 results)");
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setBorderPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleButton.setFont(toggleButton.getFont().deriveFont(Font.PLAIN, 12f));
        toggleButton.setForeground(UIManager.getColor("Label.foreground"));
        toggleButton.addActionListener(e -> toggleExpanded());

        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        togglePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        togglePanel.add(toggleButton);
        add(togglePanel);

        // Content panel (hidden by default)
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        scrollPane = new JScrollPane(contentPanel);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
        scrollPane.setVisible(false);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPane.setPreferredSize(new Dimension(600, 200));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        add(Box.createVerticalStrut(5));
        add(scrollPane);
    }

    /**
     * Toggles the expanded/collapsed state of the history panel.
     */
    private void toggleExpanded() {
        expanded = !expanded;
        scrollPane.setVisible(expanded);
        updateToggleButtonText();
        revalidate();
        repaint();
    }

    /**
     * Updates the toggle button text based on current state and result count.
     */
    private void updateToggleButtonText() {
        String arrow = expanded ? "\u25B2" : "\u25BC";  // ▲ or ▼
        String action = expanded ? "Hide" : "Show";
        int count = contentPanel.getComponentCount();
        toggleButton.setText(String.format("%s %s Pipeline History (%d result%s)",
                arrow, action, count, count == 1 ? "" : "s"));
    }

    /**
     * Updates the panel with the current history results.
     *
     * @param results List of pipeline results (newest first)
     */
    public void updateResults(List<PipelineExecutionHistory.PipelineResult> results) {
        contentPanel.removeAll();

        if (results.isEmpty()) {
            JLabel emptyLabel = new JLabel("No pipeline history yet");
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(emptyLabel);
        } else {
            for (int i = 0; i < results.size(); i++) {
                PipelineExecutionHistory.PipelineResult result = results.get(i);
                JPanel resultPanel = createResultPanel(result, i == results.size() - 1);
                contentPanel.add(resultPanel);
            }
        }

        updateToggleButtonText();
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /**
     * Creates a panel for a single history result entry.
     */
    private JPanel createResultPanel(PipelineExecutionHistory.PipelineResult result, boolean isLast) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(new EmptyBorder(5, 0, 5, 0));

        // Header row: Pipeline name | timestamp | execution time | copy button
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel pipelineLabel = new JLabel(result.getPipelineName());
        pipelineLabel.setFont(pipelineLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(pipelineLabel);

        JLabel timeLabel = new JLabel(TIME_FORMAT.format(new Date(result.getTimestamp())));
        timeLabel.setForeground(Color.GRAY);
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 11f));
        headerPanel.add(timeLabel);

        if (result.getExecutionTimeMs() > 0) {
            JLabel durationLabel = new JLabel("(" + result.getExecutionTimeMs() + "ms)");
            durationLabel.setForeground(Color.GRAY);
            durationLabel.setFont(durationLabel.getFont().deriveFont(Font.PLAIN, 10f));
            headerPanel.add(durationLabel);
        }

        JButton copyButton = new JButton("Copy");
        copyButton.setFont(copyButton.getFont().deriveFont(Font.PLAIN, 10f));
        copyButton.setMargin(new Insets(2, 8, 2, 8));
        copyButton.addActionListener(e -> {
            StringSelection selection = new StringSelection(result.getResultText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            copyButton.setText("Copied!");
            Timer timer = new Timer(1500, evt -> copyButton.setText("Copy"));
            timer.setRepeats(false);
            timer.start();
        });
        headerPanel.add(copyButton);

        panel.add(headerPanel);

        // Result text area (read-only, limited height)
        JTextArea textArea = new JTextArea(result.getResultText());
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setRows(3);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        textArea.setBackground(UIManager.getColor("TextField.background"));

        JScrollPane textScrollPane = new JScrollPane(textArea);
        textScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        textScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        textScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        textScrollPane.setPreferredSize(new Dimension(550, 60));
        textScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        panel.add(Box.createVerticalStrut(3));
        panel.add(textScrollPane);

        // Add separator unless it's the last item
        if (!isLast) {
            panel.add(Box.createVerticalStrut(8));
            JSeparator separator = new JSeparator();
            separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            separator.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(separator);
        }

        return panel;
    }

    /**
     * Expands the panel if it's collapsed.
     */
    public void expand() {
        if (!expanded) {
            toggleExpanded();
        }
    }

    /**
     * Collapses the panel if it's expanded.
     */
    public void collapse() {
        if (expanded) {
            toggleExpanded();
        }
    }

    /**
     * Returns whether the panel is currently expanded.
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Returns the count displayed on the toggle button.
     */
    public int getResultCount() {
        return contentPanel.getComponentCount();
    }
}
