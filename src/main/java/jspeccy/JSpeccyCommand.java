/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspeccy;

import configuration.AY8912Type;
import configuration.EmulatorSettingsType;
import configuration.Interface1Type;
import configuration.JSpeccySettings;
import configuration.KeyboardJoystickType;
import configuration.SpectrumType;
import configuration.TapeSettingsType;
import gui.JSpeccy;
import lombok.Data;
import lombok.NoArgsConstructor;
import machine.Keyboard.JoystickModel;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;
import java.util.Optional;

/**
 * Class for command line options handling
 *
 * @author jsanchez
 * @author jose.hernandez
 */
@Component
@CommandLine.Command(
        name = "jspeccy.jar",
        description = "JSpeccy ZX Spectrum emulator",
        resourceBundle = "gui.Bundle",
        usageHelpAutoWidth = true,
        mixinStandardHelpOptions = true)
@NoArgsConstructor
@Data
public class JSpeccyCommand implements Runnable {

    @Override
    public void run() {

        // Run the emulator
        new JSpeccy(this).setVisible(true);
    }

    // Optional program file to load at startup time.
    @CommandLine.Parameters(
            arity = "0..1",
            paramLabel = "PROGRAM-FILE",
            descriptionKey = "JSpeccy.usage.sample.text"
    )
    private String programFile;

    enum Model {
        SP16K,
        SP48K,
        SP128K,
        PLUS2,
        PLUS2A,
        PLUS3
    }

