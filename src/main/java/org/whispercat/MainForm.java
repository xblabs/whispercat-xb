package org.whispercat;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import org.whispercat.recording.RecorderForm;
import org.whispercat.settings.SettingsForm;
import org.whispercat.sidemenu.Menu;
import org.whispercat.sidemenu.MenuAction;
import org.whispercat.postprocessing.PostProcessingListForm;
import org.whispercat.postprocessing.PostProcessingForm;
import org.whispercat.postprocessing.PipelineListForm;
import org.whispercat.postprocessing.PipelineEditorForm;
import org.whispercat.postprocessing.UnitLibraryListForm;
import org.whispercat.postprocessing.UnitEditorForm;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


public class MainForm extends JLayeredPane {


    private GlobalHotkeyListener globalHotkeyListener;
    private ConfigManager configManager;
    public RecorderForm recorderForm;
    public SettingsForm settingsForm;
    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(MainForm.class);

    public MainForm() {
        init();
    }

    private void init() {
        setBorder(new EmptyBorder(5, 5, 5, 5));
        setLayout(new MainFormLayout());
        menu = new Menu();

        panelBody = new JPanel(new BorderLayout());
        initMenuArrowIcon();
        menuButton.putClientProperty(FlatClientProperties.STYLE, ""
                + "background:$Menu.button.background;"
                + "arc:999;"
                + "focusWidth:0;"
                + "borderWidth:0");
        menuButton.addActionListener((ActionEvent e) -> {
            setMenuFull(!menu.isMenuFull());
        });

        menu.getHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                menu.setSelectedMenu(0, 0);
            }
        });
        initMenuEvent();
        setLayer(menuButton, JLayeredPane.POPUP_LAYER);
        add(menuButton);
        add(menu);
        add(panelBody);

        configManager = new ConfigManager();
        // Migrate old post-processing data to new Pipeline architecture
        configManager.migrateOldPostProcessingData();
        extractNativeLibraries();
        String hotkey = configManager.getKeyCombination();
        globalHotkeyListener = new GlobalHotkeyListener(this, hotkey, configManager.getKeySequence());
    }

    @Override
    public void applyComponentOrientation(ComponentOrientation o) {
        super.applyComponentOrientation(o);
        initMenuArrowIcon();
    }

    private void initMenuArrowIcon() {
        if (menuButton == null) {
            menuButton = new JButton();
        }
        String icon = (getComponentOrientation().isLeftToRight()) ? "menu_left.svg" : "menu_right.svg";
        menuButton.setIcon(new FlatSVGIcon("icon/svg/" + icon, 0.8f));
    }

    private void initMenuEvent() {
        menu.addMenuEvent((int index, int subIndex, MenuAction action) -> {
            globalHotkeyListener.setOptionsDialogOpen(false, null, null);
            globalHotkeyListener.updateKeyCombination(configManager.getKeyCombination());
            globalHotkeyListener.updateKeySequence(configManager.getKeySequence());

            // Don't stop recording when switching screens - let user keep recording
            // Recording continues in background, only stopped by explicit user action

            // Stop audio test and auto-save settings when leaving settings screen
            if( index != 1 && settingsForm != null) {
                settingsForm.stopAudioTest();
                settingsForm.saveSettings();  // Auto-save to prevent confusion
            }

            // Reuse RecorderForm instance to preserve state (transcription, logs, etc.)
            if (index == 0) {
                if (recorderForm == null) {
                    recorderForm = new RecorderForm(configManager);
                }
                showForm(recorderForm);
                // Refresh pipelines in case new ones were created
                recorderForm.refreshPipelines();
            } else if (index == 1) {
                if (subIndex == 1) {
                    // Reuse SettingsForm instance to preserve slider values
                    if (settingsForm == null) {
                        settingsForm = new SettingsForm(configManager);
                    }
                    showForm(settingsForm);
                    globalHotkeyListener.setOptionsDialogOpen(true, settingsForm.getKeybindTextField(), settingsForm.getKeySequenceTextField());
                } else if (subIndex == 2) {
                    showForm(new LogsForm());
                } else {
                    action.cancel();
                }
            } else if (index == 2) {
                // Pipelines menu
                if (subIndex == 1) {
                    showForm(new PipelineListForm(configManager, this));
                }
                if (subIndex == 2) {
                    showForm(new PipelineEditorForm(configManager, this, null));
                }
            } else if (index == 3) {
                // Unit Library menu
                if (subIndex == 1) {
                    showForm(new UnitLibraryListForm(configManager, this));
                }
                if (subIndex == 2) {
                    showForm(new UnitEditorForm(configManager, this, null));
                }
            }
            else if (index == 9) {
            } else {
                action.cancel();
            }
        });
    }

    private void setMenuFull(boolean full) {
        String icon;
        if (getComponentOrientation().isLeftToRight()) {
            icon = (full) ? "menu_left.svg" : "menu_right.svg";
        } else {
            icon = (full) ? "menu_right.svg" : "menu_left.svg";
        }
        menuButton.setIcon(new FlatSVGIcon("icon/svg/" + icon, 0.8f));
        menu.setMenuFull(full);
        revalidate();
    }

    public void hideMenu() {
        menu.hideMenuItem();
    }

    public void showForm(Component component) {
        panelBody.removeAll();
        panelBody.add(component);
        panelBody.repaint();
        panelBody.revalidate();
    }

    public void setSelectedMenu(int index, int subIndex) {
        menu.setSelectedMenu(index, subIndex);
    }

    private Menu menu;
    private JPanel panelBody;
    private JButton menuButton;

    private class MainFormLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                return new Dimension(5, 5);
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                return new Dimension(0, 0);
            }
        }

        @Override
        public void layoutContainer(Container parent) {
            synchronized (parent.getTreeLock()) {
                boolean ltr = parent.getComponentOrientation().isLeftToRight();
                Insets insets = UIScale.scale(parent.getInsets());
                int x = insets.left;
                int y = insets.top;
                int width = parent.getWidth() - (insets.left + insets.right);
                int height = parent.getHeight() - (insets.top + insets.bottom);
                int menuWidth = UIScale.scale(menu.isMenuFull() ? menu.getMenuMaxWidth() : menu.getMenuMinWidth());
                int menuX = ltr ? x : x + width - menuWidth;
                menu.setBounds(menuX, y, menuWidth, height);
                int menuButtonWidth = menuButton.getPreferredSize().width;
                int menuButtonHeight = menuButton.getPreferredSize().height;
                int menubX;
                if (ltr) {
                    menubX = (int) (x + menuWidth - (menuButtonWidth * (menu.isMenuFull() ? 0.5f : 0.3f)));
                } else {
                    menubX = (int) (menuX - (menuButtonWidth * (menu.isMenuFull() ? 0.5f : 0.7f)));
                }
                menuButton.setBounds(menubX, UIScale.scale(30), menuButtonWidth, menuButtonHeight);
                int gap = UIScale.scale(5);
                int bodyWidth = width - menuWidth - gap;
                int bodyHeight = height;
                int bodyx = ltr ? (x + menuWidth + gap) : x;
                int bodyy = y;
                panelBody.setBounds(bodyx, bodyy, bodyWidth, bodyHeight);
            }
        }
    }

    private void extractNativeLibraries() {
        String[] platforms = {"windows", "linux", "macos"};
        String[] architectures = {"x86", "x86_64"};
        String libName = "JNativeHook";
        String baseDir = configManager.getConfigDirectory();
        for (String platform : platforms) {
            for (String arch : architectures) {
                String libFileName = System.mapLibraryName(libName);
                if (platform.equals("macos")) {
                    libFileName = libFileName.replace(".jnilib", ".dylib");
                }
                String pathInJar = "/native/" + platform + "/" + arch + "/" + libFileName;
                String outputPath = baseDir + "/" + platform + "/" + arch + "/" + libFileName;
                File outputFile = new File(outputPath);
                if (!outputFile.exists()) {
                    outputFile.getParentFile().mkdirs();
                    try (InputStream is = getClass().getResourceAsStream(pathInJar);
                         OutputStream os = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    } catch (Exception e) {
                        logger.error("Error extracting native libraries", e);
                    }
                }
            }
        }
    }
}
