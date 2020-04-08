package joystickinput;

import org.junit.Test;

import static org.junit.Assert.*;


public class JoystickRawTest {

    @Test
    public void loadLibrary() {
        assertTrue(JoystickRaw.isHelperLoaded());
    }
}