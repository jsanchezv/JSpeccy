/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * SettingsDialog.java
 *
 * Created on 03-sep-2010, 16:42:01
 */

package gui;

import configuration.JSpeccySettings;

import jakarta.xml.bind.JAXB;
import java.awt.Component;
import java.awt.Frame;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ResourceBundle;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author jsanchez
 */
@Slf4j
public class SettingsDialog extends javax.swing.JPanel {

    private final JSpeccySettings settings;
    private JDialog settingsDialog;

    /** Creates new form SettingsDialog */
    public SettingsDialog(JSpeccySettings userSettings) {
        initComponents();
        settings = userSettings;
    }

    private void updateUserSettings() {

        spectrumModel.setSelectedIndex(settings.getSpectrumSettings().getDefaultModel());

        soundMuted.setSelected(settings.getSpectrumSettings().isMutedSound());

        loadingNoise.setSelected(settings.getSpectrumSettings().isLoadingNoise());

        hifiSound.setSelected(settings.getSpectrumSettings().isHifiSound());

        enableLoadTraps.setSelected(settings.getTapeSettings().isEnableLoadTraps());

        acceleratedLoad.setSelected(settings.getTapeSettings().isAccelerateLoading());

        flashLoad.setSelected(settings.getTapeSettings().isFlashLoad());

        flashLoad.setEnabled(settings.getTapeSettings().isEnableLoadTraps());

        autoLoadTape.setSelected(settings.getTapeSettings().isAutoLoadTape());

        ULAplus.setSelected(settings.getSpectrumSettings().isULAplus());

        joystick.setSelectedIndex(settings.getKeyboardJoystickSettings().getJoystickModel());

        enabledAY48k.setSelected(settings.getSpectrumSettings().isAYEnabled48K());

        speed.setValue(settings.getSpectrumSettings().getFramesInt());

        zoomCheckbox.setSelected(settings.getSpectrumSettings().isZoomed());

        zoomSlider.setValue(settings.getSpectrumSettings().getZoom());

        if (settings.getKeyboardJoystickSettings().isIssue2()) {
            issue2.setSelected(true);
        } else
            issue3.setSelected(true);

        mapPCKeys.setSelected(settings.getKeyboardJoystickSettings().isMapPCKeys());

        enableSaveTraps.setSelected(settings.getTapeSettings().isEnableSaveTraps());

        if (settings.getTapeSettings().isHighSamplingFreq()) {
            highSampling.setSelected(true);
        } else {
            lowSampling.setSelected(true);
        }

        switch (settings.getAY8912Settings().getSoundMode()) {
            case 1: // Stereo ABC
                AYABCMode.setSelected(true);
                break;
            case 2: // Stereo ACB
                AYACBMode.setSelected(true);
                break;
            case 3: // Stereo BAC
                AYBACMode.setSelected(true);
                break;
            default:
                AYMonoMode.setSelected(true);
        }

        multifaceEnabled.setSelected(settings.getSpectrumSettings().isMultifaceEnabled());
        if (settings.getSpectrumSettings().isMf128On48K()) {
            multiface128RadioButton.setSelected(true);
        } else {
            multifaceOneRadioButton.setSelected(true);
        }

        connectedIF1.setSelected(settings.getInterface1Settings().isConnectedIF1());
        numDrivesSpinner.setValue(settings.getInterface1Settings().getMicrodriveUnits());
        cartridgeSizeSpinner.setValue(settings.getInterface1Settings().getCartridgeSize());
        autoSaveOnExit.setSelected(settings.getSpectrumSettings().isHibernateMode());
        lecEnabled.setSelected(settings.getSpectrumSettings().isLecEnabled());
        confirmActions.setSelected(settings.getEmulatorSettings().isConfirmActions());
        autosaveConfigOnExit.setSelected(settings.getEmulatorSettings().isAutosaveConfigOnExit());
        invertedEar.setSelected(settings.getTapeSettings().isInvertedEar());
        recreatedZX.setSelected(settings.getKeyboardJoystickSettings().isRecreatedZX());
    }

    public boolean showDialog(Component parent, String title) {
        Frame owner;
        if (parent instanceof Frame) {
            owner = (Frame) parent;
        } else {
            owner = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent);
        }

        if (settingsDialog == null) {
            settingsDialog = new JDialog(owner, true);
            settingsDialog.getContentPane().add(this);
            settingsDialog.pack();
            settingsDialog.setLocationRelativeTo(parent);
        }

