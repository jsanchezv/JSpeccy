/*
 * JSpeccy.java
 *
 * Created on 21 de enero de 2008, 14:27
 */

package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ListSelectionEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicFileChooserUI;
import javax.swing.plaf.metal.MetalLookAndFeel;

import configuration.JSpeccySettings;
import jakarta.xml.bind.JAXB;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jspeccy.BundlePropertyResolver;
import jspeccy.JSpeccyCommand;
import lombok.extern.slf4j.Slf4j;
import machine.Interface1DriveListener;
import machine.Keyboard.JoystickModel;
import machine.MachineTypes;
import machine.Spectrum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import snapshots.SnapshotException;
import snapshots.SnapshotFactory;
import snapshots.SnapshotFile;
import snapshots.SnapshotSZX;
import snapshots.SpectrumState;
import utilities.Tape;
import utilities.Tape.TapeState;
import utilities.TapeStateListener;

/**
 *
 * @author  jsanchez
 */
@Slf4j
@Component
public class JSpeccy extends javax.swing.JFrame {
    private Spectrum spectrum;
    private Tape tape;
    private JSpeccyScreen jscr;
    private File currentFileSnapshot, currentDirSaveSnapshot,
                 currentFileTape, currentDirLoadImage, currentDirSaveImage, currentDirRom;
    private JFileChooser openSnapshotDlg, openTapeDlg;
    private JFileChooser loadImageDlg, saveImageDlg, IF2RomDlg;
    private RecentFilesMgr recentFilesMgr;
    private ListSelectionModel lsm;
    private JSpeccySettings settings;
    private SettingsDialog settingsDialog;
    private MicrodriveDialog microdriveDialog;
    private MemoryBrowserDialog memoryBrowserDialog;
    private LoadSaveMemoryDialog loadSaveMemoryDialog;
    private FileNameExtensionFilter allSnapTapeExtension, snapshotExtension, saveSnapshotExtension,
            tapeExtension, createTapeExtension, imageExtension, screenExtension, romExtension;
    private SpectrumState memorySnapshot;
    private JSpeccyCommand clo;

    Icon mdrOn = new ImageIcon(getClass().getResource("/icons/microdrive_on.png"));
    Icon mdrOff = new ImageIcon(getClass().getResource("/icons/microdrive_off.png"));
    Icon tapeStopped = new ImageIcon(getClass().getResource("/icons/Akai24x24.png"));
    Icon tapePlaying = new ImageIcon(getClass().getResource("/icons/Akai24x24-playing.png"));
    Icon tapeRecording = new ImageIcon(getClass().getResource("/icons/Akai24x24-recording.png"));

    @Autowired
    private Environment environment;

    @Autowired
    BundlePropertyResolver bundlePropertyResolver;

    private final TransferHandler handler = new TransferHandler() {
        @Override
        public boolean canImport(TransferHandler.TransferSupport support) {
            if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return false;
            }

            // When spectrum is in acceleratedLoading method, anything can be dropped
            if (spectrum.isPaused() && tape.isTapePlaying()) {
                return false;
            }

            boolean copySupported = (COPY & support.getSourceDropActions()) == COPY;

            if (!copySupported) {
                return false;
            }

            support.setDropAction(COPY);

            return true;
        }

