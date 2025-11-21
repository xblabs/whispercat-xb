package org.whispercat.postprocessing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.whispercat.ConfigManager;
import org.whispercat.MainForm;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A form for creating or editing a Pipeline.
 * Pipelines are composed of references to ProcessingUnits from the library.
 */
public class PipelineEditorForm extends JPanel {
    private final JTextField titleField;
    private final JTextField descriptionField;
    private final JCheckBox enabledCheckBox;
    private final ConfigManager configManager;
    private final MainForm mainForm;
    private final JPanel unitsContainer;
    private final JScrollPane scrollPane;
    private final Border defaultTextFieldBorder;
    private String currentUUID;
    private List<ProcessingUnit> availableUnits;

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(PipelineEditorForm.class);

    public PipelineEditorForm(ConfigManager configManager, MainForm mainForm, Pipeline existingPipeline) {
        this.configManager = configManager;
        this.mainForm = mainForm;
        this.availableUnits = configManager.getProcessingUnits();

        setBorder(BorderFactory.createEmptyBorder(60, 20, 10, 10));
        setLayout(new BorderLayout());

        // Top panel
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setAlignmentX(LEFT_ALIGNMENT);

        // Header
        JLabel headerLabel = new JLabel("Pipeline Editor");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.PLAIN, 18f));
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        headerPanel.add(headerLabel);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        headerPanel.setAlignmentX(LEFT_ALIGNMENT);
        topPanel.add(headerPanel);

        // Title field
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel titleLabel = new JLabel("Pipeline Title:");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN));
        titleLabel.setPreferredSize(new Dimension(150, titleLabel.getPreferredSize().height));
        titlePanel.add(titleLabel);
        titleField = new JTextField(20);
        defaultTextFieldBorder = titleField.getBorder();
        titleField.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleField.getPreferredSize().height));
        titlePanel.add(titleField);
        topPanel.add(titlePanel);
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

        // Enabled checkbox
        JPanel enabledPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        enabledPanel.setAlignmentX(LEFT_ALIGNMENT);
        enabledCheckBox = new JCheckBox("Pipeline Enabled", true);
        enabledCheckBox.setToolTipText("Enable or disable this entire pipeline");
        enabledPanel.add(enabledCheckBox);
        topPanel.add(enabledPanel);

        add(topPanel, BorderLayout.NORTH);

        // Container for unit references
        unitsContainer = new JPanel();
        unitsContainer.setBorder(BorderFactory.createEmptyBorder());
        unitsContainer.setLayout(new BoxLayout(unitsContainer, BoxLayout.Y_AXIS));
        scrollPane = new JScrollPane(unitsContainer);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(600, 300));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom panel with buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        JButton doneButton = new JButton("Done");
        doneButton.setToolTipText("Save and return to Pipelines");
        doneButton.addActionListener(e -> saveAndReturn());
        bottomPanel.add(doneButton);
        bottomPanel.add(Box.createHorizontalStrut(10));

        JButton createUnitButton = new JButton("Create New Unit");
        createUnitButton.setToolTipText("Create a new unit and add it to this pipeline");
        createUnitButton.addActionListener(e -> showCreateUnitDialog());
        bottomPanel.add(createUnitButton);
        bottomPanel.add(Box.createHorizontalStrut(10));

        JButton addUnitButton = new JButton("Add Existing Unit");
        addUnitButton.setToolTipText("Add a unit from the library");
        addUnitButton.addActionListener(e -> showAddUnitDialog());
        bottomPanel.add(addUnitButton);

        add(bottomPanel, BorderLayout.SOUTH);

        // Load existing data if provided
        if (existingPipeline != null) {
            loadPipelineData(existingPipeline);
        }
    }

    private void loadPipelineData(Pipeline pipeline) {
        currentUUID = pipeline.uuid;
        titleField.setText(pipeline.title != null ? pipeline.title : "");
        descriptionField.setText(pipeline.description != null ? pipeline.description : "");
        enabledCheckBox.setSelected(pipeline.enabled);

        // Load unit references
        if (pipeline.unitReferences != null) {
            for (PipelineUnitReference ref : pipeline.unitReferences) {
                ProcessingUnit unit = configManager.getProcessingUnitByUuid(ref.unitUuid);
                if (unit != null) {
                    addUnitReferencePanel(unit, ref.enabled);
                } else {
                    logger.warn("Unit with UUID {} not found", ref.unitUuid);
                }
            }
        }
    }

    private void showCreateUnitDialog() {
        // Create a dialog for unit creation
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Create New Unit", true);
        dialog.setLayout(new BorderLayout());

        // Create a custom unit editor form with a callback
        JPanel editorPanel = createInlineUnitEditor(dialog);

        dialog.add(editorPanel, BorderLayout.CENTER);
        dialog.setSize(700, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel createInlineUnitEditor(JDialog parentDialog) {
        // Create a simplified unit editor that saves and adds to pipeline
        UnitEditorForm editorForm = new UnitEditorForm(configManager, mainForm, null) {
            @Override
            protected void saveAndReturn() {
                if (!validateData()) {
                    return;
                }

                ProcessingUnit unit = new ProcessingUnit();

                // Generate UUID for new unit
                String newUuid = java.util.UUID.randomUUID().toString();
                unit.uuid = newUuid;

                unit.name = getNameFieldText();
                unit.description = getDescriptionFieldText();
                unit.type = getTypeComboSelection();

                if ("Prompt".equals(unit.type)) {
                    unit.provider = getProviderComboSelection();
                    unit.model = getModelComboSelection();
                    unit.systemPrompt = getSystemPromptText();
                    unit.userPrompt = getUserPromptText();
                } else if ("Text Replacement".equals(unit.type)) {
                    unit.textToReplace = getTextToReplaceFieldText();
                    unit.replacementText = getReplacementTextFieldText();
                }

                // Save the unit
                configManager.saveProcessingUnit(unit);
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS, "Unit created and added to pipeline!");

                // Refresh available units list
                availableUnits = configManager.getProcessingUnits();

                // Add the new unit to the pipeline
                addUnitReferencePanel(unit, true);

                // Close the dialog
                parentDialog.dispose();
            }
        };

        return editorForm;
    }

    private void showAddUnitDialog() {
        // Refresh available units
        availableUnits = configManager.getProcessingUnits();

        if (availableUnits.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No processing units available. Please create a unit first using 'Create New Unit'.",
                    "No Units Available",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Create a combo box with available units
        String[] unitNames = availableUnits.stream()
                .map(u -> u.name)
                .toArray(String[]::new);

        String selected = (String) JOptionPane.showInputDialog(
                this,
                "Select a unit to add to this pipeline:",
                "Add Existing Unit",
                JOptionPane.PLAIN_MESSAGE,
                null,
                unitNames,
                unitNames[0]
        );

        if (selected != null) {
            ProcessingUnit unit = availableUnits.stream()
                    .filter(u -> u.name.equals(selected))
                    .findFirst()
                    .orElse(null);

            if (unit != null) {
                addUnitReferencePanel(unit, true);
            }
        }
    }

    private void addUnitReferencePanel(ProcessingUnit unit, boolean enabled) {
        UnitReferencePanel panel = new UnitReferencePanel(unit, enabled);
        unitsContainer.add(panel);
        unitsContainer.revalidate();
        unitsContainer.repaint();

        // Scroll to the new panel
        SwingUtilities.invokeLater(() -> {
            JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
            verticalBar.setValue(verticalBar.getMaximum());
        });
    }

    private void saveAndReturn() {
        if (!validateData()) {
            return;
        }

        Pipeline pipeline = new Pipeline();

        // Generate UUID if new pipeline
        if (currentUUID == null || currentUUID.trim().isEmpty()) {
            currentUUID = UUID.randomUUID().toString();
        }
        pipeline.uuid = currentUUID;

        pipeline.title = titleField.getText().trim();
        pipeline.description = descriptionField.getText().trim();
        pipeline.enabled = enabledCheckBox.isSelected();

        // Collect unit references
        pipeline.unitReferences = new ArrayList<>();
        for (Component comp : unitsContainer.getComponents()) {
            if (comp instanceof UnitReferencePanel) {
                UnitReferencePanel panel = (UnitReferencePanel) comp;
                PipelineUnitReference ref = new PipelineUnitReference();
                ref.unitUuid = panel.getUnitUuid();
                ref.enabled = panel.isEnabled();
                pipeline.unitReferences.add(ref);
            }
        }

        configManager.savePipeline(pipeline);
        Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS, "Pipeline saved!");

        // Go back to pipeline list
        mainForm.setSelectedMenu(2, 1);
        mainForm.showForm(new PipelineListForm(configManager, mainForm));
    }

    private boolean validateData() {
        // Validate title
        if (titleField.getText().trim().isEmpty()) {
            titleField.setBorder(BorderFactory.createLineBorder(Color.RED));
            titleField.requestFocusInWindow();
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR, "Pipeline title is required.");
            return false;
        } else {
            titleField.setBorder(defaultTextFieldBorder);
        }

        // Check that at least one unit is added
        if (unitsContainer.getComponentCount() == 0) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                    "Pipeline must contain at least one unit.");
            return false;
        }

        return true;
    }

    /**
     * Gets the index of a component in the unitsContainer.
     */
    private int getComponentIndex(Component component) {
        Component[] components = unitsContainer.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] == component) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Scrolls the scroll pane's viewport so that the specified component is visible.
     */
    private void scrollToComponent(Component comp) {
        if (scrollPane != null && scrollPane.getViewport() != null) {
            Rectangle rect = SwingUtilities.convertRectangle(comp.getParent(), comp.getBounds(), scrollPane.getViewport());
            scrollPane.getViewport().scrollRectToVisible(rect);
        }
    }

    /**
     * Inner class representing a single unit reference in the pipeline.
     */
    class UnitReferencePanel extends JPanel {
        private final ProcessingUnit unit;
        private final JCheckBox enabledCheckBox;

        public UnitReferencePanel(ProcessingUnit unit, boolean enabled) {
            this.unit = unit;
            setBorder(BorderFactory.createTitledBorder("Unit: " + unit.name));
            setLayout(new BorderLayout());
            setAlignmentX(LEFT_ALIGNMENT);

            // Info panel
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

            JLabel typeLabel = new JLabel("Type: " + unit.type);
            typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(typeLabel);

            if (unit.description != null && !unit.description.trim().isEmpty()) {
                JLabel descLabel = new JLabel("Description: " + unit.description);
                descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                infoPanel.add(descLabel);
            }

            add(infoPanel, BorderLayout.CENTER);

            // Control panel (right side)
            JPanel controlPanel = new JPanel(new BorderLayout());

            // Enabled checkbox on top
            enabledCheckBox = new JCheckBox("Enabled", enabled);
            enabledCheckBox.setToolTipText("Enable or disable this unit in the pipeline");
            controlPanel.add(enabledCheckBox, BorderLayout.NORTH);

            // Button panel
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

            // Up button
            JButton upButton = new JButton("Up");
            upButton.setToolTipText("Move this unit up");
            upButton.setMargin(new Insets(2, 6, 2, 6));
            upButton.addActionListener((ActionEvent e) -> {
                int index = getComponentIndex(UnitReferencePanel.this);
                if (index > 0) {
                    unitsContainer.remove(index);
                    unitsContainer.add(UnitReferencePanel.this, index - 1);
                    unitsContainer.revalidate();
                    unitsContainer.repaint();
                    scrollToComponent(UnitReferencePanel.this);
                }
            });

            // Down button
            JButton downButton = new JButton("Down");
            downButton.setToolTipText("Move this unit down");
            downButton.setMargin(new Insets(2, 6, 2, 6));
            downButton.addActionListener((ActionEvent e) -> {
                int index = getComponentIndex(UnitReferencePanel.this);
                if (index >= 0 && index < unitsContainer.getComponentCount() - 1) {
                    unitsContainer.remove(index);
                    unitsContainer.add(UnitReferencePanel.this, index + 1);
                    unitsContainer.revalidate();
                    unitsContainer.repaint();
                    scrollToComponent(UnitReferencePanel.this);
                }
            });

            // Remove button
            JButton removeButton = new JButton();
            Icon trashIcon = new FlatSVGIcon("icon/svg/trash.svg", 16, 16);
            removeButton.setIcon(trashIcon);
            removeButton.setToolTipText("Remove this unit from the pipeline");
            removeButton.addActionListener((ActionEvent e) -> {
                unitsContainer.remove(UnitReferencePanel.this);
                unitsContainer.revalidate();
                unitsContainer.repaint();
            });

            buttonPanel.add(upButton);
            buttonPanel.add(downButton);
            buttonPanel.add(Box.createVerticalStrut(5));
            buttonPanel.add(removeButton);

            controlPanel.add(buttonPanel, BorderLayout.SOUTH);
            add(controlPanel, BorderLayout.EAST);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, pref.height);
        }

        public String getUnitUuid() {
            return unit.uuid;
        }

        public boolean isEnabled() {
            return enabledCheckBox.isSelected();
        }
    }
}
