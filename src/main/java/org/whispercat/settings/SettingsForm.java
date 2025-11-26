package org.whispercat.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConfigManager;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;
import org.whispercat.recording.clients.FasterWhisperModel;
import org.whispercat.recording.clients.FasterWhisperModelsResponse;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class SettingsForm extends JPanel {
    private static final Logger logger = LogManager.getLogger(SettingsForm.class);
    // Existing components
    private final KeyCombinationTextField keyCombinationTextField;
    private final JButton clearKeybindButton;
    private final KeySequenceTextField keySequenceTextField;
    private final JButton clearKeySequenceButton;
    private final JComboBox<String> microphoneComboBox;
    private final JComboBox<Integer> bitrateComboBox;
    private final ConfigManager configManager;
    private final JCheckBox stopSoundSwitch;
    private final ThresholdProgressBar volumeBar;
    private final JButton stopTestButton;
    private final JButton testMicrophoneButton;

    // Track if settings have been modified
    private boolean settingsDirty = false;

    // Silence removal settings
    private JCheckBox silenceRemovalSwitch;
    private JSlider silenceThresholdSlider;
    private JSlider minSilenceDurationSlider;
    private JSlider minRecordingDurationSlider;
    private JCheckBox keepCompressedSwitch;
    private AudioFormat format;
    private TargetDataLine line;
    private TestWorker testWorker;

    private final JLabel whisperServerLabel;
    private final JComboBox<String> whisperServerComboBox;
    private final JPanel whisperSettingsPanel;
    private final JPanel fasterWhispererPanel;
    private final JPanel groqPanel;
    private final JPanel openaiPanel;
    private final JPanel openWebUIPanel;

    private final JTextField whisperServerUrlField;
    private final JComboBox<String> fasterWhisperModelComboBox;
    private final JComboBox<String> fasterWhisperLanguageComboBox;

    private final JTextField groqApiKeyField;
    private final JComboBox<String> groqModelComboBox;

    private JTextField openaiApiKeyField;
    private JTextField customOpenAIModelsField;

    private JTextField grokApiKeyField;

    private JTextField openwebUIApiKeyField;
    private JTextField openwebUIApiURLField;

    private static final String SERVER_FASTER_WHISPER = "Faster-Whisper";
    private static final String OPEN_WEB_UI = "Open WebUI";
    private static final String SERVER_GROQ = "Groq";
    private static final String SERVER_OPENAI = "OpenAI";

    private final Map<String, List<String>> fastModelLanguages;

    public SettingsForm(ConfigManager configManager) {
        this.configManager = configManager;

        volumeBar = new ThresholdProgressBar(0, 100);
        volumeBar.setStringPainted(true);
        volumeBar.setVisible(false);
        // Set initial threshold from config (threshold is 0.0-1.0, bar is 0-100)
        float initialThreshold = configManager.getSilenceThreshold();
        volumeBar.setThreshold((int)(initialThreshold * 100));
        stopTestButton = new JButton("Stop Test");
        stopTestButton.setVisible(false);
        stopTestButton.addActionListener(e -> stopAudioTest());

        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getResourceAsStream("/fasterwhispermodels.json")) {
            FasterWhisperModelsResponse response = mapper.readValue(is, FasterWhisperModelsResponse.class);
            fastModelLanguages = response.getData()
                    .stream()
                    .collect(Collectors.toMap(
                            FasterWhisperModel::getId,
                            model -> {
                                List<String> sortedLangs = new ArrayList<>(model.getLanguage());
                                Collections.sort(sortedLangs);
                                List<String> langs = new ArrayList<>();
                                langs.add("");
                                langs.addAll(sortedLangs);
                                return langs;
                            }
                    ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load fasterwhispermodels.json", e);
        }

        JPanel contentPanel = new JPanel(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(60, 20, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        // Row: Global key combination
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Global key combination:"), gbc);
        keyCombinationTextField = new KeyCombinationTextField();
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(keyCombinationTextField, gbc);
        clearKeybindButton = new JButton("Delete");
        gbc.gridx = 2;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        contentPanel.add(clearKeybindButton, gbc);
        clearKeybindButton.addActionListener(e -> {
            keyCombinationTextField.setText("");
            keyCombinationTextField.setKeysDisplayed(new HashSet<>());
        });

        // Row: Global key sequence
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Global key sequence:"), gbc);
        keySequenceTextField = new KeySequenceTextField();
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(keySequenceTextField, gbc);
        clearKeySequenceButton = new JButton("Delete");
        gbc.gridx = 2;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        contentPanel.add(clearKeySequenceButton, gbc);
        clearKeySequenceButton.addActionListener(e -> {
            keySequenceTextField.setText("");
            keySequenceTextField.setKeysDisplayed(new ArrayList<>());
        });

        // Row: Microphone selection
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Microphone:"), gbc);
        microphoneComboBox = new JComboBox<>(getAvailableMicrophones());
        microphoneComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                settingsDirty = true;
            }
        });
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(microphoneComboBox, gbc);
        microphoneComboBox.addActionListener(e -> stopAudioTest());
        testMicrophoneButton = new JButton("Test");
        gbc.gridx = 3;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        contentPanel.add(testMicrophoneButton, gbc);
        testMicrophoneButton.addActionListener(e -> {
            String selectedMicrophone = (String) microphoneComboBox.getSelectedItem();
            if (selectedMicrophone != null && !selectedMicrophone.isEmpty()) {
                startAudioTest(selectedMicrophone);
                volumeBar.setVisible(true);
                stopTestButton.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "No Mic selected. Please select Mic.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Row: Volume bar and Stop Test button
        row++;
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(volumeBar, gbc);
        gbc.gridx = 3;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        contentPanel.add(stopTestButton, gbc);

        // Row: Bitrate selection
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Bitrate:"), gbc);
        Integer[] bitrates = {16000, 18000, 20000, 22000, 24000, 26000, 28000, 30000, 32000};
        bitrateComboBox = new JComboBox<>(bitrates);
        bitrateComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                settingsDirty = true;
            }
        });
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(bitrateComboBox, gbc);

        // Row: Enable Finish Sound
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Enable Finish Sound:"), gbc);
        stopSoundSwitch = new JCheckBox();
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        stopSoundSwitch.addActionListener(e -> settingsDirty = true);
        contentPanel.add(stopSoundSwitch, gbc);

        row++;

        // Silence Removal Settings
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Auto-remove silence:"), gbc);
        silenceRemovalSwitch = new JCheckBox();
        silenceRemovalSwitch.setSelected(configManager.isSilenceRemovalEnabled());
        silenceRemovalSwitch.addActionListener(e -> settingsDirty = true);
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(silenceRemovalSwitch, gbc);

        row++;

        // Silence threshold slider
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        JLabel thresholdLabel = new JLabel("Silence threshold:");
        contentPanel.add(thresholdLabel, gbc);

        JPanel thresholdPanel = new JPanel(new BorderLayout(5, 0));
        silenceThresholdSlider = new JSlider(1, 50, (int)(configManager.getSilenceThreshold() * 1000));
        silenceThresholdSlider.setMajorTickSpacing(10);
        silenceThresholdSlider.setMinorTickSpacing(5);
        silenceThresholdSlider.setPaintTicks(true);
        JLabel thresholdValueLabel = new JLabel(String.format("%.3f", configManager.getSilenceThreshold()));
        thresholdPanel.add(silenceThresholdSlider, BorderLayout.CENTER);
        thresholdPanel.add(thresholdValueLabel, BorderLayout.EAST);

        silenceThresholdSlider.addChangeListener(e -> {
            float value = silenceThresholdSlider.getValue() / 1000.0f;
            thresholdValueLabel.setText(String.format("%.3f", value));
            // Update threshold indicator on volume bar
            volumeBar.setThreshold((int)(value * 100));
            // Mark settings as dirty when user interacts
            settingsDirty = true;
            // Auto-save when slider stops moving (not dragging)
            if (!silenceThresholdSlider.getValueIsAdjusting()) {
                configManager.setSilenceThreshold(value);
            }
        });

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(thresholdPanel, gbc);

        row++;

        // Hint for threshold slider
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel thresholdHint = new JLabel("<html><i>Lower = conservative (removes less), Higher = aggressive (removes more)</i></html>");
        thresholdHint.setFont(new Font("Dialog", Font.PLAIN, 10));
        thresholdHint.setForeground(Color.GRAY);
        contentPanel.add(thresholdHint, gbc);

        row++;

        // Minimum silence duration slider
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        JLabel durationLabel = new JLabel("Min silence duration:");
        contentPanel.add(durationLabel, gbc);

        JPanel durationPanel = new JPanel(new BorderLayout(5, 0));
        minSilenceDurationSlider = new JSlider(500, 10000, configManager.getMinSilenceDuration());
        minSilenceDurationSlider.setMajorTickSpacing(1000);
        minSilenceDurationSlider.setMinorTickSpacing(500);
        minSilenceDurationSlider.setPaintTicks(true);
        JLabel durationValueLabel = new JLabel(configManager.getMinSilenceDuration() + "ms");
        durationPanel.add(minSilenceDurationSlider, BorderLayout.CENTER);
        durationPanel.add(durationValueLabel, BorderLayout.EAST);

        minSilenceDurationSlider.addChangeListener(e -> {
            durationValueLabel.setText(minSilenceDurationSlider.getValue() + "ms");
            // Auto-save when slider stops moving
            if (!minSilenceDurationSlider.getValueIsAdjusting()) {
                configManager.setMinSilenceDuration(minSilenceDurationSlider.getValue());
            }
        });

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(durationPanel, gbc);

        row++;

        // Hint for duration slider
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel durationHint = new JLabel("<html><i>Minimum consecutive silence to remove (higher = more conservative)</i></html>");
        durationHint.setFont(new Font("Dialog", Font.PLAIN, 10));
        durationHint.setForeground(Color.GRAY);
        contentPanel.add(durationHint, gbc);

        row++;

        // Minimum recording duration slider
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        JLabel minRecDurationLabel = new JLabel("Min recording for removal:");
        contentPanel.add(minRecDurationLabel, gbc);

        JPanel minRecDurationPanel = new JPanel(new BorderLayout(5, 0));
        minRecordingDurationSlider = new JSlider(0, 60, configManager.getMinRecordingDurationForSilenceRemoval());
        minRecordingDurationSlider.setMajorTickSpacing(10);
        minRecordingDurationSlider.setMinorTickSpacing(5);
        minRecordingDurationSlider.setPaintTicks(true);
        JLabel minRecDurationValueLabel = new JLabel(configManager.getMinRecordingDurationForSilenceRemoval() + "s");
        minRecDurationPanel.add(minRecordingDurationSlider, BorderLayout.CENTER);
        minRecDurationPanel.add(minRecDurationValueLabel, BorderLayout.EAST);

        minRecordingDurationSlider.addChangeListener(e -> {
            int value = minRecordingDurationSlider.getValue();
            minRecDurationValueLabel.setText(value + "s");
            // Auto-save when slider stops moving
            if (!minRecordingDurationSlider.getValueIsAdjusting()) {
                configManager.setMinRecordingDurationForSilenceRemoval(value);
            }
        });

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(minRecDurationPanel, gbc);

        row++;

        // Hint for minimum recording duration
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel minRecDurationHint = new JLabel("<html><i>Only apply silence removal to recordings longer than this (0 = always apply)</i></html>");
        minRecDurationHint.setFont(new Font("Dialog", Font.PLAIN, 10));
        minRecDurationHint.setForeground(Color.GRAY);
        contentPanel.add(minRecDurationHint, gbc);

        row++;

        // Keep compressed files checkbox
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Keep compressed files:"), gbc);
        keepCompressedSwitch = new JCheckBox();
        keepCompressedSwitch.setSelected(configManager.isKeepCompressedFile());
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(keepCompressedSwitch, gbc);

        row++;

        JPanel apiSettingsPanel = new JPanel(new GridBagLayout());
        apiSettingsPanel.setBorder(BorderFactory.createTitledBorder("API Settings"));
        GridBagConstraints apiGbc = new GridBagConstraints();
        apiGbc.insets = new Insets(5, 5, 5, 5);
        apiGbc.fill = GridBagConstraints.HORIZONTAL;
        int apiRow = 0;

// ----- OpenAI API Key -----
        apiGbc.gridx = 0;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 1;
        apiGbc.weightx = 0;
        apiGbc.anchor = GridBagConstraints.EAST;
        apiSettingsPanel.add(new JLabel("OpenAI API Key:"), apiGbc);
        openaiApiKeyField = new JTextField(20);
        apiGbc.gridx = 1;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 2;
        apiGbc.weightx = 1.0;
        apiGbc.anchor = GridBagConstraints.WEST;
        apiSettingsPanel.add(openaiApiKeyField, apiGbc);

        apiRow++;

// ----- Custom OpenAI Models -----
        apiGbc.gridx = 0;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 1;
        apiGbc.weightx = 0;
        apiGbc.anchor = GridBagConstraints.EAST;
        apiSettingsPanel.add(new JLabel("Custom OpenAI Models:"), apiGbc);
        customOpenAIModelsField = new JTextField(20);
        apiGbc.gridx = 1;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 2;
        apiGbc.weightx = 1.0;
        apiGbc.anchor = GridBagConstraints.WEST;
        apiSettingsPanel.add(customOpenAIModelsField, apiGbc);

        apiRow++;

// ----- Hint Label for Custom OpenAI Models -----
        JLabel modelsHintLabel = new JLabel("Comma-separated list (e.g., gpt-4o-mini, gpt-5-nano, gpt-5-mini)");
        modelsHintLabel.setFont(new Font("Dialog", Font.ITALIC, 10));
        modelsHintLabel.setForeground(Color.GRAY);
        apiGbc.gridx = 1;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 2;
        apiGbc.anchor = GridBagConstraints.WEST;
        apiSettingsPanel.add(modelsHintLabel, apiGbc);

        apiRow++;
// ----- Separator between OpenAI and Grok -----
        JSeparator grokSeperator = new JSeparator();
        grokSeperator.setForeground(Color.GRAY);
        grokSeperator.setMinimumSize(new Dimension(0, 2));

        apiGbc.gridx = 0;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 3;
        apiGbc.fill = GridBagConstraints.HORIZONTAL;
        apiSettingsPanel.add(grokSeperator, apiGbc);
        apiGbc.fill = GridBagConstraints.HORIZONTAL; // reset fill
        apiRow++;

// ----- Grok API Key -----
        apiGbc.gridx = 0;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 1;
        apiGbc.weightx = 0;
        apiGbc.anchor = GridBagConstraints.EAST;
        apiSettingsPanel.add(new JLabel("Grok API Key:"), apiGbc);
        grokApiKeyField = new JTextField(20);
        apiGbc.gridx = 1;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 2;
        apiGbc.weightx = 1.0;
        apiGbc.anchor = GridBagConstraints.WEST;
        apiSettingsPanel.add(grokApiKeyField, apiGbc);

        apiRow++;

        JSeparator openWebUISeperator = new JSeparator();
        openWebUISeperator.setForeground(Color.GRAY);
        openWebUISeperator.setMinimumSize(new Dimension(0, 2));


        apiGbc.gridx = 0;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 3;
        apiGbc.fill = GridBagConstraints.HORIZONTAL;
        apiSettingsPanel.add(openWebUISeperator, apiGbc);
        apiGbc.fill = GridBagConstraints.HORIZONTAL;
        apiRow++;

// ----- OpenWebUI API Key -----
        apiGbc.gridx = 0;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 1;
        apiGbc.weightx = 0;
        apiGbc.anchor = GridBagConstraints.EAST;
        apiSettingsPanel.add(new JLabel("OpenWebUI API Key:"), apiGbc);
        openwebUIApiKeyField = new JTextField(20);
        apiGbc.gridx = 1;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 2;
        apiGbc.weightx = 1.0;
        apiGbc.anchor = GridBagConstraints.WEST;
        apiSettingsPanel.add(openwebUIApiKeyField, apiGbc);

        apiRow++;

// ----- OpenWebUI Server URL -----
        apiGbc.gridx = 0;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 1;
        apiGbc.weightx = 0;
        apiGbc.anchor = GridBagConstraints.EAST;
        apiSettingsPanel.add(new JLabel("OpenWebUI Server URL:"), apiGbc);
        openwebUIApiURLField = new JTextField(20);
        apiGbc.gridx = 1;
        apiGbc.gridy = apiRow;
        apiGbc.gridwidth = 2;
        apiGbc.weightx = 1.0;
        apiGbc.anchor = GridBagConstraints.WEST;
        apiSettingsPanel.add(openwebUIApiURLField, apiGbc);

// Add the API Settings panel to the content panel
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        contentPanel.add(apiSettingsPanel, gbc);

        row++;

        // ===== New Section: Whispering Server Selection and Configuration =====
        // Row: Whisper Server drop-down selection
        row++;
        whisperServerLabel = new JLabel("Choose Whisper Server:");
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(whisperServerLabel, gbc);
        String[] whisperServers = {SERVER_OPENAI, SERVER_FASTER_WHISPER, OPEN_WEB_UI}; // TODO: Add GROQ
        whisperServerComboBox = new JComboBox<>(whisperServers);
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(whisperServerComboBox, gbc);

        // Row: Whisper server settings panel (using CardLayout)
        row++;
        JPanel whisperContainerPanel = new JPanel(new BorderLayout());
        whisperContainerPanel.setBorder(BorderFactory.createTitledBorder("Whisper Server Settings"));
        whisperSettingsPanel = new JPanel(new CardLayout());

        // ----- Initialize Faster-Whisperer Panel (API-Key removal, neue Sprache-Selectbox) -----
        fasterWhispererPanel = new JPanel(new GridBagLayout());
        GridBagConstraints fwGbc = new GridBagConstraints();
        fwGbc.insets = new Insets(5, 5, 5, 5);
        fwGbc.fill = GridBagConstraints.HORIZONTAL;
        int fwRow = 0;
        // Server URL for Faster-Whisperer
        fwGbc.gridx = 0;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 1;
        fwGbc.weightx = 0;
        fwGbc.anchor = GridBagConstraints.EAST;
        fasterWhispererPanel.add(new JLabel("Server URL:"), fwGbc);
        whisperServerUrlField = new JTextField(20);
        fwGbc.gridx = 1;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 2;
        fwGbc.weightx = 1.0;
        fwGbc.anchor = GridBagConstraints.WEST;
        fasterWhispererPanel.add(whisperServerUrlField, fwGbc);
        fwRow++;

        JLabel urlHintLabel = new JLabel("Example: http://localhost:8000");
        urlHintLabel.setFont(new Font("Dialog", Font.ITALIC, 10));
        urlHintLabel.setForeground(Color.GRAY);
        fwGbc.gridx = 1;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 2;
        fwGbc.anchor = GridBagConstraints.WEST;
        fasterWhispererPanel.add(urlHintLabel, fwGbc);

        fwRow++;
        // Model selection for Faster-Whisperer
        fwGbc.gridx = 0;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 1;
        fwGbc.weightx = 0;
        fwGbc.anchor = GridBagConstraints.EAST;
        fasterWhispererPanel.add(new JLabel("Model:"), fwGbc);
        String[] fasterModels = fastModelLanguages.keySet().stream().sorted().toArray(String[]::new);
        fasterWhisperModelComboBox = new JComboBox<>(fasterModels);
        fwGbc.gridx = 1;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 2;
        fwGbc.weightx = 1.0;
        fwGbc.anchor = GridBagConstraints.WEST;
        fasterWhispererPanel.add(fasterWhisperModelComboBox, fwGbc);
        fwRow++;
        // Language selection for Faster-Whisperer
        fwGbc.gridx = 0;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 1;
        fwGbc.weightx = 0;
        fwGbc.anchor = GridBagConstraints.EAST;
        fasterWhispererPanel.add(new JLabel("Language:"), fwGbc);
        fasterWhisperLanguageComboBox = new JComboBox<>();
        fwGbc.gridx = 1;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 2;
        fwGbc.weightx = 1.0;
        fwGbc.anchor = GridBagConstraints.WEST;
        fasterWhispererPanel.add(fasterWhisperLanguageComboBox, fwGbc);

        // Action listener to update available languages whenever the model selection changes.
        fasterWhisperModelComboBox.addActionListener(e -> updateFasterWhisperLanguages());

        // ----- Initialize Groq Panel -----
        groqPanel = new JPanel(new GridBagLayout());
        GridBagConstraints groqGbc = new GridBagConstraints();
        groqGbc.insets = new Insets(5, 5, 5, 5);
        groqGbc.fill = GridBagConstraints.HORIZONTAL;
        int groqRow = 0;
        groqGbc.gridx = 0;
        groqGbc.gridy = groqRow;
        groqGbc.gridwidth = 1;
        groqGbc.weightx = 0;
        groqGbc.anchor = GridBagConstraints.EAST;
        groqPanel.add(new JLabel("Whisper API Key:"), groqGbc);
        groqApiKeyField = new JTextField(20);
        groqGbc.gridx = 1;
        groqGbc.gridy = groqRow;
        groqGbc.gridwidth = 2;
        groqGbc.weightx = 1.0;
        groqGbc.anchor = GridBagConstraints.WEST;
        groqPanel.add(groqApiKeyField, groqGbc);
        groqRow++;
        groqGbc.gridx = 0;
        groqGbc.gridy = groqRow;
        groqGbc.gridwidth = 1;
        groqGbc.weightx = 0;
        groqGbc.anchor = GridBagConstraints.EAST;
        groqPanel.add(new JLabel("Model:"), groqGbc);
        String[] groqModels = {"groq-model-1", "groq-model-2"}; // Dummy models
        groqModelComboBox = new JComboBox<>(groqModels);
        groqGbc.gridx = 1;
        groqGbc.gridy = groqRow;
        groqGbc.gridwidth = 2;
        groqGbc.weightx = 1.0;
        groqGbc.anchor = GridBagConstraints.WEST;
        groqPanel.add(groqModelComboBox, groqGbc);

        // ----- Initialize OpenAI Panel -----
        openaiPanel = new JPanel(new GridBagLayout());
        GridBagConstraints openaiGbc = new GridBagConstraints();
        openaiGbc.insets = new Insets(5, 5, 5, 5);
        openaiGbc.fill = GridBagConstraints.HORIZONTAL;
        int openaiRow = 0;
        openaiGbc.gridx = 0;
        openaiGbc.gridy = openaiRow;
        openaiGbc.gridwidth = 1;
        openaiGbc.weightx = 0;
        openaiGbc.anchor = GridBagConstraints.EAST;
        JLabel noSettingsLabel = new JLabel("No configuration required at this time :-)");
        openaiPanel.add(noSettingsLabel, openaiGbc);

        // ----- Initialize Open WebUI Panel -----
        openWebUIPanel = new JPanel(new GridBagLayout());
        GridBagConstraints openWebUIGbc = new GridBagConstraints();
        openWebUIGbc.insets = new Insets(5, 5, 5, 5);
        openWebUIGbc.fill = GridBagConstraints.HORIZONTAL;
        int openWebUIGbcRow = 0;
        openWebUIGbc.gridx = 0;
        openWebUIGbc.gridy = openWebUIGbcRow;
        openWebUIGbc.gridwidth = 1;
        openWebUIGbc.weightx = 0;
        openWebUIGbc.anchor = GridBagConstraints.EAST;
        JLabel openWebUInoSettingsLabel = new JLabel("No configuration required at this time :-)");
        openWebUIPanel.add(openWebUInoSettingsLabel, openaiGbc);

        // Add sub-panels to the card layout panel
        whisperSettingsPanel.add(openaiPanel, SERVER_OPENAI);
        whisperSettingsPanel.add(fasterWhispererPanel, SERVER_FASTER_WHISPER);
        whisperSettingsPanel.add(openWebUIPanel, OPEN_WEB_UI);
        whisperSettingsPanel.add(groqPanel, SERVER_GROQ);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        whisperContainerPanel.add(whisperSettingsPanel, BorderLayout.CENTER);
        contentPanel.add(whisperContainerPanel, gbc);

        // Add an ItemListener to switch cards based on Whisper Server selection
        whisperServerComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                CardLayout cl = (CardLayout) (whisperSettingsPanel.getLayout());
                String selectedServer = (String) whisperServerComboBox.getSelectedItem();
                cl.show(whisperSettingsPanel, selectedServer);
            }
        });
        // Set initial card based on default selection
        CardLayout cl = (CardLayout) (whisperSettingsPanel.getLayout());
        cl.show(whisperSettingsPanel, (String) whisperServerComboBox.getSelectedItem());

        // Add Apply Settings button at the bottom
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 15));
        JButton applyButton = new JButton("Apply Settings");
        applyButton.setToolTipText("Save all settings now");
        applyButton.addActionListener(e -> {
            boolean hadChanges = settingsDirty;
            saveSettings(e);
            if (!hadChanges) {
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.INFO,
                        "No changes to apply");
            }
            // saveSettings already shows success toast if there were changes
        });
        buttonPanel.add(applyButton);

        // Add note about auto-save
        JLabel autoSaveNote = new JLabel("<html><i>Settings also auto-save when you leave this screen</i></html>");
        autoSaveNote.setFont(new Font("Dialog", Font.PLAIN, 10));
        autoSaveNote.setForeground(Color.GRAY);
        buttonPanel.add(autoSaveNote);

        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 5, 10, 5);
        contentPanel.add(buttonPanel, gbc);

        loadSettings();

        // Wrap content panel in a scroll pane for vertical scrolling
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Smoother scrolling
        scrollPane.setBorder(null); // Remove border for cleaner look

        // Set the layout for the SettingsForm panel using GroupLayout
        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                        .addComponent(scrollPane)
        );
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(scrollPane)
        );
    }

    private void updateFasterWhisperLanguages() {
        String selectedModel = (String) fasterWhisperModelComboBox.getSelectedItem();
        List<String> languages = fastModelLanguages.getOrDefault(selectedModel, Arrays.asList(""));
        String previouslySelected = (String) fasterWhisperLanguageComboBox.getSelectedItem();
        fasterWhisperLanguageComboBox.removeAllItems();
        for (String lang : languages) {
            fasterWhisperLanguageComboBox.addItem(lang);
        }
        if (previouslySelected != null && languages.contains(previouslySelected)) {
            fasterWhisperLanguageComboBox.setSelectedItem(previouslySelected);
        } else {
            fasterWhisperLanguageComboBox.setSelectedItem("");
        }
    }

    private void startAudioTest(String microphoneName) {
        testMicrophoneButton.setEnabled(false);
        format = configManager.getAudioFormat();
        try {
            Mixer.Info mixerInfo = getMixerInfoByName(microphoneName);
            if (mixerInfo == null) {
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "Microphone not found.");
                return;
            }
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "Audio Line not supported. Please select another device.");
                return;
            }
            line = (TargetDataLine) mixer.getLine(dataLineInfo);
            int maxAttempts = 3;
            int attempts = 0;
            boolean opened = false;
            while (attempts < maxAttempts && !opened) {
                try {
                    line.open(format);
                    opened = true;
                } catch (LineUnavailableException ex) {
                    attempts++;
                    if (attempts < maxAttempts) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            logger.error("Interrupted while waiting to retry opening microphone line", ie);
                            return;
                        }
                    } else {
                        logger.error("Mic Line not available after " + maxAttempts + " attempts.", ex);
                        Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                                "Please try again selecting this mic.");
                        return;
                    }
                }
            }
            line.start();
            testWorker = new TestWorker();
            testWorker.execute();
        } catch (LineUnavailableException ex) {
            logger.error("Mic Line is not available. Please select another device.", ex);
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                    "Mic Line is not available. Please select another device.");
        }
    }

    public void stopAudioTest() {
        testMicrophoneButton.setEnabled(true);
        if (testWorker != null && !testWorker.isDone()) {
            testWorker.cancel(true);
        }
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        volumeBar.setVisible(false);
        stopTestButton.setVisible(false);
    }

    private Mixer.Info getMixerInfoByName(String name) {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixer : mixers) {
            if (name.startsWith(mixer.getName())) {
                return mixer;
            }
        }
        return null;
    }

    private class TestWorker extends SwingWorker<Void, Integer> {
        @Override
        protected Void doInBackground() {
            byte[] buffer = new byte[1024];
            while (!isCancelled()) {
                int bytesRead = line.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    double rms = calculateRMS(buffer, bytesRead);
                    int volume = (int) (rms * 100);
                    publish(volume);
                }
            }
            return null;
        }
        @Override
        protected void process(List<Integer> chunks) {
            int latestVolume = chunks.get(chunks.size() - 1);
            volumeBar.setValue(latestVolume);
            volumeBar.setString(latestVolume + " %");
        }
        @Override
        protected void done() {
            volumeBar.setValue(0);
        }
        private double calculateRMS(byte[] audioData, int bytesRead) {
            long sum = 0;
            for (int i = 0; i < bytesRead; i += 2) {
                if (i + 1 < bytesRead) {
                    int sample = (audioData[i + 1] << 8) | (audioData[i] & 0xFF);
                    sum += (long) sample * sample;
                }
            }
            double rms = Math.sqrt(sum / (bytesRead / 2));
            return Math.min(rms / 32768.0, 1.0);
        }
    }

    public static String formatKeyCombination(String keyCombination) {
        return Arrays.stream(keyCombination.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .map(NativeKeyEvent::getKeyText)
                .collect(Collectors.joining(" + "));
    }

    public static String formatKeySequence(String keySequence) {
        return Arrays.stream(keySequence.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .map(NativeKeyEvent::getKeyText)
                .collect(Collectors.joining(" + "));
    }

    public String[] getAvailableMicrophones() {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        return Arrays.stream(mixers)
                .filter(mixerInfo -> {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    Line.Info[] targetLineInfos = mixer.getTargetLineInfo();
                    for (Line.Info lineInfo : targetLineInfos) {
                        if (lineInfo instanceof DataLine.Info) {
                            DataLine.Info dataLineInfo = (DataLine.Info) lineInfo;
                            AudioFormat[] supportedFormats = dataLineInfo.getFormats();
                            for (AudioFormat format : supportedFormats) {
                                int channels = format.getChannels();
                                float sampleRate = format.getSampleRate();
                                boolean isChannelValid = (channels == 1 || channels == 2);
                                if (isChannelValid) {
                                    logger.info("Mixer supports format: " + mixerInfo.getName()
                                            + " | Channels: " + channels
                                            + " | Sample Rate: " + sampleRate);
                                    return true;
                                }
                            }
                        }
                    }
                    logger.info("Mixer does not support format: " + mixerInfo.getName());
                    return false;
                })
                .map(i -> i.getName() + " Description: " + i.getDescription())
                .toArray(String[]::new);
    }

    private void loadSettings() {
        // Load key combination and key sequence settings
        String keyCombination = configManager.getKeyCombination();
        if (keyCombination == null || keyCombination.isEmpty()) {
            keyCombinationTextField.setText("");
            keyCombinationTextField.setKeysDisplayed(new HashSet<>());
        } else {
            keyCombinationTextField.setText(formatKeyCombination(keyCombination));
            Set<Integer> keySet = Arrays.stream(keyCombination.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());
            keyCombinationTextField.setKeysDisplayed(keySet);
        }
        String keySequence = configManager.getProperty("keySequence");
        if (keySequence == null || keySequence.isEmpty()) {
            keySequenceTextField.setText("");
            keySequenceTextField.setKeysDisplayed(new ArrayList<>());
        } else {
            keySequenceTextField.setText(formatKeySequence(keySequence));
            List<Integer> sequenceSet = Arrays.stream(keySequence.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            keySequenceTextField.setKeysDisplayed(sequenceSet);
        }

        String apiKey = configManager.getProperty("apiKey");
        openaiApiKeyField.setText(apiKey != null ? apiKey : "");
        String customModels = configManager.getCustomOpenAIModelsString();
        customOpenAIModelsField.setText(customModels != null ? customModels : "");
        String grokApiKey = configManager.getProperty("grokApiKey");
        grokApiKeyField.setText(apiKey != null ? grokApiKey : "");
        String openwebUIApiKey = configManager.getOpenWebUIApiKey();
        openwebUIApiKeyField.setText(openwebUIApiKey != null ? openwebUIApiKey : "");
        String openwebUIApiURL = configManager.getOpenWebUIServerUrl();
        openwebUIApiURLField.setText(openwebUIApiURL != null ? openwebUIApiURL : "");

        // Microphone and bitrate settings
        String selectedMicrophone = configManager.getProperty("selectedMicrophone");
        microphoneComboBox.setSelectedItem(selectedMicrophone);
        int bitrate = configManager.getAudioBitrate();
        bitrateComboBox.setSelectedItem(bitrate);
        String finishSound = configManager.getProperty("finishSound");
        boolean isFinishSoundEnabled = Boolean.parseBoolean(finishSound);
        stopSoundSwitch.setSelected(isFinishSoundEnabled);
        // Load Whisper Server selection settings
        String whisperServer = configManager.getProperty("whisperServer");
        if (whisperServer != null && !whisperServer.isEmpty()) {
            whisperServerComboBox.setSelectedItem(whisperServer);
        }
        // Load Faster-Whisperer settings
        String serverUrl = configManager.getFasterWhisperServerUrl();
        whisperServerUrlField.setText(serverUrl != null ? serverUrl : "");
        String fasterModel = configManager.getProperty("fasterWhisperModel");
        if (fasterModel != null) {
            fasterWhisperModelComboBox.setSelectedItem(fasterModel);
        }
        // Update the languages based on the selected model
        updateFasterWhisperLanguages();
        // Load the previously selected language if available
        String selectedLanguage = configManager.getProperty("fasterWhisperLanguage");
        if (selectedLanguage != null && !selectedLanguage.isEmpty()) {
            fasterWhisperLanguageComboBox.setSelectedItem(selectedLanguage);
        } else {
            fasterWhisperLanguageComboBox.setSelectedItem("");
        }
        // Load Groq settings
        String groqApiKey = configManager.getProperty("groqApiKey");
        groqApiKeyField.setText(groqApiKey != null ? groqApiKey : "");
        String groqModel = configManager.getProperty("groqModel");
        if (groqModel != null) {
            groqModelComboBox.setSelectedItem(groqModel);
        }
    }

    private void saveSettings(ActionEvent e) {
        // Only save if settings have been modified
        if (!settingsDirty) {
            logger.debug("Settings unchanged, skipping save");
            return;
        }

        // Save key combination and sequence
        String keyCombinationString = keyCombinationTextField.getKeysDisplayed().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        configManager.setProperty("keyCombination", keyCombinationString);
        String keySequenceString = keySequenceTextField.getKeysDisplayed().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        configManager.setProperty("keySequence", keySequenceString);
        // Save OpenAI Whisper API Key
        String openaiKey = openaiApiKeyField.getText();
        configManager.setProperty("apiKey", openaiKey);

        // Save Custom OpenAI Models
        String customModels = customOpenAIModelsField.getText();
        configManager.setCustomOpenAIModelsFromString(customModels);

        String grokApiKey = grokApiKeyField.getText();
        configManager.setProperty("grokApiKey", grokApiKey);

        String openwebUIApiKey = openwebUIApiKeyField.getText();
        configManager.setOpenWebUIApiKey(openwebUIApiKey);

        String openwebUIApiURL = openwebUIApiURLField.getText();
        configManager.setOpenWebUIServerUrl(openwebUIApiURL);
        // Save microphone and bitrate settings
        configManager.setProperty("selectedMicrophone", (String) microphoneComboBox.getSelectedItem());
        int selectedBitrate = (Integer) bitrateComboBox.getSelectedItem();
        configManager.setAudioBitrate(selectedBitrate);
        boolean isFinishSoundEnabled = stopSoundSwitch.isSelected();
        configManager.setProperty("finishSound", String.valueOf(isFinishSoundEnabled));
        // Save Whisper Server selection and Faster-Whisperer settings
        String selectedWhisperServer = (String) whisperServerComboBox.getSelectedItem();
        configManager.setProperty("whisperServer", selectedWhisperServer);
        String serverUrl = whisperServerUrlField.getText();
        configManager.setProperty("fasterWhisperServerUrl", serverUrl);
        String fwModel = (String) fasterWhisperModelComboBox.getSelectedItem();
        configManager.setProperty("fasterWhisperModel", fwModel);
        String selectedLanguage = (String) fasterWhisperLanguageComboBox.getSelectedItem();
        configManager.setProperty("fasterWhisperLanguage", selectedLanguage);
        // Save Groq settings
        String groqApiKey = groqApiKeyField.getText();
        configManager.setProperty("groqApiKey", groqApiKey);
        String groqModel = (String) groqModelComboBox.getSelectedItem();
        configManager.setProperty("groqModel", groqModel);

        // Save silence removal settings
        configManager.setSilenceRemovalEnabled(silenceRemovalSwitch.isSelected());
        configManager.setSilenceThreshold(silenceThresholdSlider.getValue() / 1000.0f);
        configManager.setMinSilenceDuration(minSilenceDurationSlider.getValue());
        configManager.setMinRecordingDurationForSilenceRemoval(minRecordingDurationSlider.getValue());
        configManager.setKeepCompressedFile(keepCompressedSwitch.isSelected());

        configManager.saveConfig();

        // Reset dirty flag after successful save
        settingsDirty = false;

        Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS,
                "Settings saved.");
        logger.info("Settings saved: Key shortcuts - {}, Key sequence - {}, Microphone - {}",
                keyCombinationString, keySequenceString, microphoneComboBox.getSelectedItem());
    }

    public KeyCombinationTextField getKeybindTextField() {
        return keyCombinationTextField;
    }

    public KeySequenceTextField getKeySequenceTextField() {
        return keySequenceTextField;
    }

    /**
     * Saves all settings without requiring an ActionEvent.
     * Called when recording hotkey is triggered while on settings screen.
     */
    public void saveSettings() {
        saveSettings(null);
    }
}