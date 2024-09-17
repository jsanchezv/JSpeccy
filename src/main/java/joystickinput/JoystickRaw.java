/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package joystickinput;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author jsanchez
 */
@Slf4j
public class JoystickRaw implements Runnable {

    private static boolean helperLoaded;
    static {
        try {
            if (System.getProperty("os.arch").contains("amd64"))
                System.loadLibrary("JoystickHelper-64");
            else
                System.loadLibrary("JoystickHelper-32");
            helperLoaded = true;
        } catch (UnsatisfiedLinkError ex) {
            helperLoaded = false;
        }
    }
    
    private static final int EVENT_BUFFER = 64;
    private static final int STRUCT_EVENT_SIZE = 8;
    private static final int JOYSTICK_BUFFER_SIZE = EVENT_BUFFER * STRUCT_EVENT_SIZE;
    private byte eventBuffer[] = new byte[JOYSTICK_BUFFER_SIZE];

    private static final int TIMESTAMP_OFFSET = 0;
    private static final int VALUE_OFFSET = 4;
    private static final int TYPE_OFFSET = 6;
    private static final int NUMBER_OFFSET = 7;
    
    private static final int JS_EVENT_BUTTON = 0x01;  /* button pressed/released */
    private static final int JS_EVENT_AXIS = 0x02;    /* joystick moved */
    private static final int JS_EVENT_INIT = 0x80;    /* initial state of device */
    private static final int JS_EVENT_INIT_BUTTON = JS_EVENT_BUTTON | JS_EVENT_INIT;
    private static final int JS_EVENT_INIT_AXIS = JS_EVENT_AXIS | JS_EVENT_INIT;
    
    private static final int MAX_BUTTONS = 32;
    private static final int MAX_AXIS = 32;
    private final boolean buttonState[] = new boolean[MAX_BUTTONS];
    private final int buttonMasks[] = new int[MAX_BUTTONS];
    private final short axisValue[] = new short[MAX_AXIS];
    private int numButtons, numAxis, buttonMask, joystickId;
    private String joystickName;

    private boolean run;
    
    private final ArrayList<JoystickRawListener> buttonListeners;
    private final ArrayList<JoystickRawListener> axisListeners;
    
    private String deviceName;
    public JoystickRaw(int id) throws FileNotFoundException, IOException {
        deviceName = "/dev/input/js" + id;
        joystickId = id;
        buttonListeners = new ArrayList<>();
        axisListeners = new ArrayList<>();
        
        for (int idx = 0; idx < buttonState.length; idx++) {
            buttonMasks[idx] = (1 << idx);
        }
        
        if (helperLoaded) {
            numButtons = getNumButtonsHelper(id);
            numAxis = getNumAxisHelper(id);
            joystickName = toStringHelper(id);
            log.info("From JNI: buttons {}, axis {}", numButtons, numAxis);
            log.info("From JNI: joystick name: {}", joystickName);
        }

        if (!helperLoaded || numButtons == -1) {
            joystickName = "Linux Joystick Driver";
            int count = numButtons = numAxis = 0;
            try (DataInputStream deviceData = new DataInputStream(new FileInputStream(deviceName))) {
                count = deviceData.read(eventBuffer);
                if (count == -1) {
                    return;
                }
            } catch (IOException ex) {
//            Logger.getLogger(JoystickRaw.class.getName()).log(Level.SEVERE, null, ex);
                throw new FileNotFoundException(String.format("Joystick device %s not found", deviceName));
            }

            for (int idx = 0; idx < count; idx += STRUCT_EVENT_SIZE) {
                int type = eventBuffer[idx + TYPE_OFFSET] & 0xff;
//                int number = eventBuffer[idx + NUMBER_OFFSET] & 0xff;
//                short value = (short) ((eventBuffer[idx + VALUE_OFFSET + 1] << 8) | (eventBuffer[idx + VALUE_OFFSET] & 0xff));
                switch (type) {
                    case JS_EVENT_BUTTON:
                    case JS_EVENT_AXIS:
                        break;
                    case JS_EVENT_INIT_BUTTON:
//                        System.out.println(String.format("Event INIT received for button [%d]. Value = [%d]", number, value));
                        numButtons++;
                        break;
                    case JS_EVENT_INIT_AXIS:
//                        System.out.println(String.format("Event INIT received for axis [%d]. Value = [%d]", number, value));
                        numAxis++;
                        break;
                    default:
                        System.out.println("Unknown event received!");
                }
            }
        }

        if (numButtons > 32) {
            numButtons = 32;
        }
        
        if (numAxis > 32) {
            numAxis = 32;
        }
    }

    /**
     * Adds a new event listener to the list of event listeners.
     *
     * @param listener The new event listener.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     */
    public void addButtonListener(final JoystickRawListener listener) {

        Objects.requireNonNull(listener, "Internal Error: Listener can't be null");

        // Avoid duplicates
        if (!buttonListeners.contains(listener)) {
            buttonListeners.add(listener);
        }
    }

