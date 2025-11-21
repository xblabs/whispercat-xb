package org.whispercat.postprocessing;

import org.whispercat.ConfigManager;
import org.whispercat.MainForm;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;
import org.whispercat.postprocessing.clients.OpenWebUIModelsResponse;
import org.whispercat.postprocessing.clients.OpenWebUIProcessClient;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A form for creating or editing a single Processing Unit.
 * This is the building block that can be reused in pipelines.
 */
public class UnitEditorForm extends JPanel {
    private final JTextField nameField;
    private final JTextField descriptionField;
    private final ConfigManager configManager;
    private final MainForm mainForm;
    private final JComboBox<String> typeCombo;
    private JPanel promptPanel;
    private JPanel replacementPanel;
    private JTextArea systemPromptArea;
    private JTextArea userPromptArea;
    private Font defaultFont = new JTextArea().getFont();
    private JComboBox<String> providerCombo;
    private JComboBox<String> modelCombo;
    private JTextField textToReplaceField;
    private JTextField replacementTextField;
    private Border defaultTextAreaBorder;
    private Border defaultTextFieldBorder;
    private Border defaultReplacementFieldBorder;
    private String currentUUID;
    private String storedModel = "";
    private List<String> openWebUIModelNames = new ArrayList<>();
    private OpenWebUIProcessClient openWebUIProcessClient;

    private final String SYSTEM_PROMPT_PLACEHOLDER = "Enter system instructions, e.g., 'You are a helpful assistant.'";
    private final String USER_PROMPT_PLACEHOLDER = "Enter a user message template. For example: 'Greetings, {{input}}! Welcome to our service.' You can include the placeholder {{input}} to insert user input.";

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(UnitEditorForm.class);