        updateUserSettings();
        settingsDialog.setTitle(title);
        settingsDialog.setVisible(true);
        return true;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        keyboardButtonGroup = new javax.swing.ButtonGroup();
        samplingButtonGroup = new javax.swing.ButtonGroup();
        AYStereoModeButtonGroup = new javax.swing.ButtonGroup();
        multifaceModelButtonGroup = new javax.swing.ButtonGroup();
        buttonPanel = new javax.swing.JPanel();
        saveSettingsButton = new javax.swing.JButton();
        closeButton = new javax.swing.JButton();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        hardwarePanelTab = new javax.swing.JPanel();
        defaultModelPanel = new javax.swing.JPanel();
        spectrumModel = new javax.swing.JComboBox();
        videoPanel = new javax.swing.JPanel();
        ULAplus = new javax.swing.JCheckBox();
        zoomPanel = new javax.swing.JPanel();
        zoomCheckbox = new javax.swing.JCheckBox();
        zoomLabel = new javax.swing.JLabel();
        zoomSlider = new javax.swing.JSlider();
        highSpeedPanel = new javax.swing.JPanel();
        speed = new javax.swing.JSlider();
        autoSavePanel = new javax.swing.JPanel();
        autoSaveOnExit = new javax.swing.JCheckBox();
        soundPanelTab = new javax.swing.JPanel();
        audioPanel = new javax.swing.JPanel();
        soundMuted = new javax.swing.JCheckBox();
        loadingNoise = new javax.swing.JCheckBox();
        hifiSound = new javax.swing.JCheckBox();
        AY8912Panel = new javax.swing.JPanel();
        AYEnabled48k = new javax.swing.JPanel();
        enabledAY48k = new javax.swing.JCheckBox();
        AYStereoMode = new javax.swing.JPanel();
        AYMonoMode = new javax.swing.JRadioButton();
        AYABCMode = new javax.swing.JRadioButton();
        AYACBMode = new javax.swing.JRadioButton();
        AYBACMode = new javax.swing.JRadioButton();
        tapePanelTab = new javax.swing.JPanel();
        loadPanel = new javax.swing.JPanel();
        enableLoadTraps = new javax.swing.JCheckBox();
        flashLoad = new javax.swing.JCheckBox();
        acceleratedLoad = new javax.swing.JCheckBox();
        autoLoadTape = new javax.swing.JCheckBox();
        invertedEar = new javax.swing.JCheckBox();
        savePanel = new javax.swing.JPanel();
        enableSaveTraps = new javax.swing.JCheckBox();
        samplingPanel = new javax.swing.JPanel();
        lowSampling = new javax.swing.JRadioButton();
        highSampling = new javax.swing.JRadioButton();
        keyboardPanelTab = new javax.swing.JPanel();
        keyboard48kPanel = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        keyboardIssueInfoLabel = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        issue2 = new javax.swing.JRadioButton();
        issue3 = new javax.swing.JRadioButton();
        mapPCKeyPanel = new javax.swing.JPanel();
        mapPCKeys = new javax.swing.JCheckBox();
        recreatedZXPanel = new javax.swing.JPanel();
        recreatedZX = new javax.swing.JCheckBox();
        joystickPanel = new javax.swing.JPanel();
        joystick = new javax.swing.JComboBox();
        multifacePanelTab = new javax.swing.JPanel();
        multifacePanel = new javax.swing.JPanel();
        multifaceEnabled = new javax.swing.JCheckBox();
        multifaceModelPanel = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        multifaceOneRadioButton = new javax.swing.JRadioButton();
        multiface128RadioButton = new javax.swing.JRadioButton();
        IF1PanelTab = new javax.swing.JPanel();
        connectedIF1Panel = new javax.swing.JPanel();
        connectedIF1InfoLabel = new javax.swing.JLabel();
        jPanel4 = new javax.swing.JPanel();
        connectedIF1 = new javax.swing.JCheckBox();
        mdrPanel = new javax.swing.JPanel();
        numDrivesPanel = new javax.swing.JPanel();
        numDrivesLabel = new javax.swing.JLabel();
        numDrivesSpinner = new javax.swing.JSpinner();
        numBlocksInfoPanel = new javax.swing.JPanel();
        numBlocksInfoLabel = new javax.swing.JLabel();
        numBlocksPanel = new javax.swing.JPanel();
        numSectorsLabel = new javax.swing.JLabel();
        cartridgeSizeSpinner = new javax.swing.JSpinner();
        lecPanelTab = new javax.swing.JPanel();
        lecInfoPanel = new javax.swing.JPanel();
        lecInfoLabel = new javax.swing.JLabel();
        lecEnabledPanel = new javax.swing.JPanel();
        lecEnabled = new javax.swing.JCheckBox();
        emulatorPanelTab = new javax.swing.JPanel();
        confirmActions = new javax.swing.JCheckBox();
        autosaveConfigOnExit = new javax.swing.JCheckBox();

        setMinimumSize(new java.awt.Dimension(50, 50));
        setPreferredSize(new java.awt.Dimension(440, 400));
        setLayout(new java.awt.BorderLayout());

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        saveSettingsButton.setText(bundle.getString("SettingsDialog.saveSettingsButton.text")); // NOI18N
        saveSettingsButton.addActionListener(this::saveSettingsButtonActionPerformed);
        buttonPanel.add(saveSettingsButton);

        closeButton.setText(bundle.getString("CLOSE")); // NOI18N
        closeButton.addActionListener(this::closeButtonActionPerformed);
        buttonPanel.add(closeButton);

        add(buttonPanel, java.awt.BorderLayout.PAGE_END);

        jTabbedPane1.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        hardwarePanelTab.setLayout(new javax.swing.BoxLayout(hardwarePanelTab, javax.swing.BoxLayout.PAGE_AXIS));

        defaultModelPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.defaultModePanel.border.text"))); // NOI18N

        spectrumModel.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Spectrum 16k", "Spectrum 48k", "Spectrum 128k", "Spectrum +2", "Spectrum +2A", "Spectrum +3" }));
        spectrumModel.addActionListener(this::spectrumModelActionPerformed);
        defaultModelPanel.add(spectrumModel);

        hardwarePanelTab.add(defaultModelPanel);

        videoPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Video"));
        videoPanel.setLayout(new java.awt.GridLayout(2, 1));

