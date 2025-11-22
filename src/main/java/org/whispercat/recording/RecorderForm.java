package org.whispercat.recording;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import org.whispercat.*;
import org.whispercat.postprocessing.PostProcessingData;
import org.whispercat.postprocessing.Pipeline;
import org.whispercat.postprocessing.PostProcessingService;
import org.whispercat.recording.clients.FasterWhisperTranscribeClient;
import org.whispercat.recording.clients.OpenAITranscribeClient;
import org.whispercat.recording.clients.OpenWebUITranscribeClient;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;


public class RecorderForm extends javax.swing.JPanel {

    private final JTextArea processedText = new JTextArea(3, 20);
    private final JCheckBox enablePostProcessingCheckBox = new JCheckBox("Enable Post Processing");
    private final JButton recordButton;
    private final int baseIconSize = 40;  // Reduced from 200 for status indicator
    private final OpenAITranscribeClient whisperClient;
    private final ConfigManager configManager;
    private final FasterWhisperTranscribeClient fasterWhisperTranscribeClient;
    private final OpenWebUITranscribeClient openWebUITranscribeClient;
    private boolean isRecording = false;
    private boolean isTranscribing = false;  // Track transcription/conversion state
    private AudioRecorder recorder;
    private final JTextArea transcriptionTextArea;
    private final JPanel statusIndicatorPanel;  // Status circles instead of large logo
    private JButton copyButton;

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(RecorderForm.class);


    private JComboBox<PostProcessingItem> postProcessingSelectComboBox;
    private List<Pipeline> pipelineList;
    private JTextArea consoleLogArea;