        @Override
        public boolean importData(TransferHandler.TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            Transferable transfer = support.getTransferable();

            try {
                java.util.List<File> list =
                        (java.util.List<File>) transfer.getTransferData(DataFlavor.javaFileListFlavor);

                if (list.size() != 1) {
                    return false;
                }

                for (File file : list) {
                    if (file.isDirectory())
                        return false;

                    log.debug("File dropped: {}", file.getAbsolutePath());
                    log.debug("# selected files: {}", list.size());
                    if (snapshotExtension.accept(file)) {
                        recentFilesMgr.addRecentFile(file);
                        if (tape.isTapeRunning()) {
                            tape.stop();
                        }
                        stopEmulation();
                        try {
                            SnapshotFile snap = SnapshotFactory.getSnapshot(file);
                            SpectrumState snapState = snap.load(file);
                            if (snap instanceof SnapshotSZX snapSZX) {
                                if (snapSZX.isTapeEmbedded()) {
                                    tape.eject();
                                    tape.insertEmbeddedTape(snapSZX.getTapeName(), snapSZX.getTapeExtension(),
                                            snapSZX.getTapeData(), snapSZX.getTapeBlock());
                                }

                                if (snapSZX.isTapeLinked()) {
                                    File tapeLink = new File(snapSZX.getTapeName());

                                    if (tapeLink.exists()) {
                                        tape.eject();
                                        tape.insert(tapeLink);
                                        tape.setSelectedBlock(snapSZX.getTapeBlock());
                                    }
                                }
                            }

                            spectrum.setSpectrumState(snapState);
                            startEmulation();
                            break;
                        } catch (SnapshotException excpt) {
                            JOptionPane.showMessageDialog(getContentPane(),
                                    ResourceBundle.getBundle("gui/Bundle").getString(excpt.getMessage()),
                                    ResourceBundle.getBundle("gui/Bundle").getString(
                                    "SNAPSHOT_LOAD_ERROR"), JOptionPane.ERROR_MESSAGE);
                            startEmulation();
                        }
                    }

                    if (tapeExtension.accept(file)) {
                        // when a IF2 ROM is loaded, is needed to extract this rom to allow
                        // autoLoadTape work correctly.
                        if (spectrum.getSpectrumModel().codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3 &&
                                settings.getTapeSettings().isAutoLoadTape() &&
                                extractIF2RomMediaMenu.isEnabled()) {
                            spectrum.ejectIF2Rom();
                            insertIF2RomMediaMenu.setEnabled(true);
                            extractIF2RomMediaMenu.setEnabled(false);
                        }

                        if (tape.isTapeRunning()) {
                            tape.stop();
                        }

                        tape.eject();

                        if (tape.insert(file)) {
                            recentFilesMgr.addRecentFile(file);
                            if (settings.getTapeSettings().isAutoLoadTape()) {
                                spectrum.autoLoadTape();
                            }
                            break;
                        } else {
                            ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                            JOptionPane.showMessageDialog(getContentPane(), bundle.getString("LOAD_TAPE_ERROR"),
                                    bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                        }
                    }

                    if (romExtension.accept(file)) {
                        if (tape.isTapeRunning()) {
                            tape.stop();
                        }
                        stopEmulation();

                        if (spectrum.getSpectrumModel() != MachineTypes.SPECTRUM48K) {
                            spectrum.selectHardwareModel(MachineTypes.SPECTRUM48K);
                        }

                        spectrum.reset();

                        if (spectrum.insertIF2Rom(file)) {
                            insertIF2RomMediaMenu.setEnabled(false);
                            extractIF2RomMediaMenu.setEnabled(true);
                        } else {
                            ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                            JOptionPane.showMessageDialog(getContentPane(), bundle.getString("LOAD_ROM_ERROR"),
                                    bundle.getString("LOAD_ROM_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                        }

                        startEmulation();
                        break;
                    }

                    if (screenExtension.accept(file)) {
                        if (!spectrum.loadScreen(file)) {
                            ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                            JOptionPane.showMessageDialog(getContentPane(), bundle.getString("LOAD_SCREEN_ERROR"),
                                    bundle.getString("LOAD_SCREEN_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                        }
                        spectrum.invalidateScreen(true);
                        break;
                    }

                    if (file.getName().toLowerCase().endsWith(".bin")) {
                        if (loadSaveMemoryDialog == null) {
                            loadSaveMemoryDialog = new LoadSaveMemoryDialog(spectrum.getMemory());
                        }

                        stopEmulation();
                        loadSaveMemoryDialog.showLoadDialog(getContentPane(), file);
                        startEmulation();
                        break;
                    }
                }
            } catch (UnsupportedFlavorException | IOException e) {
                return false;
            }

            return true;
        }
    };


    public void run(final JSpeccyCommand command) {

//        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
//         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
//         */
//        try {
//            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
//                if ("Nimbus".equals(info.getName())) {
//                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
//                    break;
//                }
//            }
//        } catch (ClassNotFoundException | InstantiationException | UnsupportedLookAndFeelException | IllegalAccessException ex) {
//            log.error("An error was encountered whilst attempting so set the Nimbus look and feel", ex);
//        }

        if (UIManager.getLookAndFeel().getName().equals("Metal")) {
            try {
                // turn off bold fonts
                UIManager.put("swing.boldMetal", Boolean.FALSE);
                // re-install the Metal Look and Feel
                UIManager.setLookAndFeel(new MetalLookAndFeel());
                // Update the ComponentUIs for all Components. This needs to be invoked for all windows.
                SwingUtilities.updateComponentTreeUI(this);
            } catch (UnsupportedLookAndFeelException ex) {
                log.error("An error was encountered whilst attempting so set the Metal look and feel", ex);
            }
        }

        readSettingsFile();
        command.copyArgumentsToSettings(settings);

        initComponents();
        setTransferHandler(handler);
        initEmulator();

        if (command.isIf1().isPresent() && command.getIf1mdv() != null) {
            spectrum.getInterface1().insertFile(0, command.getIf1mdv());
        }

        if (command.getProgramFile() != null) {
            File file = new File(command.getProgramFile());
            if (snapshotExtension.accept(file)) {
                recentFilesMgr.addRecentFile(file);
                try {
                    SnapshotFile snap = SnapshotFactory.getSnapshot(file);
                    SpectrumState snapState = snap.load(file);
                    if (snap instanceof SnapshotSZX snapSZX) {
                        if (snapSZX.isTapeEmbedded()) {
                            tape.eject();
                            tape.insertEmbeddedTape(snapSZX.getTapeName(), snapSZX.getTapeExtension(),
                                    snapSZX.getTapeData(), snapSZX.getTapeBlock());
                        }

                        if (snapSZX.isTapeLinked()) {
                            File tapeLink = new File(snapSZX.getTapeName());

                            if (tapeLink.exists()) {
                                tape.eject();
                                tape.insert(tapeLink);
                                tape.setSelectedBlock(snapSZX.getTapeBlock());
                            }
                        }
                    }

                    spectrum.setSpectrumState(snapState);
                } catch (SnapshotException exception) {
                    JOptionPane.showMessageDialog(getContentPane(),
                            ResourceBundle.getBundle("gui/Bundle").getString(exception.getMessage()),
                            ResourceBundle.getBundle("gui/Bundle").getString(
                                    "SNAPSHOT_LOAD_ERROR"), JOptionPane.ERROR_MESSAGE);
                }
            }

            if (tapeExtension.accept(file)) {
                if (tape.insert(file)) {
                    recentFilesMgr.addRecentFile(file);
                    if (settings.getTapeSettings().isAutoLoadTape()) {
                        spectrum.autoLoadTape();
                    }
                } else {
                    ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                    JOptionPane.showMessageDialog(getContentPane(), bundle.getString("LOAD_TAPE_ERROR"),
                            bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                }
            }

            if (romExtension.accept(file)) {
                if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                    spectrum.selectHardwareModel(MachineTypes.SPECTRUM48K);
                }

                if (spectrum.insertIF2Rom(file)) {
                    insertIF2RomMediaMenu.setEnabled(false);
                    extractIF2RomMediaMenu.setEnabled(true);
                    spectrum.reset();
                } else {
                    ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                    JOptionPane.showMessageDialog(getContentPane(), bundle.getString("LOAD_ROM_ERROR"),
                            bundle.getString("LOAD_ROM_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        startEmulation();
    }

    private void verifyConfigFile(boolean deleteFile) {
        File file = new File(System.getProperty("user.home") + "/JSpeccy.xml");
        if (file.exists() && !deleteFile) {
            return;
        }

        if (deleteFile && (!file.delete())) {
            log.error("Unable to delete the corrupted JSpeccy.xml file");
        }

        // Si el archivo de configuración no existe, lo crea de nuevo en el
        // directorio actual copiándolo del bueno que hay siempre en el .jar
        InputStream input = null;
        BufferedOutputStream output = null;
        try {
            input = Spectrum.class.getResourceAsStream("/schema/JSpeccy.xml");
            output = new BufferedOutputStream (new FileOutputStream(System.getProperty("user.home") + "/JSpeccy.xml"));

            byte[] fileConf = new byte[input.available()];
            input.read(fileConf);
            output.write(fileConf, 0, fileConf.length);
        } catch (IOException ioException) {
            log.error("Unable to find configuration file", ioException);
        } finally {
            try {
                if (input != null) {
                    input.close();
                }

                if (output != null) {
                    output.close();
                }
            } catch (IOException ioException) {
                log.error("Unable to close configuration file", ioException);
            }
        }
    }

    private void readSettingsFile() {
        verifyConfigFile(false);

        boolean wasRead = true;
        try {
            // create a JAXBContext capable of handling classes generated into
            // the configuration package
            JAXBContext jc = JAXBContext.newInstance("configuration");

            // create an Unmarshaller
            Unmarshaller unmsh = jc.createUnmarshaller();

            // unmarshal a po instance document into a tree of Java content
            // objects composed of classes from the configuration package.
            settings = (JSpeccySettings) unmsh.unmarshal(new FileInputStream(System.getProperty("user.home") + "/JSpeccy.xml"));
        } catch (JAXBException jexcpt) {
            log.error("Something went very badly during unmarshalling!");
            wasRead = false;
        } catch (FileNotFoundException ioexcpt) {
            log.warn("Can't open the JSpeccy.xml configuration file", ioexcpt);
        }

        if (wasRead)
            return;

        log.info("Trying to create a new JSpeccy.xml file for you");

        verifyConfigFile(true);
        try {
            // create a JAXBContext capable of handling classes generated into
            // the configuration package
            JAXBContext jc = JAXBContext.newInstance("configuration");

            // create an Unmarshaller
            Unmarshaller unmsh = jc.createUnmarshaller();

            // unmarshal a po instance document into a tree of Java content
            // objects composed of classes from the configuration package.
            settings = (JSpeccySettings) unmsh.unmarshal(new FileInputStream(System.getProperty("user.home") + "/JSpeccy.xml"));
        } catch (JAXBException jexcpt) {
            log.error("Something went very very badly with unmarshalling!", jexcpt);
        } catch (FileNotFoundException ioexcpt) {
            log.error("Can't open the JSpeccy.xml configuration file anyway", ioexcpt);
            System.exit(0);
        }

    }

    private void saveRecentFiles() {
        JSpeccySettings toSave = null;
        if (!settings.getEmulatorSettings().isAutosaveConfigOnExit()) {
            try {
                // create a JAXBContext capable of handling classes generated into
                // the configuration package
                JAXBContext jc = JAXBContext.newInstance("configuration");

                // create an Unmarshaller
                Unmarshaller unmsh = jc.createUnmarshaller();

                // unmarshal a po instance document into a tree of Java content
                // objects composed of classes from the configuration package.
                toSave = (JSpeccySettings) unmsh.unmarshal(new FileInputStream(System.getProperty("user.home") + "/JSpeccy.xml"));
            } catch (JAXBException jexcpt) {
                log.error("Something during unmarshalling go very bad!", jexcpt);
            } catch (FileNotFoundException ioexcpt) {
                log.error("Can't open the JSpeccy.xml configuration file", ioexcpt);
            }
        }

        if (toSave == null)
            return;

        toSave.getRecentFilesSettings().getRecentFile().clear();
        for (int idx = 0; idx < recentFilesMgr.size(); idx++) {
            toSave.getRecentFilesSettings().getRecentFile().add(recentFilesMgr.getAbsolutePath(idx));
        }

        if (currentFileSnapshot != null)
            toSave.getRecentFilesSettings().setLastSnapshotDir(currentFileSnapshot.getParent());

        if (currentFileTape != null)
            toSave.getRecentFilesSettings().setLastTapeDir(currentFileTape.getParent());

        if (clo == null) {
            int filterM = 0;
            int zoomM = 0;

            if (palTvFilter.isSelected()) {
                filterM = 1;
            }

            if (rgbFilter.isSelected()) {
                filterM = 2;
            }

            if (bilinearZoom.isSelected()) {
                zoomM = 1;
            }

            if (bicubicZoom.isSelected()) {
                zoomM = 2;
            }

            toSave.getSpectrumSettings().setZoomMethod(zoomM);
            toSave.getSpectrumSettings().setFilterMethod(filterM);
            toSave.getSpectrumSettings().setScanLines(scanlinesFilter.isSelected());
            toSave.getSpectrumSettings().setBorderSize(jscr.getBorderMode());
            toSave.getSpectrumSettings().setZoomed(jscr.isZoomed());
        }

        try {
            BufferedOutputStream fOut =
                new BufferedOutputStream(new FileOutputStream(System.getProperty("user.home") + "/JSpeccy.xml"));
            // create a Marshaller and marshal to conf. file
            JAXB.marshal(toSave, fOut);
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

        spectrum = new Spectrum(settings);

        spectrum.selectHardwareModel(settings.getSpectrumSettings().getDefaultModel());

        spectrum.setJoystick(settings.getKeyboardJoystickSettings().getJoystickModel());

        spectrum.setBorderMode(settings.getSpectrumSettings().getBorderSize());

        spectrum.loadConfigVars();

        tape = new Tape(settings.getTapeSettings());
        spectrum.setTape(tape);
        jscr = new JSpeccyScreen();
        spectrum.setScreenComponent(jscr);
        jscr.setTvImage(spectrum.getTvImage());
        jscr.setBorderMode(settings.getSpectrumSettings().getBorderSize());
        spectrum.setSpeedLabel(speedLabel);
        tapeCatalog.setModel(tape.getTapeTableModel());
        tapeCatalog.getColumnModel().getColumn(0).setMaxWidth(150);
        lsm = tapeCatalog.getSelectionModel();
        lsm.addListSelectionListener((ListSelectionEvent event) -> {
            if (!event.getValueIsAdjusting() && event.getLastIndex() != -1) {
                tape.setSelectedBlock(lsm.getLeadSelectionIndex());
            }
        });

        tape.addTapeChangedListener(new TapeChangedListener());
        tape.addTapeBlockListener((int block) -> lsm.setSelectionInterval(block, block));

        spectrum.getInterface1().addInterface1DriveListener(new Interface1DriveListener() {

            @Override
            public void driveSelected(int unit) {
                if (unit == 0) {
                    mdrvLabel.setIcon(mdrOff);
                    mdrvLabel.setToolTipText(
                        ResourceBundle.getBundle("gui/Bundle").getString("MICRODRIVES_STOPPED"));
                } else {
                    mdrvLabel.setIcon(mdrOn);
                    mdrvLabel.setToolTipText(String.format(
                        ResourceBundle.getBundle("gui/Bundle").getString("MICRODRIVE_RUNNING"), unit));
                }
            }

            @Override
            public void driveModified(int drive) {
                // Nothing to do
            }
        });

        getContentPane().add(jscr, BorderLayout.CENTER);
        pack();
        addKeyListener(spectrum.getKeyboard());

        if (settings.getSpectrumSettings().isMutedSound()) {
            silenceMachineMenu.setSelected(true);
            silenceSoundToggleButton.setSelected(true);
        }

        int zoom = settings.getSpectrumSettings().getZoom();
        if (zoom < 2 || zoom > 4) {
            zoom = 2;
            settings.getSpectrumSettings().setZoom(zoom);
        }

        if (settings.getSpectrumSettings().isZoomed()) {
            jscr.setZoom(zoom);
            doubleSizeOption.setSelected(true);
            doubleSizeToggleButton.setSelected(true);
        } else {
            jscr.setZoom(1);
            doubleSizeOption.setSelected(false);
            doubleSizeToggleButton.setSelected(false);
        }

        pack();

        recentFilesMgr = new RecentFilesMgr(settings.getRecentFilesSettings(), recentFilesMenu);

        settingsDialog = new SettingsDialog(settings);

        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        allSnapTapeExtension = new FileNameExtensionFilter(
                bundle.getString("SNAPSHOT_TAPE_TYPE"),
                "sna", "z80", "szx", "sp", "tap", "tzx", "csw");
        snapshotExtension = new FileNameExtensionFilter(
                bundle.getString("SNAPSHOT_TYPE"), "sna", "z80", "szx", "sp");
        saveSnapshotExtension = new FileNameExtensionFilter(
                bundle.getString("SAVE_SNAPSHOT_TYPE"), "sna", "z80", "szx");
        tapeExtension = new FileNameExtensionFilter(
                bundle.getString("TAPE_TYPE"), "tap", "tzx", "csw");
        createTapeExtension = new FileNameExtensionFilter(
                bundle.getString("SAVE_TAPE_TYPE"), "tap", "tzx");
        imageExtension = new FileNameExtensionFilter(
                bundle.getString("IMAGE_TYPE"), "scr", "png");
        screenExtension = new FileNameExtensionFilter(
                bundle.getString("SCR_TYPE"), "scr");
        romExtension  = new FileNameExtensionFilter(
                bundle.getString("ROM_TYPE"), "rom");

        if (settings.getSpectrumSettings().isHibernateMode()) {
            File autoload = new File(System.getProperty("user.home") + "/JSpeccy.szx");
            if (autoload.exists()) {
                SnapshotSZX snapSZX = new SnapshotSZX();
                try {
                    spectrum.setSpectrumState(snapSZX.load(autoload));
                    if (snapSZX.isTapeLinked()) {
                        File tapeLink = new File(snapSZX.getTapeName());

                        if (tapeLink.exists()) {
                            tape.eject();
                            tape.insert(tapeLink);
                            tape.setSelectedBlock(snapSZX.getTapeBlock());
                        }
                    }
                } catch (SnapshotException ex) {
                    JOptionPane.showMessageDialog(this,
                        ResourceBundle.getBundle("gui/Bundle").getString("HIBERNATE_IMAGE_ERROR"),
                        ResourceBundle.getBundle("gui/Bundle").getString(
                        "HIBERNATE_LOAD_ERROR"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        switch (settings.getSpectrumSettings().getZoomMethod()) {
            case 1 -> { // Bilineal
                jscr.setInterpolationMethod(RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                bilinearZoom.setSelected(true);
            }
            case 2 -> { // Bicubic
                jscr.setInterpolationMethod(RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                bicubicZoom.setSelected(true);
            }
            default -> {
                jscr.setInterpolationMethod(RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                standardZoom.setSelected(true);
            }
        }

        jscr.setScanlinesFilter(settings.getSpectrumSettings().isScanLines());
        scanlinesFilter.setSelected(settings.getSpectrumSettings().isScanLines());

        switch (settings.getSpectrumSettings().getFilterMethod()) {
            case 1 -> { // PAL TV
                jscr.setPalFilter(true);
                palTvFilter.setSelected(true);
            }
            case 2 -> { // RGB
                jscr.setRgbFilter(true);
                rgbFilter.setSelected(true);
                scanlinesFilter.setEnabled(false);
            }
            default -> {
                jscr.setAnyFilter(settings.getSpectrumSettings().isScanLines());
                noneFilter.setSelected(true);
            }
        }

        updateGuiSelections();

        new Thread(spectrum, "SpectrumThread").start();
    }

    private void exitEmulator() {
        String msg;
        int dialogType;

        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N

        stopEmulation();
        if (spectrum.getInterface1().hasDirtyCartridges()) {
            msg = bundle.getString("DIRTY_CARTRIDGES_WARNING");
            dialogType = JOptionPane.WARNING_MESSAGE;
        } else {
            msg = bundle.getString("ARE_YOU_SURE_QUESTION");
            dialogType = JOptionPane.QUESTION_MESSAGE;
        }

        if (settings.getEmulatorSettings().isConfirmActions()
                || dialogType == JOptionPane.WARNING_MESSAGE) {
            int ret = JOptionPane.showConfirmDialog(getContentPane(), msg,
                    bundle.getString("QUIT_JSPECCY"),
                    JOptionPane.OK_CANCEL_OPTION, dialogType); // NOI18N

            if (ret == JOptionPane.CANCEL_OPTION) {
                startEmulation();
                return;
            }
        }

        if (tape.isTapeRunning()) {
            tape.stop();
        }

        if (settings.getSpectrumSettings().isHibernateMode()) {
            SnapshotSZX snapSZX = new SnapshotSZX();
            if (tape.getTapeFilename() != null) {
                snapSZX.setTapeLinked(true);
                snapSZX.setTapeName(tape.getTapeFilename().getAbsolutePath());
                snapSZX.setTapeBlock(tape.getSelectedBlock());
            }

            try {
                snapSZX.save(new File(System.getProperty("user.home") + "/JSpeccy.szx"), spectrum.getSpectrumState());
            } catch (SnapshotException ex) {
                JOptionPane.showMessageDialog(this,
                        ResourceBundle.getBundle("gui/Bundle").getString("HIBERNATE_SAVE_ERROR"),
                        ResourceBundle.getBundle("gui/Bundle").getString(
                        "SNAPSHOT_SAVE_ERROR"), JOptionPane.ERROR_MESSAGE);
            }
        }

        saveRecentFiles(); // debe ser lo último que se hace antes de salir!!!
        dispose();
        System.exit(0);
    }

    private void updateGuiSelections() {

        if (spectrum.getSpectrumModel().codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
            if (settings.getInterface1Settings().isConnectedIF1()) {
                IF1MediaMenu.setEnabled(true);
                mdrvLabel.setEnabled(true);
                mdrvLabel.setIcon(mdrOff);
                mdrvLabel.setToolTipText(
                ResourceBundle.getBundle("gui/Bundle").getString("MICRODRIVES_STOPPED"));
            } else {
                IF1MediaMenu.setEnabled(false);
                mdrvLabel.setEnabled(false);
                mdrvLabel.setToolTipText(null);
            }

            IF2MediaMenu.setEnabled(true);
            insertIF2RomMediaMenu.setEnabled(!spectrum.isIF2RomInserted());
            extractIF2RomMediaMenu.setEnabled(spectrum.isIF2RomInserted());
        } else {
            IF1MediaMenu.setEnabled(false);
            IF2MediaMenu.setEnabled(false);
            mdrvLabel.setEnabled(false);
            mdrvLabel.setToolTipText(null);
        }

        switch (spectrum.getSpectrumModel()) {
            case SPECTRUM16K -> spec16kHardware.setSelected(true);
            case SPECTRUM48K -> spec48kHardware.setSelected(true);
            case SPECTRUM128K -> spec128kHardware.setSelected(true);
            case SPECTRUMPLUS2 -> specPlus2Hardware.setSelected(true);
            case SPECTRUMPLUS2A -> {
                specPlus2AHardware.setSelected(true);
                IF2MediaMenu.setEnabled(false);
            }
            case SPECTRUMPLUS3 -> {
                specPlus3Hardware.setSelected(true);
                IF2MediaMenu.setEnabled(false);
            }
        }

        modelLabel.setToolTipText(spectrum.getSpectrumModel().getLongModelName());
        modelLabel.setText(spectrum.getSpectrumModel().getShortModelName());

        switch (spectrum.getJoystick()) {
            case KEMPSTON -> kempstonJoystick.setSelected(true);
            case SINCLAIR1 -> sinclair1Joystick.setSelected(true);
            case SINCLAIR2 -> sinclair2Joystick.setSelected(true);
            case CURSOR -> cursorJoystick.setSelected(true);
            case FULLER -> fullerJoystick.setSelected(true);
            default -> noneJoystick.setSelected(true);
        }

        if (settings.getSpectrumSettings().isULAplus()) {
            if (jscr.isPalFilter()) {
                noneFilter.setSelected(true);
                jscr.setAnyFilter(false);
                scanlinesFilter.setEnabled(true);
                jscr.setScanlinesFilter(scanlinesFilter.isSelected());
                jscr.repaint();
            }
            palTvFilter.setEnabled(false);
        } else {
            palTvFilter.setEnabled(true);
        }

        switch (jscr.getBorderMode()) {
            case 0 -> noBorder.setSelected(true);
            case 2 -> fullBorder.setSelected(true);
            case 3 -> hugeBorder.setSelected(true);
            default -> standardBorder.setSelected(true);
        }
    }

    private void startEmulation() {

        if (pauseToggleButton.isSelected()) {
            return;
        }

        speedLabel.setForeground(Color.black);
        updateGuiSelections();
        spectrum.startEmulation();
    }

    private void stopEmulation() {

        if (spectrum.isPaused()) {
            return;
        }

        spectrum.stopEmulation();

        speedLabel.setForeground(Color.red);
        speedLabel.setText("STOP");

        updateGuiSelections();
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
        filler4 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        saveSzxCloseButton = new javax.swing.JButton();
        filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(25, 25), new java.awt.Dimension(25, 25), new java.awt.Dimension(25, 25));
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
        closePokeDialogButton = new javax.swing.JButton();
        filtersButtonGroup = new javax.swing.ButtonGroup();
        zoomMethodButtonGroup = new javax.swing.ButtonGroup();
        borderSizeButtonGroup = new javax.swing.ButtonGroup();
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
        mainMenuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        openSnapshot = new javax.swing.JMenuItem();
        saveSnapshot = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        loadMemorySnapshot = new javax.swing.JMenuItem();
        saveMemorySnapshot = new javax.swing.JMenuItem();
        jSeparator15 = new javax.swing.JPopupMenu.Separator();
        loadBinaryFile = new javax.swing.JMenuItem();
        saveBinaryFile = new javax.swing.JMenuItem();
        jSeparator16 = new javax.swing.JPopupMenu.Separator();
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
        zoomMethodOptionMenu = new javax.swing.JMenu();
        standardZoom = new javax.swing.JRadioButtonMenuItem();
        bilinearZoom = new javax.swing.JRadioButtonMenuItem();
        bicubicZoom = new javax.swing.JRadioButtonMenuItem();
        borderSizeOptionMenu = new javax.swing.JMenu();
        noBorder = new javax.swing.JRadioButtonMenuItem();
        standardBorder = new javax.swing.JRadioButtonMenuItem();
        fullBorder = new javax.swing.JRadioButtonMenuItem();
        hugeBorder = new javax.swing.JRadioButtonMenuItem();
        filtersOptionMenu = new javax.swing.JMenu();
        noneFilter = new javax.swing.JRadioButtonMenuItem();
        palTvFilter = new javax.swing.JRadioButtonMenuItem();
        rgbFilter = new javax.swing.JRadioButtonMenuItem();
        jSeparator20 = new javax.swing.JPopupMenu.Separator();
        scanlinesFilter = new javax.swing.JCheckBoxMenuItem();
        jSeparator19 = new javax.swing.JPopupMenu.Separator();
        joystickOptionMenu = new javax.swing.JMenu();
        noneJoystick = new javax.swing.JRadioButtonMenuItem();
        kempstonJoystick = new javax.swing.JRadioButtonMenuItem();
        sinclair1Joystick = new javax.swing.JRadioButtonMenuItem();
        sinclair2Joystick = new javax.swing.JRadioButtonMenuItem();
        cursorJoystick = new javax.swing.JRadioButtonMenuItem();
        fullerJoystick = new javax.swing.JRadioButtonMenuItem();
        jSeparator18 = new javax.swing.JPopupMenu.Separator();
        settingsOptionsMenu = new javax.swing.JMenuItem();
        machineMenu = new javax.swing.JMenu();
        pauseMachineMenu = new javax.swing.JCheckBoxMenuItem();
        silenceMachineMenu = new javax.swing.JCheckBoxMenuItem();
        jSeparator17 = new javax.swing.JPopupMenu.Separator();
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
        ejectTapeMediaMenu = new javax.swing.JMenuItem();
        jSeparator5 = new javax.swing.JPopupMenu.Separator();
        createTapeMediaMenu = new javax.swing.JMenuItem();
        clearTapeMediaMenu = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JPopupMenu.Separator();
        recordStartTapeMediaMenu = new javax.swing.JMenuItem();
        recordStopTapeMediaMenu = new javax.swing.JMenuItem();
        jSeparator13 = new javax.swing.JPopupMenu.Separator();
        reloadTapeMediaMenu = new javax.swing.JMenuItem();
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
        closeKeyboardHelper.addActionListener(this::closeKeyboardHelperActionPerformed);
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
        if (tapeCatalog.getColumnModel().getColumnCount() > 0) {
            tapeCatalog.getColumnModel().getColumn(0).setResizable(false);
            tapeCatalog.getColumnModel().getColumn(0).setHeaderValue(bundle.getString("JSpeccy.tapeCatalog.columnModel.title0")); // NOI18N
            tapeCatalog.getColumnModel().getColumn(1).setHeaderValue(bundle.getString("JSpeccy.tapeCatalog.columnModel.title1")); // NOI18N
            tapeCatalog.getColumnModel().getColumn(2).setHeaderValue(bundle.getString("JSpeccy.tapeCatalog.columnModel.title2")); // NOI18N
        }

        tapeBrowserDialog.getContentPane().add(jScrollPane1, java.awt.BorderLayout.CENTER);

        tapeBrowserToolbar.setRollover(true);

        tapeBrowserButtonRec.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/32x32/player-rec.png"))); // NOI18N
        tapeBrowserButtonRec.setText(bundle.getString("JSpeccy.tapeBrowserButtonRec.text")); // NOI18N
        tapeBrowserButtonRec.setToolTipText(bundle.getString("JSpeccy.tapeBrowserButtonRec.tooltip.text")); // NOI18N
        tapeBrowserButtonRec.setEnabled(false);
        tapeBrowserButtonRec.setFocusable(false);
        tapeBrowserButtonRec.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tapeBrowserButtonRec.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tapeBrowserButtonRec.addActionListener(this::recordStartTapeMediaMenuActionPerformed);
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
        tapeBrowserButtonStop.addActionListener(this::tapeBrowserButtonStopActionPerformed);
        tapeBrowserToolbar.add(tapeBrowserButtonStop);

        tapeBrowserButtonRew.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/32x32/player_rew.png"))); // NOI18N
        tapeBrowserButtonRew.setText(bundle.getString("JSpeccy.tapeBrowserButtonRew.text")); // NOI18N
        tapeBrowserButtonRew.setToolTipText(bundle.getString("JSpeccy.tapeBrowserButtonRew.tooltip.text")); // NOI18N
        tapeBrowserButtonRew.setEnabled(false);
        tapeBrowserButtonRew.setFocusable(false);
        tapeBrowserButtonRew.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tapeBrowserButtonRew.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tapeBrowserButtonRew.addActionListener(this::rewindTapeMediaMenuActionPerformed);
        tapeBrowserToolbar.add(tapeBrowserButtonRew);

        tapeBrowserButtonPlay.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/32x32/player_play.png"))); // NOI18N
        tapeBrowserButtonPlay.setText(bundle.getString("JSpeccy.tapeBrowserButtonPlay.text")); // NOI18N
        tapeBrowserButtonPlay.setToolTipText(bundle.getString("JSpeccy.tapeBrowserButtonPlay.tooltip.text")); // NOI18N
        tapeBrowserButtonPlay.setEnabled(false);
        tapeBrowserButtonPlay.setFocusable(false);
        tapeBrowserButtonPlay.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tapeBrowserButtonPlay.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tapeBrowserButtonPlay.addActionListener(this::tapeBrowserButtonPlayActionPerformed);
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
        tapeBrowserButtonEject.addActionListener(this::tapeBrowserButtonEjectActionPerformed);
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
        saveSzxChoosePanel.add(ignoreRadioButton);

        saveSzxButtonGroup.add(linkedRadioButton);
        linkedRadioButton.setText(bundle.getString("JSpeccy.linkedRadioButton.text")); // NOI18N
        saveSzxChoosePanel.add(linkedRadioButton);

        saveSzxButtonGroup.add(embeddedRadioButton);
        embeddedRadioButton.setText(bundle.getString("JSpeccy.embeddedRadioButton.text")); // NOI18N
        saveSzxChoosePanel.add(embeddedRadioButton);

        jPanel4.add(saveSzxChoosePanel);

        saveSzxTape.getContentPane().add(jPanel4, java.awt.BorderLayout.CENTER);

        jPanel3.setLayout(new javax.swing.BoxLayout(jPanel3, javax.swing.BoxLayout.LINE_AXIS));
        jPanel3.add(filler4);

        saveSzxCloseButton.setText(bundle.getString("JSpeccy.saveSzxCloseButton.text")); // NOI18N
        saveSzxCloseButton.addActionListener(this::saveSzxCloseButtonActionPerformed);
        jPanel3.add(saveSzxCloseButton);
        jPanel3.add(filler5);

        saveSzxTape.getContentPane().add(jPanel3, java.awt.BorderLayout.PAGE_END);

        pokeDialog.setTitle(bundle.getString("JSpecy.pokeDialog.title.text")); // NOI18N
        pokeDialog.getContentPane().setLayout(new javax.swing.BoxLayout(pokeDialog.getContentPane(), javax.swing.BoxLayout.PAGE_AXIS));

        pokeAddress.setText(bundle.getString("JSpeccy.pokeAddress.text")); // NOI18N
        addrValuePanel.add(pokeAddress);

        addressSpinner.setModel(new javax.swing.SpinnerNumberModel(23296, 16384, 65535, 1));
        addressSpinner.addChangeListener(this::addressSpinnerStateChanged);
        addrValuePanel.add(addressSpinner);
        addrValuePanel.add(filler1);

        pokeValue.setText(bundle.getString("JSpeccy.pokeValue.text")); // NOI18N
        addrValuePanel.add(pokeValue);

        valueSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 255, 1));
        valueSpinner.addChangeListener(this::valueSpinnerStateChanged);
        addrValuePanel.add(valueSpinner);
        addrValuePanel.add(filler2);

        pokeButton.setText(bundle.getString("JSpeccy.pokeButton.text")); // NOI18N
        pokeButton.setEnabled(false);
        pokeButton.addActionListener(this::pokeButtonActionPerformed);
        addrValuePanel.add(pokeButton);

        pokeDialog.getContentPane().add(addrValuePanel);

        closePokeDialogButton.setText(bundle.getString("JSpeccy.closePokeDialogButton.text")); // NOI18N
        closePokeDialogButton.addActionListener(this::closePokeDialogButtonActionPerformed);
        closePokeDialogPanel.add(closePokeDialogButton);

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
    mdrvLabel.addMouseListener(new java.awt.event.MouseAdapter() {
        public void mouseClicked(java.awt.event.MouseEvent evt) {
            mdrvLabelMouseClicked(evt);
        }
    });
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
    tapeLabel.addMouseListener(new java.awt.event.MouseAdapter() {
        public void mouseClicked(java.awt.event.MouseEvent evt) {
            tapeLabelMouseClicked(evt);
        }
    });
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
    speedLabel.setMaximumSize(new java.awt.Dimension(85, 18));
    speedLabel.setMinimumSize(new java.awt.Dimension(50, 18));
    speedLabel.setPreferredSize(new java.awt.Dimension(60, 18));
    speedLabel.setRequestFocusEnabled(false);
    statusPanel.add(speedLabel);

    getContentPane().add(statusPanel, java.awt.BorderLayout.PAGE_END);

    toolbarMenu.setRollover(true);

    openSnapshotButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/fileopen.png"))); // NOI18N
    openSnapshotButton.setToolTipText(bundle.getString("JSpeccy.openSnapshotButton.toolTipText")); // NOI18N
    openSnapshotButton.setFocusable(false);
    openSnapshotButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    openSnapshotButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    openSnapshotButton.addActionListener(this::openSnapshotActionPerformed);
    toolbarMenu.add(openSnapshotButton);

    pauseToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/player_pause.png"))); // NOI18N
    pauseToggleButton.setToolTipText(bundle.getString("JSpeccy.pauseToggleButton.toolTipText")); // NOI18N
    pauseToggleButton.setFocusable(false);
    pauseToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    pauseToggleButton.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/player_play.png"))); // NOI18N
    pauseToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    pauseToggleButton.addActionListener(this::pauseMachineMenuActionPerformed);
    toolbarMenu.add(pauseToggleButton);

    fastEmulationToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/player_fwd.png"))); // NOI18N
    fastEmulationToggleButton.setText(bundle.getString("JSpeccy.fastEmulationToggleButton.text")); // NOI18N
    fastEmulationToggleButton.setToolTipText(bundle.getString("JSpeccy.fastEmulationToggleButton.toolTipText")); // NOI18N
    fastEmulationToggleButton.setFocusable(false);
    fastEmulationToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    fastEmulationToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    fastEmulationToggleButton.addActionListener(this::fastEmulationToggleButtonActionPerformed);
    toolbarMenu.add(fastEmulationToggleButton);

    doubleSizeToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/viewmag+.png"))); // NOI18N
    doubleSizeToggleButton.setToolTipText(bundle.getString("JSpeccy.doubleSizeToggleButton.toolTipText")); // NOI18N
    doubleSizeToggleButton.setFocusable(false);
    doubleSizeToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    doubleSizeToggleButton.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/viewmag-.png"))); // NOI18N
    doubleSizeToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    doubleSizeToggleButton.addActionListener(this::doubleSizeOptionActionPerformed);
    toolbarMenu.add(doubleSizeToggleButton);

    silenceSoundToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/sound-on-16x16.png"))); // NOI18N
    silenceSoundToggleButton.setToolTipText(bundle.getString("JSpeccy.silenceSoundToggleButton.toolTipText")); // NOI18N
    silenceSoundToggleButton.setFocusable(false);
    silenceSoundToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    silenceSoundToggleButton.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/sound-off-16x16.png"))); // NOI18N
    silenceSoundToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    silenceSoundToggleButton.addActionListener(this::silenceSoundToggleButtonActionPerformed);
    toolbarMenu.add(silenceSoundToggleButton);

    resetSpectrumButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/shutdown.png"))); // NOI18N
    resetSpectrumButton.setToolTipText(bundle.getString("JSpeccy.resetSpectrumButton.toolTipText")); // NOI18N
    resetSpectrumButton.setFocusable(false);
    resetSpectrumButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    resetSpectrumButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    resetSpectrumButton.addActionListener(this::resetMachineMenuActionPerformed);
    toolbarMenu.add(resetSpectrumButton);

    hardResetSpectrumButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/exit.png"))); // NOI18N
    hardResetSpectrumButton.setToolTipText(bundle.getString("JSpeccy.hardResetSpectrumButton.toolTipText")); // NOI18N
    hardResetSpectrumButton.setFocusable(false);
    hardResetSpectrumButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
    hardResetSpectrumButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
    hardResetSpectrumButton.addActionListener(this::hardResetSpectrumButtonActionPerformed);
    toolbarMenu.add(hardResetSpectrumButton);

    getContentPane().add(toolbarMenu, java.awt.BorderLayout.PAGE_START);

    fileMenu.setText(bundle.getString("JSpeccy.fileMenu.text")); // NOI18N

    openSnapshot.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F3, 0));
    openSnapshot.setText(bundle.getString("JSpeccy.openSnapshot.text")); // NOI18N
    openSnapshot.addActionListener(this::openSnapshotActionPerformed);
    fileMenu.add(openSnapshot);

    saveSnapshot.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0));
    saveSnapshot.setText(bundle.getString("JSpeccy.saveSnapshot.text")); // NOI18N
    saveSnapshot.addActionListener(this::saveSnapshotActionPerformed);
    fileMenu.add(saveSnapshot);
    fileMenu.add(jSeparator4);

    loadMemorySnapshot.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.ALT_DOWN_MASK));
    loadMemorySnapshot.setText(bundle.getString("JSpeccy.loadMemorySnapshot.text")); // NOI18N
    loadMemorySnapshot.setEnabled(false);
    loadMemorySnapshot.addActionListener(this::loadMemorySnapshotActionPerformed);
    fileMenu.add(loadMemorySnapshot);

    saveMemorySnapshot.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_DOWN_MASK));
    saveMemorySnapshot.setText(bundle.getString("JSpeccy.saveMemorySnapshot.text")); // NOI18N
    saveMemorySnapshot.addActionListener(this::saveMemorySnapshotActionPerformed);
    fileMenu.add(saveMemorySnapshot);
    fileMenu.add(jSeparator15);

    loadBinaryFile.setText(bundle.getString("JSpeccy.loadBinaryFile.text")); // NOI18N
    loadBinaryFile.addActionListener(this::loadBinaryFileActionPerformed);
    fileMenu.add(loadBinaryFile);

    saveBinaryFile.setText(bundle.getString("JSpeccy.saveBinaryFile.text")); // NOI18N
    saveBinaryFile.addActionListener(this::saveBinaryFileActionPerformed);
    fileMenu.add(saveBinaryFile);
    fileMenu.add(jSeparator16);

    loadScreenShot.setText(bundle.getString("JSpeccy.loadScreenShot.text")); // NOI18N
    loadScreenShot.addActionListener(this::loadScreenShotActionPerformed);
    fileMenu.add(loadScreenShot);

    saveScreenShot.setText(bundle.getString("JSpeccy.saveScreenShot.text")); // NOI18N
    saveScreenShot.addActionListener(this::saveScreenShotActionPerformed);
    fileMenu.add(saveScreenShot);
    fileMenu.add(jSeparator1);

    recentFilesMenu.setText(bundle.getString("JSpeccy.recentFilesMenu.text")); // NOI18N

    recentFileMenu0.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, java.awt.event.InputEvent.ALT_DOWN_MASK));
    recentFileMenu0.setText(bundle.getString("JSpeccy.recentFileMenu0.text")); // NOI18N
    recentFileMenu0.setEnabled(false);
    recentFileMenu0.addActionListener(this::recentFileMenu0ActionPerformed);
    recentFilesMenu.add(recentFileMenu0);

    recentFileMenu1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, java.awt.event.InputEvent.ALT_DOWN_MASK));
    recentFileMenu1.setText(bundle.getString("JSpeccy.recentFileMenu0.text")); // NOI18N
    recentFileMenu1.setEnabled(false);
    recentFileMenu1.addActionListener(this::recentFileMenu1ActionPerformed);
    recentFilesMenu.add(recentFileMenu1);

    recentFileMenu2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, java.awt.event.InputEvent.ALT_DOWN_MASK));
    recentFileMenu2.setText(bundle.getString("JSpeccy.recentFileMenu0.text")); // NOI18N
    recentFileMenu2.setEnabled(false);
    recentFileMenu2.addActionListener(this::recentFileMenu2ActionPerformed);
    recentFilesMenu.add(recentFileMenu2);

    recentFileMenu3.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_4, java.awt.event.InputEvent.ALT_DOWN_MASK));
    recentFileMenu3.setText(bundle.getString("JSpeccy.recentFileMenu0.text")); // NOI18N
    recentFileMenu3.setEnabled(false);
    recentFileMenu3.addActionListener(this::recentFileMenu3ActionPerformed);
    recentFilesMenu.add(recentFileMenu3);

    recentFileMenu4.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_5, java.awt.event.InputEvent.ALT_DOWN_MASK));
    recentFileMenu4.setText(bundle.getString("JSpeccy.recentFileMenu0.text")); // NOI18N
    recentFileMenu4.setEnabled(false);
    recentFileMenu4.addActionListener(this::recentFileMenu4ActionPerformed);
    recentFilesMenu.add(recentFileMenu4);

