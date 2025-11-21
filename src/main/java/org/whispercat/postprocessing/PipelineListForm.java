package org.whispercat.postprocessing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.whispercat.ConfigManager;
import org.whispercat.MainForm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * A panel that displays all saved Pipelines.
 * Each item is shown inside a bordered panel with its title, description, and enabled status,
 * plus edit and delete buttons on the right.
 */
public class PipelineListForm extends JPanel {
    private final ConfigManager configManager;
    private final MainForm mainForm;
    private final JPanel listContainer;
    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(PipelineListForm.class);

    public PipelineListForm(ConfigManager configManager, MainForm mainForm) {
        this.configManager = configManager;
        this.mainForm = mainForm;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(60, 20, 10, 10));

        // Create and add the header panel with a title.
        JLabel headerLabel = new JLabel("All Pipelines");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.PLAIN, 18f));
        headerLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(headerLabel, BorderLayout.CENTER);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // Create a container that holds the individual pipeline items.
        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        refreshList();
    }

    /**
     * Refreshes the list by reading pipelines from the ConfigManager
     * and rebuilding the UI.
     */
    public void refreshList() {
        listContainer.removeAll();

        // Get the list of pipelines.
        List<Pipeline> pipelines = configManager.getPipelines();
        logger.info("Pipelines List: {}", pipelines);

        // Sort the list alphabetically by title (case-insensitive)
        pipelines.sort((a, b) -> {
            String titleA = (a.title != null && !a.title.trim().isEmpty()) ? a.title : "No Title";
            String titleB = (b.title != null && !b.title.trim().isEmpty()) ? b.title : "No Title";
            return titleA.compareToIgnoreCase(titleB);
        });

        // For each pipeline, create a panel showing its title, description, enabled status, and buttons.
        for (Pipeline pipeline : pipelines) {
            String title = (pipeline.title != null && !pipeline.title.trim().isEmpty()) ? pipeline.title : "No Title";
            String description = (pipeline.description != null && !pipeline.description.trim().isEmpty()) ? pipeline.description : "No Description";

            // Create an item panel with a titled border using the title.
            JPanel itemPanel = new JPanel(new BorderLayout());
            itemPanel.setBorder(BorderFactory.createTitledBorder(title));

            // Create an info panel for the description and status.
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

            JLabel descriptionLabel = new JLabel("Description: " + description);
            descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(descriptionLabel);

            // Add enabled/disabled indicator
            String enabledText = pipeline.enabled ? "Enabled" : "Disabled";
            Color enabledColor = pipeline.enabled ? new Color(0, 128, 0) : Color.GRAY;
            JLabel enabledLabel = new JLabel("Status: " + enabledText);
            enabledLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            enabledLabel.setForeground(enabledColor);
            infoPanel.add(enabledLabel);

            // Show number of units in pipeline
            int unitCount = (pipeline.unitReferences != null) ? pipeline.unitReferences.size() : 0;
            JLabel unitsLabel = new JLabel("Units: " + unitCount);
            unitsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(unitsLabel);

            // Add info panel to the center.
            itemPanel.add(infoPanel, BorderLayout.CENTER);

            // Create a button panel on the right.
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

            // Edit button.
            JButton editButton = new JButton();
            editButton.setIcon(new FlatSVGIcon("icon/svg/edit.svg", 16, 16));
            editButton.setToolTipText("Edit this Pipeline");
            editButton.addActionListener((ActionEvent e) -> {
                mainForm.setSelectedMenu(2, 2); // Adjust menu index as needed
                mainForm.showForm(new PipelineEditorForm(configManager, mainForm, pipeline));
            });

            // Delete button.
            JButton deleteButton = new JButton();
            deleteButton.setIcon(new FlatSVGIcon("icon/svg/trash.svg", 16, 16));
            deleteButton.setToolTipText("Delete this Pipeline");
            deleteButton.addActionListener((ActionEvent e) -> {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Are you sure you want to delete this pipeline?",
                        "Confirm Delete",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    configManager.deletePipeline(pipeline.uuid);
                    refreshList();
                }
            });

            // Add buttons to the button panel with vertical spacing.
            buttonPanel.add(editButton);
            buttonPanel.add(Box.createVerticalStrut(5));
            buttonPanel.add(deleteButton);

            // Add the button panel to the right side.
            itemPanel.add(buttonPanel, BorderLayout.EAST);

            // Adjust the height: set maximum size to its preferred height.
            itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, itemPanel.getPreferredSize().height));

            listContainer.add(itemPanel);
        }

        listContainer.revalidate();
        listContainer.repaint();
    }
}
