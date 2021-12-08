/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package snapshots;

import z80core.Z80.IntMode;

/**
 *
 * @author jsanchez
 */
public class Z80State {
    // Acumulador y resto de registros de 8 bits
    private int regA, regB, regC, regD, regE, regH, regL;
    // Flags sIGN, zERO, 5, hALFCARRY, 3, pARITY y ADDSUB (n), carryFlag
    private int regF;
    // La última instrucción modificó los flags
    private boolean flagQ;
    // Acumulador alternativo y flags -- 8 bits
    private int regAx;
    private int regFx;
    // Registros alternativos
    private int regBx, regCx, regDx, regEx, regHx, regLx;
    // Registros de propósito específico
    // *PC -- Program Counter -- 16 bits*
    private int regPC;
    // *IX -- Registro de índice -- 16 bits*
    private int regIX;
    // *IY -- Registro de índice -- 16 bits*
    private int regIY;
    // *SP -- Stack Pointer -- 16 bits*
    private int regSP;
    // *I -- Vector de interrupción -- 8 bits*
    private int regI;
    // *R -- Refresco de memoria -- 7 bits*
    private int regR;
    //Flip-flops de interrupción
    private boolean ffIFF1 = false;
    private boolean ffIFF2 = false;
    // EI solo habilita las interrupciones DESPUES de ejecutar la
    // siguiente instrucción (excepto si la siguiente instrucción es un EI...)
    private boolean pendingEI = false;
    // Estado de la línea NMI
    private boolean activeNMI = false;
    // Si está activa la línea INT
    // En el 48 la línea INT se activa durante 32 ciclos de reloj
    // En el 128 y superiores, se activa 36 ciclos de reloj
    private boolean activeINT = false;
    // Modos de interrupción
    private IntMode modeINT = IntMode.IM0;
    // halted == true cuando la CPU está ejecutando un HALT (28/03/2010)
    private boolean halted = false;
    /**
     * Registro interno que usa la CPU de la siguiente forma
     *
     * ADD HL,xx      = Valor del registro H antes de la suma
     * LD r,(IX/IY+d) = Byte superior de la suma de IX/IY+d
     * JR d           = Byte superior de la dirección de destino del salto
     *
     * 04/12/2008     No se vayan todavía, aún hay más. Con lo que se ha
     *                implementado hasta ahora parece que funciona. El resto de
     *                la historia está contada en:
     *                http://zx.pk.ru/attachment.php?attachmentid=2989
     *
     * 25/09/2009     Se ha completado la emulación de MEMPTR. A señalar que
     *                no se puede comprobar si MEMPTR se ha emulado bien hasta
     *                que no se emula el comportamiento del registro en las
     *                instrucciones CPI y CPD. Sin ello, todos los tests de
     *                z80tests.tap fallarán aunque se haya emulado bien al
     *                registro en TODAS las otras instrucciones.
     *                Shit yourself, little parrot.
     */
    private int memptr;

    public Z80State() {
    }

    // Acceso a registros de 8 bits
    public final int getRegA() {
        return regA;
    }

    public final void setRegA(int value) {
        regA = value & 0xff;
    }

    public final int getRegF() {
        return regF;
    }

    public final void setRegF(int value) {
        regF = value & 0xff;
    }

    public final int getRegB() {
        return regB;
    }

    public final void setRegB(int value) {
        regB = value & 0xff;
    }

    public final int getRegC() {
        return regC;
    }

    public final void setRegC(int value) {
        regC = value & 0xff;
    }

    public final int getRegD() {
        return regD;
    }

    public final void setRegD(int value) {
        regD = value & 0xff;
    }

    public final int getRegE() {
        return regE;
    }

    public final void setRegE(int value) {
        regE = value & 0xff;
    }

    public final int getRegH() {
        return regH;
    }

    public final void setRegH(int value) {
        regH = value & 0xff;
    }

    public final int getRegL() {
        return regL;
    }

    public final void setRegL(int value) {
        regL = value & 0xff;
    }

    // Acceso a registros alternativos de 8 bits
    public final int getRegAx() {
        return regAx;
    }

    public final void setRegAx(int value) {
        regAx = value & 0xff;
    }

    public final int getRegFx() {
        return regFx;
    }

    public final void setRegFx(int value) {
        regFx = value & 0xff;
    }

    public final int getRegBx() {
        return regBx;
    }

    public final void setRegBx(int value) {
        regBx = value & 0xff;
    }

    public final int getRegCx() {
        return regCx;
    }

    public final void setRegCx(int value) {
        regCx = value & 0xff;
    }

    public final int getRegDx() {
        return regDx;
    }