    /**
     * Remove a new event listener from the list of event listeners.
     *
     * @param listener The event listener to remove.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     * @throws IllegalArgumentException Thrown if the listener wasn't registered.
     */
    public void removeButtonListener(final JoystickRawListener listener) {

        Objects.requireNonNull(listener, "Internal Error: Listener can't be null");

        if (!buttonListeners.remove(listener)) {
            throw new IllegalArgumentException("Internal Error: Listener was not listening on object");
        }
    }
    
    /**
     * Adds a new event listener to the list of event listeners.
     *
     * @param listener The new event listener.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     */
    public void addAxisListener(final JoystickRawListener listener) {

        Objects.requireNonNull(listener, "Internal Error: Listener can't be null");

        // Avoid duplicates
        if (!axisListeners.contains(listener)) {
            axisListeners.add(listener);
        }
    }

    /**
     * Remove a new event listener from the list of event listeners.
     *
     * @param listener The event listener to remove.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     * @throws IllegalArgumentException Thrown if the listener wasn't registered.
     */
    public void removeAxisListener(final JoystickRawListener listener) {

        Objects.requireNonNull(listener, "Internal Error: Listener can't be null");

        if (!axisListeners.remove(listener)) {
            throw new IllegalArgumentException("Internal Error: Listener was not listening on object");
        }
    }

    @Override
    public void run() {

        try (DataInputStream deviceData = new DataInputStream(new FileInputStream(deviceName))) {
            while (run) {
                int count = deviceData.read(eventBuffer);
                if (count == -1) {
                    break;
                }

//                if (count > STRUCT_EVENT_SIZE)
//                    System.out.println(String.format("Readed %d events at once", count >>> 3));
                for (int idx = 0; idx < count; idx += STRUCT_EVENT_SIZE) {
                    int type = eventBuffer[idx + TYPE_OFFSET] & 0xff;
                    int number = eventBuffer[idx + NUMBER_OFFSET] & 0xff;

                    switch (type) {
                        case JS_EVENT_BUTTON:
                        case JS_EVENT_INIT_BUTTON:
//                            System.out.println(String.format("Button [%d] pressed. Value = [%d]", number, value));
                            boolean state = eventBuffer[idx + VALUE_OFFSET] != 0;
                            synchronized (buttonState) {
                                buttonState[number] = state;
                                if (state)
                                    buttonMask |= buttonMasks[number];
                                else
                                    buttonMask &= ~buttonMasks[number];
                            }

                            if (type == JS_EVENT_BUTTON) {
                                for (final JoystickRawListener listener : buttonListeners) {
                                    listener.buttonEvent(joystickId, number, state);
                                }
                            }
                            break;
                        case JS_EVENT_AXIS:
                        case JS_EVENT_INIT_AXIS:
                            short value = (short)
                                ((eventBuffer[idx + VALUE_OFFSET + 1] << 8) | (eventBuffer[idx + VALUE_OFFSET] & 0xff));
//                            System.out.println(String.format("Axis [%d] pressed. Value = [%d]", number, value));
                            synchronized (axisValue) {
                                axisValue[number] = value;
                            }

                            if (type == JS_EVENT_AXIS) {
                                for (final JoystickRawListener listener : axisListeners) {
                                    listener.axisEvent(joystickId, number, value);
                                }
                            }
                            break;
                        default:
                            log.warn("Unknown event received!");
                    }
                }

            }
        } catch (IOException ex) {
            log.error("Error reading joystick device", ex);
        }
    }
    
    public void start() {
        if (run)
            return;

        run = true;
        new Thread(this, "JoystickThread " + deviceName).start();
    }

    public void stop() {
        run = false;
    }

    public int getButtonMask() {
        synchronized (buttonState) {
            return buttonMask;
        }
    }
    /**
     * @param index
     * @return the buttonState
     */
    public boolean getButtonState(int index) {
        if (index < 0 || index >= numButtons) {
            throw new IndexOutOfBoundsException("Button out of range");
        }

        synchronized (buttonState) {
            return buttonState[index];
        }
    }

    /**
     * @param index
     * @return the axisValue
     */
    public short getAxisValue(int index) {
        if (index < 0 || index >= numAxis) {
            throw new IndexOutOfBoundsException("Axis out of range");
        }

        synchronized (axisValue) {
            return axisValue[index];
        }
    }

    private native int getNumButtonsHelper(int joystickId);
    /**
     * @return the numButtons
     */
    public int getNumButtons() {
        return numButtons;
    }

    private native int getNumAxisHelper(int joystickId);
    /**
     * @return the numAxis
     */
    public int getNumAxis() {
        return numAxis;
    }

    private native String toStringHelper(int joystickId);
    @Override
    public String toString() {
        return joystickName;
    }
    /**
     * @return the joystickId
     */
    public int getJoystickId() {
        return joystickId;
    }
}