    fileMenu.add(recentFilesMenu);
    fileMenu.add(jSeparator7);

    thisIsTheEndMyFriend.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_DOWN_MASK));
    thisIsTheEndMyFriend.setText(bundle.getString("JSpeccy.thisIsTheEndMyFriend.text")); // NOI18N
    thisIsTheEndMyFriend.addActionListener(this::thisIsTheEndMyFriendActionPerformed);
    fileMenu.add(thisIsTheEndMyFriend);

    mainMenuBar.add(fileMenu);

    optionsMenu.setText(bundle.getString("JSpeccy.optionsMenu.text")); // NOI18N

    doubleSizeOption.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.ALT_DOWN_MASK));
    doubleSizeOption.setText(bundle.getString("JSpeccy.doubleSizeOption.text")); // NOI18N
    doubleSizeOption.addActionListener(this::doubleSizeOptionActionPerformed);
    optionsMenu.add(doubleSizeOption);

    zoomMethodOptionMenu.setText(bundle.getString("JSpeccy.zoomMethodOptionMenu.text")); // NOI18N

    zoomMethodButtonGroup.add(standardZoom);
    standardZoom.setSelected(true);
    standardZoom.setText(bundle.getString("JSpeccy.standardZoom.text")); // NOI18N
    standardZoom.addActionListener(this::standardZoomActionPerformed);
    zoomMethodOptionMenu.add(standardZoom);

    zoomMethodButtonGroup.add(bilinearZoom);
    bilinearZoom.setText(bundle.getString("JSpeccy.bilinearZoom.text")); // NOI18N
    bilinearZoom.addActionListener(this::bilinearZoomActionPerformed);
    zoomMethodOptionMenu.add(bilinearZoom);

    zoomMethodButtonGroup.add(bicubicZoom);
    bicubicZoom.setText(bundle.getString("JSpeccy.bicubicZoom.text")); // NOI18N
    bicubicZoom.addActionListener(this::bicubicZoomActionPerformed);
    zoomMethodOptionMenu.add(bicubicZoom);

    optionsMenu.add(zoomMethodOptionMenu);

    borderSizeOptionMenu.setText(bundle.getString("JSpeccy.borderSizeOptionMenu.text")); // NOI18N

    borderSizeButtonGroup.add(noBorder);
    noBorder.setText(bundle.getString("JSpeccy.noBorder.text")); // NOI18N
    noBorder.addActionListener(this::noBorderActionPerformed);
    borderSizeOptionMenu.add(noBorder);

    borderSizeButtonGroup.add(standardBorder);
    standardBorder.setSelected(true);
    standardBorder.setText(bundle.getString("JSpeccy.standardBorder.text")); // NOI18N
    standardBorder.addActionListener(this::standardBorderActionPerformed);
    borderSizeOptionMenu.add(standardBorder);

    borderSizeButtonGroup.add(fullBorder);
    fullBorder.setText(bundle.getString("JSpeccy.fullBorder.text")); // NOI18N
    fullBorder.addActionListener(this::fullBorderActionPerformed);
    borderSizeOptionMenu.add(fullBorder);

    borderSizeButtonGroup.add(hugeBorder);
    hugeBorder.setText(bundle.getString("JSpeccy.hugeBorder.text")); // NOI18N
    hugeBorder.addActionListener(this::hugeBorderActionPerformed);
    borderSizeOptionMenu.add(hugeBorder);

    optionsMenu.add(borderSizeOptionMenu);

    filtersOptionMenu.setText(bundle.getString("JSpeccy.filtersOptionMenu.text")); // NOI18N

    filtersButtonGroup.add(noneFilter);
    noneFilter.setSelected(true);
    noneFilter.setText(bundle.getString("JSpeccy.noneFilter.text")); // NOI18N
    noneFilter.addActionListener(this::noneFilterActionPerformed);
    filtersOptionMenu.add(noneFilter);

    filtersButtonGroup.add(palTvFilter);
    palTvFilter.setText(bundle.getString("JSpeccy.palTvFilter.text")); // NOI18N
    palTvFilter.addActionListener(this::palTvFilterActionPerformed);
    filtersOptionMenu.add(palTvFilter);

    filtersButtonGroup.add(rgbFilter);
    rgbFilter.setText(bundle.getString("JSpeccy.rgbFilter.text")); // NOI18N
    rgbFilter.addActionListener(this::rgbFilterActionPerformed);
    filtersOptionMenu.add(rgbFilter);
    filtersOptionMenu.add(jSeparator20);

    scanlinesFilter.setText(bundle.getString("JSpeccy.scanlinesFilter.text")); // NOI18N
    scanlinesFilter.addActionListener(this::scanlinesFilterActionPerformed);
    filtersOptionMenu.add(scanlinesFilter);

    optionsMenu.add(filtersOptionMenu);
    optionsMenu.add(jSeparator19);

    joystickOptionMenu.setText(bundle.getString("JSpeccy.joystickOptionMenu.text")); // NOI18N

    joystickButtonGroup.add(noneJoystick);
    noneJoystick.setSelected(true);
    noneJoystick.setText(bundle.getString("JSpeccy.noneJoystick.text")); // NOI18N
    noneJoystick.addActionListener(this::noneJoystickActionPerformed);
    joystickOptionMenu.add(noneJoystick);

    joystickButtonGroup.add(kempstonJoystick);
    kempstonJoystick.setText(bundle.getString("JSpeccy.kempstonJoystick.text")); // NOI18N
    kempstonJoystick.addActionListener(this::kempstonJoystickActionPerformed);
    joystickOptionMenu.add(kempstonJoystick);

    joystickButtonGroup.add(sinclair1Joystick);
    sinclair1Joystick.setText(bundle.getString("JSpeccy.sinclair1Joystick.text")); // NOI18N
    sinclair1Joystick.addActionListener(this::sinclair1JoystickActionPerformed);
    joystickOptionMenu.add(sinclair1Joystick);

    joystickButtonGroup.add(sinclair2Joystick);
    sinclair2Joystick.setText(bundle.getString("JSpeccy.sinclair2Joystick.text")); // NOI18N
    sinclair2Joystick.addActionListener(this::sinclair2JoystickActionPerformed);
    joystickOptionMenu.add(sinclair2Joystick);

    joystickButtonGroup.add(cursorJoystick);
    cursorJoystick.setText(bundle.getString("JSpeccy.cursorJoystick.text")); // NOI18N
    cursorJoystick.addActionListener(this::cursorJoystickActionPerformed);
    joystickOptionMenu.add(cursorJoystick);

    joystickButtonGroup.add(fullerJoystick);
    fullerJoystick.setText(bundle.getString("JSpeccy.fullerJoystick.text")); // NOI18N
    fullerJoystick.addActionListener(this::fullerJoystickActionPerformed);
    joystickOptionMenu.add(fullerJoystick);

    optionsMenu.add(joystickOptionMenu);
    optionsMenu.add(jSeparator18);

    settingsOptionsMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, 0));
    settingsOptionsMenu.setText(bundle.getString("JSpeccy.settings.text")); // NOI18N
    settingsOptionsMenu.addActionListener(this::settingsOptionsMenuActionPerformed);
    optionsMenu.add(settingsOptionsMenu);

    mainMenuBar.add(optionsMenu);

    machineMenu.setText(bundle.getString("JSpeccy.machineMenu.text")); // NOI18N
    machineMenu.addActionListener(this::silenceSoundToggleButtonActionPerformed);

    pauseMachineMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PAUSE, 0));
    pauseMachineMenu.setText(bundle.getString("JSpeccy.pauseMachineMenu.text")); // NOI18N
    pauseMachineMenu.addActionListener(this::pauseMachineMenuActionPerformed);
    machineMenu.add(pauseMachineMenu);

    silenceMachineMenu.setText(bundle.getString("JSpeccy.silenceMachineMenu.text")); // NOI18N
    silenceMachineMenu.addActionListener(this::silenceSoundToggleButtonActionPerformed);
    machineMenu.add(silenceMachineMenu);
    machineMenu.add(jSeparator17);

    resetMachineMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0));
    resetMachineMenu.setText(bundle.getString("JSpeccy.resetMachineMenu.text")); // NOI18N
    resetMachineMenu.addActionListener(this::resetMachineMenuActionPerformed);
    machineMenu.add(resetMachineMenu);

    hardResetMachineMenu.setText(bundle.getString("JSpeccy.hardResetMachineMenu.text")); // NOI18N
    hardResetMachineMenu.addActionListener(this::hardResetSpectrumButtonActionPerformed);
    machineMenu.add(hardResetMachineMenu);

    nmiMachineMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, java.awt.event.InputEvent.CTRL_DOWN_MASK));
    nmiMachineMenu.setText(bundle.getString("JSpeccy.nmiMachineMenu.text")); // NOI18N
    nmiMachineMenu.addActionListener(this::nmiMachineMenuActionPerformed);
    machineMenu.add(nmiMachineMenu);
    machineMenu.add(jSeparator3);

    pokeMachineMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.ALT_DOWN_MASK));
    pokeMachineMenu.setText(bundle.getString("JSpeccy.pokeMachineMenu.text")); // NOI18N
    pokeMachineMenu.addActionListener(this::pokeMachineMenuActionPerformed);
    machineMenu.add(pokeMachineMenu);

    memoryBrowserMachineMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H, java.awt.event.InputEvent.ALT_DOWN_MASK));
    memoryBrowserMachineMenu.setText(bundle.getString("JSpeccy.memoryBrowserMachineMenu.text")); // NOI18N
    memoryBrowserMachineMenu.addActionListener(this::memoryBrowserMachineMenuActionPerformed);
    machineMenu.add(memoryBrowserMachineMenu);
    machineMenu.add(jSeparator14);

    hardwareMachineMenu.setText(bundle.getString("JSpeccy.hardwareMachineMenu.text")); // NOI18N

    hardwareButtonGroup.add(spec16kHardware);
    spec16kHardware.setText(bundle.getString("JSpeccy.spec16kHardware.text")); // NOI18N
    spec16kHardware.addActionListener(this::spec16kHardwareActionPerformed);
    hardwareMachineMenu.add(spec16kHardware);

    hardwareButtonGroup.add(spec48kHardware);
    spec48kHardware.setSelected(true);
    spec48kHardware.setText(bundle.getString("JSpeccy.spec48kHardware.text")); // NOI18N
    spec48kHardware.addActionListener(this::spec48kHardwareActionPerformed);
    hardwareMachineMenu.add(spec48kHardware);

    hardwareButtonGroup.add(spec128kHardware);
    spec128kHardware.setText(bundle.getString("JSpeccy.spec128kHardware.text")); // NOI18N
    spec128kHardware.addActionListener(this::spec128kHardwareActionPerformed);
    hardwareMachineMenu.add(spec128kHardware);

    hardwareButtonGroup.add(specPlus2Hardware);
    specPlus2Hardware.setText(bundle.getString("JSpeccy.specPlus2Hardware.text")); // NOI18N
    specPlus2Hardware.addActionListener(this::specPlus2HardwareActionPerformed);
    hardwareMachineMenu.add(specPlus2Hardware);

    hardwareButtonGroup.add(specPlus2AHardware);
    specPlus2AHardware.setText(bundle.getString("JSpeccy.specPlus2AHardware.text")); // NOI18N
    specPlus2AHardware.addActionListener(this::specPlus2AHardwareActionPerformed);
    hardwareMachineMenu.add(specPlus2AHardware);

    hardwareButtonGroup.add(specPlus3Hardware);
    specPlus3Hardware.setText(bundle.getString("JSpeccy.specPlus3Hardware.text")); // NOI18N
    specPlus3Hardware.addActionListener(this::specPlus3HardwareActionPerformed);
    hardwareMachineMenu.add(specPlus3Hardware);

    machineMenu.add(hardwareMachineMenu);

    mainMenuBar.add(machineMenu);

    mediaMenu.setText(bundle.getString("JSpeccy.mediaMenu.text")); // NOI18N

    tapeMediaMenu.setText(bundle.getString("JSpeccy.tapeMediaMenu.text")); // NOI18N

    openTapeMediaMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F7, 0));
    openTapeMediaMenu.setText(bundle.getString("JSpeccy.openTapeMediaMenu.text")); // NOI18N
    openTapeMediaMenu.addActionListener(this::openTapeMediaMenuActionPerformed);
    tapeMediaMenu.add(openTapeMediaMenu);

    playTapeMediaMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F8, 0));
    playTapeMediaMenu.setText(bundle.getString("JSpeccy.playTapeMediaMenu.text")); // NOI18N
    playTapeMediaMenu.setEnabled(false);
    playTapeMediaMenu.addActionListener(this::playTapeMediaMenuActionPerformed);
    tapeMediaMenu.add(playTapeMediaMenu);

    browserTapeMediaMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.ALT_DOWN_MASK));
    browserTapeMediaMenu.setText(bundle.getString("JSpeccy.browserTapeMediaMenu.text")); // NOI18N
    browserTapeMediaMenu.addActionListener(this::browserTapeMediaMenuActionPerformed);
    tapeMediaMenu.add(browserTapeMediaMenu);

    rewindTapeMediaMenu.setText(bundle.getString("JSpeccy.rewindTapeMediaMenu.text")); // NOI18N
    rewindTapeMediaMenu.setEnabled(false);
    rewindTapeMediaMenu.addActionListener(this::rewindTapeMediaMenuActionPerformed);
    tapeMediaMenu.add(rewindTapeMediaMenu);

    ejectTapeMediaMenu.setText(bundle.getString("JSpeccy.ejectTapeMediaMenu.text")); // NOI18N
    ejectTapeMediaMenu.setEnabled(false);
    ejectTapeMediaMenu.addActionListener(this::tapeBrowserButtonEjectActionPerformed);
    tapeMediaMenu.add(ejectTapeMediaMenu);
    tapeMediaMenu.add(jSeparator5);

    createTapeMediaMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F6, 0));
    createTapeMediaMenu.setText(bundle.getString("JSpeccy.createTapeMediaMenu.text")); // NOI18N
    createTapeMediaMenu.addActionListener(this::createTapeMediaMenuActionPerformed);
    tapeMediaMenu.add(createTapeMediaMenu);

    clearTapeMediaMenu.setText(bundle.getString("JSpeccy.clearTapeMediaMenu.text")); // NOI18N
    clearTapeMediaMenu.setEnabled(false);
    clearTapeMediaMenu.addActionListener(this::clearTapeMediaMenuActionPerformed);
    tapeMediaMenu.add(clearTapeMediaMenu);
    tapeMediaMenu.add(jSeparator6);

    recordStartTapeMediaMenu.setText(bundle.getString("JSpeccy.recordStartTapeMediaMenu.text")); // NOI18N
    recordStartTapeMediaMenu.setEnabled(false);
    recordStartTapeMediaMenu.addActionListener(this::recordStartTapeMediaMenuActionPerformed);
    tapeMediaMenu.add(recordStartTapeMediaMenu);

    recordStopTapeMediaMenu.setText(bundle.getString("JSpeccy.recordStopTapeMediaMenu.text")); // NOI18N
    recordStopTapeMediaMenu.setEnabled(false);
    recordStopTapeMediaMenu.addActionListener(this::recordStopTapeMediaMenuActionPerformed);
    tapeMediaMenu.add(recordStopTapeMediaMenu);
    tapeMediaMenu.add(jSeparator13);

    reloadTapeMediaMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F7, java.awt.event.InputEvent.CTRL_DOWN_MASK));
    reloadTapeMediaMenu.setText(bundle.getString("JSpeccy.reloadTapeMediaMenu.text")); // NOI18N
    reloadTapeMediaMenu.setEnabled(false);
    reloadTapeMediaMenu.addActionListener(this::reloadTapeMediaMenuActionPerformed);
    tapeMediaMenu.add(reloadTapeMediaMenu);

    mediaMenu.add(tapeMediaMenu);
    mediaMenu.add(jSeparator8);

    IF1MediaMenu.setText(bundle.getString("JSpeccy.IF1MediaMenu.text")); // NOI18N

    microdrivesIF1MediaMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.ALT_DOWN_MASK));
    microdrivesIF1MediaMenu.setText(bundle.getString("JSpeccy.microdrivesIF1MediaMenu.text")); // NOI18N
    microdrivesIF1MediaMenu.addActionListener(this::microdrivesIF1MediaMenuActionPerformed);
    IF1MediaMenu.add(microdrivesIF1MediaMenu);

    mediaMenu.add(IF1MediaMenu);
    mediaMenu.add(jSeparator9);

    IF2MediaMenu.setText(bundle.getString("JSpeccy.IF2MediaMenu.text")); // NOI18N

    insertIF2RomMediaMenu.setText(bundle.getString("JSpeccy.insertIF2RomMediaMenu.text")); // NOI18N
    insertIF2RomMediaMenu.addActionListener(this::insertIF2RomMediaMenuActionPerformed);
    IF2MediaMenu.add(insertIF2RomMediaMenu);

    extractIF2RomMediaMenu.setText(bundle.getString("JSpeccy.extractIF2RomMediaMenu.text")); // NOI18N
    extractIF2RomMediaMenu.setEnabled(false);
    extractIF2RomMediaMenu.addActionListener(this::extractIF2RomMediaMenuActionPerformed);
    IF2MediaMenu.add(extractIF2RomMediaMenu);

    mediaMenu.add(IF2MediaMenu);

    mainMenuBar.add(mediaMenu);

    helpMenu.setText(bundle.getString("JSpeccy.helpMenu.text")); // NOI18N

    imageHelpMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
    imageHelpMenu.setText(bundle.getString("JSpeccy.imageHelpMenu.text")); // NOI18N
    imageHelpMenu.addActionListener(this::imageHelpMenuActionPerformed);
    helpMenu.add(imageHelpMenu);

    aboutHelpMenu.setText(bundle.getString("JSpeccy.aboutHelpMenu.text")); // NOI18N
    aboutHelpMenu.addActionListener(this::aboutHelpMenuActionPerformed);
    helpMenu.add(aboutHelpMenu);

    mainMenuBar.add(helpMenu);

    setJMenuBar(mainMenuBar);

    pack();
    }// </editor-fold>//GEN-END:initComponents

    private void openSnapshotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openSnapshotActionPerformed

        if( openSnapshotDlg == null ) {
            openSnapshotDlg = new JFileChooser(
                    settings.getRecentFilesSettings().getLastSnapshotDir());
            openSnapshotDlg.addChoosableFileFilter(allSnapTapeExtension);
            openSnapshotDlg.addChoosableFileFilter(snapshotExtension);
            openSnapshotDlg.addChoosableFileFilter(tapeExtension);
            openSnapshotDlg.addChoosableFileFilter(createTapeExtension);
            openSnapshotDlg.setFileFilter(allSnapTapeExtension);
        }
        else
            openSnapshotDlg.setSelectedFile(currentFileSnapshot);

        stopEmulation();

        int status = openSnapshotDlg.showOpenDialog(getContentPane());
        if (status == JFileChooser.APPROVE_OPTION) {
            currentFileSnapshot = openSnapshotDlg.getSelectedFile();
            settings.getRecentFilesSettings().setLastSnapshotDir(
                    currentFileSnapshot.getParent());

            if (snapshotExtension.accept(currentFileSnapshot)) {
                recentFilesMgr.addRecentFile(currentFileSnapshot);
                try {
                    SnapshotFile snap = SnapshotFactory.getSnapshot(currentFileSnapshot);
                    SpectrumState snapState = snap.load(currentFileSnapshot);
                    if (snap instanceof SnapshotSZX snapSZX) {
                        if (snapSZX.isTapeEmbedded()) {
                            tape.eject();
                            tape.insertEmbeddedTape(snapSZX.getTapeName(), snapSZX.getTapeExtension(),
                                    snapSZX.getTapeData(), snapSZX.getTapeBlock());
                        }

                        if (snapSZX.isTapeLinked()) {
                            File tapeLink = new File(snapSZX.getTapeName());

                            if (tapeLink.exists()) {
                                tape.eject();
                                tape.insert(tapeLink);
                                tape.setSelectedBlock(snapSZX.getTapeBlock());
                            }
                        }
                    }

                    spectrum.setSpectrumState(snapState);
                } catch (SnapshotException excpt) {
                    JOptionPane.showMessageDialog(this,
                            ResourceBundle.getBundle("gui/Bundle").getString(excpt.getMessage()),
                            ResourceBundle.getBundle("gui/Bundle").getString(
                                    "SNAPSHOT_LOAD_ERROR"), JOptionPane.ERROR_MESSAGE);
                }
            } else {
                currentFileTape = openSnapshotDlg.getSelectedFile();
                settings.getRecentFilesSettings().setLastTapeDir(
                        currentFileTape.getParent());
                tape.eject();
                if (tape.insert(currentFileTape)) {
                    recentFilesMgr.addRecentFile(currentFileTape);
                    if (settings.getTapeSettings().isAutoLoadTape()) {
                        spectrum.autoLoadTape();
                    }
                } else {
                    ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                    JOptionPane.showMessageDialog(this, bundle.getString("LOAD_TAPE_ERROR"),
                            bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        startEmulation();
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

        settings.getSpectrumSettings().setZoomed(doubleSizeOption.isSelected());

        if (settings.getSpectrumSettings().isZoomed()) {
            jscr.setZoom(settings.getSpectrumSettings().getZoom());
        } else {
            jscr.setZoom(1);
        }

        spectrum.invalidateScreen(true);
        pack();
    }//GEN-LAST:event_doubleSizeOptionActionPerformed

    private void pauseMachineMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseMachineMenuActionPerformed
        Object source = evt.getSource();
        if (source instanceof javax.swing.JCheckBoxMenuItem) {
            pauseToggleButton.setSelected(pauseMachineMenu.isSelected());
        } else {
            pauseMachineMenu.setSelected(pauseToggleButton.isSelected());
        }

        if (pauseMachineMenu.isSelected()) {
            stopEmulation();
        } else {
            startEmulation();
        }
    }//GEN-LAST:event_pauseMachineMenuActionPerformed

    private void resetMachineMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMachineMenuActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N

        if (!settings.getEmulatorSettings().isConfirmActions()) {
            spectrum.reset();
            return;
        }

        stopEmulation();

        int ret = JOptionPane.showConfirmDialog(getContentPane(),
                  bundle.getString("ARE_YOU_SURE_QUESTION"), bundle.getString("RESET_SPECTRUM"),
                  JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE); // NOI18N

        if( ret == JOptionPane.YES_OPTION )
            spectrum.reset();

        startEmulation();
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
        if (tape.isTapePlaying()) {
            tape.stop();
        } else {
            tape.play(true);
        }
    }//GEN-LAST:event_playTapeMediaMenuActionPerformed

    private void openTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openTapeMediaMenuActionPerformed

        if( openTapeDlg == null ) {
            openTapeDlg = new JFileChooser(
                    settings.getRecentFilesSettings().getLastTapeDir());
            openTapeDlg.addChoosableFileFilter(tapeExtension);
            openTapeDlg.addChoosableFileFilter(createTapeExtension);
            openTapeDlg.setFileFilter(tapeExtension);
        }
        else
            openTapeDlg.setSelectedFile(currentFileTape);

        stopEmulation();

        int status = openTapeDlg.showOpenDialog(getContentPane());
        if (status == JFileChooser.APPROVE_OPTION) {
            currentFileTape = openTapeDlg.getSelectedFile();
            settings.getRecentFilesSettings().setLastTapeDir(
                    currentFileTape.getParent());
            tape.eject();
            if (tape.insert(currentFileTape)) {
                recentFilesMgr.addRecentFile(currentFileTape);
                if (settings.getTapeSettings().isAutoLoadTape()) {
                    spectrum.autoLoadTape();
                }
            } else {
                ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                JOptionPane.showMessageDialog(this, bundle.getString("LOAD_TAPE_ERROR"),
                    bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
            }
        }

        startEmulation();
    }//GEN-LAST:event_openTapeMediaMenuActionPerformed

    private void rewindTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rewindTapeMediaMenuActionPerformed
        tape.rewind();
    }//GEN-LAST:event_rewindTapeMediaMenuActionPerformed

    private void imageHelpMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_imageHelpMenuActionPerformed
        keyboardHelper.setResizable(false);
        keyboardHelper.pack();
        keyboardHelper.setLocationRelativeTo(jscr);
        keyboardHelper.setVisible(true);
    }//GEN-LAST:event_imageHelpMenuActionPerformed

    private void aboutHelpMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutHelpMenuActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N

        stopEmulation();

        String aboutMessage = bundlePropertyResolver.resolvePlaceholders(bundle.getString("ABOUT_MESSAGE"), environment);
        String aboutTitle = bundle.getString("ABOUT_TITLE");
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icons/JSpeccy64x64.png")));
        JOptionPane.showMessageDialog(getContentPane(), aboutMessage, aboutTitle, JOptionPane.INFORMATION_MESSAGE, icon);

        startEmulation();
    }//GEN-LAST:event_aboutHelpMenuActionPerformed

    private void nmiMachineMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nmiMachineMenuActionPerformed
        spectrum.triggerNMI();
    }//GEN-LAST:event_nmiMachineMenuActionPerformed

    private void closeKeyboardHelperActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeKeyboardHelperActionPerformed
        keyboardHelper.setVisible(false);
    }//GEN-LAST:event_closeKeyboardHelperActionPerformed

    /**
     * Prompt the user for a file name to save a snapshot.
     * <p>
     * NOTE: we need to recreate the file chooser every time the user wishes to save a snapshot as there is no reliable
     * way, that works across operating systems, of clearing the file name from the dialog box before the dialog can be
     * used again for a second time.
     *
     * @param evt AWT action event
     */
    private void saveSnapshotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveSnapshotActionPerformed
        JFileChooser saveSnapshotDlg = new JFileChooser(currentDirSaveSnapshot);
        saveSnapshotDlg.addChoosableFileFilter(snapshotExtension);
        saveSnapshotDlg.setFileFilter(saveSnapshotExtension);
        currentDirSaveSnapshot = saveSnapshotDlg.getCurrentDirectory();

        stopEmulation();

        int status = saveSnapshotDlg.showSaveDialog(getContentPane());
        if (status == JFileChooser.APPROVE_OPTION) {
            currentDirSaveSnapshot = saveSnapshotDlg.getCurrentDirectory();
            if (!snapshotExtension.accept(saveSnapshotDlg.getSelectedFile())) {
                String saveName = saveSnapshotDlg.getSelectedFile().getAbsolutePath() + ".szx";
                saveSnapshotDlg.setSelectedFile(new File(saveName));
            }

            try {
                SnapshotFile snap = SnapshotFactory.getSnapshot(saveSnapshotDlg.getSelectedFile());
                if (snap instanceof SnapshotSZX snapSZX && tape.getTapeFilename() != null) {
                    tapeFilenameLabel.setText(tape.getTapeFilename().getName());
                    ignoreRadioButton.setSelected(true);
                    saveSzxTape.pack();
                    saveSzxTape.setLocationRelativeTo(jscr);
                    saveSzxTape.setVisible(true);
                    snapSZX.setTapeEmbedded(embeddedRadioButton.isSelected());
                    snapSZX.setTapeLinked(linkedRadioButton.isSelected());
                    if (snapSZX.isTapeEmbedded() || snapSZX.isTapeLinked()) {
                        snapSZX.setTapeName(tape.getTapeFilename().getAbsolutePath());
                        snapSZX.setTapeBlock(tape.getSelectedBlock());
                    }
                }
                snap.save(saveSnapshotDlg.getSelectedFile(), spectrum.getSpectrumState());
            } catch (SnapshotException excpt) {
                JOptionPane.showMessageDialog(this,
                        ResourceBundle.getBundle("gui/Bundle").getString(excpt.getMessage()),
                        ResourceBundle.getBundle("gui/Bundle").getString(
                                "SNAPSHOT_SAVE_ERROR"), JOptionPane.ERROR_MESSAGE);
            }
        }

        startEmulation();
    }//GEN-LAST:event_saveSnapshotActionPerformed

    private void noneJoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_noneJoystickActionPerformed

        spectrum.setJoystick(JoystickModel.NONE);
        noneJoystick.setSelected(true);

}//GEN-LAST:event_noneJoystickActionPerformed

    private void kempstonJoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_kempstonJoystickActionPerformed

        spectrum.setJoystick(JoystickModel.KEMPSTON);
        kempstonJoystick.setSelected(true);

    }//GEN-LAST:event_kempstonJoystickActionPerformed

    private void sinclair1JoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sinclair1JoystickActionPerformed

        spectrum.setJoystick(JoystickModel.SINCLAIR1);
        sinclair1Joystick.setSelected(true);

    }//GEN-LAST:event_sinclair1JoystickActionPerformed

    private void sinclair2JoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sinclair2JoystickActionPerformed

        spectrum.setJoystick(JoystickModel.SINCLAIR2);
        sinclair2Joystick.setSelected(true);

    }//GEN-LAST:event_sinclair2JoystickActionPerformed

    private void cursorJoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cursorJoystickActionPerformed

        spectrum.setJoystick(JoystickModel.CURSOR);
        cursorJoystick.setSelected(true);

    }//GEN-LAST:event_cursorJoystickActionPerformed

    private void spec48kHardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spec48kHardwareActionPerformed

        if (spectrum.getSpectrumModel() == MachineTypes.SPECTRUM48K) {
            return;
        }

        stopEmulation();

        spectrum.selectHardwareModel(MachineTypes.SPECTRUM48K);

        spectrum.reset();

        startEmulation();
    }//GEN-LAST:event_spec48kHardwareActionPerformed

    private void spec128kHardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spec128kHardwareActionPerformed

        if (spectrum.getSpectrumModel() == MachineTypes.SPECTRUM128K)
            return;

        stopEmulation();

        spectrum.selectHardwareModel(MachineTypes.SPECTRUM128K);

        spectrum.reset();

        startEmulation();
    }//GEN-LAST:event_spec128kHardwareActionPerformed

    private void fastEmulationToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fastEmulationToggleButtonActionPerformed
        if (fastEmulationToggleButton.isSelected())
            spectrum.changeSpeed(settings.getSpectrumSettings().getFramesInt());
        else
            spectrum.changeSpeed(1);
    }//GEN-LAST:event_fastEmulationToggleButtonActionPerformed

    private void browserTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browserTapeMediaMenuActionPerformed
        tapeBrowserDialog.pack();
        tapeBrowserDialog.setLocationRelativeTo(jscr);
        tapeCatalog.doLayout();
        tapeBrowserDialog.setVisible(true);
        tapeBrowserDialog.repaint();
    }//GEN-LAST:event_browserTapeMediaMenuActionPerformed

    private void specPlus2HardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specPlus2HardwareActionPerformed

        if (spectrum.getSpectrumModel() == MachineTypes.SPECTRUMPLUS2)
            return;

        stopEmulation();

        spectrum.selectHardwareModel(MachineTypes.SPECTRUMPLUS2);

        spectrum.reset();

        startEmulation();
    }//GEN-LAST:event_specPlus2HardwareActionPerformed

    private void specPlus2AHardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specPlus2AHardwareActionPerformed

        if (spectrum.getSpectrumModel() == MachineTypes.SPECTRUMPLUS2A)
            return;

        stopEmulation();

        spectrum.selectHardwareModel(MachineTypes.SPECTRUMPLUS2A);

        spectrum.reset();

        startEmulation();
    }//GEN-LAST:event_specPlus2AHardwareActionPerformed

    private void settingsOptionsMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_settingsOptionsMenuActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        int AYsoundMode = settings.getAY8912Settings().getSoundMode();
        boolean hifiSound = settings.getSpectrumSettings().isHifiSound();
        boolean muted = settings.getSpectrumSettings().isMutedSound();
        boolean zoomed = settings.getSpectrumSettings().isZoomed();
        boolean ayOn48k = settings.getSpectrumSettings().isAYEnabled48K();
        int zoom = settings.getSpectrumSettings().getZoom();

        stopEmulation();

        settingsDialog.showDialog(this, bundle.getString("SETTINGS_DIALOG_TITLE"));
        spectrum.loadConfigVars();

        if (muted != settings.getSpectrumSettings().isMutedSound()) {
            spectrum.muteSound(!muted);
            silenceMachineMenu.setSelected(!muted);
            silenceSoundToggleButton.setSelected(!muted);
        }

        if ((AYsoundMode != settings.getAY8912Settings().getSoundMode()
                || hifiSound != settings.getSpectrumSettings().isHifiSound()
                || ayOn48k != settings.getSpectrumSettings().isAYEnabled48K())
                && !spectrum.isMuteSound()) {
            spectrum.muteSound(true);
            spectrum.muteSound(false);
        }

        if (settings.getSpectrumSettings().isZoomed() != zoomed) {
            doubleSizeToggleButton.setSelected(settings.getSpectrumSettings().isZoomed());
            doubleSizeOption.setSelected(settings.getSpectrumSettings().isZoomed());
            jscr.setZoom(settings.getSpectrumSettings().isZoomed() ? settings.getSpectrumSettings().getZoom() : 1);
            spectrum.invalidateScreen(true);
            pack();
        } else {
            if (zoomed && zoom != settings.getSpectrumSettings().getZoom()) {
                jscr.setZoom(settings.getSpectrumSettings().getZoom());
                spectrum.invalidateScreen(true);
                pack();
            }
        }

        startEmulation();
    }//GEN-LAST:event_settingsOptionsMenuActionPerformed

    private void saveScreenShotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveScreenShotActionPerformed

        if( saveImageDlg == null ) {
            saveImageDlg = new JFileChooser(System.getProperty("user.home"));
            saveImageDlg.addChoosableFileFilter(imageExtension);
            saveImageDlg.setFileFilter(imageExtension);
            currentDirSaveImage = saveImageDlg.getCurrentDirectory();
        }
        else {
            saveImageDlg.setCurrentDirectory(currentDirSaveImage);
            BasicFileChooserUI chooserUI = (BasicFileChooserUI) saveImageDlg.getUI();
            chooserUI.setFileName("");
        }

        stopEmulation();

        int status = saveImageDlg.showSaveDialog(getContentPane());
        if( status == JFileChooser.APPROVE_OPTION ) {
            currentDirSaveImage = saveImageDlg.getCurrentDirectory();
            if (imageExtension.accept(saveImageDlg.getSelectedFile())) {
                spectrum.saveImage(saveImageDlg.getSelectedFile());
            } else {
                String saveName = saveImageDlg.getSelectedFile().getAbsolutePath() + ".scr";
                spectrum.saveImage(new File(saveName));
            }
        }

        startEmulation();
    }//GEN-LAST:event_saveScreenShotActionPerformed

    private void createTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createTapeMediaMenuActionPerformed

        if (openTapeDlg == null) {
            openTapeDlg = new JFileChooser(System.getProperty("user.home"));
            openTapeDlg.addChoosableFileFilter(createTapeExtension);
            openTapeDlg.addChoosableFileFilter(tapeExtension);
        } else {
            openTapeDlg.setCurrentDirectory(currentFileTape.getParentFile());
        }

        stopEmulation();

        openTapeDlg.setFileFilter(createTapeExtension);
        int status = openTapeDlg.showOpenDialog(this);
        if (status == JFileChooser.APPROVE_OPTION) {
            if (!createTapeExtension.accept(openTapeDlg.getSelectedFile())) {
                String saveName = openTapeDlg.getSelectedFile().getAbsolutePath() + ".tzx";
                openTapeDlg.setSelectedFile(new File(saveName));
            }
            currentFileTape = openTapeDlg.getSelectedFile();

            try {
                boolean res = currentFileTape.createNewFile();
                if (res)
                    tape.eject();
                if (res && tape.insert(currentFileTape)) {
                    recentFilesMgr.addRecentFile(currentFileTape);
                } else {
                    ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                    JOptionPane.showMessageDialog(this, bundle.getString("LOAD_TAPE_ERROR"),
                            bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                Logger.getLogger(JSpeccy.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        startEmulation();
    }//GEN-LAST:event_createTapeMediaMenuActionPerformed

    private void hardResetSpectrumButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hardResetSpectrumButtonActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N

        stopEmulation();

        if (settings.getEmulatorSettings().isConfirmActions()) {
            int ret = JOptionPane.showConfirmDialog(getContentPane(),
                    bundle.getString("ARE_YOU_SURE_QUESTION"), bundle.getString("HARD_RESET_SPECTRUM"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE); // NOI18N
            if (ret == JOptionPane.NO_OPTION) {
                startEmulation();
                return;
            }
        }

        spectrum.hardReset();
        switch (settings.getSpectrumSettings().getDefaultModel()) {
            case 0:
                spec16kHardware.setSelected(true);
                modelLabel.setToolTipText(MachineTypes.SPECTRUM16K.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUM16K.getShortModelName());
                spectrum.selectHardwareModel(MachineTypes.SPECTRUM16K);
                break;
            case 2:
                spec128kHardware.setSelected(true);
                modelLabel.setToolTipText(MachineTypes.SPECTRUM128K.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUM128K.getShortModelName());
                spectrum.selectHardwareModel(MachineTypes.SPECTRUM128K);
                break;
            case 3:
                specPlus2Hardware.setSelected(true);
                modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS2.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUMPLUS2.getShortModelName());
                spectrum.selectHardwareModel(MachineTypes.SPECTRUMPLUS2);
                break;
            case 4:
                specPlus2AHardware.setSelected(true);
                IF1MediaMenu.setEnabled(false);
                modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS2A.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUMPLUS2A.getShortModelName());
                spectrum.selectHardwareModel(MachineTypes.SPECTRUMPLUS2A);
                break;
            case 5:
                specPlus3Hardware.setSelected(true);
                IF1MediaMenu.setEnabled(false);
                modelLabel.setToolTipText(MachineTypes.SPECTRUMPLUS3.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUMPLUS3.getShortModelName());
                spectrum.selectHardwareModel(MachineTypes.SPECTRUMPLUS3);
                break;
            default:
                spec48kHardware.setSelected(true);
                modelLabel.setToolTipText(MachineTypes.SPECTRUM48K.getLongModelName());
                modelLabel.setText(MachineTypes.SPECTRUM48K.getShortModelName());
                spectrum.selectHardwareModel(MachineTypes.SPECTRUM48K);
        }

        switch (settings.getKeyboardJoystickSettings().getJoystickModel()) {
            case 1:
                kempstonJoystick.setSelected(true);
                spectrum.setJoystick(JoystickModel.KEMPSTON);
                break;
            case 2:
                sinclair1Joystick.setSelected(true);
                spectrum.setJoystick(JoystickModel.SINCLAIR1);
                break;
            case 3:
                sinclair2Joystick.setSelected(true);
                spectrum.setJoystick(JoystickModel.SINCLAIR2);
                break;
            case 4:
                cursorJoystick.setSelected(true);
                spectrum.setJoystick(JoystickModel.CURSOR);
                break;
            default:
                noneJoystick.setSelected(true);
                spectrum.setJoystick(JoystickModel.NONE);
        }

        if (settings.getSpectrumSettings().getDefaultModel() < 4) {
            IF1MediaMenu.setEnabled(settings.getInterface1Settings().isConnectedIF1());
            IF2MediaMenu.setEnabled(true);
        } else {
            IF1MediaMenu.setEnabled(false);
            IF2MediaMenu.setEnabled(false);
        }

        startEmulation();
    }//GEN-LAST:event_hardResetSpectrumButtonActionPerformed

    private void clearTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearTapeMediaMenuActionPerformed
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        int ret = JOptionPane.showConfirmDialog(getContentPane(),
            bundle.getString("ARE_YOU_SURE_QUESTION"), bundle.getString("CLEAR_TAPE"),
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE); // NOI18N

        stopEmulation();

        if (ret == JOptionPane.YES_OPTION && tape.isTapeReady()) {
            try {
                File tmp = tape.getTapeFilename();
                if (tmp.delete()) {
                    tape.eject();
                }

                if (tmp.createNewFile() && (!tape.insert(tmp))) {
                    JOptionPane.showMessageDialog(this, bundle.getString("LOAD_TAPE_ERROR"),
                            bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                Logger.getLogger(JSpeccy.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        startEmulation();
    }//GEN-LAST:event_clearTapeMediaMenuActionPerformed

    private void spec16kHardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spec16kHardwareActionPerformed

        if (spectrum.getSpectrumModel() == MachineTypes.SPECTRUM16K)
            return;

        stopEmulation();

        spectrum.selectHardwareModel(MachineTypes.SPECTRUM16K);

        spectrum.reset();

        startEmulation();
    }//GEN-LAST:event_spec16kHardwareActionPerformed

    private void specPlus3HardwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specPlus3HardwareActionPerformed

        if (spectrum.getSpectrumModel() == MachineTypes.SPECTRUMPLUS3)
            return;

        stopEmulation();

        spectrum.selectHardwareModel(MachineTypes.SPECTRUMPLUS3);

        spectrum.reset();

        startEmulation();
    }//GEN-LAST:event_specPlus3HardwareActionPerformed

    private void recordStartTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recordStartTapeMediaMenuActionPerformed

        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
        if (!tape.isTapeReady()) {
            JOptionPane.showMessageDialog(this,
                bundle.getString("RECORD_START_ERROR"), bundle.getString("RECORD_START_TITLE"),
                JOptionPane.ERROR_MESSAGE); // NOI18N
        } else {
            if (!spectrum.startRecording()) {
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
    }//GEN-LAST:event_recordStopTapeMediaMenuActionPerformed

    private void loadRecentFile(File fdopen) {
        ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N

        if (!fdopen.exists()) {
            JOptionPane.showMessageDialog(this, bundle.getString("RECENT_FILE_ERROR"),
                bundle.getString("RECENT_FILE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE); //NOI18N
        } else {
            if (snapshotExtension.accept(fdopen)) {

                stopEmulation();

                currentFileSnapshot = fdopen;
                try {
                    SnapshotFile snap = SnapshotFactory.getSnapshot(currentFileSnapshot);
                    SpectrumState snapState = snap.load(currentFileSnapshot);
                    if (snap instanceof SnapshotSZX snapSZX) {
                        if (snapSZX.isTapeEmbedded()) {
                            tape.eject();
                            tape.insertEmbeddedTape(snapSZX.getTapeName(), snapSZX.getTapeExtension(),
                                    snapSZX.getTapeData(), snapSZX.getTapeBlock());
                        }

                        if (snapSZX.isTapeLinked()) {
                            File tapeLink = new File(snapSZX.getTapeName());

                            if (tapeLink.exists()) {
                                tape.eject();
                                tape.insert(tapeLink);
                                tape.setSelectedBlock(snapSZX.getTapeBlock());
                            }
                        }
                    }
                    spectrum.setSpectrumState(snapState);
                } catch (SnapshotException excpt) {
                    JOptionPane.showMessageDialog(this,
                            ResourceBundle.getBundle("gui/Bundle").getString(excpt.getMessage()),
                            ResourceBundle.getBundle("gui/Bundle").getString(
                                    "SNAPSHOT_LOAD_ERROR"), JOptionPane.ERROR_MESSAGE);
                }

                startEmulation();

            } else {
                tape.eject();
                currentFileTape = fdopen;

                if (!tape.insert(currentFileTape)) {
                    JOptionPane.showMessageDialog(this, bundle.getString("LOAD_TAPE_ERROR"),
                        bundle.getString("LOAD_TAPE_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                } else {
                    if (settings.getTapeSettings().isAutoLoadTape()) {
                        spectrum.autoLoadTape();
                    }
                }
            }
        }
    }

    private void recentFileMenu0ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentFileMenu0ActionPerformed
        loadRecentFile(recentFilesMgr.getRecentFile(0));
    }//GEN-LAST:event_recentFileMenu0ActionPerformed

    private void recentFileMenu1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentFileMenu1ActionPerformed
        loadRecentFile(recentFilesMgr.getRecentFile(1));
    }//GEN-LAST:event_recentFileMenu1ActionPerformed

    private void recentFileMenu2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentFileMenu2ActionPerformed
        loadRecentFile(recentFilesMgr.getRecentFile(2));
    }//GEN-LAST:event_recentFileMenu2ActionPerformed

    private void recentFileMenu3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentFileMenu3ActionPerformed
        loadRecentFile(recentFilesMgr.getRecentFile(3));
    }//GEN-LAST:event_recentFileMenu3ActionPerformed

    private void recentFileMenu4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentFileMenu4ActionPerformed
        loadRecentFile(recentFilesMgr.getRecentFile(4));
    }//GEN-LAST:event_recentFileMenu4ActionPerformed

    private void insertIF2RomMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_insertIF2RomMediaMenuActionPerformed

        if (IF2RomDlg == null) {
            IF2RomDlg = new JFileChooser("/home/jsanchez/Spectrum");
            IF2RomDlg.addChoosableFileFilter(romExtension);
            IF2RomDlg.setFileFilter(romExtension);
            currentDirRom = IF2RomDlg.getCurrentDirectory();
        } else {
            IF2RomDlg.setCurrentDirectory(currentDirRom);
        }

        stopEmulation();

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

        startEmulation();
    }//GEN-LAST:event_insertIF2RomMediaMenuActionPerformed

    private void extractIF2RomMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extractIF2RomMediaMenuActionPerformed
        spectrum.ejectIF2Rom();
        insertIF2RomMediaMenu.setEnabled(true);
        extractIF2RomMediaMenu.setEnabled(false);
        spectrum.reset();
    }//GEN-LAST:event_extractIF2RomMediaMenuActionPerformed

    private void fullerJoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fullerJoystickActionPerformed
        spectrum.setJoystick(JoystickModel.FULLER);
        fullerJoystick.setSelected(true);
    }//GEN-LAST:event_fullerJoystickActionPerformed

    private void saveSzxCloseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveSzxCloseButtonActionPerformed
        saveSzxTape.setVisible(false);
    }//GEN-LAST:event_saveSzxCloseButtonActionPerformed

    private void loadScreenShotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadScreenShotActionPerformed

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

        stopEmulation();

        int status = loadImageDlg.showOpenDialog(getContentPane());
        if( status == JFileChooser.APPROVE_OPTION ) {
            currentDirLoadImage = loadImageDlg.getCurrentDirectory();

            if (!spectrum.loadScreen(loadImageDlg.getSelectedFile())) {
                ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                JOptionPane.showMessageDialog(this, bundle.getString("LOAD_SCREEN_ERROR"),
                    bundle.getString("LOAD_SCREEN_ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
            }
        }

        startEmulation();
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
        if (tape.isTapePlaying()) {
            tape.stop();
            return;
        }

        if (tape.isTapeRecording()) {
            spectrum.stopRecording();
        }
    }//GEN-LAST:event_tapeBrowserButtonStopActionPerformed

    private void tapeBrowserButtonPlayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tapeBrowserButtonPlayActionPerformed
        if (tape.isTapeReady()) {
            tape.play(true);
        }
    }//GEN-LAST:event_tapeBrowserButtonPlayActionPerformed

    private void tapeBrowserButtonEjectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tapeBrowserButtonEjectActionPerformed
        tape.eject();
    }//GEN-LAST:event_tapeBrowserButtonEjectActionPerformed

    private void reloadTapeMediaMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadTapeMediaMenuActionPerformed
        if (currentFileTape != null && currentFileTape.exists() && tape.isTapeReady()) {
            File tmp = tape.getTapeFilename();
            tape.eject();
            tape.insert(tmp);
            if (settings.getTapeSettings().isAutoLoadTape()) {
                spectrum.autoLoadTape();
            }
        }
    }//GEN-LAST:event_reloadTapeMediaMenuActionPerformed

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
        pokeDialog.setLocationRelativeTo(jscr);
    }//GEN-LAST:event_pokeMachineMenuActionPerformed

    private void addressSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_addressSpinnerStateChanged
        SpinnerNumberModel snmAddress = (SpinnerNumberModel)addressSpinner.getModel();
        SpinnerNumberModel snmValue = (SpinnerNumberModel) valueSpinner.getModel();
        snmValue.setValue(spectrum.getMemory().readByte(snmAddress.getNumber().intValue()) & 0xff);
        pokeButton.setEnabled(false);
    }//GEN-LAST:event_addressSpinnerStateChanged

    private void valueSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_valueSpinnerStateChanged
        SpinnerNumberModel snmAddress = (SpinnerNumberModel)addressSpinner.getModel();
        SpinnerNumberModel snmValue = (SpinnerNumberModel) valueSpinner.getModel();
        int address = snmAddress.getNumber().intValue() & 0xffff;
        byte value = snmValue.getNumber().byteValue();
        pokeButton.setEnabled(value != spectrum.getMemory().readByte(address));
    }//GEN-LAST:event_valueSpinnerStateChanged

    private void pokeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pokeButtonActionPerformed
        SpinnerNumberModel snmAddress = (SpinnerNumberModel)addressSpinner.getModel();
        SpinnerNumberModel snmValue = (SpinnerNumberModel) valueSpinner.getModel();
        int address = snmAddress.getNumber().intValue() & 0xffff;
        spectrum.getMemory().writeByte(address, snmValue.getNumber().byteValue());
        pokeButton.setEnabled(false);

        if (spectrum.getMemory().isScreenByte(address))
            spectrum.invalidateScreen(false);
    }//GEN-LAST:event_pokeButtonActionPerformed

    private void closePokeDialogButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closePokeDialogButtonActionPerformed
        pokeDialog.setVisible(false);
    }//GEN-LAST:event_closePokeDialogButtonActionPerformed

    private void loadMemorySnapshotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadMemorySnapshotActionPerformed
        if (memorySnapshot == null)
            return;

        stopEmulation();
        spectrum.setSpectrumState(memorySnapshot);
        startEmulation();
    }//GEN-LAST:event_loadMemorySnapshotActionPerformed

    private void saveMemorySnapshotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveMemorySnapshotActionPerformed
        memorySnapshot = spectrum.getSpectrumState();
        loadMemorySnapshot.setEnabled(true);
    }//GEN-LAST:event_saveMemorySnapshotActionPerformed

    private void loadBinaryFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadBinaryFileActionPerformed
        if (loadSaveMemoryDialog == null)
            loadSaveMemoryDialog = new LoadSaveMemoryDialog(spectrum.getMemory());

        stopEmulation();
        loadSaveMemoryDialog.showLoadDialog(this, null);
        // The display area may have been affected
        startEmulation();
    }//GEN-LAST:event_loadBinaryFileActionPerformed

    private void saveBinaryFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveBinaryFileActionPerformed
        if (loadSaveMemoryDialog == null)
            loadSaveMemoryDialog = new LoadSaveMemoryDialog(spectrum.getMemory());

        stopEmulation();
        loadSaveMemoryDialog.showSaveDialog(this);
        startEmulation();
    }//GEN-LAST:event_saveBinaryFileActionPerformed

    private void noneFilterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_noneFilterActionPerformed
        jscr.setAnyFilter(false);
        scanlinesFilter.setEnabled(true);
        jscr.setScanlinesFilter(scanlinesFilter.isSelected());
        jscr.repaint();
    }//GEN-LAST:event_noneFilterActionPerformed

    private void palTvFilterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_palTvFilterActionPerformed
        if (settings.getSpectrumSettings().isULAplus()) {
            noneFilter.setSelected(true);
            return;
        }
        jscr.setPalFilter(true);
        scanlinesFilter.setEnabled(true);
        jscr.setScanlinesFilter(scanlinesFilter.isSelected());
        jscr.repaint();
    }//GEN-LAST:event_palTvFilterActionPerformed

    private void rgbFilterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rgbFilterActionPerformed
        jscr.setRgbFilter(true);
        scanlinesFilter.setEnabled(false);
        jscr.repaint();
    }//GEN-LAST:event_rgbFilterActionPerformed

    private void scanlinesFilterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scanlinesFilterActionPerformed
        jscr.setScanlinesFilter(scanlinesFilter.isSelected());
        jscr.repaint();
    }//GEN-LAST:event_scanlinesFilterActionPerformed

    private void standardZoomActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_standardZoomActionPerformed
        jscr.setInterpolationMethod(RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        jscr.repaint();
    }//GEN-LAST:event_standardZoomActionPerformed

    private void bilinearZoomActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bilinearZoomActionPerformed
        jscr.setInterpolationMethod(RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        jscr.repaint();
    }//GEN-LAST:event_bilinearZoomActionPerformed

    private void bicubicZoomActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bicubicZoomActionPerformed
        jscr.setInterpolationMethod(RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        jscr.repaint();
    }//GEN-LAST:event_bicubicZoomActionPerformed

    private void noBorderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_noBorderActionPerformed
        if (jscr.getBorderMode() == 0) {
            return;
        }

        stopEmulation();
        spectrum.setBorderMode(0);
        jscr.setBorderMode(0);
        jscr.setTvImage(spectrum.getTvImage());
        pack();
        startEmulation();
    }//GEN-LAST:event_noBorderActionPerformed

    private void standardBorderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_standardBorderActionPerformed
        if (jscr.getBorderMode() == 1) {
            return;
        }

        stopEmulation();
        spectrum.setBorderMode(1);
        jscr.setBorderMode(1);
        jscr.setTvImage(spectrum.getTvImage());
        pack();
        startEmulation();
    }//GEN-LAST:event_standardBorderActionPerformed

    private void hugeBorderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hugeBorderActionPerformed
        if (jscr.getBorderMode() == 3) {
            return;
        }

        stopEmulation();
        spectrum.setBorderMode(3);
        jscr.setBorderMode(3);
        jscr.setTvImage(spectrum.getTvImage());
        pack();
        startEmulation();
    }//GEN-LAST:event_hugeBorderActionPerformed

    private void fullBorderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fullBorderActionPerformed
        if (jscr.getBorderMode() == 2) {
            return;
        }

        stopEmulation();
        spectrum.setBorderMode(2);
        jscr.setBorderMode(2);
        jscr.setTvImage(spectrum.getTvImage());
        pack();
        startEmulation();
    }//GEN-LAST:event_fullBorderActionPerformed

    private void tapeLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tapeLabelMouseClicked
        if (evt.getClickCount() == 2 && !evt.isConsumed()) {
            evt.consume();
            tapeBrowserDialog.pack();
            tapeBrowserDialog.setLocationRelativeTo(jscr);
            tapeCatalog.doLayout();
            tapeBrowserDialog.setVisible(true);
            tapeBrowserDialog.repaint();
        }
    }//GEN-LAST:event_tapeLabelMouseClicked

    private void mdrvLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mdrvLabelMouseClicked
        if (IF1MediaMenu.isEnabled() && evt.getClickCount() == 2 && !evt.isConsumed()) {
            evt.consume();
            ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
            if (microdriveDialog == null)
                microdriveDialog = new MicrodriveDialog(spectrum.getInterface1());

            microdriveDialog.showDialog(this, bundle.getString("MICRODRIVES_DIALOG_TITLE"));
        }
    }//GEN-LAST:event_mdrvLabelMouseClicked


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu IF1MediaMenu;
    private javax.swing.JMenu IF2MediaMenu;
    private javax.swing.JMenuItem aboutHelpMenu;
    private javax.swing.JPanel addrValuePanel;
    private javax.swing.JSpinner addressSpinner;
    private javax.swing.JRadioButtonMenuItem bicubicZoom;
    private javax.swing.JRadioButtonMenuItem bilinearZoom;
    private javax.swing.ButtonGroup borderSizeButtonGroup;
    private javax.swing.JMenu borderSizeOptionMenu;
    private javax.swing.JMenuItem browserTapeMediaMenu;
    private javax.swing.JMenuItem clearTapeMediaMenu;
    private javax.swing.JButton closeKeyboardHelper;
    private javax.swing.JButton closePokeDialogButton;
    private javax.swing.JPanel closePokeDialogPanel;
    private javax.swing.JMenuItem createTapeMediaMenu;
    private javax.swing.JRadioButtonMenuItem cursorJoystick;
    private javax.swing.JCheckBoxMenuItem doubleSizeOption;
    private javax.swing.JToggleButton doubleSizeToggleButton;
    private javax.swing.JMenuItem ejectTapeMediaMenu;
    private javax.swing.JRadioButton embeddedRadioButton;
    private javax.swing.JMenuItem extractIF2RomMediaMenu;
    private javax.swing.JToggleButton fastEmulationToggleButton;
    private javax.swing.JMenu fileMenu;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.Box.Filler filler3;
    private javax.swing.Box.Filler filler4;
    private javax.swing.Box.Filler filler5;
    private javax.swing.ButtonGroup filtersButtonGroup;
    private javax.swing.JMenu filtersOptionMenu;
    private javax.swing.JRadioButtonMenuItem fullBorder;
    private javax.swing.JRadioButtonMenuItem fullerJoystick;
    private javax.swing.JMenuItem hardResetMachineMenu;
    private javax.swing.JButton hardResetSpectrumButton;
    private javax.swing.ButtonGroup hardwareButtonGroup;
    private javax.swing.JMenu hardwareMachineMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JRadioButtonMenuItem hugeBorder;
    private javax.swing.JRadioButton ignoreRadioButton;
    private javax.swing.JMenuItem imageHelpMenu;
    private javax.swing.JMenuItem insertIF2RomMediaMenu;
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
    private javax.swing.JPopupMenu.Separator jSeparator15;
    private javax.swing.JPopupMenu.Separator jSeparator16;
    private javax.swing.JPopupMenu.Separator jSeparator17;
    private javax.swing.JPopupMenu.Separator jSeparator18;
    private javax.swing.JPopupMenu.Separator jSeparator19;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator20;
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
    private javax.swing.JMenuItem loadBinaryFile;
    private javax.swing.JMenuItem loadMemorySnapshot;
    private javax.swing.JMenuItem loadScreenShot;
    private javax.swing.JMenu machineMenu;
    private javax.swing.JMenuBar mainMenuBar;
    private javax.swing.JLabel mdrvLabel;
    private javax.swing.JMenu mediaMenu;
    private javax.swing.JMenuItem memoryBrowserMachineMenu;
    private javax.swing.JMenuItem microdrivesIF1MediaMenu;
    private javax.swing.JLabel modelLabel;
    private javax.swing.JMenuItem nmiMachineMenu;
    private javax.swing.JRadioButtonMenuItem noBorder;
    private javax.swing.JRadioButtonMenuItem noneFilter;
    private javax.swing.JRadioButtonMenuItem noneJoystick;
    private javax.swing.JMenuItem openSnapshot;
    private javax.swing.JButton openSnapshotButton;
    private javax.swing.JMenuItem openTapeMediaMenu;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JRadioButtonMenuItem palTvFilter;
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
    private javax.swing.JMenuItem reloadTapeMediaMenu;
    private javax.swing.JMenuItem resetMachineMenu;
    private javax.swing.JButton resetSpectrumButton;
    private javax.swing.JMenuItem rewindTapeMediaMenu;
    private javax.swing.JRadioButtonMenuItem rgbFilter;
    private javax.swing.JMenuItem saveBinaryFile;
    private javax.swing.JMenuItem saveMemorySnapshot;
    private javax.swing.JMenuItem saveScreenShot;
    private javax.swing.JMenuItem saveSnapshot;
    private javax.swing.ButtonGroup saveSzxButtonGroup;
    private javax.swing.JPanel saveSzxChoosePanel;
    private javax.swing.JButton saveSzxCloseButton;
    private javax.swing.JDialog saveSzxTape;
    private javax.swing.JCheckBoxMenuItem scanlinesFilter;
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
    private javax.swing.JRadioButtonMenuItem standardBorder;
    private javax.swing.JRadioButtonMenuItem standardZoom;
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
    private javax.swing.ButtonGroup zoomMethodButtonGroup;
    private javax.swing.JMenu zoomMethodOptionMenu;
    // End of variables declaration//GEN-END:variables

    private class TapeChangedListener implements TapeStateListener {

        @Override
        public void stateChanged(final TapeState state) {
            log.debug("JSpeccy::TapeChangedListener: state = {}", state);
            boolean canRec = false;
            switch (state) {
                case INSERT:
                    tapeLabel.setEnabled(true);
                    tapeLabel.setIcon(tapeStopped);
                    tapeLabel.setToolTipText(tape.getTapeFilename().getName());
                    tapeFilename.setText(tape.getTapeFilename().getName());
                    tapeBrowserButtonPlay.setEnabled(true);
                    tapeBrowserButtonStop.setEnabled(false);
                    tapeBrowserButtonRew.setEnabled(true);
                    tapeBrowserButtonEject.setEnabled(true);
                    playTapeMediaMenu.setEnabled(true);
                    recordStopTapeMediaMenu.setEnabled(false);
                    rewindTapeMediaMenu.setEnabled(true);
                    ejectTapeMediaMenu.setEnabled(true);
                    reloadTapeMediaMenu.setEnabled(true);
                    if (tape.getTapeFilename().canWrite()
                            && !tape.getTapeFilename().getName().toLowerCase().endsWith(".csw")) {
                        canRec = true;
                    }
                    clearTapeMediaMenu.setEnabled(canRec);
                    recordStartTapeMediaMenu.setEnabled(canRec);
                    tapeBrowserButtonRec.setEnabled(canRec);
                    tapeCatalog.scrollRectToVisible(tapeCatalog.getCellRect(0, 0, true));
                    break;
                case EJECT:
                    ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N
                    tapeFilename.setText(bundle.getString("JSpeccy.tapeFilename.text")); // NOI18N
                    tapeLabel.setEnabled(false);
                    tapeLabel.setToolTipText(null);
                    tapeBrowserButtonRec.setEnabled(false);
                    tapeBrowserButtonPlay.setEnabled(false);
                    tapeBrowserButtonStop.setEnabled(false);
                    tapeBrowserButtonRew.setEnabled(false);
                    tapeBrowserButtonEject.setEnabled(false);
                    playTapeMediaMenu.setEnabled(false);
                    recordStartTapeMediaMenu.setEnabled(false);
                    recordStopTapeMediaMenu.setEnabled(false);
                    clearTapeMediaMenu.setEnabled(false);
                    rewindTapeMediaMenu.setEnabled(false);
                    ejectTapeMediaMenu.setEnabled(false);
                    reloadTapeMediaMenu.setEnabled(false);
                    createTapeMediaMenu.setEnabled(true);
                    break;
                case STOP:
                    tapeLabel.setIcon(tapeStopped);
                    tapeBrowserButtonPlay.setEnabled(true);
                    tapeBrowserButtonStop.setEnabled(false);
                    tapeBrowserButtonRew.setEnabled(true);
                    tapeBrowserButtonEject.setEnabled(true);
                    playTapeMediaMenu.setEnabled(true);
                    recordStopTapeMediaMenu.setEnabled(false);
                    rewindTapeMediaMenu.setEnabled(true);
                    ejectTapeMediaMenu.setEnabled(true);
                    reloadTapeMediaMenu.setEnabled(true);
                    createTapeMediaMenu.setEnabled(true);
                    if (tape.getTapeFilename().canWrite()
                            && !tape.getTapeFilename().getName().toLowerCase().endsWith(".csw")) {
                        canRec = true;
                    }
                    clearTapeMediaMenu.setEnabled(canRec);
                    recordStartTapeMediaMenu.setEnabled(canRec);
                    tapeBrowserButtonRec.setEnabled(canRec);
                    pauseToggleButton.setEnabled(true);
                    pauseMachineMenu.setEnabled(true);
                    fastEmulationToggleButton.setEnabled(true);
                    break;
                case RECORD:
                    tapeLabel.setIcon(tapeRecording);
                    playTapeMediaMenu.setEnabled(false);
                    tapeBrowserButtonRec.setEnabled(false);
                    tapeBrowserButtonPlay.setEnabled(false);
                    tapeBrowserButtonStop.setEnabled(true);
                    tapeBrowserButtonRew.setEnabled(false);
                    tapeBrowserButtonEject.setEnabled(false);
                    recordStartTapeMediaMenu.setEnabled(false);
                    recordStopTapeMediaMenu.setEnabled(true);
                    clearTapeMediaMenu.setEnabled(false);
                    rewindTapeMediaMenu.setEnabled(false);
                    ejectTapeMediaMenu.setEnabled(false);
                    reloadTapeMediaMenu.setEnabled(false);
                    createTapeMediaMenu.setEnabled(false);
                    if (settings.getTapeSettings().isAccelerateLoading()) {
                        pauseToggleButton.setEnabled(false);
                        pauseMachineMenu.setEnabled(false);
                        fastEmulationToggleButton.setEnabled(false);
                    }
                    break;
                case PLAY:
                    tapeLabel.setIcon(tapePlaying);
                    tapeBrowserButtonRec.setEnabled(false);
                    tapeBrowserButtonPlay.setEnabled(false);
                    tapeBrowserButtonStop.setEnabled(true);
                    tapeBrowserButtonRew.setEnabled(false);
                    tapeBrowserButtonEject.setEnabled(false);
                    recordStartTapeMediaMenu.setEnabled(false);
                    recordStopTapeMediaMenu.setEnabled(false);
                    clearTapeMediaMenu.setEnabled(false);
                    rewindTapeMediaMenu.setEnabled(false);
                    ejectTapeMediaMenu.setEnabled(false);
                    reloadTapeMediaMenu.setEnabled(false);
                    createTapeMediaMenu.setEnabled(false);
                    if (settings.getTapeSettings().isAccelerateLoading()) {
                        pauseToggleButton.setEnabled(false);
                        pauseMachineMenu.setEnabled(false);
                        fastEmulationToggleButton.setEnabled(false);
                    }
                    break;
            }
        }
    }
}
