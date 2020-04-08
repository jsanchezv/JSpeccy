package configuration;

import machine.Spectrum;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class JSpeccySettingsTest {

    @Test
    public void unmarshal()
            throws JAXBException {

        JAXBContext jc = JAXBContext.newInstance(
                JSpeccySettings.class.getPackage().getName());

        Unmarshaller unmsh = jc.createUnmarshaller();

        JSpeccySettings settings = (JSpeccySettings) unmsh.unmarshal(
                Spectrum.class.getResourceAsStream("/schema/JSpeccy.xml"));
        //
        assertFalse(settings.spectrumSettings.ayEnabled48K);

        assertEquals("spectrum.rom", settings.memorySettings.rom48K);

        assertTrue(settings.tapeSettings.enableLoadTraps);

        assertEquals(0, settings.keyboardJoystickSettings.joystickModel);

        assertEquals(0, settings.ay8912Settings.soundMode);

        assertTrue(settings.recentFilesSettings.lastTapeDir.isEmpty());

        assertFalse(settings.interface1Settings.connectedIF1);

        assertTrue(settings.emulatorSettings.confirmActions);
    }
}
