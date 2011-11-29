/*
 * JSpeccy.java
 *
 * Created on 21 de enero de 2008, 14:27
 */

package gui;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.util.ResourceBundle;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicFileChooserUI;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import java.io.IOException;
import javax.xml.bind.JAXBException;
import machine.MachineTypes;
import machine.Spectrum;
import configuration.*;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.xml.bind.JAXB;

/**
 *
 * @author  jsanchez
 */
public class JSpeccy extends javax.swing.JFrame {
    Spectrum spectrum;
    JSpeccyScreen jscr;
    File currentFileSnapshot, currentDirSaveSnapshot,
        currentFileTape, currentDirLoadImage, currentDirSaveImage, currentDirRom;
    JFileChooser openSnapshotDlg, saveSnapshotDlg, openTapeDlg;
    JFileChooser loadImageDlg, saveImageDlg, IF2RomDlg;
    File recentFile[] = new File[5];
    ListSelectionModel lsm;
    JSpeccySettingsType settings;
    SettingsDialog settingsDialog;
    MicrodriveDialog microdriveDialog;
    MemoryBrowserDialog memoryBrowserDialog;
    FileNameExtensionFilter allSnapTapeExtension, snapshotExtension,
            tapeExtension, imageExtension, screenExtension, romExtension;
    
    Icon mdrOff = new ImageIcon(getClass().getResource("/icons/microdrive_off.png"));
    