    @CommandLine.Option(
            names = {"-m", "--model"},
            //paramLabel = "CommandLineOptions.metaVar.model.text",
            descriptionKey = "CommandLineOptions.model.text",
            //defaultValue = "sp48k",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private Optional<Model> model;

    @CommandLine.Option(names = {"-u", "--ulaplus"}, descriptionKey = "CommandLineOptions.ulaplus.text")
    private Optional<Boolean> ulaplus;

    @CommandLine.ArgGroup(exclusive = false)
    private If1Group if1Group = new If1Group();

    @Data
    static class If1Group {
        @CommandLine.Option(
                names = {"-if1", "--interface1"},
                required = true,
                descriptionKey = "CommandLineOptions.interface1.text")
        private Optional<Boolean> if1;

        // NOTE: this option requires the "-if1" option to be enabled.
        @CommandLine.Option(
                names = {"--microdrive-file"},
                //paramLabel = "CommandLineOptions.metaVar.file.text",
                descriptionKey = "CommandLineOptions.microdriveFile.text")
        private File if1mdv;
    }

    @CommandLine.ArgGroup(exclusive = false)
    private MultifaceGroup multifaceGroup = new MultifaceGroup();

    static class MultifaceGroup {
        @CommandLine.Option(names = {"--multiface"}, required = true, descriptionKey = "CommandLineOptions.multiface.text")
        private Optional<Boolean> multiface;

        // NOTE: this option requires the "--multiface" option to be enabled.
        @CommandLine.Option(names = {"--mf128-on-48k"}, descriptionKey = "CommandLineOptions.mf128on48k.text")
        private Optional<Boolean> mf128on48k;
    }

    @CommandLine.Option(names = {"--lec"}, descriptionKey = "CommandLineOptions.lec.text")
    private Optional<Boolean> lec;

    @CommandLine.Option(names = {"--emulate-128k-bug"}, descriptionKey = "CommandLineOptions.emulate128kBug.text")
    private Optional<Boolean> bug128k;

    @CommandLine.Option(names = {"--issue2"}, descriptionKey = "CommandLineOptions.issue2.text")
    private Optional<Boolean> issue2;

    @CommandLine.Option(
            names = {"-j", "--joystick"},
            //paramLabel = "CommandLineOptions.metaVar.model.text",
            descriptionKey = "CommandLineOptions.joystick.text",
            //defaultValue = "none",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private Optional<JoystickModel> joystick;

    @CommandLine.Option(names = {"--map-pc-keyboard"}, descriptionKey = "CommandLineOptions.mapPCkeyboard.text")
    private Optional<Boolean> mapPCkeys;

    @CommandLine.Option(names = {"--recreated-zx"}, descriptionKey = "CommandLineOptions.recreatedZX.text")
    private Optional<Boolean> recreatedZX;

    @CommandLine.Option(
            names = {"-z", "--zoom"},
            //paramLabel = "CommandLineOptions.metaVar.size.text",
            descriptionKey = "CommandLineOptions.zoom.text",
            //defaultValue = "1",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    private Optional<Integer> zoom;

    @CommandLine.Option(names = {"--scanlines"}, descriptionKey = "CommandLineOptions.scanlines.text")
    private Optional<Boolean> scanlines;

    @CommandLine.Option(names = {"--mute"}, descriptionKey = "CommandLineOptions.mute.text")
    private Optional<Boolean> silence;

    @CommandLine.Option(names = {"--melodik"}, descriptionKey = "CommandLineOptions.melodik.text")
    private Optional<Boolean> ayEnabled;

    @CommandLine.Option(names = {"--hifi-sound"}, descriptionKey = "CommandLineOptions.hifi.text")
    private Optional<Boolean> hifi;

    enum SoundMode {
        MONO,
        ABC,
        ACB,
        BAC
    }

    @CommandLine.Option(
            names = {"--sound-mode"},
            //paramLabel = "CommandLineOptions.metaVar.mode.text",
            descriptionKey = "CommandLineOptions.soundMode.text",
            //defaultValue = "mono",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private Optional<SoundMode> soundMode;

    enum ZoomFilter {
        STANDARD,
        BILINEAL,
        BICUBIC
    }

    @CommandLine.Option(
            names = {"--zoom-filter"},
            //paramLabel = "CommandLineOptions.metaVar.filter.text",
            descriptionKey = "CommandLineOptions.zoomFilter.text",
            //defaultValue = "standard",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private Optional<ZoomFilter> zoomFilter;

    enum BorderSize {
        NONE,
        STANDARD,
        FULL,
        HUGE
    }

    @CommandLine.Option(
            names = {"--border-size"},
            //paramLabel = "CommandLineOptions.metaVar.size.text",
            descriptionKey = "CommandLineOptions.borderSize.text",
            //defaultValue = "standard",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private Optional<BorderSize> borderSize;

    @CommandLine.Option(names = {"--no-load-trap"}, descriptionKey = "CommandLineOptions.noLoadTrap.text")
    private Optional<Boolean> loadTrap;

    @CommandLine.Option(names = {"--fastload"}, descriptionKey = "CommandLineOptions.fastload.text")
    private Optional<Boolean> fastload;

    @CommandLine.Option(names = {"--no-accelerated-loading"}, descriptionKey = "CommandLineOptions.noAcceleratedLoading.text")
    private Optional<Boolean> acceleratedLoading;

    @CommandLine.Option(names = {"--no-autoload"}, descriptionKey = "CommandLineOptions.noAutoLoad.text")
    private Optional<Boolean> autoload;

    @CommandLine.Option(names = {"--no-save-trap"}, descriptionKey = "CommandLineOptions.noSaveTrap.text")
    private Optional<Boolean> saveTrap;

    @CommandLine.Option(names = {"--no-confirm-actions"}, descriptionKey = "CommandLineOptions.noConfirmActions.text")
    private Optional<Boolean> confirmActions;

    /**
     * @return the if1
     */
    public Optional<Boolean> isIf1() {
        return if1Group.if1;
    }

    /**
     * @return the if1mdv
     */
    public File getIf1mdv() {
        return if1Group.if1mdv;
    }

    public void copyArgumentsToSettings(JSpeccySettings speccySettings) {
        // hardware options
        SpectrumType settings = speccySettings.getSpectrumSettings();
        model.map(Model::ordinal).ifPresent(settings::setDefaultModel);
        ulaplus.ifPresent(settings::setULAplus);
        if ((settings.getDefaultModel() != Model.PLUS2A.ordinal()) && (settings.getDefaultModel() != Model.PLUS3.ordinal())) {
            Interface1Type interface1Settings = speccySettings.getInterface1Settings();
            isIf1().ifPresent(interface1Settings::setConnectedIF1);
        }
        multifaceGroup.multiface.ifPresent(settings::setMultifaceEnabled);
        multifaceGroup.mf128on48k.ifPresent(settings::setMf128On48K);

        lec.filter(l -> settings.getDefaultModel() == Model.SP48K.ordinal()).ifPresent(settings::setLecEnabled);
        bug128k.ifPresent(settings::setEmulate128KBug);

        // Keyboard options
        KeyboardJoystickType keyboardJoystickSettings = speccySettings.getKeyboardJoystickSettings();
        issue2.ifPresent(keyboardJoystickSettings::setIssue2);
        joystick.map(JoystickModel::ordinal).ifPresent(keyboardJoystickSettings::setJoystickModel);
        mapPCkeys.ifPresent(keyboardJoystickSettings::setMapPCKeys);
        recreatedZX.ifPresent(keyboardJoystickSettings::setRecreatedZX);

        // Screen options
        zoom.ifPresent(z -> settings.setZoom((z < 2 || z > 4) ? 1 : z));
        settings.setZoomed(settings.getZoom() > 1);
        scanlines.ifPresent(settings::setScanLines);
        zoomFilter.map(ZoomFilter::ordinal).ifPresent(settings::setZoomMethod);
        borderSize.map(BorderSize::ordinal).ifPresent(settings::setBorderSize);

        // sound options
        silence.ifPresent(settings::setMutedSound);
        silence.filter(Boolean.FALSE::equals)
                .ifPresent(silent -> {
                    ayEnabled.ifPresent(settings::setAYEnabled48K);
                    hifi.ifPresent(settings::setHifiSound);
                    AY8912Type ay8912Settings = speccySettings.getAY8912Settings();
                    soundMode.map(SoundMode::ordinal).ifPresent(ay8912Settings::setSoundMode);
                });

        // Tape Options
        TapeSettingsType tapeSettings = speccySettings.getTapeSettings();
        loadTrap.ifPresent(tapeSettings::setEnableLoadTraps);
        fastload.ifPresent(tapeSettings::setFlashLoad);
        acceleratedLoading.ifPresent(al -> tapeSettings.setAccelerateLoading(!al));
        autoload.ifPresent(al -> tapeSettings.setAutoLoadTape(!al));
        saveTrap.ifPresent(st -> tapeSettings.setEnableSaveTraps(!st));

        // Emulator options
        EmulatorSettingsType emulatorSettings = speccySettings.getEmulatorSettings();
        confirmActions.ifPresent(ca -> emulatorSettings.setConfirmActions(!ca));
    }

}