    public final void setRegDx(int value) {
        regDx = value & 0xff;
    }

    public final int getRegEx() {
        return regEx;
    }

    public final void setRegEx(int value) {
        regEx = value & 0xff;
    }

    public final int getRegHx() {
        return regHx;
    }

    public final void setRegHx(int value) {
        regHx = value & 0xff;
    }

    public final int getRegLx() {
        return regLx;
    }

    public final void setRegLx(int value) {
        regLx = value & 0xff;
    }

    // Acceso a registros de 16 bits
    public final int getRegAF() {
        return (regA << 8) | regF;
    }

    public final void setRegAF(int word) {
        regA = (word >>> 8) & 0xff;

        regF = word & 0xff;
    }

    public final int getRegAFx() {
        return (regAx << 8) | regFx;
    }

    public final void setRegAFx(int word) {
        regAx = (word >>> 8) & 0xff;
        regFx = word & 0xff;
    }

    public final int getRegBC() {
        return (regB << 8) | regC;
    }

    public final void setRegBC(int word) {
        regB = (word >>> 8) & 0xff;
        regC = word & 0xff;
    }

    public final int getRegBCx() {
        return (regBx << 8) | regCx;
    }

    public final void setRegBCx(int word) {
        regBx = (word >>> 8) & 0xff;
        regCx = word & 0xff;
    }

    public final int getRegDE() {
        return (regD << 8) | regE;
    }

    public final void setRegDE(int word) {
        regD = (word >>> 8) & 0xff;
        regE = word & 0xff;
    }

    public final int getRegDEx() {
        return (regDx << 8) | regEx;
    }

    public final void setRegDEx(int word) {
        regDx = (word >>> 8) & 0xff;
        regEx = word & 0xff;
    }

    public final int getRegHL() {
        return (regH << 8) | regL;
    }

    public final void setRegHL(int word) {
        regH = (word >>> 8) & 0xff;
        regL = word & 0xff;
    }

    public final int getRegHLx() {
        return (regHx << 8) | regLx;
    }

    public final void setRegHLx(int word) {
        regHx = (word >>> 8) & 0xff;
        regLx = word & 0xff;
    }

    // Acceso a registros de propósito específico
    public final int getRegPC() {
        return regPC;
    }

    public final void setRegPC(int address) {
        regPC = address & 0xffff;
    }

    public final int getRegSP() {
        return regSP;
    }

    public final void setRegSP(int word) {
        regSP = word & 0xffff;
    }

    public final int getRegIX() {
        return regIX;
    }

    public final void setRegIX(int word) {
        regIX = word & 0xffff;
    }

    public final int getRegIY() {
        return regIY;
    }

    public final void setRegIY(int word) {
        regIY = word & 0xffff;
    }

    public final int getRegI() {
        return regI;
    }

    public final void setRegI(int value) {
        regI = value & 0xff;
    }

    public final int getRegR() {
        return regR;
    }

    public final void setRegR(int value) {
        regR = value & 0xff;
    }

    // Acceso al registro oculto MEMPTR
    public final int getMemPtr() {
        return memptr;
    }

    public final void setMemPtr(int word) {
        memptr = word & 0xffff;
    }

    // Acceso a los flip-flops de interrupción
    public final boolean isIFF1() {
        return ffIFF1;
    }

    public final void setIFF1(boolean state) {
        ffIFF1 = state;
    }

    public final boolean isIFF2() {
        return ffIFF2;
    }

    public final void setIFF2(boolean state) {
        ffIFF2 = state;
    }

    public final boolean isNMI() {
        return activeNMI;
    }

    public final void setNMI(boolean nmi) {
        activeNMI = nmi;
    }

    // La línea de NMI se activa por impulso, no por nivel
    public final void triggerNMI() {
        activeNMI = true;
    }

    // La línea INT se activa por nivel
    public final boolean isINTLine() {
        return activeINT;
    }

    public final void setINTLine(boolean intLine) {
        activeINT = intLine;
    }

    //Acceso al modo de interrupción
    public final IntMode getIM() {
        return modeINT;
    }

    public final void setIM(IntMode mode) {
        modeINT = mode;
    }

    public final boolean isHalted() {
        return halted;
    }

    public void setHalted(boolean state) {
        halted = state;
    }

    public final boolean isPendingEI() {
        return pendingEI;
    }

    public final void setPendingEI(boolean state) {
        pendingEI = state;
    }

    /**
     * @return the flagQ
     */
    public boolean isFlagQ() {
        return flagQ;
    }

    /**
     * @param flagQ the flagQ to set
     */
    public void setFlagQ(boolean flagQ) {
        this.flagQ = flagQ;
    }
}