    /** Creates new form JSpeccy */
    public JSpeccy() {
        if (UIManager.getLookAndFeel().getName().equals("Metal")) {
            try {
                // turn off bold fonts
                UIManager.put("swing.boldMetal", Boolean.FALSE);
                // re-install the Metal Look and Feel
                UIManager.setLookAndFeel(new MetalLookAndFeel());
                // Update the ComponentUIs for all Components. This
                // needs to be invoked for all windows.
                SwingUtilities.updateComponentTreeUI(this);
            } catch (UnsupportedLookAndFeelException ex) {
                Logger.getLogger(JSpeccy.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        initComponents();
        initEmulator();
    }

    private void verifyConfigFile(boolean deleteFile) {
        File file = new File("JSpeccy.xml");
        if (file.exists() && !deleteFile) {
            return;
        }

        if (deleteFile) {
            if (!file.delete()) {
                System.out.println("Can't delete the bad JSpeccy.xml");
            }
        }

        // Si el archivo de configuración no existe, lo crea de nuevo en el
        // directorio actual copiándolo del bueno que hay siempre en el .jar
        try {
            InputStream input = Spectrum.class.getResourceAsStream("/schema/JSpeccy.xml");
            FileOutputStream output = new FileOutputStream("JSpeccy.xml");

            int value = input.read();
            while (value != -1) {
                output.write(value & 0xff);
                value = input.read();
            }

            input.close();
            output.close();
        } catch (FileNotFoundException notFoundExcpt) {
            Logger.getLogger(JSpeccy.class.getName()).log(Level.SEVERE, null, notFoundExcpt);
        } catch (IOException ioExcpt) {
            Logger.getLogger(JSpeccy.class.getName()).log(Level.SEVERE, null, ioExcpt);
        }
    }

    private void readSettingsFile() {
        verifyConfigFile(false);

        boolean readed = true;
        try {
            // create a JAXBContext capable of handling classes generated into
            // the configuration package
            JAXBContext jc = JAXBContext.newInstance("configuration");

            // create an Unmarshaller
            Unmarshaller unmsh = jc.createUnmarshaller();

            // unmarshal a po instance document into a tree of Java content
            // objects composed of classes from the configuration package.
            JAXBElement<?> settingsElement =
                    (JAXBElement<?>) unmsh.unmarshal(new FileInputStream("JSpeccy.xml"));

            settings = (JSpeccySettingsType) settingsElement.getValue();
        } catch (JAXBException jexcpt) {
            System.out.println("Something during unmarshalling go very bad!");
            readed = false;
        } catch (IOException ioexcpt) {
            System.out.println("Can't open the JSpeccy.xml configuration file");
        }

        if (readed)
            return;

        System.out.println("Trying to create a new one JSpeccy.xml for you");

        verifyConfigFile(true);
        try {
            // create a JAXBContext capable of handling classes generated into
            // the configuration package
            JAXBContext jc = JAXBContext.newInstance("configuration");

            // create an Unmarshaller
            Unmarshaller unmsh = jc.createUnmarshaller();

            // unmarshal a po instance document into a tree of Java content
            // objects composed of classes from the configuration package.
            JAXBElement<?> settingsElement =
                    (JAXBElement<?>) unmsh.unmarshal(new FileInputStream("JSpeccy.xml"));

            settings = (JSpeccySettingsType) settingsElement.getValue();
        } catch (JAXBException jexcpt) {
            System.out.println("Something during unmarshalling go very very bad!");
            readed = false;
        } catch (IOException ioexcpt) {
            System.out.println("Can't open the JSpeccy.xml configuration file anyway");
            System.exit(0);
        }

    }

    private void saveRecentFiles() {
        try {
            // create a JAXBContext capable of handling classes generated into
            // the configuration package
            JAXBContext jc = JAXBContext.newInstance("configuration");

            // create an Unmarshaller
            Unmarshaller unmsh = jc.createUnmarshaller();

            // unmarshal a po instance document into a tree of Java content
            // objects composed of classes from the configuration package.
            JAXBElement<?> settingsElement =
                    (JAXBElement<?>) unmsh.unmarshal(new FileInputStream("JSpeccy.xml"));

            settings = (JSpeccySettingsType) settingsElement.getValue();
        } catch (JAXBException jexcpt) {
            System.out.println("Something during unmarshalling go very bad!");
        } catch (IOException ioexcpt) {
            System.out.println("Can't open the JSpeccy.xml configuration file");
        }

        if (recentFile[0] != null)
            settings.getRecentFilesSettings().setRecentFile0(recentFile[0].getAbsolutePath());
        if (recentFile[1] != null)
            settings.getRecentFilesSettings().setRecentFile1(recentFile[1].getAbsolutePath());
        if (recentFile[2] != null)
            settings.getRecentFilesSettings().setRecentFile2(recentFile[2].getAbsolutePath());
        if (recentFile[3] != null)
            settings.getRecentFilesSettings().setRecentFile3(recentFile[3].getAbsolutePath());
        if (recentFile[4] != null)
            settings.getRecentFilesSettings().setRecentFile4(recentFile[4].getAbsolutePath());
        if (currentFileSnapshot != null)
            settings.getRecentFilesSettings().setLastSnapshotDir(currentFileSnapshot.getParent());
        if (currentFileTape != null)
            settings.getRecentFilesSettings().setLastTapeDir(currentFileTape.getParent());

        try {
            BufferedOutputStream fOut =
                new BufferedOutputStream(new FileOutputStream("JSpeccy.xml"));
            // create an element for marshalling
            JAXBElement<JSpeccySettingsType> confElement =
                (new ObjectFactory()).createJSpeccySettings(settings);

            // create a Marshaller and marshal to conf. file
            JAXB.marshal(confElement, fOut);
            try {
                fOut.close();
            } catch (IOException ex) {
                Logger.getLogger(SettingsDialog.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(SettingsDialog.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void initEmulator() {

        readSettingsFile();
        spectrum = new Spectrum(settings);
        jscr = new JSpeccyScreen();
        spectrum.setScreenComponent(jscr);
        jscr.setTvImage(spectrum.getTvImage());
        spectrum.setSpeedLabel(speedLabel);
        spectrum.tape.setTapeIcon(tapeLabel);
        tapeCatalog.setModel(spectrum.tape.getTapeTableModel());
        tapeCatalog.getColumnModel().getColumn(0).setMaxWidth(150);
        lsm = tapeCatalog.getSelectionModel();
        lsm.addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent event) {

                if (!event.getValueIsAdjusting() && event.getLastIndex() != -1) {
                    spectrum.tape.setSelectedBlock(lsm.getLeadSelectionIndex());
                }
            }
        });
        spectrum.tape.setListSelectionModel(lsm);
        getContentPane().add(jscr, BorderLayout.CENTER);
        pack();
        addKeyListener(spectrum.getKeyboard());

        // Synchronize the file user settings with GUI settings
        IF1MediaMenu.setEnabled(settings.getInterface1Settings().isConnectedIF1());
        if (settings.getInterface1Settings().isConnectedIF1()) {
            mdrvLabel.setDisabledIcon(mdrOff);
        }
        
        IF2MediaMenu.setEnabled(true);
        insertIF2RomMediaMenu.setEnabled(!spectrum.isIF2RomInserted());
        extractIF2RomMediaMenu.setEnabled(spectrum.isIF2RomInserted());
        
        switch (settings.getSpectrumSettings().getDefaultModel()) {
            case 0:
                spec16kHardware.setSelected(true);
                modelLabel.setToolTipText(MachineTypes.SPECTRUM16K.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUM16K.getShortModelName());
                break;
            case 2:
                spec128kHardware.setSelected(true);
                modelLabel.setToolTipText(MachineTypes.SPECTRUM128K.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUM128K.getShortModelName());
                break;
            case 3:
                specPlus2Hardware.setSelected(true);
                modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS2.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUMPLUS2.getShortModelName());
                break;
            case 4:
                specPlus2AHardware.setSelected(true);
                modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS2A.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUMPLUS2A.getShortModelName());
                IF1MediaMenu.setEnabled(false);
                mdrvLabel.setDisabledIcon(null);
                IF2MediaMenu.setEnabled(false);
                break;
            case 5:
                specPlus3Hardware.setSelected(true);
                modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS3.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUMPLUS3.getShortModelName());
                IF1MediaMenu.setEnabled(false);
                mdrvLabel.setDisabledIcon(null);
                IF2MediaMenu.setEnabled(false);
                break;
            default:
                spec48kHardware.setSelected(true);
                modelLabel.setToolTipText(MachineTypes.SPECTRUM48K.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUM48K.getShortModelName());
        }

        switch (settings.getKeyboardJoystickSettings().getJoystickModel()) {
            case 1:
                kempstonJoystick.setSelected(true);
                break;
            case 2:
                sinclair1Joystick.setSelected(true);
                break;
            case 3:
                sinclair2Joystick.setSelected(true);
                break;
            case 4:
                cursorJoystick.setSelected(true);
                break;
            default:
                noneJoystick.setSelected(true);
        }

        if (settings.getSpectrumSettings().isMutedSound()) {
            silenceMachineMenu.setSelected(true);
            silenceSoundToggleButton.setSelected(true);
        }

        if (settings.getSpectrumSettings().isDoubleSize()) {
            jscr.setDoubleSize(true);
            doubleSizeOption.setSelected(true);
            doubleSizeToggleButton.setSelected(true);
            pack();
        }

        if (settings.getRecentFilesSettings().getRecentFile0() != null
            && !settings.getRecentFilesSettings().getRecentFile0().isEmpty()) {
            recentFile[0] = new File(settings.getRecentFilesSettings().getRecentFile0());
            recentFileMenu0.setText(recentFile[0].getName());
            recentFileMenu0.setToolTipText(recentFile[0].getAbsolutePath());
            recentFileMenu0.setEnabled(true);
        }

        if (settings.getRecentFilesSettings().getRecentFile1() != null
            && !settings.getRecentFilesSettings().getRecentFile1().isEmpty()) {
            recentFile[1] = new File(settings.getRecentFilesSettings().getRecentFile1());
            recentFileMenu1.setText(recentFile[1].getName());
            recentFileMenu1.setToolTipText(recentFile[1].getAbsolutePath());
            recentFileMenu1.setEnabled(true);
        }

        if (settings.getRecentFilesSettings().getRecentFile2() != null
            && !settings.getRecentFilesSettings().getRecentFile2().isEmpty()) {
            recentFile[2] = new File(settings.getRecentFilesSettings().getRecentFile2());
            recentFileMenu2.setText(recentFile[2].getName());
            recentFileMenu2.setToolTipText(recentFile[2].getAbsolutePath());
            recentFileMenu2.setEnabled(true);
        }

        if (settings.getRecentFilesSettings().getRecentFile3() != null
            && !settings.getRecentFilesSettings().getRecentFile3().isEmpty()) {
            recentFile[3] = new File(settings.getRecentFilesSettings().getRecentFile3());
            recentFileMenu3.setText(recentFile[3].getName());
            recentFileMenu3.setToolTipText(recentFile[3].getAbsolutePath());
            recentFileMenu3.setEnabled(true);
        }

        if (settings.getRecentFilesSettings().getRecentFile4() != null
            && !settings.getRecentFilesSettings().getRecentFile4().isEmpty()) {
            recentFile[4] = new File(settings.getRecentFilesSettings().getRecentFile4());
            recentFileMenu4.setText(recentFile[4].getName());
            recentFileMenu4.setToolTipText(recentFile[4].getAbsolutePath());
            recentFileMenu4.setEnabled(true);
        }

        settingsDialog = new SettingsDialog(settings);
//        microdriveDialog = new MicrodriveDialog(spectrum.getInterface1());
        spectrum.getInterface1().setMdrvIcon(mdrvLabel);
        
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        allSnapTapeExtension = new FileNameExtensionFilter(
                bundle.getString("SNAPSHOT_TAPE_TYPE"),
                "sna", "z80", "szx", "tap", "tzx", "csw");
        snapshotExtension = new FileNameExtensionFilter(
                bundle.getString("SNAPSHOT_TYPE"), "sna", "z80", "szx");
        tapeExtension = new FileNameExtensionFilter(
                bundle.getString("TAPE_TYPE"), "tap", "tzx", "csw");
        imageExtension = new FileNameExtensionFilter(
                bundle.getString("IMAGE_TYPE"), "scr", "png");
        screenExtension = new FileNameExtensionFilter(
                bundle.getString("SCR_TYPE"), "scr");
        romExtension  = new FileNameExtensionFilter(
                bundle.getString("ROM_TYPE"), "rom");
        spectrum.start();
    }
    
    private void exitEmulator() {
        String msg;
        int dialogType;
        
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        
        if (spectrum.getInterface1().hasDirtyCartridges()) {
            msg = bundle.getString("DIRTY_CARTRIDGES_WARNING");
            dialogType = JOptionPane.WARNING_MESSAGE;
        } else {
            msg = bundle.getString("ARE_YOU_SURE_QUESTION");
            dialogType = JOptionPane.QUESTION_MESSAGE;
        }
        
        int ret = JOptionPane.showConfirmDialog(getContentPane(), msg,
                bundle.getString("QUIT_JSPECCY"),
                JOptionPane.YES_NO_OPTION, dialogType); // NOI18N
        
        if( ret == JOptionPane.YES_OPTION ) {
            spectrum.stopEmulation();
            saveRecentFiles();
            dispose();
            System.exit(0);
        }
    }

    private void rotateRecentFile(File lastname) {
        
        for (int idx = 0; idx < 5; idx++) {
            if(recentFile[idx] != null &&
                lastname.getAbsolutePath().equals(recentFile[idx].getAbsolutePath()))
                return;
        }
        
        recentFile[4] = recentFile[3];
        recentFile[3] = recentFile[2];
        recentFile[2] = recentFile[1];
        recentFile[1] = recentFile[0];
        recentFile[0] = lastname;

        if (recentFile[0] != null && !recentFile[0].getName().isEmpty()) {
            recentFileMenu0.setText(recentFile[0].getName());
            recentFileMenu0.setToolTipText(recentFile[0].getAbsolutePath());
            recentFileMenu0.setEnabled(true);
            settings.getRecentFilesSettings().setRecentFile0(recentFile[0].getAbsolutePath());
        }

        if (recentFile[1] != null && !recentFile[1].getName().isEmpty()) {
            recentFileMenu1.setText(recentFile[1].getName());
            recentFileMenu1.setToolTipText(recentFile[1].getAbsolutePath());
            recentFileMenu1.setEnabled(true);
            settings.getRecentFilesSettings().setRecentFile1(recentFile[1].getAbsolutePath());
        }

        if (recentFile[2] != null && !recentFile[2].getName().isEmpty()) {
            recentFileMenu2.setText(recentFile[2].getName());
            recentFileMenu2.setToolTipText(recentFile[2].getAbsolutePath());
            recentFileMenu2.setEnabled(true);
            settings.getRecentFilesSettings().setRecentFile2(recentFile[2].getAbsolutePath());
        }

        if (recentFile[3] != null && !recentFile[3].getName().isEmpty()) {
            recentFileMenu3.setText(recentFile[3].getName());
            recentFileMenu3.setToolTipText(recentFile[3].getAbsolutePath());
            recentFileMenu3.setEnabled(true);
            settings.getRecentFilesSettings().setRecentFile3(recentFile[3].getAbsolutePath());
        }

        if (recentFile[4] != null && !recentFile[4].getName().isEmpty()) {
            recentFileMenu4.setText(recentFile[4].getName());
            recentFileMenu4.setToolTipText(recentFile[4].getAbsolutePath());
            recentFileMenu4.setEnabled(true);
            settings.getRecentFilesSettings().setRecentFile4(recentFile[4].getAbsolutePath());
        }
    }
    
    private void updateGuiSelections() {
        
        IF1MediaMenu.setEnabled(spectrum.isIF1Connected());
        if (settings.getInterface1Settings().isConnectedIF1()) {
            mdrvLabel.setDisabledIcon(mdrOff);
        } else {
            mdrvLabel.setDisabledIcon(null);
        }
        IF2MediaMenu.setEnabled(true);
        insertIF2RomMediaMenu.setEnabled(!spectrum.isIF2RomInserted());
        extractIF2RomMediaMenu.setEnabled(spectrum.isIF2RomInserted());
        
        switch (spectrum.getSpectrumModel()) {
            case SPECTRUM16K:
                spec16kHardware.setSelected(true);
                break;
            case SPECTRUM48K:
                spec48kHardware.setSelected(true);
                break;
            case SPECTRUM128K:
                spec128kHardware.setSelected(true);
                break;
            case SPECTRUMPLUS2:
                specPlus2Hardware.setSelected(true);
                break;
            case SPECTRUMPLUS2A:
                specPlus2AHardware.setSelected(true);
                IF2MediaMenu.setEnabled(false);
                break;
            case SPECTRUMPLUS3:
                specPlus3Hardware.setSelected(true);
                IF2MediaMenu.setEnabled(false);
                break;
        }
        
        modelLabel.setToolTipText(spectrum.getSpectrumModel().getLongModelName());
        modelLabel.setText(spectrum.getSpectrumModel().getShortModelName());

        switch (spectrum.getJoystick()) {
            case NONE:
                noneJoystick.setSelected(true);
                break;
            case KEMPSTON:
                kempstonJoystick.setSelected(true);
                break;
            case SINCLAIR1:
                sinclair1Joystick.setSelected(true);
                break;
            case SINCLAIR2:
                sinclair2Joystick.setSelected(true);
                break;
            case CURSOR:
                cursorJoystick.setSelected(true);
                break;
            case FULLER:
                fullerJoystick.setSelected(true);
        }
        
        if (spectrum.tape.isTapeInserted()) {
            playTapeMediaMenu.setEnabled(true);
            tapeBrowserButtonRec.setEnabled(spectrum.tape.getTapeFilename().canWrite());
            tapeBrowserButtonPlay.setEnabled(true);
            tapeBrowserButtonStop.setEnabled(true);
            tapeBrowserButtonRew.setEnabled(true);
            tapeBrowserButtonEject.setEnabled(true);
            tapeFilename.setText(spectrum.tape.getTapeFilename().getName());
            if (spectrum.tape.getTapeFilename().getName().toLowerCase().endsWith(".csw")) {
                clearTapeMediaMenu.setEnabled(false);
                recordStartTapeMediaMenu.setEnabled(false);
                tapeBrowserButtonRec.setEnabled(false);
            }
        } else {
            playTapeMediaMenu.setEnabled(false);
            tapeBrowserButtonRec.setEnabled(false);
            tapeBrowserButtonPlay.setEnabled(false);
            tapeBrowserButtonRew.setEnabled(false);
            tapeBrowserButtonEject.setEnabled(false);
            tapeBrowserButtonStop.setEnabled(false);
            tapeFilename.setText(null);
        }
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        keyboardHelper = new javax.swing.JDialog(this);
        keyboardImage = new javax.swing.JLabel();
        closeKeyboardHelper = new javax.swing.JButton();
        joystickButtonGroup = new javax.swing.ButtonGroup();
        hardwareButtonGroup = new javax.swing.ButtonGroup();
        tapeBrowserDialog = new javax.swing.JDialog();
        jScrollPane1 = new javax.swing.JScrollPane();
        tapeCatalog = new javax.swing.JTable();
        jPanel2 = new javax.swing.JPanel();
        tapeBrowserToolbar = new javax.swing.JToolBar();
        tapeBrowserButtonRec = new javax.swing.JButton();
        jSeparator11 = new javax.swing.JToolBar.Separator();
        tapeBrowserButtonStop = new javax.swing.JButton();
        tapeBrowserButtonRew = new javax.swing.JButton();
        tapeBrowserButtonPlay = new javax.swing.JButton();
        jSeparator12 = new javax.swing.JToolBar.Separator();
        tapeBrowserButtonEject = new javax.swing.JButton();
        tapeFilename = new javax.swing.JLabel();
        saveSzxTape = new javax.swing.JDialog();
        jPanel4 = new javax.swing.JPanel();
        tapeMessageInfo = new javax.swing.JLabel();
        tapeFilenameLabel = new javax.swing.JLabel();
        saveSzxChoosePanel = new javax.swing.JPanel();
        ignoreRadioButton = new javax.swing.JRadioButton();
        linkedRadioButton = new javax.swing.JRadioButton();
        embeddedRadioButton = new javax.swing.JRadioButton();
        jPanel3 = new javax.swing.JPanel();
        saveSzxCloseButton = new javax.swing.JButton();
        saveSzxButtonGroup = new javax.swing.ButtonGroup();
        pokeDialog = new javax.swing.JDialog();
        addrValuePanel = new javax.swing.JPanel();
        pokeAddress = new javax.swing.JLabel();
        addressSpinner = new javax.swing.JSpinner();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(20, 16), new java.awt.Dimension(20, 16), new java.awt.Dimension(20, 16));
        pokeValue = new javax.swing.JLabel();
        valueSpinner = new javax.swing.JSpinner();
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(20, 16), new java.awt.Dimension(20, 16), new java.awt.Dimension(20, 16));
        pokeButton = new javax.swing.JButton();
        closePokeDialogPanel = new javax.swing.JPanel();
        closeButton = new javax.swing.JButton();
        statusPanel = new javax.swing.JPanel();
        modelLabel = new javax.swing.JLabel();
        filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        mdrvLabel = new javax.swing.JLabel();
        jSeparator10 = new javax.swing.JSeparator();
        tapeLabel = new javax.swing.JLabel();
        jSeparator2 = new javax.swing.JSeparator();
        speedLabel = new javax.swing.JLabel();
        toolbarMenu = new javax.swing.JToolBar();
        openSnapshotButton = new javax.swing.JButton();
        pauseToggleButton = new javax.swing.JToggleButton();
        fastEmulationToggleButton = new javax.swing.JToggleButton();
        doubleSizeToggleButton = new javax.swing.JToggleButton();
        silenceSoundToggleButton = new javax.swing.JToggleButton();
        resetSpectrumButton = new javax.swing.JButton();
        hardResetSpectrumButton = new javax.swing.JButton();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        openSnapshot = new javax.swing.JMenuItem();
        saveSnapshot = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        loadScreenShot = new javax.swing.JMenuItem();
        saveScreenShot = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        recentFilesMenu = new javax.swing.JMenu();
        recentFileMenu0 = new javax.swing.JMenuItem();
        recentFileMenu1 = new javax.swing.JMenuItem();
        recentFileMenu2 = new javax.swing.JMenuItem();
        recentFileMenu3 = new javax.swing.JMenuItem();
        recentFileMenu4 = new javax.swing.JMenuItem();
        jSeparator7 = new javax.swing.JPopupMenu.Separator();
        thisIsTheEndMyFriend = new javax.swing.JMenuItem();
        optionsMenu = new javax.swing.JMenu();
        doubleSizeOption = new javax.swing.JCheckBoxMenuItem();
        joystickOptionMenu = new javax.swing.JMenu();
        noneJoystick = new javax.swing.JRadioButtonMenuItem();
        kempstonJoystick = new javax.swing.JRadioButtonMenuItem();
        sinclair1Joystick = new javax.swing.JRadioButtonMenuItem();
        sinclair2Joystick = new javax.swing.JRadioButtonMenuItem();
        cursorJoystick = new javax.swing.JRadioButtonMenuItem();
        fullerJoystick = new javax.swing.JRadioButtonMenuItem();
        settingsOptionsMenu = new javax.swing.JMenuItem();
        machineMenu = new javax.swing.JMenu();
        pauseMachineMenu = new javax.swing.JCheckBoxMenuItem();
        silenceMachineMenu = new javax.swing.JCheckBoxMenuItem();
        resetMachineMenu = new javax.swing.JMenuItem();
        hardResetMachineMenu = new javax.swing.JMenuItem();
        nmiMachineMenu = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        pokeMachineMenu = new javax.swing.JMenuItem();
        memoryBrowserMachineMenu = new javax.swing.JMenuItem();
        jSeparator14 = new javax.swing.JPopupMenu.Separator();
        hardwareMachineMenu = new javax.swing.JMenu();
        spec16kHardware = new javax.swing.JRadioButtonMenuItem();
        spec48kHardware = new javax.swing.JRadioButtonMenuItem();
        spec128kHardware = new javax.swing.JRadioButtonMenuItem();
        specPlus2Hardware = new javax.swing.JRadioButtonMenuItem();
        specPlus2AHardware = new javax.swing.JRadioButtonMenuItem();
        specPlus3Hardware = new javax.swing.JRadioButtonMenuItem();
        mediaMenu = new javax.swing.JMenu();
        tapeMediaMenu = new javax.swing.JMenu();
        openTapeMediaMenu = new javax.swing.JMenuItem();
        playTapeMediaMenu = new javax.swing.JMenuItem();
        browserTapeMediaMenu = new javax.swing.JMenuItem();
        rewindTapeMediaMenu = new javax.swing.JMenuItem();
        jSeparator5 = new javax.swing.JPopupMenu.Separator();
        createTapeMediaMenu = new javax.swing.JMenuItem();
        clearTapeMediaMenu = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JPopupMenu.Separator();
        recordStartTapeMediaMenu = new javax.swing.JMenuItem();
        recordStopTapeMediaMenu = new javax.swing.JMenuItem();
        jSeparator13 = new javax.swing.JPopupMenu.Separator();
        refreshTapeMediaMenu = new javax.swing.JMenuItem();
        jSeparator8 = new javax.swing.JPopupMenu.Separator();
        IF1MediaMenu = new javax.swing.JMenu();
        microdrivesIF1MediaMenu = new javax.swing.JMenuItem();
        jSeparator9 = new javax.swing.JPopupMenu.Separator();
        IF2MediaMenu = new javax.swing.JMenu();
        insertIF2RomMediaMenu = new javax.swing.JMenuItem();
        extractIF2RomMediaMenu = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        imageHelpMenu = new javax.swing.JMenuItem();
        aboutHelpMenu = new javax.swing.JMenuItem();

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        keyboardHelper.setTitle(bundle.getString("JSpeccy.keyboardHelper.title")); // NOI18N

        keyboardImage.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/Keyboard48k.png"))); // NOI18N
        keyboardImage.setText(bundle.getString("JSpeccy.keyboardImage.text")); // NOI18N
        keyboardHelper.getContentPane().add(keyboardImage, java.awt.BorderLayout.PAGE_START);

        closeKeyboardHelper.setText(bundle.getString("JSpeccy.closeKeyboardHelper.text")); // NOI18N
        closeKeyboardHelper.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeKeyboardHelperActionPerformed(evt);
            }
        });
        keyboardHelper.getContentPane().add(closeKeyboardHelper, java.awt.BorderLayout.PAGE_END);

        tapeBrowserDialog.setTitle(bundle.getString("JSpeccy.tapeBrowserDialog.title")); // NOI18N

        tapeCatalog.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null}
            },
            new String [] {
                "Block Number", "Block Type", "Block information"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        tapeCatalog.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        tapeCatalog.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tapeCatalog.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(tapeCatalog);
        tapeCatalog.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tapeCatalog.getColumnModel().getColumn(0).setResizable(false);
        tapeCatalog.getColumnModel().getColumn(0).setHeaderValue(bundle.getString("JSpeccy.tapeCatalog.columnModel.title0")); // NOI18N
        tapeCatalog.getColumnModel().getColumn(1).setHeaderValue(bundle.getString("JSpeccy.tapeCatalog.columnModel.title1")); // NOI18N
        tapeCatalog.getColumnModel().getColumn(2).setHeaderValue(bundle.getString("JSpeccy.tapeCatalog.columnModel.title2")); // NOI18N

        tapeBrowserDialog.getContentPane().add(jScrollPane1, java.awt.BorderLayout.CENTER);

        tapeBrowserToolbar.setRollover(true);

        tapeBrowserButtonRec.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/32x32/player-rec.png"))); // NOI18N
        tapeBrowserButtonRec.setText(bundle.getString("JSpeccy.tapeBrowserButtonRec.text")); // NOI18N
        tapeBrowserButtonRec.setToolTipText(bundle.getString("JSpeccy.tapeBrowserButtonRec.tooltip.text")); // NOI18N
        tapeBrowserButtonRec.setEnabled(false);
        tapeBrowserButtonRec.setFocusable(false);
        tapeBrowserButtonRec.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tapeBrowserButtonRec.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tapeBrowserButtonRec.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recordStartTapeMediaMenuActionPerformed(evt);
            }
        });
        tapeBrowserToolbar.add(tapeBrowserButtonRec);

        jSeparator11.setSeparatorSize(new java.awt.Dimension(25, 10));
        tapeBrowserToolbar.add(jSeparator11);

        tapeBrowserButtonStop.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/32x32/player_stop.png"))); // NOI18N
        tapeBrowserButtonStop.setText(bundle.getString("JSpeccy.tapeBrowserButtonStop.text")); // NOI18N
        tapeBrowserButtonStop.setToolTipText(bundle.getString("JSpeccy.tapeBrowserButtonStop.tooltip.text")); // NOI18N
        tapeBrowserButtonStop.setEnabled(false);
        tapeBrowserButtonStop.setFocusable(false);
        tapeBrowserButtonStop.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tapeBrowserButtonStop.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tapeBrowserButtonStop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tapeBrowserButtonStopActionPerformed(evt);
            }
        });
        tapeBrowserToolbar.add(tapeBrowserButtonStop);

        tapeBrowserButtonRew.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/32x32/player_rew.png"))); // NOI18N
        tapeBrowserButtonRew.setText(bundle.getString("JSpeccy.tapeBrowserButtonRew.text")); // NOI18N
        tapeBrowserButtonRew.setToolTipText(bundle.getString("JSpeccy.tapeBrowserButtonRew.tooltip.text")); // NOI18N
        tapeBrowserButtonRew.setEnabled(false);
        tapeBrowserButtonRew.setFocusable(false);
        tapeBrowserButtonRew.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tapeBrowserButtonRew.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tapeBrowserButtonRew.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rewindTapeMediaMenuActionPerformed(evt);
            }
        });
        tapeBrowserToolbar.add(tapeBrowserButtonRew);

        tapeBrowserButtonPlay.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/32x32/player_play.png"))); // NOI18N
        tapeBrowserButtonPlay.setText(bundle.getString("JSpeccy.tapeBrowserButtonPlay.text")); // NOI18N
        tapeBrowserButtonPlay.setToolTipText(bundle.getString("JSpeccy.tapeBrowserButtonPlay.tooltip.text")); // NOI18N
        tapeBrowserButtonPlay.setEnabled(false);
        tapeBrowserButtonPlay.setFocusable(false);
        tapeBrowserButtonPlay.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tapeBrowserButtonPlay.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tapeBrowserButtonPlay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tapeBrowserButtonPlayActionPerformed(evt);
            }
        });
        tapeBrowserToolbar.add(tapeBrowserButtonPlay);

        jSeparator12.setSeparatorSize(new java.awt.Dimension(25, 10));
        tapeBrowserToolbar.add(jSeparator12);

        tapeBrowserButtonEject.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/32x32/player_eject.png"))); // NOI18N
        tapeBrowserButtonEject.setText(bundle.getString("JSpeccy.tapeBrowserButtonEject.text")); // NOI18N
        tapeBrowserButtonEject.setToolTipText(bundle.getString("JSpeccy.tapeBrowserButtonEject.tooltip.text")); // NOI18N
        tapeBrowserButtonEject.setEnabled(false);
        tapeBrowserButtonEject.setFocusable(false);
        tapeBrowserButtonEject.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tapeBrowserButtonEject.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tapeBrowserButtonEject.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tapeBrowserButtonEjectActionPerformed(evt);
            }
        });
        tapeBrowserToolbar.add(tapeBrowserButtonEject);

        jPanel2.add(tapeBrowserToolbar);

        tapeBrowserDialog.getContentPane().add(jPanel2, java.awt.BorderLayout.PAGE_END);

        tapeFilename.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tapeFilename.setText(bundle.getString("JSpeccy.tapeFilename.text")); // NOI18N
        tapeBrowserDialog.getContentPane().add(tapeFilename, java.awt.BorderLayout.PAGE_START);

        saveSzxTape.setTitle(bundle.getString("JSpeccy.saveSzxTapeDialog.title.text")); // NOI18N
        saveSzxTape.setModal(true);

        jPanel4.setLayout(new java.awt.GridLayout(3, 0, 5, 5));

        tapeMessageInfo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tapeMessageInfo.setText(bundle.getString("JSpeccy.tapeMessageInfo.text")); // NOI18N
        jPanel4.add(tapeMessageInfo);

        tapeFilenameLabel.setForeground(new java.awt.Color(204, 0, 0));
        tapeFilenameLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jPanel4.add(tapeFilenameLabel);

        saveSzxChoosePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("JSpeccy.saveSzxChoosePanel.title"))); // NOI18N

        saveSzxButtonGroup.add(ignoreRadioButton);
        ignoreRadioButton.setSelected(true);
        ignoreRadioButton.setText(bundle.getString("JSpeccy.ignoreRadioButton.text")); // NOI18N
        ignoreRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ignoreRadioButtonActionPerformed(evt);
            }
        });
        saveSzxChoosePanel.add(ignoreRadioButton);

        saveSzxButtonGroup.add(linkedRadioButton);
        linkedRadioButton.setText(bundle.getString("JSpeccy.linkedRadioButton.text")); // NOI18N
        linkedRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                linkedRadioButtonActionPerformed(evt);
            }
        });
        saveSzxChoosePanel.add(linkedRadioButton);

        saveSzxButtonGroup.add(embeddedRadioButton);
        embeddedRadioButton.setText(bundle.getString("JSpeccy.embeddedRadioButton.text")); // NOI18N
        embeddedRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                embeddedRadioButtonActionPerformed(evt);
            }
        });
        saveSzxChoosePanel.add(embeddedRadioButton);

        jPanel4.add(saveSzxChoosePanel);

        saveSzxTape.getContentPane().add(jPanel4, java.awt.BorderLayout.CENTER);

        saveSzxCloseButton.setText(bundle.getString("JSpeccy.saveSzxCloseButton.text")); // NOI18N
        saveSzxCloseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveSzxCloseButtonActionPerformed(evt);
            }
        });
        jPanel3.add(saveSzxCloseButton);

        saveSzxTape.getContentPane().add(jPanel3, java.awt.BorderLayout.PAGE_END);

        pokeDialog.setTitle(bundle.getString("JSpecy.pokeDialog.title.text")); // NOI18N
        pokeDialog.getContentPane().setLayout(new javax.swing.BoxLayout(pokeDialog.getContentPane(), javax.swing.BoxLayout.PAGE_AXIS));

        pokeAddress.setText(bundle.getString("JSpeccy.pokeAddress.text")); // NOI18N
        addrValuePanel.add(pokeAddress);

        addressSpinner.setModel(new javax.swing.SpinnerNumberModel(23296, 16384, 65535, 1));
        addressSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                addressSpinnerStateChanged(evt);
            }
        });
        addrValuePanel.add(addressSpinner);
        addrValuePanel.add(filler1);

        pokeValue.setText(bundle.getString("JSpeccy.pokeValue.text")); // NOI18N
        addrValuePanel.add(pokeValue);

        valueSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 255, 1));
        valueSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                valueSpinnerStateChanged(evt);
            }
        });
        addrValuePanel.add(valueSpinner);
        addrValuePanel.add(filler2);

        pokeButton.setText(bundle.getString("JSpeccy.pokeButton.text")); // NOI18N
        pokeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pokeButtonActionPerformed(evt);
            }
        });
        addrValuePanel.add(pokeButton);

        pokeDialog.getContentPane().add(addrValuePanel);

        closeButton.setText(bundle.getString("JSpeccy.closeButton.text")); // NOI18N
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });
        closePokeDialogPanel.add(closeButton);

        pokeDialog.getContentPane().add(closePokeDialogPanel);

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle(bundle.getString("JSpeccy.title")); // NOI18N
        setIconImage(Toolkit.getDefaultToolkit().getImage(
            JSpeccy.class.getResource("/icons/JSpeccy48x48.png")));
    setResizable(false);
    addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(java.awt.event.WindowEvent evt) {
            formWindowClosing(evt);
        }
    });

    statusPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    statusPanel.setLayout(new javax.swing.BoxLayout(statusPanel, javax.swing.BoxLayout.LINE_AXIS));

    modelLabel.setText(bundle.getString("JSpeccy.modelLabel.text")); // NOI18N
    modelLabel.setToolTipText(bundle.getString("JSpeccy.modelLabel.toolTipText")); // NOI18N
    modelLabel.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
    statusPanel.add(modelLabel);
    statusPanel.add(filler3);

    mdrvLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    mdrvLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/microdrive_on.png"))); // NOI18N
    mdrvLabel.setText(bundle.getString("JSpeccy.mdrvLabel.text")); // NOI18N
    mdrvLabel.setEnabled(false);
    mdrvLabel.setMaximumSize(new java.awt.Dimension(26, 26));
    mdrvLabel.setMinimumSize(new java.awt.Dimension(26, 26));
    mdrvLabel.setPreferredSize(new java.awt.Dimension(26, 26));
    statusPanel.add(mdrvLabel);

    jSeparator10.setOrientation(javax.swing.SwingConstants.VERTICAL);
    jSeparator10.setMaximumSize(new java.awt.Dimension(5, 32767));
    jSeparator10.setMinimumSize(new java.awt.Dimension(3, 16));
    jSeparator10.setPreferredSize(new java.awt.Dimension(3, 16));
    jSeparator10.setRequestFocusEnabled(false);
    statusPanel.add(jSeparator10);

    tapeLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    tapeLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/Akai24x24.png"))); // NOI18N
    tapeLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
    tapeLabel.setEnabled(false);
    tapeLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    tapeLabel.setPreferredSize(new java.awt.Dimension(30, 26));
    tapeLabel.setRequestFocusEnabled(false);
    statusPanel.add(tapeLabel);

    jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);
    jSeparator2.setMaximumSize(new java.awt.Dimension(5, 32767));
    jSeparator2.setMinimumSize(new java.awt.Dimension(3, 16));
    jSeparator2.setPreferredSize(new java.awt.Dimension(3, 16));
    jSeparator2.setRequestFocusEnabled(false);
    statusPanel.add(jSeparator2);

    speedLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    speedLabel.setText(bundle.getString("JSpeccy.speedLabel.text")); // NOI18N
    speedLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
    speedLabel.setMaximumSize(new java.awt.Dimension(75, 18));
    speedLabel.setMinimumSize(new java.awt.Dimension(40, 18));
    speedLabel.setPreferredSize(new java.awt.Dimension(50, 18));
    speedLabel.setRequestFocusEnabled(false);
    statusPanel.add(speedLabel);

    getContentPane().add(statusPanel, java.awt.BorderLayout.PAGE_END);

    toolbarMenu.setRollover(true);

    openSnapshotButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/fileopen.png"))); // NOI18N
    openSnapshotButton.setToolTipText(bundle.getString("JSpeccy.openSnapshotButton.toolTipText")); // NOI18N
    openSnapshotButton.setFocusable(false);
    openSnapshotButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    openSnapshotButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    openSnapshotButton.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            openSnapshotActionPerformed(evt);
        }
    });
    toolbarMenu.add(openSnapshotButton);

    pauseToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/player_pause.png"))); // NOI18N
    pauseToggleButton.setToolTipText(bundle.getString("JSpeccy.pauseToggleButton.toolTipText")); // NOI18N
    pauseToggleButton.setFocusable(false);
    pauseToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    pauseToggleButton.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/player_play.png"))); // NOI18N
    pauseToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    pauseToggleButton.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            pauseMachineMenuActionPerformed(evt);
        }
    });
    toolbarMenu.add(pauseToggleButton);

    fastEmulationToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/player_fwd.png"))); // NOI18N
    fastEmulationToggleButton.setText(bundle.getString("JSpeccy.fastEmulationToggleButton.text")); // NOI18N
    fastEmulationToggleButton.setToolTipText(bundle.getString("JSpeccy.fastEmulationToggleButton.toolTipText")); // NOI18N
    fastEmulationToggleButton.setFocusable(false);
    fastEmulationToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    fastEmulationToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    fastEmulationToggleButton.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            fastEmulationToggleButtonActionPerformed(evt);
        }
    });
    toolbarMenu.add(fastEmulationToggleButton);

    doubleSizeToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/viewmag+.png"))); // NOI18N
    doubleSizeToggleButton.setToolTipText(bundle.getString("JSpeccy.doubleSizeToggleButton.toolTipText")); // NOI18N
    doubleSizeToggleButton.setFocusable(false);
    doubleSizeToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    doubleSizeToggleButton.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/viewmag-.png"))); // NOI18N
    doubleSizeToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    doubleSizeToggleButton.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            doubleSizeOptionActionPerformed(evt);
        }
    });
    toolbarMenu.add(doubleSizeToggleButton);

    silenceSoundToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/sound-on-16x16.png"))); // NOI18N
    silenceSoundToggleButton.setToolTipText(bundle.getString("JSpeccy.silenceSoundToggleButton.toolTipText")); // NOI18N
    silenceSoundToggleButton.setFocusable(false);
    silenceSoundToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    silenceSoundToggleButton.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/sound-off-16x16.png"))); // NOI18N
    silenceSoundToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    silenceSoundToggleButton.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            silenceSoundToggleButtonActionPerformed(evt);
        }
    });
    toolbarMenu.add(silenceSoundToggleButton);

    resetSpectrumButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/shutdown.png"))); // NOI18N
    resetSpectrumButton.setToolTipText(bundle.getString("JSpeccy.resetSpectrumButton.toolTipText")); // NOI18N
    resetSpectrumButton.setFocusable(false);
    resetSpectrumButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    resetSpectrumButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    resetSpectrumButton.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            resetMachineMenuActionPerformed(evt);
        }
    });
    toolbarMenu.add(resetSpectrumButton);

    hardResetSpectrumButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/exit.png"))); // NOI18N
    hardResetSpectrumButton.setToolTipText(bundle.getString("JSpeccy.hardResetSpectrumButton.toolTipText")); // NOI18N
    hardResetSpectrumButton.setFocusable(false);
    hardResetSpectrumButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    hardResetSpectrumButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    hardResetSpectrumButton.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            hardResetSpectrumButtonActionPerformed(evt);
        }
    });
    toolbarMenu.add(hardResetSpectrumButton);

    getContentPane().add(toolbarMenu, java.awt.BorderLayout.PAGE_START);

    fileMenu.setText(bundle.getString("JSpeccy.fileMenu.text")); // NOI18N

    openSnapshot.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F3, 0));
    openSnapshot.setText(bundle.getString("JSpeccy.openSnapshot.text")); // NOI18N
    openSnapshot.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            openSnapshotActionPerformed(evt);
        }
    });
    fileMenu.add(openSnapshot);

    saveSnapshot.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0));
    saveSnapshot.setText(bundle.getString("JSpeccy.saveSnapshot.text")); // NOI18N
    saveSnapshot.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveSnapshotActionPerformed(evt);
        }
    });
    fileMenu.add(saveSnapshot);
    fileMenu.add(jSeparator4);

    loadScreenShot.setText(bundle.getString("JSpeccy.loadScreenShot.text")); // NOI18N
    loadScreenShot.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            loadScreenShotActionPerformed(evt);
        }
    });
    fileMenu.add(loadScreenShot);

    saveScreenShot.setText(bundle.getString("JSpeccy.saveScreenShot.text")); // NOI18N
    saveScreenShot.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveScreenShotActionPerformed(evt);
        }
    });
    fileMenu.add(saveScreenShot);
    fileMenu.add(jSeparator1);

    recentFilesMenu.setText(bundle.getString("JSpeccy.recentFilesMenu.text")); // NOI18N

    recentFileMenu0.setText(bundle.getString("JSpeccy.recentFileMenu0.text")); // NOI18N
    recentFileMenu0.setEnabled(false);
    recentFileMenu0.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            recentFileMenu0ActionPerformed(evt);
        }
    });
    recentFilesMenu.add(recentFileMenu0);

    recentFileMenu1.setText(bundle.getString("JSpeccy.recentFileMenu0.text")); // NOI18N
    recentFileMenu1.setEnabled(false);
    recentFileMenu1.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            recentFileMenu1ActionPerformed(evt);
        }
    });
    recentFilesMenu.add(recentFileMenu1);

    recentFileMenu2.setText(bundle.getString("JSpeccy.recentFileMenu0.text")); // NOI18N
    recentFileMenu2.setEnabled(false);
    recentFileMenu2.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            recentFileMenu2ActionPerformed(evt);
        }
    });
    recentFilesMenu.add(recentFileMenu2);

    recentFileMenu3.setText(bundle.getString("JSpeccy.recentFileMenu0.text")); // NOI18N
    recentFileMenu3.setEnabled(false);
    recentFileMenu3.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            recentFileMenu3ActionPerformed(evt);
        }
    });
    recentFilesMenu.add(recentFileMenu3);

    recentFileMenu4.setText(bundle.getString("JSpeccy.recentFileMenu0.text")); // NOI18N
    recentFileMenu4.setEnabled(false);
    recentFileMenu4.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            recentFileMenu4ActionPerformed(evt);
        }
    });
    recentFilesMenu.add(recentFileMenu4);

    fileMenu.add(recentFilesMenu);
    fileMenu.add(jSeparator7);

    thisIsTheEndMyFriend.setText(bundle.getString("JSpeccy.thisIsTheEndMyFriend.text")); // NOI18N
    thisIsTheEndMyFriend.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            thisIsTheEndMyFriendActionPerformed(evt);
        }
    });
    fileMenu.add(thisIsTheEndMyFriend);

    jMenuBar1.add(fileMenu);

    optionsMenu.setText(bundle.getString("JSpeccy.optionsMenu.text")); // NOI18N

    doubleSizeOption.setText(bundle.getString("JSpeccy.doubleSizeOption.text")); // NOI18N
    doubleSizeOption.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            doubleSizeOptionActionPerformed(evt);
        }
    });
    optionsMenu.add(doubleSizeOption);

    joystickOptionMenu.setText(bundle.getString("JSpeccy.joystickOptionMenu.text")); // NOI18N

    joystickButtonGroup.add(noneJoystick);
    noneJoystick.setSelected(true);
    noneJoystick.setText(bundle.getString("JSpeccy.noneJoystick.text")); // NOI18N
    noneJoystick.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            noneJoystickActionPerformed(evt);
        }
    });
    joystickOptionMenu.add(noneJoystick);

    joystickButtonGroup.add(kempstonJoystick);
    kempstonJoystick.setText(bundle.getString("JSpeccy.kempstonJoystick.text")); // NOI18N
    kempstonJoystick.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            kempstonJoystickActionPerformed(evt);
        }
    });
    joystickOptionMenu.add(kempstonJoystick);

    joystickButtonGroup.add(sinclair1Joystick);
    sinclair1Joystick.setText(bundle.getString("JSpeccy.sinclair1Joystick.text")); // NOI18N
    sinclair1Joystick.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            sinclair1JoystickActionPerformed(evt);
        }
    });
    joystickOptionMenu.add(sinclair1Joystick);

    joystickButtonGroup.add(sinclair2Joystick);
    sinclair2Joystick.setText(bundle.getString("JSpeccy.sinclair2Joystick.text")); // NOI18N
    sinclair2Joystick.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            sinclair2JoystickActionPerformed(evt);
        }
    });
    joystickOptionMenu.add(sinclair2Joystick);

    joystickButtonGroup.add(cursorJoystick);
    cursorJoystick.setText(bundle.getString("JSpeccy.cursorJoystick.text")); // NOI18N
    cursorJoystick.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            cursorJoystickActionPerformed(evt);
        }
    });
    joystickOptionMenu.add(cursorJoystick);

    joystickButtonGroup.add(fullerJoystick);
    fullerJoystick.setText(bundle.getString("JSpeccy.fullerJoystick.text")); // NOI18N
    fullerJoystick.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            fullerJoystickActionPerformed(evt);
        }
    });
    joystickOptionMenu.add(fullerJoystick);

    optionsMenu.add(joystickOptionMenu);

    settingsOptionsMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, 0));
    settingsOptionsMenu.setText(bundle.getString("JSpeccy.settings.text")); // NOI18N
    settingsOptionsMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            settingsOptionsMenuActionPerformed(evt);
        }
    });
    optionsMenu.add(settingsOptionsMenu);

    jMenuBar1.add(optionsMenu);

    machineMenu.setText(bundle.getString("JSpeccy.machineMenu.text")); // NOI18N
    machineMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            silenceSoundToggleButtonActionPerformed(evt);
        }
    });

    pauseMachineMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PAUSE, 0));
    pauseMachineMenu.setText(bundle.getString("JSpeccy.pauseMachineMenu.text")); // NOI18N
    pauseMachineMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            pauseMachineMenuActionPerformed(evt);
        }
    });
    machineMenu.add(pauseMachineMenu);

    silenceMachineMenu.setText(bundle.getString("JSpeccy.silenceMachineMenu.text_1")); // NOI18N
    silenceMachineMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            silenceSoundToggleButtonActionPerformed(evt);
        }
    });
    machineMenu.add(silenceMachineMenu);

    resetMachineMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0));
    resetMachineMenu.setText(bundle.getString("JSpeccy.resetMachineMenu.text")); // NOI18N
    resetMachineMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            resetMachineMenuActionPerformed(evt);
        }
    });
    machineMenu.add(resetMachineMenu);

    hardResetMachineMenu.setText(bundle.getString("JSpeccy.hardResetMachineMenu.text")); // NOI18N
    hardResetMachineMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            hardResetSpectrumButtonActionPerformed(evt);
        }
    });
    machineMenu.add(hardResetMachineMenu);

    nmiMachineMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, java.awt.event.InputEvent.CTRL_MASK));
    nmiMachineMenu.setText(bundle.getString("JSpeccy.nmiMachineMenu.text")); // NOI18N
    nmiMachineMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            nmiMachineMenuActionPerformed(evt);
        }
    });
    machineMenu.add(nmiMachineMenu);
    machineMenu.add(jSeparator3);

    pokeMachineMenu.setText(bundle.getString("JSpeccy.pokeMachineMenu.text")); // NOI18N
    pokeMachineMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            pokeMachineMenuActionPerformed(evt);
        }
    });
    machineMenu.add(pokeMachineMenu);

    memoryBrowserMachineMenu.setText(bundle.getString("JSpeccy.memoryBrowserMachineMenu.text")); // NOI18N
    memoryBrowserMachineMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            memoryBrowserMachineMenuActionPerformed(evt);
        }
    });
    machineMenu.add(memoryBrowserMachineMenu);
    machineMenu.add(jSeparator14);

    hardwareMachineMenu.setText(bundle.getString("JSpeccy.hardwareMachineMenu.text")); // NOI18N

    hardwareButtonGroup.add(spec16kHardware);
    spec16kHardware.setText(bundle.getString("JSpeccy.spec16kHardware.text")); // NOI18N
    spec16kHardware.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            spec16kHardwareActionPerformed(evt);
        }
    });
    hardwareMachineMenu.add(spec16kHardware);

    hardwareButtonGroup.add(spec48kHardware);
    spec48kHardware.setSelected(true);
    spec48kHardware.setText(bundle.getString("JSpeccy.spec48kHardware.text")); // NOI18N
    spec48kHardware.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            spec48kHardwareActionPerformed(evt);
        }
    });
    hardwareMachineMenu.add(spec48kHardware);

    hardwareButtonGroup.add(spec128kHardware);
    spec128kHardware.setText(bundle.getString("JSpeccy.spec128kHardware.text")); // NOI18N
    spec128kHardware.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            spec128kHardwareActionPerformed(evt);
        }
    });
    hardwareMachineMenu.add(spec128kHardware);

    hardwareButtonGroup.add(specPlus2Hardware);
    specPlus2Hardware.setText(bundle.getString("JSpeccy.specPlus2Hardware.text")); // NOI18N
    specPlus2Hardware.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            specPlus2HardwareActionPerformed(evt);
        }
    });
    hardwareMachineMenu.add(specPlus2Hardware);

    hardwareButtonGroup.add(specPlus2AHardware);
    specPlus2AHardware.setText(bundle.getString("JSpeccy.specPlus2AHardware.text")); // NOI18N
    specPlus2AHardware.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            specPlus2AHardwareActionPerformed(evt);
        }
    });
    hardwareMachineMenu.add(specPlus2AHardware);

    hardwareButtonGroup.add(specPlus3Hardware);
    specPlus3Hardware.setText(bundle.getString("JSpeccy.specPlus3Hardware.text")); // NOI18N
    specPlus3Hardware.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            specPlus3HardwareActionPerformed(evt);
        }
    });
    hardwareMachineMenu.add(specPlus3Hardware);

    machineMenu.add(hardwareMachineMenu);

    jMenuBar1.add(machineMenu);

    mediaMenu.setText(bundle.getString("JSpeccy.mediaMenu.text")); // NOI18N

    tapeMediaMenu.setText(bundle.getString("JSpeccy.tapeMediaMenu.text")); // NOI18N

    openTapeMediaMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F7, 0));
    openTapeMediaMenu.setText(bundle.getString("JSpeccy.openTapeMediaMenu.text")); // NOI18N
    openTapeMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            openTapeMediaMenuActionPerformed(evt);
        }
    });
    tapeMediaMenu.add(openTapeMediaMenu);

    playTapeMediaMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F8, 0));
    playTapeMediaMenu.setText(bundle.getString("JSpeccy.playTapeMediaMenu.text")); // NOI18N
    playTapeMediaMenu.setEnabled(false);
    playTapeMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            playTapeMediaMenuActionPerformed(evt);
        }
    });
    tapeMediaMenu.add(playTapeMediaMenu);

    browserTapeMediaMenu.setText(bundle.getString("JSpeccy.browserTapeMediaMenu.text")); // NOI18N
    browserTapeMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            browserTapeMediaMenuActionPerformed(evt);
        }
    });
    tapeMediaMenu.add(browserTapeMediaMenu);

    rewindTapeMediaMenu.setText(bundle.getString("JSpeccy.rewindTapeMediaMenu.text")); // NOI18N
    rewindTapeMediaMenu.setEnabled(false);
    rewindTapeMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            rewindTapeMediaMenuActionPerformed(evt);
        }
    });
    tapeMediaMenu.add(rewindTapeMediaMenu);
    tapeMediaMenu.add(jSeparator5);

    createTapeMediaMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F6, 0));
    createTapeMediaMenu.setText(bundle.getString("JSpeccy.createTapeMediaMenu.text")); // NOI18N
    createTapeMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            createTapeMediaMenuActionPerformed(evt);
        }
    });
    tapeMediaMenu.add(createTapeMediaMenu);

    clearTapeMediaMenu.setText(bundle.getString("JSpeccy.clearTapeMediaMenu.text")); // NOI18N
    clearTapeMediaMenu.setEnabled(false);
    clearTapeMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            clearTapeMediaMenuActionPerformed(evt);
        }
    });
    tapeMediaMenu.add(clearTapeMediaMenu);
    tapeMediaMenu.add(jSeparator6);

    recordStartTapeMediaMenu.setText(bundle.getString("JSpeccy.recordStartTapeMediaMenu.text")); // NOI18N
    recordStartTapeMediaMenu.setEnabled(false);
    recordStartTapeMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            recordStartTapeMediaMenuActionPerformed(evt);
        }
    });
    tapeMediaMenu.add(recordStartTapeMediaMenu);

    recordStopTapeMediaMenu.setText(bundle.getString("JSpeccy.recordStopTapeMediaMenu.text")); // NOI18N
    recordStopTapeMediaMenu.setEnabled(false);
    recordStopTapeMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            recordStopTapeMediaMenuActionPerformed(evt);
        }
    });
    tapeMediaMenu.add(recordStopTapeMediaMenu);
    tapeMediaMenu.add(jSeparator13);

    refreshTapeMediaMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F7, java.awt.event.InputEvent.CTRL_MASK));
    refreshTapeMediaMenu.setText(bundle.getString("JSpeccy.refreshTapeMediaMenu.text")); // NOI18N
    refreshTapeMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            refreshTapeMediaMenuActionPerformed(evt);
        }
    });
    tapeMediaMenu.add(refreshTapeMediaMenu);

    mediaMenu.add(tapeMediaMenu);
    mediaMenu.add(jSeparator8);

    IF1MediaMenu.setText(bundle.getString("JSpeccy.IF1MediaMenu.text")); // NOI18N

    microdrivesIF1MediaMenu.setText(bundle.getString("JSpeccy.microdrivesIF1MediaMenu.text")); // NOI18N
    microdrivesIF1MediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            microdrivesIF1MediaMenuActionPerformed(evt);
        }
    });
    IF1MediaMenu.add(microdrivesIF1MediaMenu);

    mediaMenu.add(IF1MediaMenu);
    mediaMenu.add(jSeparator9);

    IF2MediaMenu.setText(bundle.getString("JSpeccy.IF2MediaMenu.text")); // NOI18N

    insertIF2RomMediaMenu.setText(bundle.getString("JSpeccy.insertIF2RomMediaMenu.text")); // NOI18N
    insertIF2RomMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            insertIF2RomMediaMenuActionPerformed(evt);
        }
    });
    IF2MediaMenu.add(insertIF2RomMediaMenu);

    extractIF2RomMediaMenu.setText(bundle.getString("JSpeccy.extractIF2RomMediaMenu.text")); // NOI18N
    extractIF2RomMediaMenu.setEnabled(false);
    extractIF2RomMediaMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            extractIF2RomMediaMenuActionPerformed(evt);
        }
    });
    IF2MediaMenu.add(extractIF2RomMediaMenu);

    mediaMenu.add(IF2MediaMenu);

    jMenuBar1.add(mediaMenu);

    helpMenu.setText(bundle.getString("JSpeccy.helpMenu.text")); // NOI18N

    imageHelpMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
    imageHelpMenu.setText(bundle.getString("JSpeccy.imageHelpMenu.text")); // NOI18N
    imageHelpMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            imageHelpMenuActionPerformed(evt);
        }
    });
    helpMenu.add(imageHelpMenu);

    aboutHelpMenu.setText(bundle.getString("JSpeccy.aboutHelpMenu.text")); // NOI18N
    aboutHelpMenu.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            aboutHelpMenuActionPerformed(evt);
        }
    });
    helpMenu.add(aboutHelpMenu);

    jMenuBar1.add(helpMenu);

    setJMenuBar(jMenuBar1);

    pack();
    }// </editor-fold>//GEN-END:initComponents

    private void openSnapshotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openSnapshotActionPerformed
        boolean paused = spectrum.isPaused();
        if( openSnapshotDlg == null ) {
            openSnapshotDlg = new JFileChooser(
                    settings.getRecentFilesSettings().getLastSnapshotDir());
            openSnapshotDlg.addChoosableFileFilter(allSnapTapeExtension);
            openSnapshotDlg.addChoosableFileFilter(snapshotExtension);
            openSnapshotDlg.addChoosableFileFilter(tapeExtension);
            openSnapshotDlg.setFileFilter(allSnapTapeExtension);
        }
        else
            openSnapshotDlg.setSelectedFile(currentFileSnapshot);

        if (!paused)
            spectrum.stopEmulation();

        int status = openSnapshotDlg.showOpenDialog(getContentPane());
        if (status == JFileChooser.APPROVE_OPTION) {
            currentFileSnapshot = openSnapshotDlg.getSelectedFile();
            settings.getRecentFilesSettings().setLastSnapshotDir(
                    currentFileSnapshot.getParent());
            
            if (snapshotExtension.accept(currentFileSnapshot)) {
                rotateRecentFile(currentFileSnapshot);
                spectrum.loadSnapshot(currentFileSnapshot);
                updateGuiSelections();
            } else {
                currentFileTape = openSnapshotDlg.getSelectedFile();
                settings.getRecentFilesSettings().setLastTapeDir(
                        currentFileTape.getParent());
                spectrum.tape.eject();
                if (spectrum.tape.insert(currentFileTape)) {
                    rotateRecentFile(currentFileTape);
                    tapeFilename.setText(currentFileTape.getName());
                    playTapeMediaMenu.setEnabled(true);
                    rewindTapeMediaMenu.setEnabled(true);
                    clearTapeMediaMenu.setEnabled(currentFileTape.canWrite());
                    recordStartTapeMediaMenu.setEnabled(currentFileTape.canWrite());
                    tapeBrowserButtonRec.setEnabled(currentFileTape.canWrite());
                    tapeBrowserButtonPlay.setEnabled(true);
                    tapeBrowserButtonStop.setEnabled(true);
                    tapeBrowserButtonRew.setEnabled(true);
                    tapeBrowserButtonEject.setEnabled(true);
                    if (currentFileTape.getName().toLowerCase().endsWith(".csw")) {
                        clearTapeMediaMenu.setEnabled(false);
                        recordStartTapeMediaMenu.setEnabled(false);
                        tapeBrowserButtonRec.setEnabled(false);
                    }
                } else {
                    ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                    JOptionPane.showMessageDialog(this, bundle.getString("LOAD_TAPE_ERROR"),
                        bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        if (!paused)
            spectrum.startEmulation();
    }//GEN-LAST:event_openSnapshotActionPerformed

    private void thisIsTheEndMyFriendActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_thisIsTheEndMyFriendActionPerformed
        exitEmulator();
    }//GEN-LAST:event_thisIsTheEndMyFriendActionPerformed

    private void doubleSizeOptionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doubleSizeOptionActionPerformed
        Object source = evt.getSource();
        if( source instanceof javax.swing.JCheckBoxMenuItem )
            doubleSizeToggleButton.setSelected(doubleSizeOption.isSelected());
        else
            doubleSizeOption.setSelected(doubleSizeToggleButton.isSelected());

        settings.getSpectrumSettings().setDoubleSize(doubleSizeToggleButton.isSelected());
        jscr.setDoubleSize(doubleSizeToggleButton.isSelected());
        pack();
    }//GEN-LAST:event_doubleSizeOptionActionPerformed

    private void pauseMachineMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseMachineMenuActionPerformed
        Object source = evt.getSource();
        if( source instanceof javax.swing.JCheckBoxMenuItem )
            pauseToggleButton.setSelected(pauseMachineMenu.isSelected());
        else
            pauseMachineMenu.setSelected(pauseToggleButton.isSelected());
        
        if( pauseMachineMenu.isSelected() )
            spectrum.stopEmulation();
        else
            spectrum.startEmulation();
    }//GEN-LAST:event_pauseMachineMenuActionPerformed

    private void resetMachineMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMachineMenuActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        int ret = JOptionPane.showConfirmDialog(getContentPane(),
                  bundle.getString("ARE_YOU_SURE_QUESTION"), bundle.getString("RESET_SPECTRUM"),
                  JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE); // NOI18N

        if( ret == JOptionPane.YES_OPTION )
            spectrum.reset();
    }//GEN-LAST:event_resetMachineMenuActionPerformed

    private void silenceSoundToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_silenceSoundToggleButtonActionPerformed
        Object source = evt.getSource();
        if( source instanceof javax.swing.JToggleButton )
            silenceMachineMenu.setSelected(silenceSoundToggleButton.isSelected());
        else
            silenceSoundToggleButton.setSelected(silenceMachineMenu.isSelected());

        spectrum.muteSound(silenceSoundToggleButton.isSelected());
    }//GEN-LAST:event_silenceSoundToggleButtonActionPerformed

    private void playTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_playTapeMediaMenuActionPerformed
        if (spectrum.tape.isTapePlaying()) {
            spectrum.tape.stop();
            tapeBrowserButtonRec.setEnabled(currentFileTape.canWrite());
            tapeBrowserButtonPlay.setEnabled(true);
            tapeBrowserButtonRew.setEnabled(true);
            tapeBrowserButtonEject.setEnabled(true);
        } else {
            spectrum.tape.play();
        }
    }//GEN-LAST:event_playTapeMediaMenuActionPerformed

    private void openTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openTapeMediaMenuActionPerformed
        boolean paused = spectrum.isPaused();
        if( openTapeDlg == null ) {
            openTapeDlg = new JFileChooser(
                    settings.getRecentFilesSettings().getLastTapeDir());
            openTapeDlg.addChoosableFileFilter(tapeExtension);
            openTapeDlg.setFileFilter(tapeExtension);
        }
        else
            openTapeDlg.setSelectedFile(currentFileTape);

        if (!paused)
            spectrum.stopEmulation();

        int status = openTapeDlg.showOpenDialog(getContentPane());
        if (status == JFileChooser.APPROVE_OPTION) {
            currentFileTape = openTapeDlg.getSelectedFile();
            settings.getRecentFilesSettings().setLastTapeDir(
                    currentFileTape.getParent());
            spectrum.tape.eject();
            if (spectrum.tape.insert(currentFileTape)) {
                rotateRecentFile(currentFileTape);
                tapeFilename.setText(currentFileTape.getName());
                playTapeMediaMenu.setEnabled(true);
                clearTapeMediaMenu.setEnabled(currentFileTape.canWrite());
                rewindTapeMediaMenu.setEnabled(true);
                recordStartTapeMediaMenu.setEnabled(currentFileTape.canWrite());
                tapeBrowserButtonRec.setEnabled(currentFileTape.canWrite());
                tapeBrowserButtonPlay.setEnabled(true);
                tapeBrowserButtonStop.setEnabled(true);
                tapeBrowserButtonRew.setEnabled(true);
                tapeBrowserButtonEject.setEnabled(true);
                if (currentFileTape.getName().toLowerCase().endsWith(".csw")) {
                    clearTapeMediaMenu.setEnabled(false);
                    recordStartTapeMediaMenu.setEnabled(false);
                    tapeBrowserButtonRec.setEnabled(false);
                }
            } else {
                ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                JOptionPane.showMessageDialog(this, bundle.getString("LOAD_TAPE_ERROR"),
                    bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
            }
        }

        if (!paused)
            spectrum.startEmulation();
    }//GEN-LAST:event_openTapeMediaMenuActionPerformed

    private void rewindTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rewindTapeMediaMenuActionPerformed
        spectrum.tape.rewind();
    }//GEN-LAST:event_rewindTapeMediaMenuActionPerformed

    private void imageHelpMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_imageHelpMenuActionPerformed
        keyboardHelper.setResizable(false);
        keyboardHelper.pack();
        keyboardHelper.setVisible(true);
    }//GEN-LAST:event_imageHelpMenuActionPerformed

    private void aboutHelpMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutHelpMenuActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        JOptionPane.showMessageDialog(getContentPane(),
            bundle.getString("ABOUT_MESSAGE"), bundle.getString("ABOUT_TITLE"),
            JOptionPane.INFORMATION_MESSAGE,
            new javax.swing.ImageIcon(getClass().getResource("/icons/JSpeccy64x64.png")));
    }//GEN-LAST:event_aboutHelpMenuActionPerformed

    private void nmiMachineMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nmiMachineMenuActionPerformed
        spectrum.triggerNMI();
    }//GEN-LAST:event_nmiMachineMenuActionPerformed

    private void closeKeyboardHelperActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeKeyboardHelperActionPerformed
        keyboardHelper.setVisible(false);
    }//GEN-LAST:event_closeKeyboardHelperActionPerformed

    private void saveSnapshotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveSnapshotActionPerformed
        boolean paused = spectrum.isPaused();
        if( saveSnapshotDlg == null ) {
            saveSnapshotDlg = new JFileChooser("/home/jsanchez/Spectrum");
            saveSnapshotDlg.addChoosableFileFilter(snapshotExtension);
            saveSnapshotDlg.setFileFilter(snapshotExtension);
            currentDirSaveSnapshot = saveSnapshotDlg.getCurrentDirectory();
        }
        else {
            saveSnapshotDlg.setCurrentDirectory(currentDirSaveSnapshot);
            BasicFileChooserUI chooserUI = (BasicFileChooserUI) saveSnapshotDlg.getUI();
            chooserUI.setFileName("");
        }

        if (!paused)
            spectrum.stopEmulation();

        int status = saveSnapshotDlg.showSaveDialog(getContentPane());
        if( status == JFileChooser.APPROVE_OPTION ) {
            currentDirSaveSnapshot = saveSnapshotDlg.getCurrentDirectory();
            if (spectrum.tape.getTapeFilename() != null &&
                    saveSnapshotDlg.getSelectedFile().getName().toLowerCase().endsWith("szx")) {
                tapeFilenameLabel.setText(spectrum.tape.getTapeFilename().getName());
                ignoreRadioButton.setSelected(true);
                spectrum.setSzxTapeMode(0); // ignore by default
                saveSzxTape.pack();
                saveSzxTape.setVisible(true);
            }
            spectrum.saveSnapshot(saveSnapshotDlg.getSelectedFile());
        }
        if (!paused)
            spectrum.startEmulation();
    }//GEN-LAST:event_saveSnapshotActionPerformed

    private void noneJoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_noneJoystickActionPerformed

        spectrum.setJoystick(Spectrum.Joystick.NONE);
        noneJoystick.setSelected(true);

}//GEN-LAST:event_noneJoystickActionPerformed

    private void kempstonJoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_kempstonJoystickActionPerformed

        spectrum.setJoystick(Spectrum.Joystick.KEMPSTON);
        kempstonJoystick.setSelected(true);

    }//GEN-LAST:event_kempstonJoystickActionPerformed

    private void sinclair1JoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sinclair1JoystickActionPerformed

        spectrum.setJoystick(Spectrum.Joystick.SINCLAIR1);
        sinclair1Joystick.setSelected(true);

    }//GEN-LAST:event_sinclair1JoystickActionPerformed

    private void sinclair2JoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sinclair2JoystickActionPerformed

        spectrum.setJoystick(Spectrum.Joystick.SINCLAIR2);
        sinclair2Joystick.setSelected(true);

    }//GEN-LAST:event_sinclair2JoystickActionPerformed

    private void cursorJoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cursorJoystickActionPerformed

        spectrum.setJoystick(Spectrum.Joystick.CURSOR);
        cursorJoystick.setSelected(true);
        
    }//GEN-LAST:event_cursorJoystickActionPerformed

    private void spec48kHardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spec48kHardwareActionPerformed

        spectrum.selectHardwareModel(MachineTypes.SPECTRUM48K, true);
        modelLabel.setToolTipText(MachineTypes.SPECTRUM48K.getLongModelName());
        modelLabel.setText(MachineTypes.SPECTRUM48K.getShortModelName());
        
        if (settings.getInterface1Settings().isConnectedIF1()) {
            mdrvLabel.setDisabledIcon(mdrOff);
            IF1MediaMenu.setEnabled(true);
        } else {
            mdrvLabel.setDisabledIcon(null);
            IF1MediaMenu.setEnabled(false);
        }
        
        IF2MediaMenu.setEnabled(true);
        
        spectrum.reset();
    }//GEN-LAST:event_spec48kHardwareActionPerformed

    private void spec128kHardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spec128kHardwareActionPerformed

        spectrum.selectHardwareModel(MachineTypes.SPECTRUM128K, true);
        modelLabel.setToolTipText(MachineTypes.SPECTRUM128K.getLongModelName());
        modelLabel.setText(MachineTypes.SPECTRUM128K.getShortModelName());
        
        if (settings.getInterface1Settings().isConnectedIF1()) {
            mdrvLabel.setDisabledIcon(mdrOff);
            IF1MediaMenu.setEnabled(true);
        } else {
            mdrvLabel.setDisabledIcon(null);
            IF1MediaMenu.setEnabled(false);
        }
        
        IF2MediaMenu.setEnabled(true);
        
        spectrum.reset();
    }//GEN-LAST:event_spec128kHardwareActionPerformed

    private void fastEmulationToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fastEmulationToggleButtonActionPerformed
        if (fastEmulationToggleButton.isSelected())
            spectrum.changeSpeed(settings.getSpectrumSettings().getFramesInt());
        else
            spectrum.changeSpeed(1);
    }//GEN-LAST:event_fastEmulationToggleButtonActionPerformed

    private void browserTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browserTapeMediaMenuActionPerformed
        tapeBrowserDialog.setVisible(true);
        tapeBrowserDialog.pack();
        tapeCatalog.doLayout();
    }//GEN-LAST:event_browserTapeMediaMenuActionPerformed

    private void specPlus2HardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specPlus2HardwareActionPerformed

        spectrum.selectHardwareModel(MachineTypes.SPECTRUMPLUS2, true);
        modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS2.getLongModelName());
        modelLabel.setText(MachineTypes.SPECTRUMPLUS2.getShortModelName());
        
        if (settings.getInterface1Settings().isConnectedIF1()) {
            mdrvLabel.setDisabledIcon(mdrOff);
            IF1MediaMenu.setEnabled(true);
        } else {
            mdrvLabel.setDisabledIcon(null);
            IF1MediaMenu.setEnabled(false);
        }
        
        IF2MediaMenu.setEnabled(true);
        
        spectrum.reset();
        
    }//GEN-LAST:event_specPlus2HardwareActionPerformed

    private void specPlus2AHardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specPlus2AHardwareActionPerformed

        spectrum.selectHardwareModel(MachineTypes.SPECTRUMPLUS2A, true);
        modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS2A.getLongModelName());
        modelLabel.setText(MachineTypes.SPECTRUMPLUS2A.getShortModelName());
        
        IF1MediaMenu.setEnabled(false);
        mdrvLabel.setDisabledIcon(null);
        IF2MediaMenu.setEnabled(false);
        
        spectrum.reset();

    }//GEN-LAST:event_specPlus2AHardwareActionPerformed

    private void settingsOptionsMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_settingsOptionsMenuActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        int AYsoundMode = settings.getAY8912Settings().getSoundMode();
        boolean hifiSound = settings.getSpectrumSettings().isHifiSound();
        boolean muted = settings.getSpectrumSettings().isMutedSound();
        boolean doubleSize = settings.getSpectrumSettings().isDoubleSize();
        settingsDialog.showDialog(this, bundle.getString("SETTINGS_DIALOG_TITLE"));
        spectrum.loadConfigVars();
        if (muted != settings.getSpectrumSettings().isMutedSound()) {
            spectrum.muteSound(!muted);
            silenceMachineMenu.setSelected(!muted);
            silenceSoundToggleButton.setSelected(!muted);
        }
        
        if ((AYsoundMode !=  settings.getAY8912Settings().getSoundMode() ||
            hifiSound != settings.getSpectrumSettings().isHifiSound()) &&
            !spectrum.isMuteSound()) {
            spectrum.muteSound(true);
            spectrum.muteSound(false);
        }
        
        if (spectrum.getSpectrumModel().codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
            if (settings.getInterface1Settings().isConnectedIF1()) {
                IF1MediaMenu.setEnabled(true);
                mdrvLabel.setDisabledIcon(mdrOff);
            } else {
                IF1MediaMenu.setEnabled(false);
                mdrvLabel.setDisabledIcon(null);
            }
            IF2MediaMenu.setEnabled(true);
        } else {
            IF1MediaMenu.setEnabled(false);
            IF2MediaMenu.setEnabled(false);
        }
        
        if (doubleSize != settings.getSpectrumSettings().isDoubleSize()) {
            doubleSizeToggleButton.setSelected(!doubleSize);
            doubleSizeOption.setSelected(!doubleSize);
            jscr.setDoubleSize(!doubleSize);
            pack();
        }
    }//GEN-LAST:event_settingsOptionsMenuActionPerformed

    private void saveScreenShotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveScreenShotActionPerformed
        boolean paused = spectrum.isPaused();
        if( saveImageDlg == null ) {
            saveImageDlg = new JFileChooser("/home/jsanchez/Spectrum");
            saveImageDlg.addChoosableFileFilter(imageExtension);
            saveImageDlg.setFileFilter(imageExtension);
            currentDirSaveImage = saveImageDlg.getCurrentDirectory();
        }
        else {
            saveImageDlg.setCurrentDirectory(currentDirSaveImage);
            BasicFileChooserUI chooserUI = (BasicFileChooserUI) saveImageDlg.getUI();
            chooserUI.setFileName("");
        }

        if (!paused)
            spectrum.stopEmulation();

        int status = saveImageDlg.showSaveDialog(getContentPane());
        if( status == JFileChooser.APPROVE_OPTION ) {
            //spectrum.stopEmulation();
            currentDirSaveImage = saveImageDlg.getCurrentDirectory();
            spectrum.saveImage(saveImageDlg.getSelectedFile());
        }
        if (!paused)
            spectrum.startEmulation();
    }//GEN-LAST:event_saveScreenShotActionPerformed

    private void createTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createTapeMediaMenuActionPerformed
        boolean paused = spectrum.isPaused();
        if (openTapeDlg == null) {
            openTapeDlg = new JFileChooser("/home/jsanchez/Spectrum");
            openTapeDlg.addChoosableFileFilter(tapeExtension);
            openTapeDlg.setFileFilter(tapeExtension);
        } else {
            openTapeDlg.setCurrentDirectory(currentFileTape.getParentFile());
        }

        if (!paused) {
            spectrum.stopEmulation();
        }

        int status = openTapeDlg.showOpenDialog(this);
        if (status == JFileChooser.APPROVE_OPTION) {
            currentFileTape = openTapeDlg.getSelectedFile();
            try {
                currentFileTape.createNewFile();
                spectrum.tape.eject();
                if (spectrum.tape.insert(currentFileTape)) {
                    rotateRecentFile(currentFileTape);
                    tapeFilename.setText(currentFileTape.getName());
                    playTapeMediaMenu.setEnabled(true);
                    clearTapeMediaMenu.setEnabled(currentFileTape.canWrite());
                    rewindTapeMediaMenu.setEnabled(true);
                    recordStartTapeMediaMenu.setEnabled(currentFileTape.canWrite());
                } else {
                    ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                    JOptionPane.showMessageDialog(this, bundle.getString("LOAD_TAPE_ERROR"),
                            bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                Logger.getLogger(JSpeccy.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        if (!paused) {
            spectrum.startEmulation();
        }
    }//GEN-LAST:event_createTapeMediaMenuActionPerformed

    private void hardResetSpectrumButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hardResetSpectrumButtonActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        int ret = JOptionPane.showConfirmDialog(getContentPane(),
            bundle.getString("ARE_YOU_SURE_QUESTION"), bundle.getString("HARD_RESET_SPECTRUM"),
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE); // NOI18N

        if (ret == JOptionPane.YES_OPTION) {
            spectrum.hardReset();
            switch (settings.getSpectrumSettings().getDefaultModel()) {
                case 0:
                    spec16kHardware.setSelected(true);
                    modelLabel.setToolTipText(MachineTypes.SPECTRUM16K.getLongModelName());
                    modelLabel.setText(MachineTypes.SPECTRUM16K.getShortModelName());
                    break;
                case 2:
                    spec128kHardware.setSelected(true);
                    modelLabel.setToolTipText(MachineTypes.SPECTRUM128K.getLongModelName());
                    modelLabel.setText(MachineTypes.SPECTRUM128K.getShortModelName());
                    break;
                case 3:
                    specPlus2Hardware.setSelected(true);
                    modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS2.getLongModelName());
                    modelLabel.setText(MachineTypes.SPECTRUMPLUS2.getShortModelName());
                    break;
                case 4:
                    specPlus2AHardware.setSelected(true);
                    IF1MediaMenu.setEnabled(false);
                    modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS2A.getLongModelName());
                    modelLabel.setText(MachineTypes.SPECTRUMPLUS2A.getShortModelName());
                    break;
                case 5:
                    specPlus3Hardware.setSelected(true);
                    IF1MediaMenu.setEnabled(false);
                    modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS3.getLongModelName());
                    modelLabel.setText(MachineTypes.SPECTRUMPLUS3.getShortModelName());
                    break;
                default:
                    spec48kHardware.setSelected(true);
                    modelLabel.setToolTipText(MachineTypes.SPECTRUM48K.getLongModelName());
                    modelLabel.setText(MachineTypes.SPECTRUM48K.getShortModelName());
            }

            switch (settings.getKeyboardJoystickSettings().getJoystickModel()) {
                case 1:
                    kempstonJoystick.setSelected(true);
                    break;
                case 2:
                    sinclair1Joystick.setSelected(true);
                    break;
                case 3:
                    sinclair2Joystick.setSelected(true);
                    break;
                case 4:
                    cursorJoystick.setSelected(true);
                    break;
                default:
                    noneJoystick.setSelected(true);
            }

            if (settings.getSpectrumSettings().getDefaultModel() < 4) {
                IF1MediaMenu.setEnabled(settings.getInterface1Settings().isConnectedIF1());
                IF2MediaMenu.setEnabled(true);
            } else {
                IF1MediaMenu.setEnabled(false);
                IF2MediaMenu.setEnabled(false);
            }
        }
    }//GEN-LAST:event_hardResetSpectrumButtonActionPerformed

    private void clearTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearTapeMediaMenuActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        int ret = JOptionPane.showConfirmDialog(getContentPane(),
            bundle.getString("ARE_YOU_SURE_QUESTION"), bundle.getString("CLEAR_TAPE"),
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE); // NOI18N

        if (ret == JOptionPane.YES_OPTION && spectrum.tape.isTapeReady()) {
            try {
                if (currentFileTape.delete()) {
                    spectrum.tape.eject();
                }

                if (currentFileTape.createNewFile()) {
                    if (!spectrum.tape.insert(currentFileTape)) {
                        JOptionPane.showMessageDialog(this, bundle.getString("LOAD_TAPE_ERROR"),
                            bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(JSpeccy.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_clearTapeMediaMenuActionPerformed

    private void spec16kHardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spec16kHardwareActionPerformed

        spectrum.selectHardwareModel(MachineTypes.SPECTRUM16K, true);
        modelLabel.setToolTipText(MachineTypes.SPECTRUM16K.getLongModelName());
        modelLabel.setText(MachineTypes.SPECTRUM16K.getShortModelName());
        
        if (settings.getInterface1Settings().isConnectedIF1()) {
            mdrvLabel.setDisabledIcon(mdrOff);
            IF1MediaMenu.setEnabled(true);
        } else {
            mdrvLabel.setDisabledIcon(null);
            IF1MediaMenu.setEnabled(false);
        }
        
        IF2MediaMenu.setEnabled(true);
        
        spectrum.reset();
    }//GEN-LAST:event_spec16kHardwareActionPerformed

    private void specPlus3HardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specPlus3HardwareActionPerformed

        spectrum.selectHardwareModel(MachineTypes.SPECTRUMPLUS3, true);
        modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS3.getLongModelName());
        modelLabel.setText(MachineTypes.SPECTRUMPLUS3.getShortModelName());
        
        IF1MediaMenu.setEnabled(false);
        mdrvLabel.setDisabledIcon(null);
        IF2MediaMenu.setEnabled(false);
        
        spectrum.reset();
    }//GEN-LAST:event_specPlus3HardwareActionPerformed

    private void recordStartTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recordStartTapeMediaMenuActionPerformed

        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        if (!spectrum.tape.isTapeReady()) {
            JOptionPane.showMessageDialog(this,
                bundle.getString("RECORD_START_ERROR"), bundle.getString("RECORD_START_TITLE"),
                JOptionPane.ERROR_MESSAGE); // NOI18N
        } else {
            
            if (spectrum.startRecording()) {
                tapeBrowserButtonRec.setEnabled(false);
                tapeBrowserButtonPlay.setEnabled(false);
                tapeBrowserButtonRew.setEnabled(false);
                tapeBrowserButtonRec.setEnabled(false);
                tapeBrowserButtonEject.setEnabled(false);
                tapeBrowserButtonStop.setEnabled(true);
                recordStartTapeMediaMenu.setEnabled(false);
                recordStopTapeMediaMenu.setEnabled(true);
            } else {
                JOptionPane.showMessageDialog(this,
                bundle.getString("RECORD_START_FORMAT_ERROR"),
                    bundle.getString("RECORD_START_FORMAT_TITLE"),
                JOptionPane.ERROR_MESSAGE); // NOI18N
            }
        }

        playTapeMediaMenu.setSelected(false);
    }//GEN-LAST:event_recordStartTapeMediaMenuActionPerformed

    private void recordStopTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recordStopTapeMediaMenuActionPerformed
        spectrum.stopRecording();
        recordStartTapeMediaMenu.setEnabled(true);
        recordStopTapeMediaMenu.setEnabled(false);
        playTapeMediaMenu.setSelected(true);
        tapeBrowserButtonRec.setEnabled(true);
        tapeBrowserButtonPlay.setEnabled(true);
        tapeBrowserButtonRew.setEnabled(true);
        tapeBrowserButtonEject.setEnabled(true);
        if (currentFileTape.getName().toLowerCase().endsWith(".csw")) {
            clearTapeMediaMenu.setEnabled(false);
            recordStartTapeMediaMenu.setEnabled(false);
            tapeBrowserButtonRec.setEnabled(false);
        }
    }//GEN-LAST:event_recordStopTapeMediaMenuActionPerformed

    private void loadRecentFile(int idx) {
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N

        if (!recentFile[idx].exists()) {
            JOptionPane.showMessageDialog(this, bundle.getString("RECENT_FILE_ERROR"),
                bundle.getString("RECENT_FILE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE); //NOI18N
        } else {
            if (snapshotExtension.accept(recentFile[idx])) {
                boolean paused = spectrum.isPaused();

                if (!paused) {
                    spectrum.stopEmulation();
                }

                currentFileSnapshot = recentFile[idx];
                spectrum.loadSnapshot(currentFileSnapshot);
                updateGuiSelections();

                if (!paused) {
                    spectrum.startEmulation();
                }
            } else {
                spectrum.tape.eject();
                currentFileTape = recentFile[idx];
                
                if (spectrum.tape.insert(currentFileTape)) {
                    tapeFilename.setText(currentFileTape.getName());
                    playTapeMediaMenu.setEnabled(true);
                    clearTapeMediaMenu.setEnabled(currentFileTape.canWrite());
                    rewindTapeMediaMenu.setEnabled(true);
                    recordStartTapeMediaMenu.setEnabled(currentFileTape.canWrite());
                    tapeBrowserButtonRec.setEnabled(currentFileTape.canWrite());
                    tapeBrowserButtonPlay.setEnabled(true);
                    tapeBrowserButtonRew.setEnabled(true);
                    tapeBrowserButtonEject.setEnabled(true);
                    tapeBrowserButtonStop.setEnabled(true);
                    if (currentFileTape.getName().toLowerCase().endsWith(".csw")) {
                        clearTapeMediaMenu.setEnabled(false);
                        recordStartTapeMediaMenu.setEnabled(false);
                        tapeBrowserButtonRec.setEnabled(false);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, bundle.getString("LOAD_TAPE_ERROR"),
                        bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void recentFileMenu0ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentFileMenu0ActionPerformed
        loadRecentFile(0);
    }//GEN-LAST:event_recentFileMenu0ActionPerformed

    private void recentFileMenu1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentFileMenu1ActionPerformed
        loadRecentFile(1);
    }//GEN-LAST:event_recentFileMenu1ActionPerformed

    private void recentFileMenu2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentFileMenu2ActionPerformed
        loadRecentFile(2);
    }//GEN-LAST:event_recentFileMenu2ActionPerformed

    private void recentFileMenu3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentFileMenu3ActionPerformed
        loadRecentFile(3);
    }//GEN-LAST:event_recentFileMenu3ActionPerformed

    private void recentFileMenu4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentFileMenu4ActionPerformed
        loadRecentFile(4);
    }//GEN-LAST:event_recentFileMenu4ActionPerformed

    private void insertIF2RomMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_insertIF2RomMediaMenuActionPerformed
        boolean paused = spectrum.isPaused();

        if( IF2RomDlg == null ) {
            IF2RomDlg = new JFileChooser("/home/jsanchez/Spectrum");
            IF2RomDlg.addChoosableFileFilter(romExtension);
            IF2RomDlg.setFileFilter(romExtension);
            currentDirRom = IF2RomDlg.getCurrentDirectory();
        }
        else
            IF2RomDlg.setCurrentDirectory(currentDirRom);

        if (!paused)
            spectrum.stopEmulation();

        int status = IF2RomDlg.showOpenDialog(getContentPane());
        if (status == JFileChooser.APPROVE_OPTION) {
            currentDirRom = IF2RomDlg.getCurrentDirectory();
            if (spectrum.insertIF2Rom(IF2RomDlg.getSelectedFile())) {
                insertIF2RomMediaMenu.setEnabled(false);
                extractIF2RomMediaMenu.setEnabled(true);
                spectrum.reset();
            } else {
                ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                JOptionPane.showMessageDialog(this, bundle.getString("LOAD_ROM_ERROR"),
                    bundle.getString("LOAD_ROM_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
            }
        }

        if (!paused)
            spectrum.startEmulation();
    }//GEN-LAST:event_insertIF2RomMediaMenuActionPerformed

    private void extractIF2RomMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extractIF2RomMediaMenuActionPerformed
        spectrum.ejectIF2Rom();
        insertIF2RomMediaMenu.setEnabled(true);
        extractIF2RomMediaMenu.setEnabled(false);
        spectrum.reset();
    }//GEN-LAST:event_extractIF2RomMediaMenuActionPerformed

    private void fullerJoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fullerJoystickActionPerformed
        spectrum.setJoystick(Spectrum.Joystick.FULLER);
        fullerJoystick.setSelected(true);
    }//GEN-LAST:event_fullerJoystickActionPerformed

    private void saveSzxCloseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveSzxCloseButtonActionPerformed
        saveSzxTape.setVisible(false);
    }//GEN-LAST:event_saveSzxCloseButtonActionPerformed

    private void linkedRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linkedRadioButtonActionPerformed
        spectrum.setSzxTapeMode(1); // linked tape
    }//GEN-LAST:event_linkedRadioButtonActionPerformed

    private void embeddedRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_embeddedRadioButtonActionPerformed
        spectrum.setSzxTapeMode(2); // embedded tape
    }//GEN-LAST:event_embeddedRadioButtonActionPerformed

    private void ignoreRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ignoreRadioButtonActionPerformed
        spectrum.setSzxTapeMode(0); // ignore tape
    }//GEN-LAST:event_ignoreRadioButtonActionPerformed

    private void loadScreenShotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadScreenShotActionPerformed
        boolean paused = spectrum.isPaused();
        if( loadImageDlg == null ) {
            loadImageDlg = new JFileChooser("/home/jsanchez/Spectrum");
            loadImageDlg.addChoosableFileFilter(screenExtension);
            loadImageDlg.setFileFilter(screenExtension);
            currentDirLoadImage = loadImageDlg.getCurrentDirectory();
        }
        else {
            loadImageDlg.setCurrentDirectory(currentDirLoadImage);
            BasicFileChooserUI chooserUI = (BasicFileChooserUI) loadImageDlg.getUI();
            chooserUI.setFileName("");
        }

        if (!paused)
            spectrum.stopEmulation();

        int status = loadImageDlg.showOpenDialog(getContentPane());
        if( status == JFileChooser.APPROVE_OPTION ) {
            currentDirLoadImage = loadImageDlg.getCurrentDirectory();

            if (!spectrum.loadScreen(loadImageDlg.getSelectedFile())) {
                ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                JOptionPane.showMessageDialog(this, bundle.getString("LOAD_SCREEN_ERROR"),
                    bundle.getString("LOAD_SCREEN_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
            }
        }

        if (!paused)
            spectrum.startEmulation();
    }//GEN-LAST:event_loadScreenShotActionPerformed
    
    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        exitEmulator();
    }//GEN-LAST:event_formWindowClosing

    private void microdrivesIF1MediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_microdrivesIF1MediaMenuActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        if (microdriveDialog == null)
            microdriveDialog = new MicrodriveDialog(spectrum.getInterface1());
        
        microdriveDialog.showDialog(this, bundle.getString("MICRODRIVES_DIALOG_TITLE"));
    }//GEN-LAST:event_microdrivesIF1MediaMenuActionPerformed

    private void tapeBrowserButtonStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tapeBrowserButtonStopActionPerformed
        if (spectrum.tape.isTapePlaying()) {
            spectrum.tape.stop();
            tapeBrowserButtonRec.setEnabled(currentFileTape.canWrite());
            tapeBrowserButtonPlay.setEnabled(true);
            tapeBrowserButtonRew.setEnabled(true);
            tapeBrowserButtonEject.setEnabled(true);
            if (currentFileTape.getName().toLowerCase().endsWith(".csw")) {
                clearTapeMediaMenu.setEnabled(false);
                recordStartTapeMediaMenu.setEnabled(false);
                tapeBrowserButtonRec.setEnabled(false);
            }
            return;
        }

        if (spectrum.tape.isTapeRecording()) {
            spectrum.stopRecording();
            tapeBrowserButtonRec.setEnabled(true);
            tapeBrowserButtonPlay.setEnabled(true);
            tapeBrowserButtonRew.setEnabled(true);
            tapeBrowserButtonEject.setEnabled(true);
            playTapeMediaMenu.setSelected(true);
            recordStartTapeMediaMenu.setEnabled(true);
            recordStopTapeMediaMenu.setEnabled(false);
            if (currentFileTape.getName().toLowerCase().endsWith(".csw")) {
                clearTapeMediaMenu.setEnabled(false);
                recordStartTapeMediaMenu.setEnabled(false);
                tapeBrowserButtonRec.setEnabled(false);
            }
        }
    }//GEN-LAST:event_tapeBrowserButtonStopActionPerformed

    private void tapeBrowserButtonPlayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tapeBrowserButtonPlayActionPerformed
        if (spectrum.tape.isTapeReady()) {
            spectrum.tape.play();
        }
    }//GEN-LAST:event_tapeBrowserButtonPlayActionPerformed

    private void tapeBrowserButtonEjectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tapeBrowserButtonEjectActionPerformed
        if (spectrum.tape.eject()) {
            tapeBrowserButtonRec.setEnabled(false);
            tapeBrowserButtonPlay.setEnabled(false);
            tapeBrowserButtonRew.setEnabled(false);
            tapeBrowserButtonEject.setEnabled(false);
            tapeBrowserButtonStop.setEnabled(false);
            tapeFilename.setText(null);
            playTapeMediaMenu.setEnabled(false);
        }
    }//GEN-LAST:event_tapeBrowserButtonEjectActionPerformed

    private void refreshTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshTapeMediaMenuActionPerformed
        if (currentFileTape != null && currentFileTape.exists() &&
                spectrum.tape.isTapeReady()) {
            spectrum.tape.eject();
            spectrum.tape.insert(currentFileTape);
        }
    }//GEN-LAST:event_refreshTapeMediaMenuActionPerformed

    private void memoryBrowserMachineMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_memoryBrowserMachineMenuActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        if (memoryBrowserDialog == null)
            memoryBrowserDialog = new MemoryBrowserDialog(spectrum.getMemory());
        
        memoryBrowserDialog.showDialog(this, bundle.getString("MEMORY_BROWSER_DIALOG_TITLE"));
    }//GEN-LAST:event_memoryBrowserMachineMenuActionPerformed

    private void pokeMachineMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pokeMachineMenuActionPerformed
        SpinnerNumberModel snmAddress = (SpinnerNumberModel)addressSpinner.getModel();
        SpinnerNumberModel snmValue = (SpinnerNumberModel) valueSpinner.getModel();
        snmValue.setValue(spectrum.getMemory().readByte(snmAddress.getNumber().intValue()) & 0xff);
        pokeDialog.setVisible(true);
        pokeDialog.pack();
    }//GEN-LAST:event_pokeMachineMenuActionPerformed

    private void addressSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_addressSpinnerStateChanged
        SpinnerNumberModel snmAddress = (SpinnerNumberModel)addressSpinner.getModel();
        SpinnerNumberModel snmValue = (SpinnerNumberModel) valueSpinner.getModel();
        snmValue.setValue(spectrum.getMemory().readByte(snmAddress.getNumber().intValue()) & 0xff);
    }//GEN-LAST:event_addressSpinnerStateChanged

    private void valueSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_valueSpinnerStateChanged
        // TODO add your handling code here:
    }//GEN-LAST:event_valueSpinnerStateChanged

    private void pokeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pokeButtonActionPerformed
        SpinnerNumberModel snmAddress = (SpinnerNumberModel)addressSpinner.getModel();
        SpinnerNumberModel snmValue = (SpinnerNumberModel) valueSpinner.getModel();
        int address = snmAddress.getNumber().intValue() & 0xffff;
        spectrum.getMemory().writeByte(address, snmValue.getNumber().byteValue());
        
        if (spectrum.getMemory().isScreenByte(address))
            spectrum.invalidateScreen(false);
    }//GEN-LAST:event_pokeButtonActionPerformed

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        pokeDialog.setVisible(false);
    }//GEN-LAST:event_closeButtonActionPerformed
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new JSpeccy().setVisible(true);
            }
        });
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu IF1MediaMenu;
    private javax.swing.JMenu IF2MediaMenu;
    private javax.swing.JMenuItem aboutHelpMenu;
    private javax.swing.JPanel addrValuePanel;
    private javax.swing.JSpinner addressSpinner;
    private javax.swing.JMenuItem browserTapeMediaMenu;
    private javax.swing.JMenuItem clearTapeMediaMenu;
    private javax.swing.JButton closeButton;
    private javax.swing.JButton closeKeyboardHelper;
    private javax.swing.JPanel closePokeDialogPanel;
    private javax.swing.JMenuItem createTapeMediaMenu;
    private javax.swing.JRadioButtonMenuItem cursorJoystick;
    private javax.swing.JCheckBoxMenuItem doubleSizeOption;
    private javax.swing.JToggleButton doubleSizeToggleButton;
    private javax.swing.JRadioButton embeddedRadioButton;
    private javax.swing.JMenuItem extractIF2RomMediaMenu;
    private javax.swing.JToggleButton fastEmulationToggleButton;
    private javax.swing.JMenu fileMenu;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.Box.Filler filler3;
    private javax.swing.JRadioButtonMenuItem fullerJoystick;
    private javax.swing.JMenuItem hardResetMachineMenu;
    private javax.swing.JButton hardResetSpectrumButton;
    private javax.swing.ButtonGroup hardwareButtonGroup;
    private javax.swing.JMenu hardwareMachineMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JRadioButton ignoreRadioButton;
    private javax.swing.JMenuItem imageHelpMenu;
    private javax.swing.JMenuItem insertIF2RomMediaMenu;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator10;
    private javax.swing.JToolBar.Separator jSeparator11;
    private javax.swing.JToolBar.Separator jSeparator12;
    private javax.swing.JPopupMenu.Separator jSeparator13;
    private javax.swing.JPopupMenu.Separator jSeparator14;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JPopupMenu.Separator jSeparator5;
    private javax.swing.JPopupMenu.Separator jSeparator6;
    private javax.swing.JPopupMenu.Separator jSeparator7;
    private javax.swing.JPopupMenu.Separator jSeparator8;
    private javax.swing.JPopupMenu.Separator jSeparator9;
    private javax.swing.ButtonGroup joystickButtonGroup;
    private javax.swing.JMenu joystickOptionMenu;
    private javax.swing.JRadioButtonMenuItem kempstonJoystick;
    private javax.swing.JDialog keyboardHelper;
    private javax.swing.JLabel keyboardImage;
    private javax.swing.JRadioButton linkedRadioButton;
    private javax.swing.JMenuItem loadScreenShot;
    private javax.swing.JMenu machineMenu;
    private javax.swing.JLabel mdrvLabel;
    private javax.swing.JMenu mediaMenu;
    private javax.swing.JMenuItem memoryBrowserMachineMenu;
    private javax.swing.JMenuItem microdrivesIF1MediaMenu;
    private javax.swing.JLabel modelLabel;
    private javax.swing.JMenuItem nmiMachineMenu;
    private javax.swing.JRadioButtonMenuItem noneJoystick;
    private javax.swing.JMenuItem openSnapshot;
    private javax.swing.JButton openSnapshotButton;
    private javax.swing.JMenuItem openTapeMediaMenu;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JCheckBoxMenuItem pauseMachineMenu;
    private javax.swing.JToggleButton pauseToggleButton;
    private javax.swing.JMenuItem playTapeMediaMenu;
    private javax.swing.JLabel pokeAddress;
    private javax.swing.JButton pokeButton;
    private javax.swing.JDialog pokeDialog;
    private javax.swing.JMenuItem pokeMachineMenu;
    private javax.swing.JLabel pokeValue;
    private javax.swing.JMenuItem recentFileMenu0;
    private javax.swing.JMenuItem recentFileMenu1;
    private javax.swing.JMenuItem recentFileMenu2;
    private javax.swing.JMenuItem recentFileMenu3;
    private javax.swing.JMenuItem recentFileMenu4;
    private javax.swing.JMenu recentFilesMenu;
    private javax.swing.JMenuItem recordStartTapeMediaMenu;
    private javax.swing.JMenuItem recordStopTapeMediaMenu;
    private javax.swing.JMenuItem refreshTapeMediaMenu;
    private javax.swing.JMenuItem resetMachineMenu;
    private javax.swing.JButton resetSpectrumButton;
    private javax.swing.JMenuItem rewindTapeMediaMenu;
    private javax.swing.JMenuItem saveScreenShot;
    private javax.swing.JMenuItem saveSnapshot;
    private javax.swing.ButtonGroup saveSzxButtonGroup;
    private javax.swing.JPanel saveSzxChoosePanel;
    private javax.swing.JButton saveSzxCloseButton;
    private javax.swing.JDialog saveSzxTape;
    private javax.swing.JMenuItem settingsOptionsMenu;
    private javax.swing.JCheckBoxMenuItem silenceMachineMenu;
    private javax.swing.JToggleButton silenceSoundToggleButton;
    private javax.swing.JRadioButtonMenuItem sinclair1Joystick;
    private javax.swing.JRadioButtonMenuItem sinclair2Joystick;
    private javax.swing.JRadioButtonMenuItem spec128kHardware;
    private javax.swing.JRadioButtonMenuItem spec16kHardware;
    private javax.swing.JRadioButtonMenuItem spec48kHardware;
    private javax.swing.JRadioButtonMenuItem specPlus2AHardware;
    private javax.swing.JRadioButtonMenuItem specPlus2Hardware;
    private javax.swing.JRadioButtonMenuItem specPlus3Hardware;
    private javax.swing.JLabel speedLabel;
    private javax.swing.JPanel statusPanel;
    private javax.swing.JButton tapeBrowserButtonEject;
    private javax.swing.JButton tapeBrowserButtonPlay;
    private javax.swing.JButton tapeBrowserButtonRec;
    private javax.swing.JButton tapeBrowserButtonRew;
    private javax.swing.JButton tapeBrowserButtonStop;
    private javax.swing.JDialog tapeBrowserDialog;
    private javax.swing.JToolBar tapeBrowserToolbar;
    private javax.swing.JTable tapeCatalog;
    private javax.swing.JLabel tapeFilename;
    private javax.swing.JLabel tapeFilenameLabel;
    private javax.swing.JLabel tapeLabel;
    private javax.swing.JMenu tapeMediaMenu;
    private javax.swing.JLabel tapeMessageInfo;
    private javax.swing.JMenuItem thisIsTheEndMyFriend;
    private javax.swing.JToolBar toolbarMenu;
    private javax.swing.JSpinner valueSpinner;
    // End of variables declaration//GEN-END:variables
    
}