    public RecorderForm(ConfigManager configManager) {
        this.configManager = configManager;
        this.whisperClient = new OpenAITranscribeClient(configManager);
        this.fasterWhisperTranscribeClient = new FasterWhisperTranscribeClient(configManager);
        this.openWebUITranscribeClient = new OpenWebUITranscribeClient(configManager);


        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(new EmptyBorder(30, 50, 10, 50));

        // Create status indicator panel (small circles next to button)
        statusIndicatorPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        statusIndicatorPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Status indicator (small circle that changes color)
        JPanel statusCircle = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Status colors: green (ready), red (recording), blue (transcribing)
                g2.setColor(new Color(144, 238, 144)); // Light green - ready/idle
                if (isRecording) {
                    g2.setColor(new Color(255, 99, 71)); // Tomato red - recording
                } else if (isTranscribing) {
                    g2.setColor(new Color(100, 149, 237)); // Cornflower blue - transcribing/converting
                }
                g2.fillOval(2, 2, 16, 16);

                // Border
                g2.setColor(Color.DARK_GRAY);
                g2.drawOval(2, 2, 16, 16);
                g2.dispose();
            }
        };
        statusCircle.setPreferredSize(new Dimension(20, 20));
        statusCircle.setOpaque(false);

        recordButton = new JButton("Start Recording");
        recordButton.addActionListener(e -> {
            toggleRecording();
        });

        statusIndicatorPanel.add(statusCircle);
        statusIndicatorPanel.add(recordButton);

        JPanel transcriptionPanel = new JPanel();
        transcriptionPanel.setLayout(new BoxLayout(transcriptionPanel, BoxLayout.Y_AXIS));

        transcriptionTextArea = new JTextArea(3, 20);
        transcriptionTextArea.setLineWrap(true);
        transcriptionTextArea.setWrapStyleWord(true);
        transcriptionTextArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JScrollPane transcriptionTextScrollPane = new JScrollPane(transcriptionTextArea);
        transcriptionTextScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        transcriptionTextScrollPane.setMinimumSize(new Dimension(600, transcriptionTextArea.getPreferredSize().height + 10));

        transcriptionPanel.add(transcriptionTextScrollPane);

        copyButton = new JButton("Copy");
        copyButton.setToolTipText("Copy transcription to clipboard");
        copyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        copyButton.addActionListener(e -> copyTranscriptionToClipboard(transcriptionTextArea.getText()));

        // Add components to center panel with proper spacing
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(statusIndicatorPanel);  // Status indicator + record button
        centerPanel.add(Box.createVerticalStrut(20));  // Section spacing
        centerPanel.add(transcriptionPanel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(copyButton);
        centerPanel.add(Box.createVerticalStrut(15));

        // Drag & drop hint
        JLabel dragDropLabel = new JLabel("Drag & drop an audio file here.");
        dragDropLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        dragDropLabel.setForeground(Color.GRAY);
        centerPanel.add(dragDropLabel);
        centerPanel.add(Box.createVerticalStrut(20));  // Section spacing
        centerPanel.add(Box.createVerticalGlue());

        centerPanel.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                // Accept file list flavor
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.List<File> fileList = (java.util.List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!fileList.isEmpty()) {
                        File droppedFile = fileList.get(0);
                        String lowerName = droppedFile.getName().toLowerCase();
                        if (!(lowerName.endsWith(".wav") || lowerName.endsWith(".mp3"))) {
                            Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING, "Only .wav and .mp3 files allowed.");
                            return false;
                        }
                        // Call the unified stopRecording method with the dropped file.
                        stopRecording(droppedFile);
                        return true;
                    }
                } catch (Exception ex) {
                    logger.error("Error importing dropped file", ex);
                }
                return false;
            }
        });


        JPanel postProcessingContainerPanel = new JPanel();
        postProcessingContainerPanel.setLayout(new BoxLayout(postProcessingContainerPanel, BoxLayout.Y_AXIS));
        postProcessingContainerPanel.setBorder(new EmptyBorder(10, 50, 50, 50));
        postProcessingContainerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Responsive options panel with FlowLayout (flexbox-style)
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        optionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        optionsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        // New checkbox to control auto-paste from clipboard
        JCheckBox autoPasteCheckBox = new JCheckBox("Paste from clipboard (Ctrl+V)");
        autoPasteCheckBox.setSelected(configManager.isAutoPasteEnabled());
        autoPasteCheckBox.addActionListener(e -> {
            configManager.setAutoPasteEnabled(autoPasteCheckBox.isSelected());
        });

        // enablePostProcessingCheckBox uses default alignment (text on right)

        JCheckBox loadOnStartupCheckBox = new JCheckBox("Activate on startup");
        loadOnStartupCheckBox.setVisible(false); // initially hidden
        loadOnStartupCheckBox.addActionListener(e -> {
            if (loadOnStartupCheckBox.isSelected()) {
                configManager.setPostProcessingOnStartup(true);
            } else {
                configManager.setPostProcessingOnStartup(false);
            }
        });

        JLabel selectLabel = new JLabel("Select Post-Processing:");

        postProcessingSelectComboBox = new JComboBox<>();
        populatePostProcessingComboBox();
        postProcessingSelectComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                PostProcessingItem selectedItem = (PostProcessingItem) e.getItem();
                if (selectedItem != null) {
                    configManager.setLastUsedPostProcessingUUID(selectedItem.uuid);
                }
            }
        });

        // CardPanel for pipeline selection (show/hide based on checkbox)
        JPanel cardPanel = new JPanel(new CardLayout());
        cardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel pipelineSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pipelineSelectionPanel.add(selectLabel);
        pipelineSelectionPanel.add(postProcessingSelectComboBox);

        JPanel placeholderPanel = new JPanel();
        placeholderPanel.setPreferredSize(new Dimension(0, 0));

        cardPanel.add(placeholderPanel, "none");
        cardPanel.add(pipelineSelectionPanel, "active");
        CardLayout cl = (CardLayout) cardPanel.getLayout();
        cl.show(cardPanel, "none");

        // Add controls to responsive options panel
        optionsPanel.add(autoPasteCheckBox);
        optionsPanel.add(enablePostProcessingCheckBox);
        optionsPanel.add(loadOnStartupCheckBox);

        postProcessingContainerPanel.add(optionsPanel);
        postProcessingContainerPanel.add(Box.createVerticalStrut(10));
        postProcessingContainerPanel.add(cardPanel);


        processedText.setLineWrap(true);
        processedText.setWrapStyleWord(true);
        processedText.setRows(3);
        processedText.setMinimumSize(new Dimension(Integer.MAX_VALUE, processedText.getPreferredSize().height));
        processedText.setAlignmentX(Component.LEFT_ALIGNMENT);
        //processedText.setVisible(false);

        JScrollPane processedTextScrollPane = new JScrollPane(processedText);
        processedTextScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        processedTextScrollPane.setMinimumSize(new Dimension(600, processedText.getPreferredSize().height + 10));

        processedTextScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        processedTextScrollPane.setVisible(false);
        JLabel additionalTextLabel = new JLabel("Post Processed text:");
        additionalTextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        additionalTextLabel.setVisible(false);

        postProcessingContainerPanel.add(additionalTextLabel);
        postProcessingContainerPanel.add(processedTextScrollPane);


        JButton copyProcessedTextButton = new JButton("Copy");
        JPanel copyButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        copyButtonPanel.add(copyProcessedTextButton);
        copyButtonPanel.setVisible(false);
        copyButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyProcessedTextButton.setVisible(true);
        copyProcessedTextButton.addActionListener(e -> copyTranscriptionToClipboard(processedText.getText()));
        postProcessingContainerPanel.add(Box.createVerticalStrut(10));
        postProcessingContainerPanel.add(copyButtonPanel);

        // Load saved state for post-processing checkbox
        enablePostProcessingCheckBox.setSelected(configManager.isPostProcessingEnabled());

        enablePostProcessingCheckBox.addActionListener(e -> {
            boolean selected = enablePostProcessingCheckBox.isSelected();
            // Save state when changed
            configManager.setPostProcessingEnabled(selected);
            if (selected) {
                cl.show(cardPanel, "active");
            } else {
                cl.show(cardPanel, "none");
            }
            additionalTextLabel.setVisible(selected);
            loadOnStartupCheckBox.setVisible(selected);
            processedTextScrollPane.setVisible(selected);
            copyButtonPanel.setVisible(selected);
            postProcessingContainerPanel.revalidate();
            postProcessingContainerPanel.repaint();
        });

        // Trigger initial UI setup based on loaded state
        if (enablePostProcessingCheckBox.isSelected()) {
            cl.show(cardPanel, "active");
            additionalTextLabel.setVisible(true);
            loadOnStartupCheckBox.setVisible(true);
            processedTextScrollPane.setVisible(true);
            copyButtonPanel.setVisible(true);
        }

        if (configManager.isPostProcessingOnStartup()) {
            loadOnStartupCheckBox.setSelected(true);
            if (!enablePostProcessingCheckBox.isSelected()) {
                enablePostProcessingCheckBox.doClick();
            }
        }

        // Console log panel
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.setBorder(BorderFactory.createTitledBorder("Execution Log"));
        consoleLogArea = new JTextArea(15, 20);  // Increased from 8 to 15 rows for better visibility
        consoleLogArea.setEditable(false);
        consoleLogArea.setLineWrap(true);
        consoleLogArea.setWrapStyleWord(true);
        consoleLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane consoleScrollPane = new JScrollPane(consoleLogArea);
        consoleScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        consolePanel.add(consoleScrollPane, BorderLayout.CENTER);

        // Add clear button for console log
        JPanel consoleButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        JButton clearLogButton = new JButton("Clear Log");
        clearLogButton.setToolTipText("Clear the execution log");
        clearLogButton.addActionListener(e -> consoleLogArea.setText(""));
        consoleButtonPanel.add(clearLogButton);
        consolePanel.add(consoleButtonPanel, BorderLayout.SOUTH);

        // Register console with ConsoleLogger singleton
        ConsoleLogger.getInstance().setConsoleArea(consoleLogArea);

        checkSettings();

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);

        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                        .addComponent(centerPanel)
                        .addComponent(postProcessingContainerPanel)
                        .addComponent(consolePanel)
        );

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(centerPanel)
                        .addGap(10)  // Reduced spacing between sections (was 20)
                        .addComponent(postProcessingContainerPanel)
                        .addGap(10)  // Reduced spacing between sections (was 20)
                        .addComponent(consolePanel, 250, 300, 350)  // Increased height for taller log (was 150)
                        .addContainerGap(10, Short.MAX_VALUE)  // Reduced bottom margin
        );

        // Enable drag & drop for audio files
        setupDragAndDrop(centerPanel);
    }

    /**
     * Sets up drag and drop support for audio files.
     * Supported formats: WAV, MP3, M4A, FLAC
     * Note: OGG files are accepted but conversion may fail due to Java codec limitations.
     *       Users should convert OGG to WAV manually using ffmpeg, VLC, or Audacity.
     */
    private void setupDragAndDrop(JPanel panel) {
        panel.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                // Only check if it's a drop operation with file list flavor
                // DO NOT access the actual data here - that causes InvalidDnDOperationException
                return support.isDrop() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);

                    // Validate: only accept single audio files
                    if (files != null && files.size() == 1) {
                        File file = files.get(0);
                        String fileName = file.getName().toLowerCase();

                        // Accept WAV, MP3, OGG, M4A, FLAC files
                        // Note: OGG may fail conversion due to Java codec limitations
                        if (fileName.endsWith(".wav") || fileName.endsWith(".mp3") ||
                            fileName.endsWith(".ogg") || fileName.endsWith(".m4a") ||
                            fileName.endsWith(".flac")) {
                            handleDroppedAudioFile(file);
                            return true;
                        } else {
                            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                                    "Unsupported file type. Use WAV, MP3, OGG, M4A, or FLAC files.");
                            return false;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error importing dropped file", e);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Error importing file: " + e.getMessage());
                }
                return false;
            }
        });
    }

    /**
     * Handles a dropped audio file by converting if necessary and transcribing.
     * Uses background processing to prevent UI freezing on large files.
     */
    private void handleDroppedAudioFile(File file) {
        logger.info("Dropped file: " + file.getName());
        ConsoleLogger console = ConsoleLogger.getInstance();
        console.separator();
        console.log("📁 Dropped audio file: " + file.getName());
        console.log("   Size: " + String.format("%.2f MB", file.length() / (1024.0 * 1024.0)));

        // Check file size limits BEFORE processing (API limits vary by server)
        long fileSizeBytes = file.length();
        String server = configManager.getWhisperServer();

        // OpenAI has 25MB limit
        if (server.equals("OpenAI") && fileSizeBytes > 25 * 1024 * 1024) {
            String errorMsg = String.format("File too large (%.1f MB). OpenAI limit is 25 MB.",
                                           fileSizeBytes / (1024.0 * 1024.0));
            console.logError(errorMsg);
            console.log("💡 Try:");
            console.log("   • Enable silence removal in Options to reduce file size");
            console.log("   • Use a different transcription service (Faster-Whisper, Open WebUI)");
            console.log("   • Split the audio into smaller files");
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                    "File exceeds 25 MB OpenAI limit");
            return;
        }

        // Update UI to show processing (blue indicator)
        updateUIForRecordingStop();

        // Process file in background (OGG conversion can take time on large files)
        new FileDropProcessingWorker(file).execute();
    }

    /**
     * Converts an OGG file to WAV format.
     * First attempts to use ffmpeg if available, otherwise falls back to Java AudioSystem.
     */
    private File convertOggToWav(File oggFile) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        try {
            // Create temporary WAV file
            File wavFile = File.createTempFile("whispercat_converted_", ".wav");
            wavFile.deleteOnExit();

            // First, try using ffmpeg if available
            if (isFfmpegAvailable()) {
                console.log("⚙ Using ffmpeg for OGG conversion...");
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg",
                        "-i", oggFile.getAbsolutePath(),
                        "-ar", "16000",  // 16kHz sample rate (good for speech)
                        "-ac", "1",      // Mono
                        "-y",            // Overwrite output file
                        wavFile.getAbsolutePath()
                    );
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        console.logSuccess("ffmpeg conversion successful");
                        logger.info("Successfully converted OGG to WAV using ffmpeg");
                        return wavFile;
                    } else {
                        console.logError("ffmpeg conversion failed with exit code: " + exitCode);
                        logger.error("ffmpeg conversion failed with exit code: " + exitCode);
                    }
                } catch (Exception e) {
                    logger.error("Error using ffmpeg for conversion", e);
                    console.logError("ffmpeg conversion error: " + e.getMessage());
                }
            } else {
                console.log("⚠ ffmpeg not found, trying Java AudioSystem...");
            }

            // Fallback: try Java's AudioSystem (will likely fail for OGG)
            try {
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(oggFile);
                javax.sound.sampled.AudioSystem.write(audioStream,
                        javax.sound.sampled.AudioFileFormat.Type.WAVE, wavFile);
                audioStream.close();
                console.logSuccess("Java AudioSystem conversion successful");
                logger.info("Successfully converted OGG to WAV using AudioSystem");
                return wavFile;
            } catch (Exception e) {
                logger.error("Failed to convert OGG using AudioSystem. OGG codec not supported.", e);
                // Both methods failed
                return null;
            }
        } catch (Exception e) {
            logger.error("Error creating temp file for conversion", e);
            return null;
        }
    }

    /**
     * Check if ffmpeg is available on the system.
     */
    private boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static class PostProcessingItem {
        private final String title;
        private final String uuid;

        public PostProcessingItem(String title, String uuid) {
            this.title = title;
            this.uuid = uuid;
        }

        // Return only title for display in the ComboBox.
        @Override
        public String toString() {
            return title;
        }
    }

    private void populatePostProcessingComboBox() {
        postProcessingSelectComboBox.removeAllItems();
        // Get the list of pipelines (only show enabled ones)
        pipelineList = configManager.getPipelines();
        String lastUsedPipelineUUID = configManager.getLastUsedPipelineUUID();
        Integer lastUsedIndex = null;
        for (int index = 0; index < pipelineList.size(); index++) {
            Pipeline pipeline = pipelineList.get(index);
            // Only show enabled pipelines in the dropdown
            if (pipeline.enabled) {
                PostProcessingItem item = new PostProcessingItem(pipeline.title, pipeline.uuid);
                if (pipeline.uuid.equals(lastUsedPipelineUUID)) {
                    lastUsedIndex = postProcessingSelectComboBox.getItemCount(); // Index in filtered list
                }
                postProcessingSelectComboBox.addItem(item);
            }
        }

        if (lastUsedIndex != null) {
            postProcessingSelectComboBox.setSelectedIndex(lastUsedIndex);
        }
    }

    private boolean isToggleInProgress = false;

    public void toggleRecording() {


        if (isToggleInProgress || isStoppingInProgress) {
            logger.info("Toggle in progress or stopping in progress. Ignoring.");
            return;
        }
        if (!isRecording) {
            if (!checkSettings()) return;
            startRecording();
            updateUIForRecordingStart();
            updateTrayMenu();

        } else {
            stopRecording(false);
        }
    }

    private void startRecording() {
        try {
            isRecording = true;
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File audioFile = new File(System.getProperty("java.io.tmpdir"), "record_" + timeStamp + ".wav");
            recorder = new AudioRecorder(audioFile, configManager);
            new Thread(recorder::start).start();
            logger.info("Recording started: " + audioFile.getPath());
            recordButton.setText("Stop Recording");
        } catch (Exception e) {
            logger.error("An error occurred while starting the recording", e);
            isRecording = false;
        }
    }

    private boolean isStoppingInProgress = false;

    public void stopRecording(boolean cancelledRecording) {
        // Set isRecording to false BEFORE updateUIForRecordingStop()
        // so the status indicator shows blue (transcribing), not red (recording)
        isRecording = false;
        updateUIForRecordingStop();
        isStoppingInProgress = true;
        recordButton.setText("Converting. Please wait...");
        //recordButton.setEnabled(false);
        if (recorder != null) {
            recorder.stop();
            logger.info("Recording stopped");
            if (!cancelledRecording) {
                new RecorderForm.AudioTranscriptionWorker(recorder.getOutputFile()).execute();
            } else {
                logger.info("Recording cancelled");
                updateTrayMenu();
            }
        }
    }

    public void stopRecording(File audioFile) {
        isStoppingInProgress = true;
        recordButton.setText("Converting. Please wait...");
        recordButton.setEnabled(false);
        new RecorderForm.AudioTranscriptionWorker(audioFile).execute();
    }

    public void playClickSound() {
        if (configManager.isStopSoundEnabled()) {
            new Thread(() -> {
                try {
                    InputStream audioSrc = getClass().getResourceAsStream("/stop.wav");
                    InputStream bufferedIn = new BufferedInputStream(audioSrc);
                    AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioStream);
                    clip.start();
                } catch (Exception e) {
                    logger.error(e);
                }
            }).start();
        }
    }

    private void copyTranscriptionToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }

    private void pasteFromClipboard() {
        if (!configManager.isAutoPasteEnabled()) {
            return;
        }
        try {
            Robot robot = new Robot();
            robot.delay(500);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } catch (AWTException e) {
            logger.error("An error occurred while pasting from clipboard", e);
        }
    }

    private void updateUIForRecordingStart() {

        processedText.setFocusable(false);
        processedText.setFocusable(true);
        transcriptionTextArea.setFocusable(false);
        transcriptionTextArea.setFocusable(true);

        // Repaint status indicator to show recording state (red circle)
        statusIndicatorPanel.repaint();

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                isToggleInProgress = true;
                recordButton.setEnabled(false);
                Thread.sleep(1000);
                return null;
            }

            @Override
            protected void done() {
                isToggleInProgress = false;
                recordButton.setEnabled(true);
                recordButton.setText("Stop Recording");
            }
        };
        worker.execute();
    }

    private void updateUIForRecordingStop() {
        // Set transcribing state (blue indicator)
        isTranscribing = true;
        statusIndicatorPanel.repaint();

        recordButton.setText("Converting. Please wait...");
        recordButton.setEnabled(false);
    }

    private void resetUIAfterTranscription() {
        isStoppingInProgress = false;
        isTranscribing = false;  // Reset to idle state (green indicator)

        // Repaint status indicator to show ready state (green circle)
        statusIndicatorPanel.repaint();

        recordButton.setText("Start Recording");
        recordButton.setEnabled(true);
    }

    private boolean checkSettings() {
        boolean settingsSet = true;
        if ((configManager.getWhisperServer().equals("OpenAI") || configManager.getWhisperServer().isEmpty()) && (configManager.getApiKey() == null || configManager.getApiKey().length() == 0)) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                    "API Key must be set in options.");
            settingsSet = false;
        }
        if (configManager.getMicrophone() == null || configManager.getMicrophone().length() == 0) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                    "Microphone must be set in options.");
            settingsSet = false;
        }
        return settingsSet;
    }


    private void updateTrayMenu() {
        TrayIconManager manager = AudioRecorderUI.getTrayIconManager();
        if (manager != null) {
            manager.updateTrayMenu(isRecording);
        }
    }

    /**
     * Background worker for processing dropped audio files.
     * Handles OGG conversion off the EDT to prevent UI freezing on large files.
     */
    private class FileDropProcessingWorker extends SwingWorker<File, Void> {
        private final File originalFile;

        public FileDropProcessingWorker(File file) {
            this.originalFile = file;
        }

        @Override
        protected File doInBackground() {
            ConsoleLogger console = ConsoleLogger.getInstance();
            File fileToTranscribe = originalFile;

            // Check if OGG file and convert to WAV (in background to prevent UI freeze)
            if (originalFile.getName().toLowerCase().endsWith(".ogg")) {
                logger.info("Converting OGG file to WAV in background...");
                console.log("⚙ Converting OGG to WAV...");
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.INFO,
                        "Converting OGG to WAV...");
                fileToTranscribe = convertOggToWav(originalFile);
                if (fileToTranscribe == null) {
                    console.logError("OGG conversion failed");
                    if (!isFfmpegAvailable()) {
                        console.log("⚠ ffmpeg not installed - automatic OGG conversion unavailable");
                        console.log("💡 Install ffmpeg for automatic OGG conversion:");
                        console.log("   Windows: choco install ffmpeg  OR  download from ffmpeg.org");
                        console.log("   Linux: sudo apt install ffmpeg");
                        console.log("   macOS: brew install ffmpeg");
                        console.log("");
                        console.log("💡 Or convert manually:");
                        console.log("   ffmpeg -i input.ogg output.wav");
                        console.log("   Or use VLC, Audacity, or any audio converter");
                        Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                                "OGG conversion failed. Install ffmpeg or convert to WAV manually.");
                    } else {
                        console.log("💡 Solution: Convert to WAV manually using VLC, Audacity, or ffmpeg");
                        console.log("   Example: ffmpeg -i input.ogg output.wav");
                        Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                                "OGG conversion failed. Convert to WAV manually (use VLC/Audacity/ffmpeg).");
                    }
                    return null;
                }
                console.logSuccess("OGG converted to WAV successfully");
            }

            return fileToTranscribe;
        }

        @Override
        protected void done() {
            try {
                File fileToTranscribe = get();
                if (fileToTranscribe != null) {
                    // Conversion successful (or wasn't needed), proceed with transcription
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.INFO,
                            "Transcribing audio file...");
                    new AudioTranscriptionWorker(fileToTranscribe).execute();
                } else {
                    // Conversion failed, reset UI
                    resetUIAfterTranscription();
                }
            } catch (Exception e) {
                logger.error("Error processing dropped file", e);
                ConsoleLogger.getInstance().logError("Error processing file: " + e.getMessage());
                resetUIAfterTranscription();
            }
        }
    }

    private class AudioTranscriptionWorker extends SwingWorker<String, Void> {
        private final File audioFile;

        public AudioTranscriptionWorker(File audioFile) {
            this.audioFile = audioFile;
        }

        @Override
        protected String doInBackground() {
            ConsoleLogger console = ConsoleLogger.getInstance();
            try {
                // Apply silence removal if enabled
                File fileToTranscribe = audioFile;
                if (configManager.isSilenceRemovalEnabled()) {
                    console.separator();
                    fileToTranscribe = SilenceRemover.removeSilence(
                        audioFile,
                        configManager.getSilenceThreshold(),
                        configManager.getMinSilenceDuration(),
                        configManager.isKeepCompressedFile(),
                        configManager.getMinRecordingDurationForSilenceRemoval()
                    );
                }

                String server = configManager.getWhisperServer();
                console.separator();
                console.log("Starting transcription using " + server);
                console.log("Audio file: " + fileToTranscribe.getName());

                // Check file size limits BEFORE attempting upload
                long fileSizeBytes = fileToTranscribe.length();
                long fileSizeMB = fileSizeBytes / (1024 * 1024);
                console.log(String.format("File size: %.2f MB", fileSizeBytes / (1024.0 * 1024.0)));

                // OpenAI has 25MB limit
                if (server.equals("OpenAI") && fileSizeBytes > 25 * 1024 * 1024) {
                    String errorMsg = String.format("File too large (%.1f MB). OpenAI limit is 25 MB.",
                                                   fileSizeBytes / (1024.0 * 1024.0));
                    console.logError(errorMsg);
                    console.log("💡 Try enabling silence removal in Options to reduce file size");
                    console.log("   Or use a different transcription service (Faster-Whisper, Open WebUI)");
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "File exceeds 25 MB OpenAI limit. Enable silence removal or use different service.");
                    return null;
                }

                long transcriptionStartTime = System.currentTimeMillis();
                String result = null;

                if (server.equals("OpenAI")) {
                    logger.info("Transcribing audio using OpenAI");
                    result = whisperClient.transcribe(fileToTranscribe);
                } else if (server.equals("Faster-Whisper")) {
                    logger.info("Transcribing audio using Faster-Whisper");
                    result = fasterWhisperTranscribeClient.transcribe(fileToTranscribe);
                } else if (server.equals("Open WebUI")) {
                    logger.info("Transcribing audio using Open WebUI");
                    result = openWebUITranscribeClient.transcribeAudio(fileToTranscribe);
                } else {
                    logger.error("Unknown Whisper server: " + server);
                    console.logError("Unknown Whisper server: " + server);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Unknown Whisper server: " + server);
                    return null;
                }

                long transcriptionTime = System.currentTimeMillis() - transcriptionStartTime;
                console.log(String.format("Transcription took %dms", transcriptionTime));
                return result;
            } catch (Exception e) {
                logger.error("Error during transcription", e);
                ConsoleLogger.getInstance().logError("Transcription failed: " + e.getMessage());
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "Error during transcription. See logs.");
                return null;
            }
        }

        @Override
        protected void done() {
            ConsoleLogger console = ConsoleLogger.getInstance();
            String transcript = null;
            try {
                transcript = get();
                if (transcript != null) {
                    logger.info("Transcribed text: " + transcript);
                    transcriptionTextArea.setText(transcript);
                    console.logSuccess("Transcription completed");
                    console.log("Transcript length: " + transcript.length() + " characters");
                    // Show success notification
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS,
                            "Transcription completed!");
                    // Show system-level notification
                    TrayIconManager trayManager = AudioRecorderUI.getTrayIconManager();
                    if (trayManager != null) {
                        trayManager.showSystemNotification("WhisperCat", "Transcription completed");
                    }
                } else {
                    logger.warn("Transcription resulted in null");
                    console.logError("Transcription returned null");
                }
            } catch (Exception e) {
                logger.error("An error occurred while finishing the transcription", e);
                console.logError("Error finishing transcription: " + e.getMessage());
            } finally {
                isRecording = false;
            }

            // Only run post-processing if we have a valid transcript
            if (transcript != null && !transcript.trim().isEmpty() &&
                enablePostProcessingCheckBox.isSelected() && postProcessingSelectComboBox.getSelectedItem() != null) {
                    PostProcessingItem selectedItem = (PostProcessingItem) postProcessingSelectComboBox.getSelectedItem();
                    if (selectedItem != null && selectedItem.uuid != null) {
                        Pipeline pipeline = configManager.getPipelineByUuid(selectedItem.uuid);
                        if (pipeline != null) {
                            // Update UI to show post-processing is happening (keep blue indicator)
                            recordButton.setText("Post-processing...");
                            recordButton.setEnabled(false);
                            isTranscribing = true;  // Keep blue indicator during post-processing
                            statusIndicatorPanel.repaint();

                            PostProcessingService ppService = new PostProcessingService(configManager);
                            String processedText = ppService.applyPipeline(transcript, pipeline);
                            RecorderForm.this.processedText.setText(processedText);
                            // Show pipeline completion toast
                            Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS,
                                    "Post-processing completed!");
                            // Show system-level notification
                            TrayIconManager trayManager = AudioRecorderUI.getTrayIconManager();
                            if (trayManager != null) {
                                trayManager.showSystemNotification("WhisperCat", "Post-processing completed");
                            }
                            playClickSound();
                            // Remove focus from text areas to prevent pasting into them
                            transcriptionTextArea.transferFocus();
                            RecorderForm.this.processedText.transferFocus();
                            copyTranscriptionToClipboard(processedText);
                            pasteFromClipboard();
                            updateTrayMenu();
                            // Remember the last used pipeline
                            configManager.setLastUsedPipelineUUID(pipeline.uuid);
                        } else {
                            logger.error("Pipeline not found for UUID: " + selectedItem.uuid);
                            console.logError("Pipeline not found: " + selectedItem.uuid);
                            updateTrayMenu();
                        }
                    }
            } else if (transcript != null && !transcript.trim().isEmpty()) {
                // No post-processing, just copy raw transcript if auto-paste enabled
                if (configManager.isAutoPasteEnabled()) {
                    // Remove focus from transcription area to prevent pasting into itself
                    transcriptionTextArea.transferFocus();
                    copyTranscriptionToClipboard(transcript);
                    pasteFromClipboard();
                }
                playClickSound();
                updateTrayMenu();
            }

            // Reset UI after everything is done (keeps blue circle during post-processing)
            resetUIAfterTranscription();
        }
    }
}