    public UnitEditorForm(ConfigManager configManager, MainForm mainForm, ProcessingUnit existingUnit) {
        this.configManager = configManager;
        this.mainForm = mainForm;

        setBorder(BorderFactory.createEmptyBorder(60, 20, 10, 10));
        setLayout(new BorderLayout());

        // Top panel for name, description, and type
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setAlignmentX(LEFT_ALIGNMENT);

        // Header
        JLabel headerLabel = new JLabel("Processing Unit Editor");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.PLAIN, 18f));
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        headerPanel.add(headerLabel);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        headerPanel.setAlignmentX(LEFT_ALIGNMENT);

        // Name field
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        namePanel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel nameLabel = new JLabel("Unit Name:");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN));
        nameLabel.setPreferredSize(new Dimension(150, nameLabel.getPreferredSize().height));
        namePanel.add(nameLabel);
        nameField = new JTextField(20);
        defaultTextFieldBorder = nameField.getBorder();
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, nameField.getPreferredSize().height));
        namePanel.add(nameField);
        topPanel.add(namePanel);
        topPanel.add(Box.createVerticalStrut(10));

        // Description field
        JPanel descriptionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        descriptionPanel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel descriptionLabel = new JLabel("Description:");
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(Font.PLAIN));
        descriptionLabel.setPreferredSize(new Dimension(150, descriptionLabel.getPreferredSize().height));
        descriptionPanel.add(descriptionLabel);
        descriptionField = new JTextField(40);
        descriptionField.setMaximumSize(new Dimension(Integer.MAX_VALUE, descriptionField.getPreferredSize().height));
        descriptionPanel.add(descriptionField);
        topPanel.add(descriptionPanel);
        topPanel.add(Box.createVerticalStrut(10));

        // Type selector
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        typePanel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel typeLabel = new JLabel("Processing Type:");
        typeLabel.setPreferredSize(new Dimension(150, typeLabel.getPreferredSize().height));
        typePanel.add(typeLabel);
        typeCombo = new JComboBox<>(new String[]{"Prompt", "Text Replacement"});
        typePanel.add(typeCombo);
        topPanel.add(typePanel);

        add(topPanel, BorderLayout.NORTH);

        // Center panel for type-specific fields
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        // Prompt panel
        promptPanel = new JPanel();
        promptPanel.setLayout(new BoxLayout(promptPanel, BoxLayout.Y_AXIS));
        promptPanel.add(Box.createVerticalStrut(20));

        // Provider and Model
        JPanel providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel providerLabel = new JLabel("Provider:");
        providerPanel.add(providerLabel);
        providerPanel.add(Box.createHorizontalStrut(5));
        providerCombo = new JComboBox<>(new String[]{"OpenAI", "Open WebUI"});
        providerPanel.add(providerCombo);
        providerPanel.add(Box.createHorizontalStrut(15));
        JLabel modelLabel = new JLabel("Model:");
        providerPanel.add(modelLabel);
        providerPanel.add(Box.createHorizontalStrut(5));

        // Load custom OpenAI models from configuration
        List<String> customModels = configManager.getCustomOpenAIModels();
        modelCombo = new JComboBox<>(customModels.toArray(new String[0]));
        providerPanel.add(modelCombo);

        // Provider change listener
        providerCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateModelCombo();
            }
        });

        promptPanel.add(providerPanel);
        promptPanel.add(Box.createVerticalStrut(10));

        // System Prompt
        JPanel systemPanel = new JPanel(new BorderLayout());
        systemPanel.setBorder(null);
        JLabel systemLabel = new JLabel("System Prompt:");
        systemPanel.add(systemLabel, BorderLayout.NORTH);
        systemPromptArea = new JTextArea(5, 15);
        systemPromptArea.setLineWrap(true);
        systemPromptArea.setWrapStyleWord(true);
        defaultTextAreaBorder = systemPromptArea.getBorder();
        JScrollPane systemScrollPane = new JScrollPane(systemPromptArea);
        systemScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, systemScrollPane.getPreferredSize().height));
        systemPanel.add(systemScrollPane, BorderLayout.CENTER);
        promptPanel.add(systemPanel);
        promptPanel.add(Box.createVerticalStrut(10));

        // User Prompt
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBorder(null);
        JLabel userLabel = new JLabel("User Prompt:");
        userPanel.add(userLabel, BorderLayout.NORTH);
        userPromptArea = new JTextArea(5, 15);
        userPromptArea.setLineWrap(true);
        userPromptArea.setWrapStyleWord(true);
        JScrollPane userScrollPane = new JScrollPane(userPromptArea);
        userScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, userScrollPane.getPreferredSize().height));
        userPanel.add(userScrollPane, BorderLayout.CENTER);
        promptPanel.add(userPanel);

        centerPanel.add(promptPanel);

        // Text Replacement panel
        replacementPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        replacementPanel.add(new JLabel("Text to Replace:"));
        textToReplaceField = new JTextField(10);
        defaultReplacementFieldBorder = textToReplaceField.getBorder();
        replacementPanel.add(textToReplaceField);
        replacementPanel.add(Box.createHorizontalStrut(10));
        replacementPanel.add(new JLabel("Replacement Text:"));
        replacementTextField = new JTextField(10);
        replacementPanel.add(replacementTextField);
        centerPanel.add(replacementPanel);

        add(centerPanel, BorderLayout.CENTER);

        // Bottom panel with save button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton saveButton = new JButton("Save Unit");
        saveButton.addActionListener(e -> saveUnit());
        bottomPanel.add(saveButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            mainForm.setSelectedMenu(2, 2); // Go back to unit library
            mainForm.showForm(new UnitLibraryListForm(configManager, mainForm));
        });
        bottomPanel.add(Box.createHorizontalStrut(10));
        bottomPanel.add(cancelButton);

        add(bottomPanel, BorderLayout.SOUTH);

        // Set up field visibility based on type
        updateFieldsVisibility();
        typeCombo.addActionListener(e -> updateFieldsVisibility());

        // Set up placeholders
        setPlaceholder(systemPromptArea, SYSTEM_PROMPT_PLACEHOLDER, defaultFont);
        setPlaceholder(userPromptArea, USER_PROMPT_PLACEHOLDER, defaultFont);

        // Load existing data if provided
        if (existingUnit != null) {
            loadUnitData(existingUnit);
        }

        // Load Open WebUI models if configured
        if ((!configManager.getOpenWebUIServerUrl().isEmpty() || !configManager.getOpenWebUIApiKey().isEmpty()) && existingUnit != null) {
            openWebUIProcessClient = new OpenWebUIProcessClient(configManager);
            loadOpenWebUIModels();
        }
    }

    private void updateFieldsVisibility() {
        String selection = (String) typeCombo.getSelectedItem();
        if ("Prompt".equals(selection)) {
            promptPanel.setVisible(true);
            replacementPanel.setVisible(false);
        } else if ("Text Replacement".equals(selection)) {
            promptPanel.setVisible(false);
            replacementPanel.setVisible(true);
        }
        revalidate();
        repaint();
    }

    private void loadUnitData(ProcessingUnit unit) {
        currentUUID = unit.uuid;
        nameField.setText(unit.name != null ? unit.name : "");
        descriptionField.setText(unit.description != null ? unit.description : "");
        typeCombo.setSelectedItem(unit.type);

        if ("Prompt".equals(unit.type)) {
            storedModel = unit.model;
            providerCombo.setSelectedItem(unit.provider);
            modelCombo.setSelectedItem(unit.model);

            if (unit.systemPrompt != null && !unit.systemPrompt.trim().isEmpty()) {
                systemPromptArea.setText(unit.systemPrompt);
                systemPromptArea.setFont(defaultFont);
            } else {
                systemPromptArea.setText(SYSTEM_PROMPT_PLACEHOLDER);
                systemPromptArea.setFont(defaultFont.deriveFont(Font.ITALIC));
            }

            if (unit.userPrompt != null && !unit.userPrompt.trim().isEmpty()) {
                userPromptArea.setText(unit.userPrompt);
                userPromptArea.setFont(defaultFont);
            } else {
                userPromptArea.setText(USER_PROMPT_PLACEHOLDER);
                userPromptArea.setFont(defaultFont.deriveFont(Font.ITALIC));
            }
        } else if ("Text Replacement".equals(unit.type)) {
            textToReplaceField.setText(unit.textToReplace);
            replacementTextField.setText(unit.replacementText);
        }

        updateFieldsVisibility();
    }

    private void saveUnit() {
        if (!validateData()) {
            return;
        }

        ProcessingUnit unit = new ProcessingUnit();

        // Generate UUID if new unit
        if (currentUUID == null || currentUUID.trim().isEmpty()) {
            currentUUID = UUID.randomUUID().toString();
        }
        unit.uuid = currentUUID;

        unit.name = nameField.getText().trim();
        unit.description = descriptionField.getText().trim();
        unit.type = (String) typeCombo.getSelectedItem();

        if ("Prompt".equals(unit.type)) {
            unit.provider = (String) providerCombo.getSelectedItem();
            unit.model = (String) modelCombo.getSelectedItem();

            String sysText = systemPromptArea.getText();
            if (sysText.equals(SYSTEM_PROMPT_PLACEHOLDER)) {
                sysText = "";
            }
            unit.systemPrompt = sysText;

            String userText = userPromptArea.getText();
            if (userText.equals(USER_PROMPT_PLACEHOLDER)) {
                userText = "";
            }
            unit.userPrompt = userText;
        } else if ("Text Replacement".equals(unit.type)) {
            unit.textToReplace = textToReplaceField.getText();
            unit.replacementText = replacementTextField.getText();
        }

        configManager.saveProcessingUnit(unit);
        Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS, "Unit saved successfully!");

        // Go back to unit library list
        mainForm.setSelectedMenu(2, 2);
        mainForm.showForm(new UnitLibraryListForm(configManager, mainForm));
    }

    private boolean validateData() {
        // Validate name
        if (nameField.getText().trim().isEmpty()) {
            nameField.setBorder(BorderFactory.createLineBorder(Color.RED));
            nameField.requestFocusInWindow();
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR, "Unit name is required.");
            return false;
        } else {
            nameField.setBorder(defaultTextFieldBorder);
        }

        String type = (String) typeCombo.getSelectedItem();
        if ("Text Replacement".equals(type)) {
            if (textToReplaceField.getText().trim().isEmpty()) {
                textToReplaceField.setBorder(BorderFactory.createLineBorder(Color.RED));
                textToReplaceField.requestFocusInWindow();
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "For 'Text Replacement', the 'Text to Replace' field must be filled.");
                return false;
            } else {
                textToReplaceField.setBorder(defaultReplacementFieldBorder);
            }
        } else if ("Prompt".equals(type)) {
            String systemText = systemPromptArea.getText();
            String userText = userPromptArea.getText();

            if (systemText.equals(SYSTEM_PROMPT_PLACEHOLDER)) {
                systemText = "";
            }
            if (userText.equals(USER_PROMPT_PLACEHOLDER)) {
                userText = "";
            }

            boolean systemEmpty = systemText.trim().isEmpty();
            boolean userEmpty = userText.trim().isEmpty();

            if (systemEmpty && userEmpty) {
                userPromptArea.setBorder(BorderFactory.createLineBorder(Color.RED));
                userPromptArea.requestFocusInWindow();
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "For 'Prompt', at least 'System Prompt' or 'User Prompt' must be filled.");
                return false;
            } else {
                systemPromptArea.setBorder(defaultTextAreaBorder);
            }

            if (!userEmpty && !userText.contains("{{input}}")) {
                userPromptArea.setBorder(BorderFactory.createLineBorder(Color.ORANGE));
                userPromptArea.requestFocusInWindow();
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                        "The User Prompt should include the placeholder '{{input}}' at least once.");
            }
        }

        return true;
    }

    private void updateModelCombo() {
        String provider = (String) providerCombo.getSelectedItem();
        String previousSelection = (storedModel != null) ? storedModel : "";

        modelCombo.removeAllItems();
        if ("Open WebUI".equals(provider)) {
            if (openWebUIModelNames == null || openWebUIModelNames.isEmpty()) {
                if (!previousSelection.isEmpty()) {
                    modelCombo.addItem(previousSelection);
                    modelCombo.setSelectedItem(previousSelection);
                } else {
                    modelCombo.addItem("No models loaded");
                }
                return;
            }

            boolean loadedContainsPrevious = false;
            for (String name : openWebUIModelNames) {
                modelCombo.addItem(name);
                if (name.equals(previousSelection)) {
                    loadedContainsPrevious = true;
                }
            }

            if (!previousSelection.isEmpty() && !loadedContainsPrevious) {
                modelCombo.insertItemAt(previousSelection, 0);
                modelCombo.setSelectedItem(previousSelection);
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                        "The previously selected Open WebUI model \"" + previousSelection + "\" is not available anymore.");
            } else if (loadedContainsPrevious) {
                modelCombo.setSelectedItem(previousSelection);
            }
        } else { // OpenAI
            List<String> customModels = configManager.getCustomOpenAIModels();
            for (String m : customModels) {
                modelCombo.addItem(m);
            }
            for (int i = 0; i < modelCombo.getItemCount(); i++) {
                if (modelCombo.getItemAt(i).equals(previousSelection)) {
                    modelCombo.setSelectedItem(previousSelection);
                    break;
                }
            }
        }
    }

    private void loadOpenWebUIModels() {
        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                OpenWebUIModelsResponse modelsResponse = openWebUIProcessClient.fetchModels();
                return modelsResponse.getModelNames();
            }

            @Override
            protected void done() {
                try {
                    List<String> models = get();
                    openWebUIModelNames.clear();
                    openWebUIModelNames.addAll(models);
                    updateModelCombo();
                } catch (Exception ex) {
                    logger.error("Error loading Open WebUI models: ", ex);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Error loading Open WebUI models. See logs.");
                }
            }
        };
        worker.execute();
    }

    private void setPlaceholder(JTextArea textArea, String placeholder, Font defaultFont) {
        if (textArea.getText().trim().isEmpty()) {
            textArea.setFont(defaultFont.deriveFont(Font.ITALIC));
            textArea.setText(placeholder);
        } else {
            textArea.setFont(defaultFont);
        }

        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (textArea.getText().equals(placeholder)) {
                    textArea.setText("");
                    textArea.setFont(defaultFont);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (textArea.getText().trim().isEmpty()) {
                    textArea.setFont(defaultFont.deriveFont(Font.ITALIC));
                    textArea.setText(placeholder);
                } else {
                    textArea.setFont(defaultFont);
                }
            }
        });
    }
}