        ULAplus.setText(bundle.getString("SettingsDialog.hardwarePanel.ULAplus.text")); // NOI18N
        ULAplus.setPreferredSize(new java.awt.Dimension(440, 24));
        ULAplus.addActionListener(this::ULAplusActionPerformed);
        videoPanel.add(ULAplus);

        zoomPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        zoomPanel.setMaximumSize(new java.awt.Dimension(32767, 53));
        zoomPanel.setPreferredSize(new java.awt.Dimension(100, 53));
        zoomPanel.setLayout(new java.awt.GridLayout(1, 3));

        zoomCheckbox.setText(bundle.getString("SettingsDialog.hardwarePanel.zoomCheckbox.text")); // NOI18N
        zoomCheckbox.addActionListener(this::zoomCheckboxActionPerformed);
        zoomPanel.add(zoomCheckbox);

        zoomLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        zoomLabel.setText(bundle.getString("SettingsDialog.hardwarePanel.zoomLabel.text")); // NOI18N
        zoomPanel.add(zoomLabel);

        zoomSlider.setMajorTickSpacing(1);
        zoomSlider.setMaximum(4);
        zoomSlider.setMinimum(2);
        zoomSlider.setPaintLabels(true);
        zoomSlider.setPaintTicks(true);
        zoomSlider.setSnapToTicks(true);
        zoomSlider.setValue(2);
        zoomSlider.addChangeListener(this::zoomSliderStateChanged);
        zoomPanel.add(zoomSlider);

        videoPanel.add(zoomPanel);

        hardwarePanelTab.add(videoPanel);

        highSpeedPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.highSpeedPanel.border.text"))); // NOI18N

        speed.setMajorTickSpacing(1);
        speed.setMaximum(10);
        speed.setMinimum(2);
        speed.setPaintLabels(true);
        speed.setPaintTicks(true);
        speed.setSnapToTicks(true);
        speed.setPreferredSize(new java.awt.Dimension(300, 43));
        speed.addChangeListener(this::speedStateChanged);
        highSpeedPanel.add(speed);

        hardwarePanelTab.add(highSpeedPanel);

        autoSavePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.autoSavePanel.border.text"))); // NOI18N

        autoSaveOnExit.setText(bundle.getString("SettingsDialog.autoSaveOnExit.text")); // NOI18N
        autoSaveOnExit.setToolTipText(bundle.getString("SettingsDialog.autoSaveOnExit.tooltip.text")); // NOI18N
        autoSaveOnExit.addActionListener(this::autoSaveOnExitActionPerformed);
        autoSavePanel.add(autoSaveOnExit);

        hardwarePanelTab.add(autoSavePanel);

        jTabbedPane1.addTab(bundle.getString("SettingsDialog.hardwarePanel.TabTitle"), hardwarePanelTab); // NOI18N

        soundPanelTab.setLayout(new java.awt.GridLayout(2, 0));

        audioPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.audioPanel.border.text"))); // NOI18N
        audioPanel.setLayout(new java.awt.GridLayout(3, 0));

        soundMuted.setText(bundle.getString("SettingsDialog.soundPanel.soundMuted.text")); // NOI18N
        soundMuted.addActionListener(this::soundMutedActionPerformed);
        audioPanel.add(soundMuted);

        loadingNoise.setText(bundle.getString("SettingsDialog.soundPanel.loadingNoise.text")); // NOI18N
        loadingNoise.addActionListener(this::loadingNoiseActionPerformed);
        audioPanel.add(loadingNoise);

        hifiSound.setText(bundle.getString("SettingsDialog.audioPanel.hifiSound.text")); // NOI18N
        hifiSound.addActionListener(this::hifiSoundActionPerformed);
        audioPanel.add(hifiSound);

        soundPanelTab.add(audioPanel);

        AY8912Panel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.AY8912Panel.border.text"))); // NOI18N
        AY8912Panel.setLayout(new java.awt.GridLayout(1, 2));

        AYEnabled48k.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.AYEnabled48kPanel.border.text"))); // NOI18N

        enabledAY48k.setText(bundle.getString("SettingsDialog.soundPanel.enabledAY48k.text")); // NOI18N
        enabledAY48k.addActionListener(this::enabledAY48kActionPerformed);
        AYEnabled48k.add(enabledAY48k);

        AY8912Panel.add(AYEnabled48k);

        AYStereoMode.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.AYStereoMode.border.text"))); // NOI18N
        AYStereoMode.setLayout(new java.awt.GridLayout(4, 0));

        AYStereoModeButtonGroup.add(AYMonoMode);
        AYMonoMode.setSelected(true);
        AYMonoMode.setText(bundle.getString("SettingsDialog.AYMonoMode.RadioButton.text")); // NOI18N
        AYMonoMode.addActionListener(this::AYMonoModeActionPerformed);
        AYStereoMode.add(AYMonoMode);

        AYStereoModeButtonGroup.add(AYABCMode);
        AYABCMode.setText(bundle.getString("SettingsDialog.AYABCMode.RadioButton.text")); // NOI18N
        AYABCMode.addActionListener(this::AYABCModeActionPerformed);
        AYStereoMode.add(AYABCMode);

        AYStereoModeButtonGroup.add(AYACBMode);
        AYACBMode.setText(bundle.getString("SettingsDialog.AYACBMode.RadioButton.text")); // NOI18N
        AYACBMode.addActionListener(this::AYACBModeActionPerformed);
        AYStereoMode.add(AYACBMode);

