package joystickinput;

import org.junit.Test;

import static org.junit.Assert.assertTrue;


public class JoystickRawTest {

    @Test
    public void loadLibrary() {
        if (System.getProperty("os.name").contains("Linux")) {
            assertTrue(JoystickRaw.isHelperLoaded());
        }
    }
}