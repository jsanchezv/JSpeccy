/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import configuration.JSpeccySettingsType;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import machine.Keyboard.JoystickModel;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 *
 * Class for command line options handling
 * 
 * @author jsanchez
 */
public class CommandLineOptions {
    JSpeccySettingsType settings;
    CommandLineOptions(JSpeccySettingsType config) {
        settings = config;
    }

    @Option(name = "-h", aliases = "--help", usage = "CommandLineOptions.help.text")
    private boolean printUsage;

    enum Model { sp16k, sp48k, sp128k, plus2, plus2a, plus3 }
    @Option(name = "-m", aliases = "--model", metaVar = "CommandLineOptions.metaVar.model.text",
            usage = "CommandLineOptions.model.text")
    private Model model = Model.sp48k;
    
    @Option(name = "-u", aliases = "--ulaplus", usage = "CommandLineOptions.ulaplus.text")
    private boolean ulaplus;
    
    @Option(name = "-if1", aliases = "--interface1", usage = "CommandLineOptions.interface1.text")
    private boolean if1;
    
    @Option(name = "--microdrive-file", depends = "-if1", metaVar = "CommandLineOptions.metaVar.file.text",
            usage = "CommandLineOptions.microdriveFile.text")
    private File if1mdv;
    
    @Option(name = "--multiface", usage = "CommandLineOptions.multiface.text")
    private boolean multiface;
    
    @Option(name = "--mf128-on-48k", depends = "--multiface" , usage = "CommandLineOptions.mf128on48k.text")
    private boolean mf128on48k;
    
    @Option(name = "--lec", usage = "CommandLineOptions.lec.text")
    private boolean lec;
    
    @Option(name = "--emulate-128k-bug", usage = "CommandLineOptions.emulate128kBug.text")
    private boolean bug128k;
    
    @Option(name = "--issue2", usage = "CommandLineOptions.issue2.text")
    private boolean issue2;

    @Option(name = "-j", aliases = "--joystick", metaVar = "CommandLineOptions.metaVar.model.text",
            usage = "CommandLineOptions.joystick.text")
    private JoystickModel joystick = JoystickModel.NONE;
    
    @Option(name = "--map-pc-keyboard", usage = "CommandLineOptions.mapPCkeyboard.text")
    private boolean mapPCkeys;

    @Option(name = "-z", aliases = "--zoom", metaVar = "CommandLineOptions.metaVar.size.text",
            usage = "CommandLineOptions.zoom.text")
    private int zoom = 1;
    
    @Option(name = "--scanlines", usage = "CommandLineOptions.scanlines.text")
    private boolean scanlines;
    
    @Option(name = "--mute", usage = "CommandLineOptions.mute.text")
    private boolean silence;

    @Option(name = "--melodik", usage = "CommandLineOptions.melodik.text")
    private boolean ayEnabled;
    
    @Option(name = "--hifi-sound", usage = "CommandLineOptions.hifi.text")
    private boolean hifi;

    enum SoundMode { MONO, ABC, ACB, BAC };
    @Option(name = "--sound-mode", metaVar = "CommandLineOptions.metaVar.mode.text",
            usage = "CommandLineOptions.soundMode.text")
    private SoundMode soundMode = SoundMode.MONO;
    
    enum ZoomFilter { STANDARD, BILINEAL, BICUBIC };
    @Option(name = "--zoom-filter", metaVar = "CommandLineOptions.metaVar.filter.text",
            usage = "CommandLineOptions.zoomFilter.text")
    private ZoomFilter zoomFilter = ZoomFilter.STANDARD;
    
    enum BorderSize { NONE, STANDARD, FULL, HUGE };
    @Option(name = "--border-size", metaVar = "CommandLineOptions.metaVar.size.text",
            usage = "CommandLineOptions.borderSize.text")
    private BorderSize borderSize = BorderSize.STANDARD;
    
    @Option(name = "--no-load-trap", usage = "CommandLineOptions.noLoadTrap.text")
    private boolean loadTrap;
    
    @Option(name = "--fastload", usage = "CommandLineOptions.fastload.text")
    private boolean fastload;
    
    @Option(name = "--no-accelerated-loading", usage = "CommandLineOptions.noAcceleratedLoading.text")
    private boolean acceleratedLoading;
    
    @Option(name = "--no-autoload", usage = "CommandLineOptions.noAutoLoad.text")
    private boolean autoload;
    
    @Option(name = "--no-save-trap", usage = "CommandLineOptions.noSaveTrap.text")
    private boolean saveTrap;
    
    @Option(name = "--no-confirm-actions", usage = "CommandLineOptions.noConfirmActions.text")
    private boolean confirmActions;

    // receives other command line parameters than options
    @Argument
    private List<String> arguments = new ArrayList<>();

    /**
     * @return the argumentsissue2
     */
    public List<String> getArguments() {
        return arguments;
    }
    
    /**
     * @return the printUsage
     */
    public boolean isPrintUsage() {
        return printUsage;
    }

    /**
     * @return the if1
     */
    public boolean isIf1() {
        return if1;
    }

    /**
     * @return the if1mdv
     */
    public File getIf1mdv() {
        return if1mdv;
    }
    
    public void copyArgumentsToSettings() {
        // hardware options
        settings.getSpectrumSettings().setDefaultModel(model.ordinal());
        settings.getSpectrumSettings().setULAplus(ulaplus);
        if (model != Model.plus2a && model != Model.plus3)
            settings.getInterface1Settings().setConnectedIF1(if1);
        settings.getSpectrumSettings().setMultifaceEnabled(multiface);
        if (multiface)
            settings.getSpectrumSettings().setMf128On48K(mf128on48k);
        if (lec && model == Model.sp48k)
            settings.getSpectrumSettings().setLecEnabled(lec);
        settings.getSpectrumSettings().setEmulate128KBug(bug128k);

        // Keyboard options
        settings.getKeyboardJoystickSettings().setIssue2(issue2);
        settings.getKeyboardJoystickSettings().setJoystickModel(joystick.ordinal());
        settings.getKeyboardJoystickSettings().setMapPCKeys(mapPCkeys);

        // Screen options
        if (zoom < 2 || zoom > 4)
            zoom = 1;
        if (zoom > 1)
            settings.getSpectrumSettings().setZoom(zoom);
        settings.getSpectrumSettings().setZoomed(zoom > 1);
        settings.getSpectrumSettings().setScanLines(scanlines);
        settings.getSpectrumSettings().setZoomMethod(zoomFilter.ordinal());
        settings.getSpectrumSettings().setBorderSize(borderSize.ordinal());
        
        // sound options
        settings.getSpectrumSettings().setMutedSound(silence);
        if (!silence) {
            settings.getSpectrumSettings().setAYEnabled48K(ayEnabled);
            settings.getSpectrumSettings().setHifiSound(hifi);
            settings.getAY8912Settings().setSoundMode(soundMode.ordinal());
        }
        
        // Tape Options
        settings.getTapeSettings().setEnableLoadTraps(!loadTrap);
        settings.getTapeSettings().setFlashLoad(fastload);
        settings.getTapeSettings().setAccelerateLoading(!acceleratedLoading);
        settings.getTapeSettings().setAutoLoadTape(!autoload);
        settings.getTapeSettings().setEnableSaveTraps(!saveTrap);
        
        // Emulator options
        settings.getEmulatorSettings().setConfirmActions(!confirmActions);
    }
}