        AYStereoModeButtonGroup.add(AYBACMode);
        AYBACMode.setText(bundle.getString("SettingsDialog.AYBACMode.RadioButton.text")); // NOI18N
        AYBACMode.addActionListener(this::AYBACModeActionPerformed);
        AYStereoMode.add(AYBACMode);

        AY8912Panel.add(AYStereoMode);

        soundPanelTab.add(AY8912Panel);

        jTabbedPane1.addTab(bundle.getString("SettingsDialog.soundPanel.TabTitle"), soundPanelTab); // NOI18N

        tapePanelTab.setLayout(new java.awt.GridLayout(2, 0));

        loadPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.loadPanel.border.text"))); // NOI18N
        loadPanel.setLayout(new java.awt.GridLayout(5, 1));

        enableLoadTraps.setText(bundle.getString("SettingsDialog.tapePanel.enableLoadTraps.text")); // NOI18N
        enableLoadTraps.addActionListener(this::enableLoadTrapsActionPerformed);
        loadPanel.add(enableLoadTraps);

        flashLoad.setText(bundle.getString("SettingsDialog.tapePanel.flashload.text")); // NOI18N
        flashLoad.addActionListener(this::flashLoadActionPerformed);
        loadPanel.add(flashLoad);

        acceleratedLoad.setText(bundle.getString("SettingsDialog.tapePanel.acceleratedLoad.text")); // NOI18N
        acceleratedLoad.addActionListener(this::acceleratedLoadActionPerformed);
        loadPanel.add(acceleratedLoad);

        autoLoadTape.setText(bundle.getString("SettingsDialog.tapePanel.autoLoadTape.text")); // NOI18N
        autoLoadTape.addActionListener(this::autoLoadTapeActionPerformed);
        loadPanel.add(autoLoadTape);

        invertedEar.setText(bundle.getString("SettingsDialog.tapePanel.invertedEar.text")); // NOI18N
        invertedEar.addActionListener(this::invertedEarActionPerformed);
        loadPanel.add(invertedEar);

        tapePanelTab.add(loadPanel);

        savePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.savePanel.border.text"))); // NOI18N
        savePanel.setLayout(new java.awt.GridLayout(1, 2));

        enableSaveTraps.setSelected(true);
        enableSaveTraps.setText(bundle.getString("SettingsDialog.savePanel.enableSaveTraps.text")); // NOI18N
        enableSaveTraps.addActionListener(this::enableSaveTrapsActionPerformed);
        savePanel.add(enableSaveTraps);

        samplingPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.highSamplingFreq.border.text"))); // NOI18N
        samplingPanel.setLayout(new java.awt.GridLayout(2, 1));

        samplingButtonGroup.add(lowSampling);
        lowSampling.setSelected(true);
        lowSampling.setText("DRB (44.1 kHz)");
        lowSampling.setToolTipText("Direct Recording Block");
        lowSampling.addActionListener(this::lowSamplingActionPerformed);
        samplingPanel.add(lowSampling);

        samplingButtonGroup.add(highSampling);
        highSampling.setText("CSW Z-RLE (48 kHz)");
        highSampling.setToolTipText("<html>Compressed Square Wave<br>Run Lenght Encoding</html>");
        highSampling.addActionListener(this::highSamplingActionPerformed);
        samplingPanel.add(highSampling);

        savePanel.add(samplingPanel);

        tapePanelTab.add(savePanel);

        jTabbedPane1.addTab(bundle.getString("SettingsDialog.tapePanel.TabTitle"), tapePanelTab); // NOI18N

        keyboardPanelTab.setLayout(new javax.swing.BoxLayout(keyboardPanelTab, javax.swing.BoxLayout.PAGE_AXIS));

        keyboard48kPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.keyboard48kPanel.title.text"))); // NOI18N
        keyboard48kPanel.setMinimumSize(new java.awt.Dimension(317, 120));
        keyboard48kPanel.setLayout(new javax.swing.BoxLayout(keyboard48kPanel, javax.swing.BoxLayout.PAGE_AXIS));

        keyboardIssueInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        keyboardIssueInfoLabel.setText(bundle.getString("SettingsDialog.keyboardIssueInfoLabel.text")); // NOI18N
        jPanel3.add(keyboardIssueInfoLabel);

        keyboard48kPanel.add(jPanel3);

        jPanel2.setMinimumSize(new java.awt.Dimension(307, 25));
        jPanel2.setPreferredSize(new java.awt.Dimension(307, 25));

        keyboardButtonGroup.add(issue2);
        issue2.setText(bundle.getString("SettingsDialog.issue2RadioButton.text")); // NOI18N
        issue2.addActionListener(this::issue2ActionPerformed);
        jPanel2.add(issue2);

        keyboardButtonGroup.add(issue3);
        issue3.setSelected(true);
        issue3.setText(bundle.getString("SettingsDialog.issue3RadioButton.text")); // NOI18N
        issue3.addActionListener(this::issue2ActionPerformed);
        jPanel2.add(issue3);

        keyboard48kPanel.add(jPanel2);

        keyboardPanelTab.add(keyboard48kPanel);

        mapPCKeyPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.mapPCKeysPanel.TabTitle"))); // NOI18N
        mapPCKeyPanel.setMinimumSize(new java.awt.Dimension(102, 50));
        mapPCKeyPanel.setPreferredSize(new java.awt.Dimension(102, 50));

