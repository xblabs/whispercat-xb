package org.whispercat.recording;

import org.whispercat.ConfigManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Dialog that shows options for handling large audio files that exceed the API limit.
 * Presents available options based on system capabilities (FFmpeg, local Whisper, etc.).
 */
public class LargeFileOptionsDialog extends JDialog {

    private AudioFileAnalyzer.LargeFileOption selectedOption = null;
    private boolean cancelled = true;
    private JCheckBox rememberChoiceCheckbox;
    private final ConfigManager configManager;

    /**
     * Creates a new dialog for large file options.
     *
     * @param parent Parent window
     * @param analysis Analysis result from AudioFileAnalyzer
     * @param configManager Configuration manager for checking available services
     */
    public LargeFileOptionsDialog(Window parent, AudioFileAnalyzer.AnalysisResult analysis,
                                   ConfigManager configManager) {
        super(parent, "Large Audio File Detected", ModalityType.APPLICATION_MODAL);
        this.configManager = configManager;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(20, 25, 20, 25));

        // File info section
        JPanel infoPanel = createInfoPanel(analysis);
        mainPanel.add(infoPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Separator
        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        mainPanel.add(separator);
        mainPanel.add(Box.createVerticalStrut(15));

        // Options section
        JLabel promptLabel = new JLabel("How would you like to proceed?");
        promptLabel.setFont(promptLabel.getFont().deriveFont(Font.BOLD));
        promptLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(promptLabel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Radio buttons for options
        ButtonGroup optionGroup = new ButtonGroup();
        JPanel optionsPanel = createOptionsPanel(analysis, optionGroup);
        mainPanel.add(optionsPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Remember choice checkbox
        rememberChoiceCheckbox = new JCheckBox("Remember my choice for files over 25MB");
        rememberChoiceCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(rememberChoiceCheckbox);
        mainPanel.add(Box.createVerticalStrut(20));

        // Buttons
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(parent);

        // Set minimum size
        setMinimumSize(new Dimension(450, getHeight()));
    }

    /**
     * Creates the file information panel.
     */
    private JPanel createInfoPanel(AudioFileAnalyzer.AnalysisResult analysis) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // File name
        JLabel fileLabel = new JLabel("File: " + analysis.file.getName());
        fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fileLabel);

        // File size with warning color
        JLabel sizeLabel = new JLabel(String.format("Size: %s (exceeds 25MB API limit)",
            analysis.getFormattedFileSize()));
        sizeLabel.setForeground(new Color(200, 80, 80)); // Soft red
        sizeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(sizeLabel);

        // Duration if available
        if (analysis.estimatedDurationSeconds != null) {
            JLabel durationLabel = new JLabel("Estimated duration: " + analysis.getFormattedDuration());
            durationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(durationLabel);
        }

        // Format
        JLabel formatLabel = new JLabel("Format: " + analysis.format.toUpperCase());
        formatLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(formatLabel);

        return panel;
    }

    /**
     * Creates the options panel with radio buttons.
     */
    private JPanel createOptionsPanel(AudioFileAnalyzer.AnalysisResult analysis, ButtonGroup group) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Option 1: Split and transcribe
        if (analysis.canSplitNatively || analysis.ffmpegAvailable) {
            JRadioButton splitOption = createOptionRadioButton(
                AudioFileAnalyzer.LargeFileOption.SPLIT_AND_TRANSCRIBE,
                "Split into chunks and transcribe sequentially",
                createSplitDescription(analysis),
                true  // Default selection
            );
            group.add(splitOption);
            panel.add(splitOption);
            panel.add(Box.createVerticalStrut(5));
            panel.add(createSubLabel("Will create ~" + analysis.suggestedChunkCount +
                " chunks of ~20 minutes each"));
            panel.add(createSubLabel("Transcriptions will be merged automatically"));
            panel.add(createSubLabel("Estimated time: " + analysis.getEstimatedTranscriptionTime()));
            panel.add(Box.createVerticalStrut(15));
        }

        // Option 2: Compress first (requires FFmpeg)
        if (analysis.ffmpegAvailable) {
            boolean canCompress = analysis.estimatedDurationSeconds != null &&
                FfmpegCompressor.canFitWithCompression(
                    analysis.fileSizeBytes, analysis.estimatedDurationSeconds);

            JRadioButton compressOption = createOptionRadioButton(
                AudioFileAnalyzer.LargeFileOption.COMPRESS_FIRST,
                "Compress with FFmpeg first, then transcribe",
                null,
                false
            );
            compressOption.setEnabled(canCompress);
            group.add(compressOption);
            panel.add(compressOption);
            panel.add(Box.createVerticalStrut(5));

            if (canCompress) {
                panel.add(createSubLabel("Convert to MP3 mono 16kHz (~15MB estimated)"));
                panel.add(createSubLabel("Single transcription request"));
                panel.add(createSubLabel("Requires: FFmpeg installed " + checkMark(true)));
            } else {
                JLabel notAvailable = createSubLabel("File too long for compression alone - use split instead");
                notAvailable.setForeground(Color.GRAY);
                panel.add(notAvailable);
            }
            panel.add(Box.createVerticalStrut(15));
        }

