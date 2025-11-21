package org.whispercat;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Thread-safe console logger for displaying pipeline execution details in the UI.
 */
public class ConsoleLogger {
    private static ConsoleLogger instance;
    private JTextArea consoleArea;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    private ConsoleLogger() {
    }

    public static synchronized ConsoleLogger getInstance() {
        if (instance == null) {
            instance = new ConsoleLogger();
        }
        return instance;
    }

    public void setConsoleArea(JTextArea consoleArea) {
        this.consoleArea = consoleArea;
    }

    public void log(String message) {
        if (consoleArea != null) {
            SwingUtilities.invokeLater(() -> {
                String timestamp = timeFormat.format(new Date());
                consoleArea.append("[" + timestamp + "] " + message + "\n");
                // Auto-scroll to bottom
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            });
        }
    }

    public void logStep(String stepName, int currentStep, int totalSteps) {
        log("Step " + currentStep + "/" + totalSteps + ": " + stepName);
    }

    public void logPrompt(String type, String content) {
        log(type + ": " + (content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content));
    }

    public void logError(String message) {
        log("ERROR: " + message);
    }

    public void logSuccess(String message) {
        log("✓ " + message);
    }

    public void clear() {
        if (consoleArea != null) {
            SwingUtilities.invokeLater(() -> consoleArea.setText(""));
        }
    }

    public void separator() {
        log("─────────────────────────────────────────");
    }
}