        mapPCKeys.setText(bundle.getString("SettingsDialog.mapPCKEysPanel.enabled.text")); // NOI18N
        mapPCKeys.addActionListener(this::mapPCKeysActionPerformed);
        mapPCKeyPanel.add(mapPCKeys);

        keyboardPanelTab.add(mapPCKeyPanel);

        recreatedZXPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.recreatedZXPanel.title.text"))); // NOI18N

        recreatedZX.setText(bundle.getString("SettingsDialog.recreatedZX.enabled.text")); // NOI18N
        recreatedZX.addActionListener(this::recreatedZXActionPerformed);
        recreatedZXPanel.add(recreatedZX);

        keyboardPanelTab.add(recreatedZXPanel);

        joystickPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.joystickPanel.border.text"))); // NOI18N
        joystickPanel.setMinimumSize(new java.awt.Dimension(177, 50));
        joystickPanel.setPreferredSize(new java.awt.Dimension(177, 50));

        joystick.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "None", "Kempston", "Sinclair 1", "Sinclair 2", "Cursor/AGF/Protek", "Fuller" }));
        joystick.addActionListener(this::joystickActionPerformed);
        joystickPanel.add(joystick);

        keyboardPanelTab.add(joystickPanel);

        jTabbedPane1.addTab(bundle.getString("SettingsDialog.keyboardPanel.TabTitle"), keyboardPanelTab); // NOI18N

        multifacePanelTab.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.MultifacePanel.border.text"))); // NOI18N
        multifacePanelTab.setLayout(new javax.swing.BoxLayout(multifacePanelTab, javax.swing.BoxLayout.PAGE_AXIS));

        multifaceEnabled.setText(bundle.getString("SettingsDialog.multifacePanel.enabled.text")); // NOI18N
        multifaceEnabled.addActionListener(this::multifaceEnabledActionPerformed);
        multifacePanel.add(multifaceEnabled);

        multifacePanelTab.add(multifacePanel);

        multifaceModelPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.MultifaceModelPanel.border.text"))); // NOI18N
        multifaceModelPanel.setLayout(new java.awt.GridLayout(2, 0));

        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel2.setText(bundle.getString("SettingsDialog.MultifaceModelPanel.label.text")); // NOI18N
        multifaceModelPanel.add(jLabel2);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.MultifaceModelPanelRadioButton.border.text"))); // NOI18N
        jPanel1.setLayout(new java.awt.GridLayout(2, 0));

        multifaceModelButtonGroup.add(multifaceOneRadioButton);
        multifaceOneRadioButton.setSelected(true);
        multifaceOneRadioButton.setText(bundle.getString("SettingsDialog.multifaceOne.RadioButton.text")); // NOI18N
        multifaceOneRadioButton.addActionListener(this::multifaceOneRadioButtonActionPerformed);
        jPanel1.add(multifaceOneRadioButton);

        multifaceModelButtonGroup.add(multiface128RadioButton);
        multiface128RadioButton.setText(bundle.getString("SettingsDialog.multiface128.RadioButton.text")); // NOI18N
        multiface128RadioButton.addActionListener(this::multifaceOneRadioButtonActionPerformed);
        jPanel1.add(multiface128RadioButton);

        multifaceModelPanel.add(jPanel1);

        multifacePanelTab.add(multifaceModelPanel);

        jTabbedPane1.addTab(bundle.getString("SettingsDialog.multifacePanel.TabTitle"), multifacePanelTab); // NOI18N

        IF1PanelTab.setLayout(new java.awt.GridLayout(2, 1));

        connectedIF1Panel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.connectedIF1.border.text"))); // NOI18N
        connectedIF1Panel.setLayout(new java.awt.GridLayout(2, 0));

        connectedIF1InfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        connectedIF1InfoLabel.setText(bundle.getString("SettingsDialog.connectedIF1InfoLabel.text")); // NOI18N
        connectedIF1Panel.add(connectedIF1InfoLabel);

        connectedIF1.setText(bundle.getString("SettingsDialog.connectedIF1Panel.enabled.text")); // NOI18N
        connectedIF1.addActionListener(this::connectedIF1ActionPerformed);
        jPanel4.add(connectedIF1);

        connectedIF1Panel.add(jPanel4);

        IF1PanelTab.add(connectedIF1Panel);

        mdrPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("ZX Microdrives"));
        mdrPanel.setLayout(new java.awt.GridLayout(3, 1));

        numDrivesLabel.setText(bundle.getString("SettingsDialog.mdrPanel.numDrivesLabel.text")); // NOI18N
        numDrivesPanel.add(numDrivesLabel);

        numDrivesSpinner.setModel(new javax.swing.SpinnerNumberModel(Byte.valueOf((byte)8), Byte.valueOf((byte)1), Byte.valueOf((byte)8), Byte.valueOf((byte)1)));
        numDrivesSpinner.setPreferredSize(new java.awt.Dimension(40, 20));
        numDrivesSpinner.addChangeListener(this::numDrivesSpinnerStateChanged);
        numDrivesPanel.add(numDrivesSpinner);

        mdrPanel.add(numDrivesPanel);

        numBlocksInfoLabel.setText(bundle.getString("SettingsDialog.numBlocksInfoLabel.text")); // NOI18N
        numBlocksInfoPanel.add(numBlocksInfoLabel);

        mdrPanel.add(numBlocksInfoPanel);

        numSectorsLabel.setText(bundle.getString("SettingsDialog.mdrPanel.numSectorsLabel.text")); // NOI18N
        numBlocksPanel.add(numSectorsLabel);

        cartridgeSizeSpinner.setModel(new javax.swing.SpinnerNumberModel(180, 10, 253, 1));
        cartridgeSizeSpinner.addChangeListener(this::cartridgeSizeSpinnerStateChanged);
        numBlocksPanel.add(cartridgeSizeSpinner);

        mdrPanel.add(numBlocksPanel);

        IF1PanelTab.add(mdrPanel);

        jTabbedPane1.addTab("Interface I", IF1PanelTab);

        lecPanelTab.setLayout(new javax.swing.BoxLayout(lecPanelTab, javax.swing.BoxLayout.PAGE_AXIS));

        lecInfoPanel.setMaximumSize(new java.awt.Dimension(400, 640));
        lecInfoPanel.setPreferredSize(new java.awt.Dimension(400, 100));
        lecInfoPanel.setLayout(new javax.swing.BoxLayout(lecInfoPanel, javax.swing.BoxLayout.LINE_AXIS));

        lecInfoLabel.setText(bundle.getString("SettingsDialog.lecPanelTab.lecInfoLabel.text")); // NOI18N
        lecInfoLabel.setMaximumSize(new java.awt.Dimension(2147483647, 640));
        lecInfoLabel.setPreferredSize(new java.awt.Dimension(380, 288));
        lecInfoPanel.add(lecInfoLabel);

        lecPanelTab.add(lecInfoPanel);

        lecEnabledPanel.setMaximumSize(new java.awt.Dimension(32767, 160));

        lecEnabled.setText(bundle.getString("SettingsDialog.lecPanelTab.lecEnabledLabel.text")); // NOI18N
        lecEnabled.addActionListener(this::lecEnabledActionPerformed);
        lecEnabledPanel.add(lecEnabled);

        lecPanelTab.add(lecEnabledPanel);

        jTabbedPane1.addTab("LEC", lecPanelTab);

        emulatorPanelTab.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("SettingsDialog.emulatorTab.border.text"))); // NOI18N
        emulatorPanelTab.setLayout(new java.awt.GridLayout(9, 0));

        confirmActions.setText(bundle.getString("SettingsDialog.confirmActions.text")); // NOI18N
        confirmActions.addActionListener(this::confirmActionsActionPerformed);
        emulatorPanelTab.add(confirmActions);

        autosaveConfigOnExit.setText(bundle.getString("SettingsDialog.autosaveConfigOnExit.text")); // NOI18N
        autosaveConfigOnExit.addActionListener(this::autosaveConfigOnExitActionPerformed);
        emulatorPanelTab.add(autosaveConfigOnExit);

        jTabbedPane1.addTab(bundle.getString("SettingsDialog.emulatorPanel.title.text"), emulatorPanelTab); // NOI18N

        add(jTabbedPane1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        settingsDialog.setVisible(false);
    }//GEN-LAST:event_closeButtonActionPerformed

    private void spectrumModelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spectrumModelActionPerformed
        settings.getSpectrumSettings().setDefaultModel(spectrumModel.getSelectedIndex());
    }//GEN-LAST:event_spectrumModelActionPerformed

    private void ULAplusActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ULAplusActionPerformed
        settings.getSpectrumSettings().setULAplus(ULAplus.isSelected());
    }//GEN-LAST:event_ULAplusActionPerformed

    private void speedStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_speedStateChanged
        settings.getSpectrumSettings().setFramesInt(speed.getValue());
    }//GEN-LAST:event_speedStateChanged

    private void issue2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_issue2ActionPerformed
        settings.getKeyboardJoystickSettings().setIssue2(issue2.isSelected());
    }//GEN-LAST:event_issue2ActionPerformed

    private void joystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_joystickActionPerformed
        settings.getKeyboardJoystickSettings().setJoystickModel(joystick.getSelectedIndex());
    }//GEN-LAST:event_joystickActionPerformed

    private void saveSettingsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveSettingsButtonActionPerformed
        if (settings.getEmulatorSettings().isConfirmActions()) {
            ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
            int ret = JOptionPane.showConfirmDialog(this,
                    bundle.getString("ARE_YOU_SURE_QUESTION"), bundle.getString("SAVE_SETTINGS_QUESTION"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE); // NOI18N

            if (ret == JOptionPane.NO_OPTION) {
                return;
            }
        }

        try ( BufferedOutputStream fOut =
                new BufferedOutputStream(new FileOutputStream(System.getProperty("user.home") + "/JSpeccy.xml"))) {
            // create an element for marshalling
//            JAXBElement<JSpeccySettingsType> confElement =
//                (new ObjectFactory()).createJSpeccySettings(settings);

            // create a Marshaller and marshal to conf. file
            JAXB.marshal(settings, fOut);
        } catch (IOException ex) {
            log.error("IOException: ", ex);
        }
    }//GEN-LAST:event_saveSettingsButtonActionPerformed

    private void soundMutedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_soundMutedActionPerformed
        settings.getSpectrumSettings().setMutedSound(soundMuted.isSelected());
    }//GEN-LAST:event_soundMutedActionPerformed

    private void loadingNoiseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadingNoiseActionPerformed
        settings.getSpectrumSettings().setLoadingNoise(loadingNoise.isSelected());
    }//GEN-LAST:event_loadingNoiseActionPerformed

    private void enabledAY48kActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enabledAY48kActionPerformed
        settings.getSpectrumSettings().setAYEnabled48K(enabledAY48k.isSelected());
    }//GEN-LAST:event_enabledAY48kActionPerformed

    private void enableLoadTrapsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableLoadTrapsActionPerformed
        settings.getTapeSettings().setEnableLoadTraps(enableLoadTraps.isSelected());
        flashLoad.setEnabled(settings.getTapeSettings().isEnableLoadTraps());
    }//GEN-LAST:event_enableLoadTrapsActionPerformed

    private void acceleratedLoadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_acceleratedLoadActionPerformed
        settings.getTapeSettings().setAccelerateLoading(acceleratedLoad.isSelected());
    }//GEN-LAST:event_acceleratedLoadActionPerformed

    private void enableSaveTrapsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableSaveTrapsActionPerformed
        settings.getTapeSettings().setEnableSaveTraps(enableSaveTraps.isSelected());
    }//GEN-LAST:event_enableSaveTrapsActionPerformed

    private void lowSamplingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lowSamplingActionPerformed
        settings.getTapeSettings().setHighSamplingFreq(false);
    }//GEN-LAST:event_lowSamplingActionPerformed

    private void highSamplingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_highSamplingActionPerformed
        settings.getTapeSettings().setHighSamplingFreq(true);
    }//GEN-LAST:event_highSamplingActionPerformed

    private void AYMonoModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AYMonoModeActionPerformed
        settings.getAY8912Settings().setSoundMode(0);
    }//GEN-LAST:event_AYMonoModeActionPerformed

    private void AYABCModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AYABCModeActionPerformed
        settings.getAY8912Settings().setSoundMode(1);
    }//GEN-LAST:event_AYABCModeActionPerformed

    private void AYACBModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AYACBModeActionPerformed
        settings.getAY8912Settings().setSoundMode(2);
    }//GEN-LAST:event_AYACBModeActionPerformed

    private void AYBACModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AYBACModeActionPerformed
        settings.getAY8912Settings().setSoundMode(3);
    }//GEN-LAST:event_AYBACModeActionPerformed

    private void multifaceEnabledActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_multifaceEnabledActionPerformed
        settings.getSpectrumSettings().setMultifaceEnabled(multifaceEnabled.isSelected());
    }//GEN-LAST:event_multifaceEnabledActionPerformed

    private void multifaceOneRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_multifaceOneRadioButtonActionPerformed
        settings.getSpectrumSettings().setMf128On48K(multiface128RadioButton.isSelected());
    }//GEN-LAST:event_multifaceOneRadioButtonActionPerformed

    private void hifiSoundActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hifiSoundActionPerformed
        settings.getSpectrumSettings().setHifiSound(hifiSound.isSelected());
    }//GEN-LAST:event_hifiSoundActionPerformed

    private void connectedIF1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_connectedIF1ActionPerformed
        settings.getInterface1Settings().setConnectedIF1(connectedIF1.isSelected());
    }//GEN-LAST:event_connectedIF1ActionPerformed

    private void numDrivesSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_numDrivesSpinnerStateChanged
        settings.getInterface1Settings().setMicrodriveUnits(
                ((SpinnerNumberModel)numDrivesSpinner.getModel()).getNumber().byteValue());
    }//GEN-LAST:event_numDrivesSpinnerStateChanged

    private void cartridgeSizeSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_cartridgeSizeSpinnerStateChanged
        settings.getInterface1Settings().setCartridgeSize(
                ((SpinnerNumberModel)cartridgeSizeSpinner.getModel()).getNumber().intValue());
    }//GEN-LAST:event_cartridgeSizeSpinnerStateChanged

    private void mapPCKeysActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mapPCKeysActionPerformed
        settings.getKeyboardJoystickSettings().setMapPCKeys(mapPCKeys.isSelected());
    }//GEN-LAST:event_mapPCKeysActionPerformed

    private void flashLoadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flashLoadActionPerformed
        settings.getTapeSettings().setFlashLoad(flashLoad.isSelected());
    }//GEN-LAST:event_flashLoadActionPerformed

    private void autoSaveOnExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoSaveOnExitActionPerformed
        settings.getSpectrumSettings().setHibernateMode(autoSaveOnExit.isSelected());
    }//GEN-LAST:event_autoSaveOnExitActionPerformed

    private void lecEnabledActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lecEnabledActionPerformed
        settings.getSpectrumSettings().setLecEnabled(lecEnabled.isSelected());
    }//GEN-LAST:event_lecEnabledActionPerformed

    private void zoomSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zoomSliderStateChanged
        settings.getSpectrumSettings().setZoom(zoomSlider.getValue());
    }//GEN-LAST:event_zoomSliderStateChanged

    private void zoomCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomCheckboxActionPerformed
        settings.getSpectrumSettings().setZoomed(zoomCheckbox.isSelected());
    }//GEN-LAST:event_zoomCheckboxActionPerformed

    private void autoLoadTapeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoLoadTapeActionPerformed
        settings.getTapeSettings().setAutoLoadTape(autoLoadTape.isSelected());
    }//GEN-LAST:event_autoLoadTapeActionPerformed

    private void confirmActionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_confirmActionsActionPerformed
        settings.getEmulatorSettings().setConfirmActions(confirmActions.isSelected());
    }//GEN-LAST:event_confirmActionsActionPerformed

    private void autosaveConfigOnExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autosaveConfigOnExitActionPerformed
        settings.getEmulatorSettings().setAutosaveConfigOnExit(autosaveConfigOnExit.isSelected());
    }//GEN-LAST:event_autosaveConfigOnExitActionPerformed

    private void invertedEarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_invertedEarActionPerformed
        settings.getTapeSettings().setInvertedEar(invertedEar.isSelected());
    }//GEN-LAST:event_invertedEarActionPerformed

    private void recreatedZXActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recreatedZXActionPerformed
        settings.getKeyboardJoystickSettings().setRecreatedZX(recreatedZX.isSelected());
    }//GEN-LAST:event_recreatedZXActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel AY8912Panel;
    private javax.swing.JRadioButton AYABCMode;
    private javax.swing.JRadioButton AYACBMode;
    private javax.swing.JRadioButton AYBACMode;
    private javax.swing.JPanel AYEnabled48k;
    private javax.swing.JRadioButton AYMonoMode;
    private javax.swing.JPanel AYStereoMode;
    private javax.swing.ButtonGroup AYStereoModeButtonGroup;
    private javax.swing.JPanel IF1PanelTab;
    private javax.swing.JCheckBox ULAplus;
    private javax.swing.JCheckBox acceleratedLoad;
    private javax.swing.JPanel audioPanel;
    private javax.swing.JCheckBox autoLoadTape;
    private javax.swing.JCheckBox autoSaveOnExit;
    private javax.swing.JPanel autoSavePanel;
    private javax.swing.JCheckBox autosaveConfigOnExit;
    private javax.swing.JPanel buttonPanel;
    private javax.swing.JSpinner cartridgeSizeSpinner;
    private javax.swing.JButton closeButton;
    private javax.swing.JCheckBox confirmActions;
    private javax.swing.JCheckBox connectedIF1;
    private javax.swing.JLabel connectedIF1InfoLabel;
    private javax.swing.JPanel connectedIF1Panel;
    private javax.swing.JPanel defaultModelPanel;
    private javax.swing.JPanel emulatorPanelTab;
    private javax.swing.JCheckBox enableLoadTraps;
    private javax.swing.JCheckBox enableSaveTraps;
    private javax.swing.JCheckBox enabledAY48k;
    private javax.swing.JCheckBox flashLoad;
    private javax.swing.JPanel hardwarePanelTab;
    private javax.swing.JCheckBox hifiSound;
    private javax.swing.JRadioButton highSampling;
    private javax.swing.JPanel highSpeedPanel;
    private javax.swing.JCheckBox invertedEar;
    private javax.swing.JRadioButton issue2;
    private javax.swing.JRadioButton issue3;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JComboBox joystick;
    private javax.swing.JPanel joystickPanel;
    private javax.swing.JPanel keyboard48kPanel;
    private javax.swing.ButtonGroup keyboardButtonGroup;
    private javax.swing.JLabel keyboardIssueInfoLabel;
    private javax.swing.JPanel keyboardPanelTab;
    private javax.swing.JCheckBox lecEnabled;
    private javax.swing.JPanel lecEnabledPanel;
    private javax.swing.JLabel lecInfoLabel;
    private javax.swing.JPanel lecInfoPanel;
    private javax.swing.JPanel lecPanelTab;
    private javax.swing.JPanel loadPanel;
    private javax.swing.JCheckBox loadingNoise;
    private javax.swing.JRadioButton lowSampling;
    private javax.swing.JPanel mapPCKeyPanel;
    private javax.swing.JCheckBox mapPCKeys;
    private javax.swing.JPanel mdrPanel;
    private javax.swing.JRadioButton multiface128RadioButton;
    private javax.swing.JCheckBox multifaceEnabled;
    private javax.swing.ButtonGroup multifaceModelButtonGroup;
    private javax.swing.JPanel multifaceModelPanel;
    private javax.swing.JRadioButton multifaceOneRadioButton;
    private javax.swing.JPanel multifacePanel;
    private javax.swing.JPanel multifacePanelTab;
    private javax.swing.JLabel numBlocksInfoLabel;
    private javax.swing.JPanel numBlocksInfoPanel;
    private javax.swing.JPanel numBlocksPanel;
    private javax.swing.JLabel numDrivesLabel;
    private javax.swing.JPanel numDrivesPanel;
    private javax.swing.JSpinner numDrivesSpinner;
    private javax.swing.JLabel numSectorsLabel;
    private javax.swing.JCheckBox recreatedZX;
    private javax.swing.JPanel recreatedZXPanel;
    private javax.swing.ButtonGroup samplingButtonGroup;
    private javax.swing.JPanel samplingPanel;
    private javax.swing.JPanel savePanel;
    private javax.swing.JButton saveSettingsButton;
    private javax.swing.JCheckBox soundMuted;
    private javax.swing.JPanel soundPanelTab;
    private javax.swing.JComboBox spectrumModel;
    private javax.swing.JSlider speed;
    private javax.swing.JPanel tapePanelTab;
    private javax.swing.JPanel videoPanel;
    private javax.swing.JCheckBox zoomCheckbox;
    private javax.swing.JLabel zoomLabel;
    private javax.swing.JPanel zoomPanel;
    private javax.swing.JSlider zoomSlider;
    // End of variables declaration//GEN-END:variables

}