        // Option 3: Use local Whisper
        boolean localWhisperConfigured = isLocalWhisperConfigured();
        JRadioButton localOption = createOptionRadioButton(
            AudioFileAnalyzer.LargeFileOption.USE_LOCAL_WHISPER,
            "Use Faster-Whisper (local) - no size limit",
            null,
            false
        );
        localOption.setEnabled(localWhisperConfigured);
        group.add(localOption);
        panel.add(localOption);
        panel.add(Box.createVerticalStrut(5));

        if (localWhisperConfigured) {
            panel.add(createSubLabel("Requires: Faster-Whisper server running " + checkMark(true)));
            panel.add(createSubLabel("No file size limit"));
        } else {
            JLabel notConfigured = createSubLabel("Status: Not configured " + checkMark(false));
            notConfigured.setForeground(Color.GRAY);
            panel.add(notConfigured);
            panel.add(createSubLabel("Configure in Settings > Whisper Server"));
        }

        return panel;
    }

    /**
     * Creates a radio button for an option.
     */
    private JRadioButton createOptionRadioButton(AudioFileAnalyzer.LargeFileOption option,
                                                   String text, String tooltip, boolean selected) {
        JRadioButton button = new JRadioButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setSelected(selected);
        if (tooltip != null) {
            button.setToolTipText(tooltip);
        }
        button.addActionListener(e -> selectedOption = option);

        if (selected) {
            selectedOption = option;
        }

        return button;
    }

    /**
     * Creates a sub-label with indentation.
     */
    private JLabel createSubLabel(String text) {
        JLabel label = new JLabel("    " + text);
        label.setFont(label.getFont().deriveFont(label.getFont().getSize() - 1f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Creates description for split option.
     */
    private String createSplitDescription(AudioFileAnalyzer.AnalysisResult analysis) {
        if (analysis.canSplitNatively) {
            return "Uses native Java audio splitting (no FFmpeg needed)";
        } else {
            return "Uses FFmpeg to split file into chunks";
        }
    }

    /**
     * Returns a check mark or X mark.
     */
    private String checkMark(boolean available) {
        return available ? "\u2713" : "\u2717";  // Unicode check mark or X
    }

    /**
     * Checks if local Whisper (Faster-Whisper) is configured.
     */
    private boolean isLocalWhisperConfigured() {
        if (configManager == null) return false;

        String whisperServer = configManager.getWhisperServer();
        return "Faster-Whisper".equals(whisperServer);
    }

    /**
     * Creates the button panel.
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            selectedOption = AudioFileAnalyzer.LargeFileOption.CANCEL;
            cancelled = true;
            dispose();
        });

        JButton proceedButton = new JButton("Proceed");
        proceedButton.addActionListener(e -> {
            if (selectedOption != null && selectedOption != AudioFileAnalyzer.LargeFileOption.CANCEL) {
                cancelled = false;

                // Save preference if checkbox is selected
                if (rememberChoiceCheckbox.isSelected() && configManager != null) {
                    configManager.setLargeFileDefaultOption(selectedOption.name());
                }

                dispose();
            }
        });

        panel.add(cancelButton);
        panel.add(proceedButton);

        return panel;
    }

    /**
     * Shows the dialog and returns the selected option.
     *
     * @return The selected option, or CANCEL if cancelled
     */
    public AudioFileAnalyzer.LargeFileOption showAndGetSelection() {
        setVisible(true);
        return cancelled ? AudioFileAnalyzer.LargeFileOption.CANCEL : selectedOption;
    }

    /**
     * Returns true if the dialog was cancelled.
     */
    public boolean wasCancelled() {
        return cancelled;
    }

    /**
     * Returns the selected option.
     */
    public AudioFileAnalyzer.LargeFileOption getSelectedOption() {
        return selectedOption;
    }

    /**
     * Returns true if user chose to remember their choice.
     */
    public boolean shouldRememberChoice() {
        return rememberChoiceCheckbox != null && rememberChoiceCheckbox.isSelected();
    }
}
