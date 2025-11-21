package org.whispercat.postprocessing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.whispercat.ConfigManager;
import org.whispercat.MainForm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * A panel that displays all saved Processing Units in the library.
 * Each item is shown inside a bordered panel with its name and description,
 * plus edit and delete buttons on the right.
 */
public class UnitLibraryListForm extends JPanel {
    private final ConfigManager configManager;
    private final MainForm mainForm;
    private final JPanel listContainer;
    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(UnitLibraryListForm.class);

    public UnitLibraryListForm(ConfigManager configManager, MainForm mainForm) {
        this.configManager = configManager;
        this.mainForm = mainForm;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(60, 20, 10, 10));

        // Create and add the header panel with a title.
        JLabel headerLabel = new JLabel("Processing Unit Library");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.PLAIN, 18f));
        headerLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(headerLabel, BorderLayout.CENTER);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // Create a container that holds the individual unit items.
        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        refreshList();
    }

    /**
     * Refreshes the list by reading units from the ConfigManager
     * and rebuilding the UI.
     */
    public void refreshList() {
        listContainer.removeAll();

        // Get the list of processing units.
        List<ProcessingUnit> units = configManager.getProcessingUnits();
        logger.info("Processing Units List: {}", units);

        // Sort the list alphabetically by name (case-insensitive)
        units.sort((a, b) -> {
            String nameA = (a.name != null && !a.name.trim().isEmpty()) ? a.name : "No Name";
            String nameB = (b.name != null && !b.name.trim().isEmpty()) ? b.name : "No Name";
            return nameA.compareToIgnoreCase(nameB);
        });

        // For each unit, create a panel showing its name, description, and buttons.
        for (ProcessingUnit unit : units) {
            String name = (unit.name != null && !unit.name.trim().isEmpty()) ? unit.name : "No Name";
            String description = (unit.description != null && !unit.description.trim().isEmpty()) ? unit.description : "No Description";

            // Create an item panel with a titled border using the name.
            JPanel itemPanel = new JPanel(new BorderLayout());
            itemPanel.setBorder(BorderFactory.createTitledBorder(name));

            // Create an info panel for the description and type.
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

            JLabel descriptionLabel = new JLabel("Description: " + description);
            descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(descriptionLabel);

            JLabel typeLabel = new JLabel("Type: " + unit.type);
            typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(typeLabel);

            // Add info panel to the center.
            itemPanel.add(infoPanel, BorderLayout.CENTER);

            // Create a button panel on the right.
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

            // Edit button.
            JButton editButton = new JButton();
            editButton.setIcon(new FlatSVGIcon("icon/svg/edit.svg", 16, 16));
            editButton.setToolTipText("Edit this Processing Unit");
            editButton.addActionListener((ActionEvent e) -> {
                mainForm.setSelectedMenu(3, 2); // Unit Library -> Create/Edit Unit
                mainForm.showForm(new UnitEditorForm(configManager, mainForm, unit));
            });

            // Delete button.
            JButton deleteButton = new JButton();
            deleteButton.setIcon(new FlatSVGIcon("icon/svg/trash.svg", 16, 16));
            deleteButton.setToolTipText("Delete this Processing Unit");
            deleteButton.addActionListener((ActionEvent e) -> {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Are you sure you want to delete this unit? It may be used in pipelines.",
                        "Confirm Delete",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    configManager.deleteProcessingUnit(unit.uuid);
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
