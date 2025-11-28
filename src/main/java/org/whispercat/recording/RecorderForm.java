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
import org.whispercat.recording.AudioFileAnalyzer;
import org.whispercat.recording.AudioFileAnalyzer.AnalysisResult;
import org.whispercat.recording.AudioFileAnalyzer.LargeFileOption;
import org.whispercat.recording.WavChunker;
import org.whispercat.recording.FfmpegChunker;
import org.whispercat.recording.FfmpegCompressor;
import org.whispercat.recording.LargeFileOptionsDialog;
import org.whispercat.recording.ChunkedTranscriptionWorker;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
    private JButton runPipelineButton;
    private boolean isManualPipelineRunning = false;
    private final PipelineExecutionHistory pipelineHistory = new PipelineExecutionHistory();
    private HistoryPanel historyPanel;

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

        // Add document listener to update Run Pipeline button state
        transcriptionTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateRunPipelineButtonState(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateRunPipelineButtonState(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateRunPipelineButtonState(); }
        });

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
                updateRunPipelineButtonState();
            }
        });

        // Pipeline selection panel - always visible so user can run pipelines manually
        JPanel pipelineSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pipelineSelectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pipelineSelectionPanel.add(selectLabel);
        pipelineSelectionPanel.add(postProcessingSelectComboBox);

        // Run Pipeline button for manual pipeline execution
        runPipelineButton = new JButton("\u25B6 Run Pipeline");
        runPipelineButton.setToolTipText("Run selected pipeline on transcription text");
        runPipelineButton.setEnabled(false);
        runPipelineButton.addActionListener(e -> runManualPipeline());
        pipelineSelectionPanel.add(runPipelineButton);

        // Add controls to responsive options panel
        optionsPanel.add(autoPasteCheckBox);
        optionsPanel.add(enablePostProcessingCheckBox);
        optionsPanel.add(loadOnStartupCheckBox);

        postProcessingContainerPanel.add(optionsPanel);
        postProcessingContainerPanel.add(Box.createVerticalStrut(10));
        postProcessingContainerPanel.add(pipelineSelectionPanel);


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
        JLabel additionalTextLabel = new JLabel("Post Processed text:");
        additionalTextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        postProcessingContainerPanel.add(additionalTextLabel);
        postProcessingContainerPanel.add(processedTextScrollPane);


        JButton copyProcessedTextButton = new JButton("Copy");
        JPanel copyButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        copyButtonPanel.add(copyProcessedTextButton);
        copyButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyProcessedTextButton.addActionListener(e -> copyTranscriptionToClipboard(processedText.getText()));
        postProcessingContainerPanel.add(Box.createVerticalStrut(10));
        postProcessingContainerPanel.add(copyButtonPanel);

        // History panel for pipeline execution results
        historyPanel = new HistoryPanel();
        historyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        postProcessingContainerPanel.add(Box.createVerticalStrut(5));
        postProcessingContainerPanel.add(historyPanel);

        // Load saved state for post-processing checkbox
        enablePostProcessingCheckBox.setSelected(configManager.isPostProcessingEnabled());

        enablePostProcessingCheckBox.addActionListener(e -> {
            boolean selected = enablePostProcessingCheckBox.isSelected();
            // Save state when changed
            configManager.setPostProcessingEnabled(selected);
            // Show/hide the "Activate on startup" checkbox
            loadOnStartupCheckBox.setVisible(selected);
            postProcessingContainerPanel.revalidate();
            postProcessingContainerPanel.repaint();
        });

        // Trigger initial UI setup based on loaded state
        if (enablePostProcessingCheckBox.isSelected()) {
            loadOnStartupCheckBox.setVisible(true);
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
     */
    private void setupDragAndDrop(JPanel panel) {
        panel.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop()) {
                    return false;
                }
                // Only check if the data flavor is supported, don't access transfer data yet
                // Accessing transfer data in canImport() causes InvalidDnDOperationException
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
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
                    if (files != null && files.size() == 1) {
                        File file = files.get(0);
                        String fileName = file.getName().toLowerCase();
                        // Accept WAV, MP3, OGG, M4A, FLAC files
                        if (fileName.endsWith(".wav") || fileName.endsWith(".mp3") ||
                            fileName.endsWith(".ogg") || fileName.endsWith(".m4a") ||
                            fileName.endsWith(".flac")) {
                            handleDroppedAudioFile(file);
                            return true;
                        } else {
                            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                                    "Unsupported file type. Please drop WAV, MP3, OGG, M4A, or FLAC files.");
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
     * Includes pre-flight analysis for large files and chunking support.
     */
    private void handleDroppedAudioFile(File file) {
        logger.info("Dropped file: " + file.getName());
        ConsoleLogger console = ConsoleLogger.getInstance();

        // Set transcribing state (blue indicator)
        isTranscribing = true;
        statusIndicatorPanel.repaint();
        recordButton.setText("Analyzing...");
        recordButton.setEnabled(false);

        // Pre-flight analysis
        AnalysisResult analysis = AudioFileAnalyzer.analyze(file);

        // Check if file exceeds API limit and we're using OpenAI
        if (analysis.exceedsApiLimit && "OpenAI".equals(configManager.getWhisperServer())) {
            handleLargeFile(file, analysis);
            return;
        }

        // Normal flow for files within limits
        processAudioFile(file);
    }

    /**
     * Handles a large audio file that exceeds the API limit.
     * Shows options dialog or uses saved preference.
     */
    private void handleLargeFile(File file, AnalysisResult analysis) {
        ConsoleLogger console = ConsoleLogger.getInstance();
        console.log("File exceeds 25MB API limit - showing options...");

        // Check for saved preference
        String savedOption = configManager.getLargeFileDefaultOption();
        LargeFileOption selectedOption = null;

        if (!savedOption.isEmpty()) {
            try {
                selectedOption = LargeFileOption.valueOf(savedOption);
                console.log("Using saved preference: " + selectedOption.getDescription());
            } catch (IllegalArgumentException e) {
                // Invalid saved option, will show dialog
                configManager.clearLargeFileDefaultOption();
            }
        }

        // Show dialog if no valid saved preference
        if (selectedOption == null) {
            LargeFileOptionsDialog dialog = new LargeFileOptionsDialog(
                SwingUtilities.getWindowAncestor(this),
                analysis,
                configManager
            );
            selectedOption = dialog.showAndGetSelection();
        }

        // Handle the selected option
        switch (selectedOption) {
            case SPLIT_AND_TRANSCRIBE:
                handleChunkedTranscription(file, analysis);
                break;
            case COMPRESS_FIRST:
                handleCompressAndTranscribe(file);
                break;
            case USE_LOCAL_WHISPER:
                handleLocalWhisperTranscription(file);
                break;
            case CANCEL:
            default:
                console.log("Large file handling cancelled");
                resetUIAfterTranscription();
                break;
        }
    }

    /**
     * Processes a normal audio file (within API limits).
     */
    private void processAudioFile(File file) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        recordButton.setText("Converting...");

        File fileToTranscribe = file;

        // Check if OGG file and convert to WAV (FLAC is natively supported by OpenAI, no conversion needed)
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".ogg")) {
            logger.info("Converting OGG file to WAV...");
            console.log("Converting OGG file to WAV using ffmpeg...");
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.INFO,
                    "Converting OGG to WAV...");
            fileToTranscribe = convertOggToWav(file);
            if (fileToTranscribe == null) {
                console.logError("Failed to convert OGG file");
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "Failed to convert OGG file. Please convert to WAV manually.");
                resetUIAfterTranscription();
                return;
            }
            console.logSuccess("Successfully converted OGG to WAV");
        } else if (fileName.endsWith(".flac")) {
            console.log("FLAC file detected - using directly (no conversion needed)");
        }

        // Transcribe the file
        logger.info("Transcribing audio using " + configManager.getWhisperServer());
        console.separator();
        console.log("Starting transcription using " + configManager.getWhisperServer());
        console.log("Audio file: " + fileToTranscribe.getName());
        Notificationmanager.getInstance().showNotification(ToastNotification.Type.INFO,
                "Transcribing audio file...");
        new AudioTranscriptionWorker(fileToTranscribe).execute();
    }

    /**
     * Handles chunked transcription for large files.
     * Splits the file into chunks and transcribes each sequentially.
     */
    private void handleChunkedTranscription(File file, AnalysisResult analysis) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        console.separator();
        console.log("Starting chunked transcription for large file...");
        recordButton.setText("Splitting file...");

        // Run chunking in background to not block UI
        new SwingWorker<java.util.List<File>, Void>() {
            @Override
            protected java.util.List<File> doInBackground() {
                // Choose chunking method based on format
                if (analysis.canSplitNatively && "wav".equals(analysis.format)) {
                    console.log("Using native WAV chunking (no FFmpeg needed)");
                    WavChunker.ChunkResult result = WavChunker.splitWavFileBySize(
                        file, AudioFileAnalyzer.TARGET_CHUNK_SIZE);
                    if (result.success) {
                        return result.chunks;
                    } else {
                        console.logError("WAV chunking failed: " + result.errorMessage);
                        return null;
                    }
                } else if (analysis.ffmpegAvailable) {
                    console.log("Using FFmpeg chunking");
                    FfmpegChunker.ChunkResult result = FfmpegChunker.splitBySize(
                        file, AudioFileAnalyzer.TARGET_CHUNK_SIZE);
                    if (result.success) {
                        return result.chunks;
                    } else {
                        console.logError("FFmpeg chunking failed: " + result.errorMessage);
                        return null;
                    }
                } else {
                    console.logError("No chunking method available (FFmpeg not installed)");
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    java.util.List<File> chunks = get();
                    if (chunks == null || chunks.isEmpty()) {
                        Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Failed to split file. Check logs for details.");
                        resetUIAfterTranscription();
                        return;
                    }

                    // Start chunked transcription
                    startChunkedTranscription(chunks);

                } catch (Exception e) {
                    logger.error("Error during file chunking", e);
                    console.logError("Chunking failed: " + e.getMessage());
                    resetUIAfterTranscription();
                }
            }
        }.execute();
    }

    /**
     * Starts transcription of chunked audio files.
     */
    private void startChunkedTranscription(java.util.List<File> chunks) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        console.log("Starting transcription of " + chunks.size() + " chunks...");
        recordButton.setText("Transcribing 1/" + chunks.size() + "...");

        ChunkedTranscriptionWorker worker = new ChunkedTranscriptionWorker(
            chunks, configManager, new ChunkedTranscriptionWorker.Callback() {
                @Override
                public void onProgress(ChunkedTranscriptionWorker.Progress progress) {
                    recordButton.setText(String.format("Transcribing %d/%d...",
                        progress.currentChunk + 1, progress.totalChunks));
                }

                @Override
                public void onComplete(String fullTranscript) {
                    transcriptionTextArea.setText(fullTranscript);

                    // Start new history session for this transcription
                    pipelineHistory.startNewSession(fullTranscript);
                    processedText.setText("");
                    historyPanel.updateResults(pipelineHistory.getResults());

                    console.logSuccess("Chunked transcription completed");
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS,
                        "Transcription completed!");

                    // Handle post-processing if enabled
                    handlePostTranscriptionActions(fullTranscript);

                    resetUIAfterTranscription();
                    updateTrayMenu();
                }

                @Override
                public void onError(String errorMessage) {
                    console.logError("Chunked transcription failed: " + errorMessage);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "Transcription failed. See logs for details.");
                    resetUIAfterTranscription();
                }

                @Override
                public void onCancelled() {
                    console.log("Chunked transcription cancelled");
                    resetUIAfterTranscription();
                }
            }
        );

        worker.execute();
    }

    /**
     * Handles compression and transcription for large files.
     * Compresses the file first, then transcribes if within limits.
     */
    private void handleCompressAndTranscribe(File file) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        console.separator();
        console.log("Compressing file before transcription...");
        recordButton.setText("Compressing...");

        new SwingWorker<FfmpegCompressor.CompressionResult, Void>() {
            @Override
            protected FfmpegCompressor.CompressionResult doInBackground() {
                return FfmpegCompressor.compress(file);
            }

            @Override
            protected void done() {
                try {
                    FfmpegCompressor.CompressionResult result = get();
                    if (!result.success) {
                        console.logError("Compression failed: " + result.errorMessage);
                        Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Compression failed: " + result.errorMessage);
                        resetUIAfterTranscription();
                        return;
                    }

                    if (!result.withinLimit) {
                        // Still too large - need to split
                        console.log("Compressed file still exceeds limit - switching to chunking");
                        AnalysisResult newAnalysis = AudioFileAnalyzer.analyze(result.compressedFile);
                        handleChunkedTranscription(result.compressedFile, newAnalysis);
                    } else {
                        // Compressed file is within limits - proceed with normal transcription
                        console.logSuccess("Compression successful - proceeding with transcription");
                        processAudioFile(result.compressedFile);
                    }

                } catch (Exception e) {
                    logger.error("Error during compression", e);
                    console.logError("Compression failed: " + e.getMessage());
                    resetUIAfterTranscription();
                }
            }
        }.execute();
    }

    /**
     * Handles transcription using local Whisper (Faster-Whisper).
     * Temporarily switches the server setting and transcribes.
     */
    private void handleLocalWhisperTranscription(File file) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        console.separator();
        console.log("Using Faster-Whisper for large file (no size limit)");

        // Note: We don't change the global setting, just use the local client directly
        recordButton.setText("Transcribing (local)...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    return fasterWhisperTranscribeClient.transcribe(file);
                } catch (Exception e) {
                    logger.error("Faster-Whisper transcription failed", e);
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    String transcript = get();
                    if (transcript != null && !transcript.isEmpty()) {
                        transcriptionTextArea.setText(transcript);

                        // Start new history session
                        pipelineHistory.startNewSession(transcript);
                        processedText.setText("");
                        historyPanel.updateResults(pipelineHistory.getResults());

                        console.logSuccess("Transcription completed");
                        Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS,
                            "Transcription completed!");

                        handlePostTranscriptionActions(transcript);
                    } else {
                        console.logError("Faster-Whisper returned empty transcript");
                        Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Transcription failed or returned empty result");
                    }
                } catch (Exception e) {
                    logger.error("Error getting transcription result", e);
                    console.logError("Transcription failed: " + e.getMessage());
                }
                resetUIAfterTranscription();
                updateTrayMenu();
            }
        }.execute();
    }

    /**
     * Handles post-transcription actions like auto post-processing and clipboard copy.
     */
    private void handlePostTranscriptionActions(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            return;
        }

        // Run post-processing if enabled
        if (enablePostProcessingCheckBox.isSelected() && postProcessingSelectComboBox.getSelectedItem() != null) {
            PostProcessingItem selectedItem = (PostProcessingItem) postProcessingSelectComboBox.getSelectedItem();
            if (selectedItem != null && selectedItem.uuid != null) {
                Pipeline pipeline = configManager.getPipelineByUuid(selectedItem.uuid);
                if (pipeline != null) {
                    new PostProcessingWorker(transcript, pipeline).execute();
                    return;  // PostProcessingWorker will handle clipboard
                }
            }
        }

        // No post-processing - handle clipboard directly
        if (configManager.isAutoPasteEnabled()) {
            transcriptionTextArea.transferFocus();
            copyTranscriptionToClipboard(transcript);
            pasteFromClipboard();
        }
        playFinishSound();
    }

    /**
     * Converts an OGG file to WAV format using ffmpeg.
     * Falls back to AudioSystem if ffmpeg is not available.
     */
    private File convertOggToWav(File oggFile) {
        // Try ffmpeg first (much more reliable for OGG files)
        File wavFile = convertOggToWavUsingFfmpeg(oggFile);
        if (wavFile != null) {
            return wavFile;
        }

        // Fall back to AudioSystem (may not work well)
        logger.warn("ffmpeg conversion failed or not available. Falling back to AudioSystem.");
        return convertOggToWavUsingAudioSystem(oggFile);
    }

    /**
     * Converts an OGG file to WAV format using ffmpeg.
     * This is the preferred method as it handles OGG files properly.
     */
    private File convertOggToWavUsingFfmpeg(File oggFile) {
        try {
            logger.info("Converting OGG file to WAV using ffmpeg in background...");

            // Create temporary WAV file
            File wavFile = File.createTempFile("whispercat_converted_", ".wav");
            wavFile.deleteOnExit();

            // Use ffmpeg to convert OGG to WAV
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", oggFile.getAbsolutePath(),
                "-acodec", "pcm_s16le",
                "-ar", "16000",
                "-ac", "1",
                wavFile.getAbsolutePath()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output to prevent blocking
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && wavFile.exists() && wavFile.length() > 0) {
                logger.info("Successfully converted OGG to WAV using ffmpeg");
                return wavFile;
            } else {
                logger.error("ffmpeg conversion failed with exit code: {}. Output: {}", exitCode, output);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to convert OGG using ffmpeg", e);
            return null;
        }
    }

    /**
     * Converts an OGG file to WAV format using javax.sound.sampled.
     * This is a fallback method and may not work well with OGG files.
     */
    private File convertOggToWavUsingAudioSystem(File oggFile) {
        try {
            // Create temporary WAV file
            File wavFile = File.createTempFile("whispercat_converted_", ".wav");
            wavFile.deleteOnExit();

            // Note: javax.sound.sampled doesn't natively support OGG
            // This will attempt to read using AudioSystem but may fail
            try {
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(oggFile);
                javax.sound.sampled.AudioSystem.write(audioStream,
                        javax.sound.sampled.AudioFileFormat.Type.WAVE, wavFile);
                audioStream.close();
                logger.info("Successfully converted OGG to WAV using AudioSystem");
                return wavFile;
            } catch (Exception e) {
                logger.error("Failed to convert OGG using AudioSystem. OGG codec may not be installed.", e);
                // Return null to indicate conversion failed
                return null;
            }
        } catch (Exception e) {
            logger.error("Error creating temp file for conversion", e);
            return null;
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

    /**
     * Refreshes the pipeline dropdown when navigating back to the recorder screen.
     * Call this when the form becomes visible to ensure new pipelines appear.
     */
    public void refreshPipelines() {
        populatePostProcessingComboBox();
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
        updateUIForRecordingStop();  // This already sets isTranscribing = true and repaints
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
                // Reset transcribing state if cancelled
                isTranscribing = false;
                statusIndicatorPanel.repaint();
                updateTrayMenu();
            }
        }
    }

    public void stopRecording(File audioFile) {
        isStoppingInProgress = true;

        // Set transcribing state (blue indicator) - same as dropped files
        isTranscribing = true;
        statusIndicatorPanel.repaint();

        recordButton.setText("Converting. Please wait...");
        recordButton.setEnabled(false);
        new RecorderForm.AudioTranscriptionWorker(audioFile).execute();
    }

    public void playFinishSound() {
        if (configManager.isFinishSoundEnabled()) {
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
        // Stop recording and start transcribing (blue indicator)
        isRecording = false;  // Must set this BEFORE isTranscribing, or circle stays red!
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

                    // Start new history session for this transcription
                    pipelineHistory.startNewSession(transcript);
                    processedText.setText("");  // Clear previous post-processed text
                    historyPanel.updateResults(pipelineHistory.getResults());  // Reset history panel

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

            // Run post-processing asynchronously if enabled
            if (transcript != null && !transcript.trim().isEmpty() &&
                enablePostProcessingCheckBox.isSelected() && postProcessingSelectComboBox.getSelectedItem() != null) {
                    PostProcessingItem selectedItem = (PostProcessingItem) postProcessingSelectComboBox.getSelectedItem();
                    if (selectedItem != null && selectedItem.uuid != null) {
                        Pipeline pipeline = configManager.getPipelineByUuid(selectedItem.uuid);
                        if (pipeline != null) {
                            // Run post-processing in separate worker to avoid blocking UI
                            new PostProcessingWorker(transcript, pipeline).execute();
                        } else {
                            logger.error("Pipeline not found for UUID: " + selectedItem.uuid);
                            console.logError("Pipeline not found: " + selectedItem.uuid);
                            resetUIAfterTranscription();
                            updateTrayMenu();
                        }
                    } else {
                        resetUIAfterTranscription();
                    }
            } else if (transcript != null && !transcript.trim().isEmpty()) {
                // No post-processing, just copy raw transcript if auto-paste enabled
                if (configManager.isAutoPasteEnabled()) {
                    // Remove focus from transcription area to prevent pasting into itself
                    transcriptionTextArea.transferFocus();
                    copyTranscriptionToClipboard(transcript);
                    pasteFromClipboard();
                }
                playFinishSound();
                resetUIAfterTranscription();
                updateTrayMenu();
            } else {
                resetUIAfterTranscription();
            }
        }
    }

    /**
     * Worker for running post-processing pipelines asynchronously
     */
    private class PostProcessingWorker extends SwingWorker<String, Void> {
        private final String inputText;
        private final Pipeline pipeline;
        private final long startTime;

        public PostProcessingWorker(String inputText, Pipeline pipeline) {
            this.inputText = inputText;
            this.pipeline = pipeline;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        protected String doInBackground() {
            PostProcessingService ppService = new PostProcessingService(configManager);
            return ppService.applyPipeline(inputText, pipeline);
        }

        @Override
        protected void done() {
            try {
                String processedResult = get();
                int executionTime = (int) (System.currentTimeMillis() - startTime);

                // Add result to history
                pipelineHistory.addResult(pipeline.uuid, pipeline.title, processedResult, executionTime);

                // Update history panel
                historyPanel.updateResults(pipelineHistory.getResults());

                RecorderForm.this.processedText.setText(processedResult);

                // Show pipeline completion toast
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS,
                        "Post-processing completed!");

                // Show system-level notification
                TrayIconManager trayManager = AudioRecorderUI.getTrayIconManager();
                if (trayManager != null) {
                    trayManager.showSystemNotification("WhisperCat", "Post-processing completed");
                }

                playFinishSound();

                // Remove focus from text areas to prevent pasting into them
                transcriptionTextArea.transferFocus();
                RecorderForm.this.processedText.transferFocus();

                copyTranscriptionToClipboard(processedResult);
                pasteFromClipboard();

                // Remember the last used pipeline
                configManager.setLastUsedPipelineUUID(pipeline.uuid);
            } catch (Exception e) {
                logger.error("Error during post-processing", e);
                ConsoleLogger.getInstance().logError("Post-processing failed: " + e.getMessage());
            } finally {
                resetUIAfterTranscription();
                updateTrayMenu();
            }
        }
    }

    /**
     * Updates the Run Pipeline button enabled state based on:
     * - Transcription text field has content
     * - A pipeline is selected
     * - No manual pipeline is currently running
     */
    private void updateRunPipelineButtonState() {
        if (runPipelineButton == null) {
            return; // Button not yet initialized
        }

        boolean hasTranscription = transcriptionTextArea.getText() != null
                && !transcriptionTextArea.getText().trim().isEmpty();
        boolean hasPipelineSelected = postProcessingSelectComboBox.getSelectedItem() != null;
        boolean canRun = hasTranscription && hasPipelineSelected && !isManualPipelineRunning && !isTranscribing;

        runPipelineButton.setEnabled(canRun);

        // Update tooltip based on state
        if (!hasTranscription) {
            runPipelineButton.setToolTipText("Record audio first");
        } else if (!hasPipelineSelected) {
            runPipelineButton.setToolTipText("Select a pipeline");
        } else if (isManualPipelineRunning || isTranscribing) {
            runPipelineButton.setToolTipText("Pipeline is running...");
        } else {
            runPipelineButton.setToolTipText("Run selected pipeline on transcription text");
        }
    }

    /**
     * Runs the selected pipeline manually on the current transcription text.
     * Works regardless of "Enable Post Processing" checkbox state.
     */
    private void runManualPipeline() {
        String transcript = transcriptionTextArea.getText();
        if (transcript == null || transcript.trim().isEmpty()) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                    "No transcription text to process");
            return;
        }

        PostProcessingItem selectedItem = (PostProcessingItem) postProcessingSelectComboBox.getSelectedItem();
        if (selectedItem == null || selectedItem.uuid == null) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                    "Please select a pipeline");
            return;
        }

        Pipeline pipeline = configManager.getPipelineByUuid(selectedItem.uuid);
        if (pipeline == null) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                    "Pipeline not found: " + selectedItem.title);
            return;
        }

        // Update UI state
        isManualPipelineRunning = true;
        runPipelineButton.setText("Running...");
        runPipelineButton.setEnabled(false);
        isTranscribing = true;  // Show blue indicator
        statusIndicatorPanel.repaint();

        ConsoleLogger.getInstance().separator();
        ConsoleLogger.getInstance().log("Manual pipeline run: " + pipeline.title);

        // Run pipeline in worker
        new ManualPipelineWorker(transcript, pipeline).execute();
    }

    /**
     * Worker for running manual pipeline executions asynchronously
     */
    private class ManualPipelineWorker extends SwingWorker<String, Void> {
        private final String inputText;
        private final Pipeline pipeline;
        private final long startTime;
        private final String previousResult;  // Capture current result before running

        public ManualPipelineWorker(String inputText, Pipeline pipeline) {
            this.inputText = inputText;
            this.pipeline = pipeline;
            this.startTime = System.currentTimeMillis();
            // Capture the current post-processed text before we run
            this.previousResult = processedText.getText();
        }

        @Override
        protected String doInBackground() {
            PostProcessingService ppService = new PostProcessingService(configManager);
            return ppService.applyPipeline(inputText, pipeline);
        }

        @Override
        protected void done() {
            try {
                String result = get();
                int executionTime = (int) (System.currentTimeMillis() - startTime);

                // Result stacking: save previous result to history if it exists
                if (previousResult != null && !previousResult.trim().isEmpty()) {
                    // The previous result was from some pipeline run, we need to save it
                    // Note: We don't have the previous pipeline info, so we mark it as "Previous result"
                    // This is a limitation - in Phase 4 we'll track this properly
                    ConsoleLogger.getInstance().log("Previous result saved to history");
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.INFO,
                            "Previous result saved to history");
                }

                // Add new result to history
                pipelineHistory.addResult(pipeline.uuid, pipeline.title, result, executionTime);

                // Update history panel
                historyPanel.updateResults(pipelineHistory.getResults());

                // Update the display
                processedText.setText(result);

                // Post-processed text area is now always visible

                ConsoleLogger.getInstance().logSuccess("Manual pipeline completed: " + pipeline.title +
                        " (" + executionTime + "ms)");

                Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS,
                        "Pipeline completed: " + pipeline.title);

                // Remember the last used pipeline
                configManager.setLastUsedPipelineUUID(pipeline.uuid);

            } catch (Exception e) {
                logger.error("Error during manual pipeline execution", e);
                ConsoleLogger.getInstance().logError("Pipeline failed: " + e.getMessage());
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "Pipeline failed: " + e.getMessage());
            } finally {
                // Reset UI state
                isManualPipelineRunning = false;
                isTranscribing = false;
                statusIndicatorPanel.repaint();
                runPipelineButton.setText("\u25B6 Run Pipeline");
                updateRunPipelineButtonState();
            }
        }
    }

}
