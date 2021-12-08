/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package snapshots;

import machine.Keyboard.JoystickModel;
import machine.MachineTypes;

/**
 *
 * @author jsanchez
 */
public class SpectrumState {
    private MachineTypes spectrumModel;
    private Z80State z80;
    private MemoryState memory;
    private AY8912State ay8912;
    private int tstates, portFE, earBit, port7ffd, port1ffd, portFD;
    private byte numMicrodrives = 1;
    private boolean ULAPlusEnabled, ULAPlusActive, issue2, multiface, connectedLec;
    private boolean connectedIF1, enabledAY, enabledAYon48k;
    private JoystickModel joystick;
    // Color palette
    private int ULAPlusPalette[];
    // Palette group
    private int paletteGroup;

    public SpectrumState () {
    }

    /**
     * @return the spectrumModel
     */
    public MachineTypes getSpectrumModel() {
        return spectrumModel;
    }

    /**
     * @param spectrumModel the spectrumModel to set
     */
    public void setSpectrumModel(MachineTypes spectrumModel) {
        this.spectrumModel = spectrumModel;
    }

    /**
     * @return the z80
     */
    public Z80State getZ80State() {
        return z80;
    }

    /**
     * @param z80 the z80 to set
     */
    public void setZ80State(Z80State z80) {
        this.z80 = z80;
    }

    /**
     * @return the memory
     */
    public MemoryState getMemoryState() {
        return memory;
    }

    /**
     * @param memory the memory to set
     */
    public void setMemoryState(MemoryState memory) {
        this.memory = memory;
    }

    /**
     * @return the ay8912
     */
    public AY8912State getAY8912State() {
        return ay8912;
    }

    /**
     * @param ay8912 the ay8912 to set
     */
    public void setAY8912State(AY8912State ay8912) {
        this.ay8912 = ay8912;
    }

    /**
     * @return the tstates
     */
    public int getTstates() {
        return tstates;
    }

    /**
     * @param tstates the tstates to set
     */
    public void setTstates(int tstates) {
        this.tstates = tstates;
    }

    /**
     * @return the earBit
     */
    public int getEarBit() {
        return earBit;
    }

    /**
     * @param earBit the earBit to set
     */
    public void setEarBit(int earBit) {
        this.earBit = earBit & 0xff;
    }

    /**
     * @return the port7ffd
     */
    public int getPort7ffd() {
        return port7ffd;
    }

    /**
     * @param port7ffd the port7ffd to set
     */
    public void setPort7ffd(int port7ffd) {
        this.port7ffd = port7ffd & 0xff;
    }

    /**
     * @return the port1ffd
     */
    public int getPort1ffd() {
        return port1ffd;
    }

    /**
     * @param port1ffd the port1ffd to set
     */
    public void setPort1ffd(int port1ffd) {
        this.port1ffd = port1ffd & 0xff;
    }

    /**
     * @return the ULAPlusEnabled
     */
    public boolean isULAPlusEnabled() {
        return ULAPlusEnabled;
    }

    /**
     * @param ULAplusOn the ULAPlusEnabled to set
     */
    public void setULAPlusEnabled(boolean ULAplusOn) {
        this.ULAPlusEnabled = ULAplusOn;
    }

    /**
     * @return the ULAPlusActive
     */
    public boolean isULAPlusActive() {
        return ULAPlusActive;
    }

    /**
     * @param ULAPlusActive the ULAPlusActive to set
     */
    public void setULAPlusActive(boolean ULAPlusActive) {
        this.ULAPlusActive = ULAPlusActive;
    }

    /**
     * @return the paletteGroup
     */
    public int getPaletteGroup() {
        return paletteGroup;
    }

    /**
     * @param paletteGroup the paletteGroup to set
     */
    public void setPaletteGroup(int paletteGroup) {
        this.paletteGroup = paletteGroup & 0xff;
    }

    /**
     * @return the ULAPlusPalette
     */
    public int[] getULAPlusPalette() {
        return ULAPlusPalette;
    }

    /**
     * @param ULAPlusPalette the UlaPlusPalette to set
     */
    public void setULAPlusPalette(int[] UlaPlusPalette) {
        this.ULAPlusPalette = UlaPlusPalette;
    }

    /**
     * @return the issue2
     */
    public boolean isIssue2() {
        return issue2;
    }

    /**
     * @param issue2 the issue2 to set
     */
    public void setIssue2(boolean issue2) {
        this.issue2 = issue2;
    }

    /**
     * @return the multiface
     */
    public boolean isMultiface() {
        return multiface;
    }

    /**
     * @param multiface the multiface to set
     */
    public void setMultiface(boolean multiface) {
        this.multiface = multiface;
    }

    /**
     * @return the connectedIF1
     */
    public boolean isConnectedIF1() {
        return connectedIF1;
    }

    /**
     * @param connectedIF1 the connectedIF1 to set
     */
    public void setConnectedIF1(boolean connectedIF1) {
        this.connectedIF1 = connectedIF1;
    }

    /**
     * @return the numMicrodrives
     */
    public byte getNumMicrodrives() {
        return numMicrodrives;
    }

    /**
     * @param numMicrodrives the numMicrodrives to set
     */
    public void setNumMicrodrives(byte numMicrodrives) {
        if (numMicrodrives < 1 || numMicrodrives > 8)
            numMicrodrives = 8;

        this.numMicrodrives = numMicrodrives;
    }

    /**
     * @return the joystick
     */
    public JoystickModel getJoystick() {
        return joystick;
    }

    /**
     * @param joystick the joystick to set
     */
    public void setJoystick(JoystickModel joystick) {
        this.joystick = joystick;
    }

    /**
     * @return the portFE
     */
    public int getPortFE() {
        return portFE;
    }

    /**
     * @param portFE the portFE to set
     */
    public void setPortFE(int portFE) {
        this.portFE = portFE;
    }

    /**
     * @return the border colour
     */
    public int getBorder() {
        return portFE & 0x07;
    }

    /**
     * @param portFE the border colour
     */
    public void setBorder(int color) {
        portFE &= 0xF8;
        this.portFE |= color;
    }

    /**
     * @return the enabledAY
     */
    public boolean isEnabledAY() {
        return enabledAY;
    }

    /**
     * @param ayEnabled the enabledAY to set
     */
    public void setEnabledAY(boolean ayEnabled) {
        this.enabledAY = ayEnabled;
    }

    /**
     * @return the enabledAYon48k
     */
    public boolean isEnabledAYon48k() {
        return enabledAYon48k;
    }

    /**
     * @param enabledAYon48k the enabledAYon48k to set
     */
    public void setEnabledAYon48k(boolean enabledAYon48k) {
        this.enabledAYon48k = enabledAYon48k;
    }

    /**
     * @return the connectedLec
     */
    public boolean isConnectedLec() {
        return connectedLec;
    }

    /**
     * @param connectedLec the connectedLec to set
     */
    public void setConnectedLec(boolean connectedLec) {
        this.connectedLec = connectedLec;
    }

    /**
     * @return the portFD
     */
    public int getPortFD() {
        return portFD;
    }

    /**
     * @param portFD the portFD to set
     */
    public void setPortFD(int portFD) {
        this.portFD = portFD;
    }
}
