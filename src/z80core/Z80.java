//-----------------------------------------------------------------------------
//Title:        Emulador en Java de un Sinclair ZX Spectrum 48K
//Version:      1.0 B
//Copyright:    Copyright (c) 2004
//Author:       Alberto Sánchez Terrén
//Clase:        Z80.java
//Descripción:  La clase Z80 es la más extensa de todas ya que debe implementar
//				la estructura del microprocesador Z80 y la ejecución de todas
//				las instrucciones del repertorio del mismo.
//-----------------------------------------------------------------------------
/*
 * Versión: 2.0
 * Autor:   José Luis Sánchez Villanueva
 *
 * Notas:   09/01/2008 pasa los 68 tests de ZEXALL, con lo que se supone
 *          que realiza una implementación correcta de la Z80.
 * 
 *          14/01/2008 pasa también los tests de fuse 0.10, exceptuando
 *          los que fuse no implementa bien (BIT n,(HL)).
 *          Del resto, cumple con los contenidos de los registros, flags,
 *          y t-estados.
 * 
 *          15/01/2008 faltaban los flags de las instrucciones IN r,(C).
 *
 *          03/12/2008 se descomponen las instrucciones para poder
 *          implementar la contended-memory del Spectrum.
 *
 *          21/09/2009 modificación a lo bestia del emulador. Los flags
 *          se convierten de boolean a bits en un int. El único
 *          que se deja como estaba es el carryFlag. Ahora los
 *          flags se sacan de tablas precalculadas.
 *
 *          22/09/2009 Optimizado el tratamiento del HALFCARRY_FLAG en los
 *          métodos add16/add/sub/cp.
 *
 *          23/09/2009 Los métodos de más 8000 bytecodes no son compilados
 *          por el JIT a menos que se obliguemos a ello, cosa poco aconsejable.
 *          El método decodeDDFDCD original tenía más de 12000 bytecodes, así que
 *          se subdivide en 2 métodos que procesan los códigos por rangos:
 *          0x00-0x7F y 0x80-0xFF quedando todos por debajo de los 7000 bytecodes.
 *
 *          25/09/2009 Se completa la emulación del registro interno MEMPTR.
 *          Ahora el core-Z80 supera todos los test de MEMPTR del z80tests.tap.
 *          (http://homepage.ntlworld.com/mark.woodmass/z80tests.tap)
 *          Mis agradecimientos a ·The Woodster· por el programa, a Boo-boo que
 *          investigo el funcionamiento de MEMPTR y a Vladimir Kladov por
 *          traducir al inglés el documento original.
 *
 *          02/10/2009 Se modifica el core para que soporte el retriggering de
 *          interrupciones, cosa que en realidad, estaba pensada desde el
 *          principio.
 *
 *          28/03/2010 Se corrige un problema con el manejo de las interrupciones.
 *          Para ello es necesario introducir el flag 'halted'. El problema surgía
 *          únicamente cuando la INT se producía cuando la instrucción a la que
 *          apunta PC es un HALT pero éste aún no se ha ejecutado. En ese caso,
 *          el HALT se ejecutará a la vuelta de la interrupción. Hasta ahora,
 *          la dirección de retorno de la INT que se guardaba en el stack era la
 *          de PC+1 como si el HALT ya se hubiera ejecutado, cuando esto último
 *          era falso. Gracias a Woodster por el programa de test y por informarme
 *          del fallo. Thanks!, Woodster. :)
 *          Creo también los métodos isHalted/setHalted para acceder al flag. De paso,
 *          duplico el método push para que tenga dos parámetros y pdoer usarla así
 *          con los registros de propósito general, para que sea más rápido.
 *
 */
package z80core;

import java.util.Arrays;

public class Z80 {

    private final MemIoOps MemIoImpl;
    // Número de estados T que se llevan ejecutados
    public int tEstados;

    private boolean execDone;

    //Código de la instrucción a ejecutar
    private int opCode;

    // Posiciones de los flags
    private static final int CARRY_MASK = 0x01;
    private static final int ADDSUB_MASK = 0x02;
    private static final int PARITY_MASK = 0x04;
    private static final int OVERFLOW_MASK = 0x04; // alias de PARITY_MASK
    private static final int BIT3_MASK = 0x08;
    private static final int HALFCARRY_MASK = 0x10;
    private static final int BIT5_MASK = 0x20;
    private static final int ZERO_MASK = 0x40;
    private static final int SIGN_MASK = 0x80;
    // Máscaras de conveniencia
    private static final int FLAG_53_MASK = BIT5_MASK | BIT3_MASK;
    private static final int FLAG_SZ_MASK = SIGN_MASK | ZERO_MASK;
    private static final int FLAG_SZHN_MASK = FLAG_SZ_MASK | HALFCARRY_MASK | ADDSUB_MASK;
    private static final int FLAG_SZP_MASK = FLAG_SZ_MASK | PARITY_MASK;
    private static final int FLAG_SZHP_MASK = FLAG_SZP_MASK | HALFCARRY_MASK;

    // Acumulador y resto de registros de 8 bits
    private int regA, regB, regC, regD, regE, regH, regL;

    // Flags sIGN, zERO, 5, hALFCARRY, 3, pARITY y ADDSUB (n)
    private int sz5h3pnFlags;
    // El flag Carry es el único que se trata aparte
    private boolean carryFlag;

    // Acumulador alternativo y flags -- 8 bits
    private int regAalt;
    private int flagFalt;
    // Registros alternativos
    private int regBalt, regCalt, regDalt, regEalt, regHalt, regLalt;

    // Registros de propósito específico
    // *PC -- Program Counter -- 16 bits*
    private int regPC;

    // *IX -- Registro de índice -- 16 bits*
    //private IndexRegister IX = new IndexRegister();
    private int regIX;

    // *IY -- Registro de índice -- 16 bits*
    //private IndexRegister IY = new IndexRegister();
    private int regIY;

    // *SP -- Stack Pointer -- 16 bits*
    private int regSP;

    // *I -- Vector de interrupción -- 8 bits*
    private int regI;

    // *R -- Refresco de memoria -- 7 bits*
    private int regR;

    // *R7 -- Refresco de memoria -- 1 bit* (bit superior de R)
    private boolean regRbit7;

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

    // Modo de interrupción
    private int modeINT = 0;

    // Posibles modos de interrupción
    public final int IM0 = 0;
    public final int IM1 = 1;
    public final int IM2 = 2;

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
     *                Shit you, little parrot.
     */
    private int memptr;

    /* Algunos flags se precalculan para un tratamiento más rápido
     * Concretamente, SIGN, ZERO, los bits 3, 5, PARITY y ADDSUB:
     * sz53n_addTable tiene el ADDSUB flag a 0 y paridad sin calcular
     * sz53pn_addTable tiene el ADDSUB flag a 0 y paridad calculada
     * sz53n_subTable tiene el ADDSUB flag a 1 y paridad sin calcular
     * sz53pn_subTable tiene el ADDSUB flag a 1 y paridad calculada
     * El resto de bits están a 0 en las cuatro tablas lo que es
     * importante para muchas operaciones que ponen ciertos flags a 0 por real
     * decreto. Si lo ponen a 1 por el mismo método basta con hacer un OR con
     * la máscara correspondiente.
     */
    private static final int sz53n_addTable[] = new int[256];
    private static final int sz53pn_addTable[] = new int[256];
    private static final int sz53n_subTable[] = new int[256];
    private static final int sz53pn_subTable[] = new int[256];
    static {
        boolean evenBits;
        Arrays.fill(sz53n_addTable, 0);
        Arrays.fill(sz53n_subTable, 0);
        Arrays.fill(sz53pn_addTable, 0);
        Arrays.fill(sz53pn_subTable, 0);

        for (int idx = 0; idx < 256; idx++) {
            if( idx > 0x7f )
                sz53n_addTable[idx] |= SIGN_MASK;

            evenBits = true;
            for (int mask = 0x01; mask < 0x100; mask <<= 1) {
                if ((idx & mask) != 0)
                    evenBits = !evenBits;
            }

            sz53n_addTable[idx] |= (idx & FLAG_53_MASK);
            sz53n_subTable[idx] = sz53n_addTable[idx] | ADDSUB_MASK;

            if( evenBits ) {
                sz53pn_addTable[idx] = sz53n_addTable[idx] | PARITY_MASK;
                sz53pn_subTable[idx] = sz53n_subTable[idx] | PARITY_MASK;
            } else {
                sz53pn_addTable[idx] = sz53n_addTable[idx];
                sz53pn_subTable[idx] = sz53n_subTable[idx];
            }
        }
        sz53n_addTable[0] |= ZERO_MASK;
        sz53pn_addTable[0] |= ZERO_MASK;
        sz53n_subTable[0] |= ZERO_MASK;
        sz53pn_subTable[0] |= ZERO_MASK;
    }

    // Constructor de la clase
    public Z80(MemIoOps memoria) {
        MemIoImpl = memoria;
        tEstados = 0;
        execDone = false;
        reset();
    }

    // Acceso a registros de 8 bits
    public final int getRegA() {
        return regA;
    }

    public final void setRegA(int valor) {
        regA = valor & 0xff;
    }

    public final int getRegB() {
        return regB;
    }

    public final void setRegB(int valor) {
        regB = valor & 0xff;
    }

    public final int getRegC() {
        return regC;
    }

    public final void setRegC(int valor) {
        regC = valor & 0xff;
    }

    public final int getRegD() {
        return regD;
    }

    public final void setRegD(int valor) {
        regD = valor & 0xff;
    }

    public final int getRegE() {
        return regE;
    }

    public final void setRegE(int valor) {
        regE = valor & 0xff;
    }

    public final int getRegH() {
        return regH;
    }

    public final void setRegH(int valor) {
        regH = valor & 0xff;
    }

    public final int getRegL() {
        return regL;
    }

    public final void setRegL(int valor) {
        regL = valor & 0xff;
    }

    // Acceso a registros de 16 bits
    public final int getRegAF() {
        return (regA << 8) | getFlags();
    }

    public final void setRegAF(int valor) {
        regA = (valor >>> 8) & 0xff;
        setFlags(valor & 0xff);
    }

    public final int getRegAFalt() {
        return (regAalt << 8) | flagFalt;
    }

    public final void setRegAFalt(int valor) {
        regAalt = (valor >>> 8) & 0xff;
        flagFalt = valor & 0xff;
    }

    public final int getRegBC() {
        return((regB << 8) | regC);
    }

    public final void setRegBC(int word) {
        word &= 0xffff;
        regB = word >>> 8;
        regC = word & 0x00ff;
    }

    private final void incRegBC() {
        regC++;
        if( regC < 0x100 )
            return;
        regC = 0;
        regB++;
        if( regB < 0x100 )
            return;
        regB = 0;
    }

    private final void decRegBC() {
        regC--;
        if( regC >= 0 )
            return;
        regC = 0xff;
        regB--;
        if( regB >= 0 )
            return;
        regB = 0xff;
    }

    public final int getRegBCalt() {
        return ((regBalt << 8) | regCalt);
    }

    public final void setRegBCalt(int word) {
        word &= 0xffff;
        regBalt = word >>> 8;
        regCalt = word & 0x00ff;
    }

    public final int getRegDE() {
        return((regD << 8) | regE);
    }

    public final void setRegDE(int word) {
        word &= 0xffff;
        regD = word >>> 8;
        regE = word & 0x00ff;
    }

    private final void incRegDE() {
        regE++;
        if( regE < 0x100 )
            return;
        regE = 0;
        regD++;
        if( regD < 0x100 )
            return;
        regD = 0;
    }

    private final void decRegDE() {
        regE--;
        if( regE >= 0 )
            return;
        regE = 0xff;
        regD--;
        if( regD >= 0 )
            return;
        regD = 0xff;
    }

    public final int getRegDEalt() {
        return ((regDalt << 8) | regEalt);
    }

    public final void setRegDEalt(int word) {
        word &= 0xffff;
        regDalt = word >>> 8;
        regEalt = word & 0x00ff;
    }

    public final int getRegHL() {
        return((regH << 8) | regL);
    }

    public final void setRegHL(int word) {
        word &= 0xffff;
        regH = word >>> 8;
        regL = word & 0x00ff;
    }

    /* Las funciones incRegXX y decRegXX están escritas pensando en que
     * puedan aprovechar el camino más corto aunque tengan un poco más de
     * código (al menos en bytecodes lo tienen)
     */
    private final void incRegHL() {
        regL++;
        if( regL < 0x100 )
            return;
        regL = 0;
        regH++;
        if( regH < 0x100 )
            return;
        regH = 0;
    }

    private final void decRegHL() {
        regL--;
        if( regL >= 0 )
            return;
        regL = 0xff;
        regH--;
        if( regH >= 0 )
            return;
        regH = 0xff;
    }

    public final int getRegHLalt() {
        return ((regHalt << 8) | regLalt);
    }

    public final void setRegHLalt(int word) {
        word &= 0xffff;
        regHalt = word >>> 8;
        regLalt = word & 0x00ff;
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

    public final void setRegIX(int valor) {
        regIX = valor & 0xffff;
    }

    public final int getRegIY() {
        return regIY;
    }

    public final void setRegIY(int valor) {
        regIY = valor & 0xffff;
    }

    public final int getRegI() {
        return regI;
    }

    public final void setRegI(int valor) {
        regI = valor & 0xff;
    }

    public final int getRegR() {
        if( regRbit7 )
            return ((regR & 0x7f) | SIGN_MASK);
        return (regR & 0x7f);
    }

    public final void setRegR(int valor) {
        valor &= 0xff;
        regR = valor;
        regRbit7 = (valor > 0x7f);
    }

    public final int getPairIR() {
        if( regRbit7 )
            return (regI << 8) | ((regR & 0x7f) | SIGN_MASK);
        return (regI << 8) | (regR & 0x7f);
    }

    // Acceso a los flags uno a uno
    public final boolean isCarryFlag() {
        return carryFlag;
    }

    public final void setCarryFlag(boolean valor) {
        carryFlag = valor;
    }

    public final boolean isAddSubFlag() {
        return (sz5h3pnFlags & ADDSUB_MASK) != 0;
    }

    public final void setAddSubFlag(boolean valor) {
        if( valor )
            sz5h3pnFlags |= ADDSUB_MASK;
        else
            sz5h3pnFlags &= ~ADDSUB_MASK;
    }

    public final boolean isParOverFlag() {
        return (sz5h3pnFlags & PARITY_MASK) != 0;
    }

    public final void setParOverFlag(boolean valor) {
        if( valor )
            sz5h3pnFlags |= PARITY_MASK;
        else
            sz5h3pnFlags &= ~PARITY_MASK;
    }

    public final boolean isBit3Flag() {
        return (sz5h3pnFlags & BIT3_MASK) != 0;
    }

    public final void setBit3Fag(boolean valor) {
        if( valor )
            sz5h3pnFlags |= BIT3_MASK;
        else
            sz5h3pnFlags &= ~BIT3_MASK;
    }

    public final boolean isHalfCarryFlag() {
        return (sz5h3pnFlags & HALFCARRY_MASK) != 0;
    }

    public final void setHalfCarryFlag(boolean valor) {
        if( valor )
            sz5h3pnFlags |= HALFCARRY_MASK;
        else
            sz5h3pnFlags &= ~HALFCARRY_MASK;
    }

    public final boolean isBit5Flag() {
        return (sz5h3pnFlags & BIT5_MASK) != 0;
    }

    public final void setBit5Flag(boolean valor) {
        if( valor )
            sz5h3pnFlags |= BIT5_MASK;
        else
            sz5h3pnFlags &= ~BIT5_MASK;
    }

    public final boolean isZeroFlag() {
        return (sz5h3pnFlags & ZERO_MASK) != 0;
    }

    public final void setZeroFlag(boolean valor) {
        if( valor )
            sz5h3pnFlags |= ZERO_MASK;
        else
            sz5h3pnFlags &= ~ZERO_MASK;
    }

    public final boolean isSignFlag() {
        return (sz5h3pnFlags & SIGN_MASK) != 0;
    }

    public final void setSignFlag(boolean valor) {
        if( valor )
            sz5h3pnFlags |= SIGN_MASK;
        else
            sz5h3pnFlags &= ~SIGN_MASK;
    }

    // Acceso a los flags F
    public final int getFlags() {
        int regF = sz5h3pnFlags;

        if( carryFlag )
            regF |= CARRY_MASK;

        return regF;
    }

    public final void setFlags(int regF) {
        sz5h3pnFlags = regF & ~CARRY_MASK;

        if( (regF & CARRY_MASK) != 0 )
            carryFlag = true;
        else
            carryFlag = false;

    }

    // Acceso a los flip-flops de interrupción
    public final boolean isIFF1() {
        return ffIFF1;
    }

    public final void setIFF1(boolean valor) {
        ffIFF1 = valor;
    }

    public final boolean isIFF2() {
        return ffIFF2;
    }

    public final void setIFF2(boolean valor) {
        ffIFF2 = valor;
    }

    // La línea de NMI se activa por impulso, no por nivel
    public final void emitNMI() {
        activeNMI = true;
    }

    // La línea INT se activa por nivel
    public final void setINTLine(boolean intLine) {
        activeINT = intLine;
    }

    //Acceso al modo de interrupción
    public final int getIM() {
        return modeINT;
    }

    public final void setIM(int modo) {
        modeINT = modo;
    }

    public final boolean isHalted() {
        return halted;
    }

    public void setHalted(boolean isHalted) {
        halted = isHalted;
    }

    // Reset
    /* Según el documento de Sean Young, que se encuentra en
     * [http://www.myquest.com/z80undocumented], la mejor manera de emular el
     * reset es poniendo PC, IFF1, IFF2, R e IM0 a 0 y todos los demás registros
     * a 0xFFFF
     */
    public void reset() {
        regPC = 0;
        regSP = 0xffff;

        regA = regAalt = 0xff;
        regB = regBalt = 0xff;
        regC = regCalt = 0xff;
        regD = regDalt = 0xff;
        regE = regEalt = 0xff;
        regH = regHalt = 0xff;
        regL = regLalt = 0xff;

        setFlags(0xff);
        flagFalt = 0xff;

        regIX = 0xffff;
        regIY = 0xffff;

        regI = 0xff;
        regR = 0x00;
        regRbit7 = false;

        ffIFF1 = false;
        ffIFF2 = false;
        pendingEI = false;
        activeNMI = false;
        activeINT = false;
        halted = false;

        setIM(IM0);

        memptr = 0;

        tEstados = 0;
    }

    // Incrementa un valor de 8 bits modificando los flags oportunos
    private final int inc8(int valor) {
        valor++;
        valor &= 0xff;
        sz5h3pnFlags = sz53n_addTable[valor];

        if( (valor & 0x0f) == 0 )
            sz5h3pnFlags |= HALFCARRY_MASK;

        if( valor == 0x80 )
            sz5h3pnFlags |= OVERFLOW_MASK;

        return valor;
    }

    // Decrementa un valor de 8 bits modificando los flags oportunos
    private final int dec8(int valor) {
        valor--;
        valor &= 0xff;
        sz5h3pnFlags = sz53n_subTable[valor];

        if( (valor & 0x0f) == 0x0f )
            sz5h3pnFlags |= HALFCARRY_MASK;

        if( valor == 0x7f )
            sz5h3pnFlags |= OVERFLOW_MASK;

        return valor;
    }

    // Rota a la izquierda el valor del argumento
    // El bit 0 y el flag C toman el valor del bit 7 antes de la operación
    private final int rlc(int aux) {
        carryFlag = (aux > 0x7f);
        aux <<= 1;
        if( carryFlag )
            aux |= CARRY_MASK;
        aux &= 0xff;
        sz5h3pnFlags = sz53pn_addTable[aux];
        return aux;
    }

    // Rota a la izquierda el valor del argumento
    // El bit 7 va al carry flag
    // El bit 0 toma el valor del flag C antes de la operación
    private final int rl(int aux) {
        boolean carry = carryFlag;
        carryFlag = (aux > 0x7f);
        aux <<= 1;
        if( carry )
            aux |= CARRY_MASK;
        aux &= 0xff;
        sz5h3pnFlags = sz53pn_addTable[aux];
        return aux;
    }

    // Rota a la izquierda el valor del argumento
    // El bit 7 va al carry flag
    // El bit 0 toma el valor 0
    private final int sla(int aux) {
        carryFlag = (aux > 0x7f);
        aux <<= 1;
        aux &= 0xfe;
        sz5h3pnFlags = sz53pn_addTable[aux];
        return aux;
    }

    // Rota a la izquierda el valor del argumento (como sla salvo por el bit 0)
    // El bit 7 va al carry flag
    // El bit 0 toma el valor 1
    // Instrucción indocumentada
    private final int sll(int aux) {
        carryFlag = (aux > 0x7f);
        aux <<= 1;
        aux |= CARRY_MASK;
        aux &= 0xff;
        sz5h3pnFlags = sz53pn_addTable[aux];
        return aux;
    }

    // Rota a la derecha el valor del argumento
    // El bit 7 y el flag C toman el valor del bit 0 antes de la operación
    private final int rrc(int aux) {
        carryFlag = (aux & CARRY_MASK) != 0;
        aux >>>= 1;
        if( carryFlag )
            aux |= SIGN_MASK;
        sz5h3pnFlags = sz53pn_addTable[aux];
        return aux;
    }

    // Rota a la derecha el valor del argumento
    // El bit 0 va al carry flag
    // El bit 7 toma el valor del flag C antes de la operación
    private final int rr(int aux) {
        boolean carry = carryFlag;
        carryFlag = (aux & CARRY_MASK) != 0;
        aux >>>= 1;
        if( carry )
            aux |= SIGN_MASK;
        sz5h3pnFlags = sz53pn_addTable[aux];
        return aux;
    }

    // A = A7 A6 A5 A4 (HL)3 (HL)2 (HL)1 (HL)0
    // (HL) = A3 A2 A1 A0 (HL)7 (HL)6 (HL)5 (HL)4
    // Los bits 3,2,1 y 0 de (HL) se copian a los bits 3,2,1 y 0 de A.
    // Los 4 bits bajos que había en A se copian a los bits 7,6,5 y 4 de (HL).
    // Los 4 bits altos que había en (HL) se copian a los 4 bits bajos de (HL)
    // Los 4 bits superiores de A no se tocan. ¡p'habernos matao!
    private final void rrd() {
        int aux = (regA & 0x0f) << 4;
        int regHL = getRegHL();
        int memHL = MemIoImpl.peek8(regHL);
        regA = (regA & 0xf0) | (memHL & 0x0f);
        MemIoImpl.contendedStates(regHL, 4);
        MemIoImpl.poke8(regHL, (memHL >>> 4) | aux);
        sz5h3pnFlags = sz53pn_addTable[regA];
        memptr = (regHL + 1) & 0xffff;
    }

    // A = A7 A6 A5 A4 (HL)7 (HL)6 (HL)5 (HL)4
    // (HL) = (HL)3 (HL)2 (HL)1 (HL)0 A3 A2 A1 A0
    // Los 4 bits bajos que había en (HL) se copian a los bits altos de (HL).
    // Los 4 bits altos que había en (HL) se copian a los 4 bits bajos de A
    // Los bits 3,2,1 y 0 de A se copian a los bits 3,2,1 y 0 de (HL).
    // Los 4 bits superiores de A no se tocan. ¡p'habernos matao!
    private final void rld() {
        int aux = regA & 0x0f;
        int regHL = getRegHL();
        int memHL = MemIoImpl.peek8(regHL);
        regA = (regA & 0xf0) | (memHL >>> 4);
        MemIoImpl.contendedStates(regHL, 4);
        MemIoImpl.poke8(regHL, ((memHL << 4) | aux) & 0xff);
        sz5h3pnFlags = sz53pn_addTable[regA];
        memptr = (regHL + 1) & 0xffff;
    }

    // Rota a la derecha 1 bit el valor del argumento
    // El bit 0 pasa al carry.
    // El bit 7 conserva el valor que tenga
    private final int sra(int aux) {
        int tmp = aux & SIGN_MASK;
        carryFlag = (aux & CARRY_MASK) != 0;
        aux = (aux >> 1) | tmp;
        sz5h3pnFlags = sz53pn_addTable[aux];
        return aux;
    }

    // Rota a la derecha 1 bit el valor del argumento
    // El bit 0 pasa al carry.
    // El bit 7 toma el valor 0
    private final int srl(int aux) {
        carryFlag = (aux & CARRY_MASK) != 0;
        aux >>>= 1;
        sz5h3pnFlags = sz53pn_addTable[aux];
        return aux;
    }

    // Suma dos operandos de 16 bits sin carry afectando a los flags
    private final int add16(int reg16, int oper16) {
        int res = reg16 + oper16;
        carryFlag = (res > 0xffff);
        sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZP_MASK) | ((res >>> 8) & FLAG_53_MASK);

        res &= 0xffff;
        if ( (res & 0x0fff) < (reg16 & 0x0fff) )
            sz5h3pnFlags |= HALFCARRY_MASK;

        memptr = (reg16 + 1) & 0xffff;
        return res;
    }

    // Suma de 8 bits afectando a los flags
    private final void add(int valor) {
        int res = regA + valor;
        carryFlag = (res > 0xff);
        res &= 0xff;
        sz5h3pnFlags = sz53n_addTable[res];

        /* El módulo 16 del resultado será menor que el módulo 16 del registro A
         * si ha habido HalfCarry. Sucede lo mismo para todos los métodos suma
         * SIN carry */
        if( (res & 0x0f) < (regA & 0x0f) )
            sz5h3pnFlags |= HALFCARRY_MASK;

        if( ((regA ^ ~valor) & (regA ^ res)) > 0x7f )
            sz5h3pnFlags |= OVERFLOW_MASK;

        regA = res;
    }

    // Suma con acarreo de 8 bits
    private final void adc(int valor) {
        int carry = (carryFlag ? CARRY_MASK : 0x00);
        int res = regA + valor + carry;

        carryFlag = (res > 0xff);
        res &= 0xff;
        sz5h3pnFlags = sz53n_addTable[res];

        if( (regA & 0x0f) + (valor & 0x0f) +  carry > 0x0f )
            sz5h3pnFlags |= HALFCARRY_MASK;

        if( ((regA ^ ~valor) & (regA ^ res)) > 0x7f )
            sz5h3pnFlags |= OVERFLOW_MASK;

        regA = res;
    }

    // Suma con acarreo de 16 bits
    private final void adc16(int valor) {
        int carry = (carryFlag ? CARRY_MASK : 0x00);
        int regHL = getRegHL();
        memptr = (regHL + 1) & 0xffff;
        int res = regHL + valor + carry;
        
        carryFlag = (res > 0xffff);
        res &= 0xffff;
        setRegHL(res);
        sz5h3pnFlags = sz53n_addTable[regH];
        if( res != 0 )
            sz5h3pnFlags &= ~ZERO_MASK;

        if( (regHL & 0x0fff)+ (valor & 0x0fff) + carry > 0x0fff )
            sz5h3pnFlags |= HALFCARRY_MASK;

        if( ((regHL ^ ~valor) & (regHL ^ res)) > 0x7fff )
            sz5h3pnFlags |= OVERFLOW_MASK;
    }

    // Resta de 8 bits
    private final void sub(int valor) {
        int res = regA - valor;
        carryFlag = (res & 0x100) != 0;
        res &= 0xff;
        sz5h3pnFlags = sz53n_subTable[res];

        /* El módulo 16 del resultado será mayor que el módulo 16 del registro A
         * si ha habido HalfCarry. Sucede lo mismo para todos los métodos resta
         * SIN carry, incluído cp */
        if( (res & 0x0f) > (regA & 0x0f) )
            sz5h3pnFlags |= HALFCARRY_MASK;

        if( ((regA ^ valor) & (regA ^ res)) > 0x7f )
            sz5h3pnFlags |= OVERFLOW_MASK;

        regA = res;
    }

    // Resta con acarreo de 8 bits
    private final void sbc(int valor) {
        int carry = (carryFlag ? CARRY_MASK : 0x00);
        int res = regA - valor - carry;
        
        carryFlag = (res & 0x100) != 0;
        res &= 0xff;
        sz5h3pnFlags = sz53n_subTable[res];

        if( (((regA & 0x0f) - (valor & 0x0f) -  carry) & 0x10) != 0 )
            sz5h3pnFlags |= HALFCARRY_MASK;

        if( ((regA ^ valor) & (regA ^ res)) > 0x7f )
            sz5h3pnFlags |= OVERFLOW_MASK;

        regA = res;
    }

    // Resta con acarreo de 16 bits
    private final void sbc16(int valor) {
        int carry = (carryFlag ? CARRY_MASK : 0x00);
        int regHL = getRegHL();
        memptr = (regHL + 1) & 0xffff;
        int res = regHL - valor - carry;
        
        carryFlag = (res & 0x10000) != 0;
        res &= 0xffff;
        setRegHL(res);
        sz5h3pnFlags = sz53n_subTable[regH];
        if( res != 0 )
            sz5h3pnFlags &= ~ZERO_MASK;

        if( (((regHL & 0x0fff) - (valor & 0x0fff) -  carry) & 0x1000) != 0 )
            sz5h3pnFlags |= HALFCARRY_MASK;

        if( ((regHL ^ valor) & (regHL ^ res)) > 0x7fff )
            sz5h3pnFlags |= OVERFLOW_MASK;
    }

    // Operación AND lógica
    private final void and(int valor) {
        regA = regA & valor;
        carryFlag = false;
        sz5h3pnFlags = sz53pn_addTable[regA] | HALFCARRY_MASK;
    }

    // Operación XOR lógica
    public final void xor(int valor) {
        regA = regA ^ valor;
        carryFlag = false;
        sz5h3pnFlags = sz53pn_addTable[regA];
    }

    // Operación OR lógica
    private final void or(int valor) {
        regA = regA | valor;
        carryFlag = false;
        sz5h3pnFlags = sz53pn_addTable[regA];
    }

    // Operación de comparación con el registro A
    // es como SUB, pero solo afecta a los flags
    // Los flags SIGN y ZERO se calculan a partir del resultado
    // Los flags 3 y 5 se copian desde el operando (sigh!)
    public final void cp(int valor) {
        int res = regA - valor;
        carryFlag = (res & 0x100) != 0;
        res &= 0xff;
        sz5h3pnFlags = (sz53n_addTable[valor] & FLAG_53_MASK) |
            // No necesito preservar H, pero está a 0 en la tabla de todas formas
                       (sz53n_subTable[res] & FLAG_SZHN_MASK);

        if( (res & 0x0f) > (regA & 0x0f) )
            sz5h3pnFlags |= HALFCARRY_MASK;

        if( ((regA ^ valor) & (regA ^ res)) > 0x7f )
            sz5h3pnFlags |= OVERFLOW_MASK;
    }

    // DAA
    // Importada para ti, directamente desde fuse 0.9.0 (thanks Phil)
    private final void daa() {
        int suma = 0;
        boolean carry = carryFlag;

        if ( (sz5h3pnFlags & HALFCARRY_MASK) != 0 || (regA & 0x0f) > 0x09) {
            suma = 6;
        }

        if (carry || (regA > 0x99)) {
            suma |= 0x60;
        }

        if (regA > 0x99) {
            carry = true;
        }

        if ( (sz5h3pnFlags & ADDSUB_MASK) != 0 ) {
            sub(suma);
            sz5h3pnFlags = (sz5h3pnFlags & HALFCARRY_MASK) | sz53pn_subTable[regA];
        } else {
            add(suma);
            sz5h3pnFlags = (sz5h3pnFlags & HALFCARRY_MASK) | sz53pn_addTable[regA];
        }

        carryFlag = carry;
        // Los add/sub ya ponen el resto de los flags
    }

    // POP
    private final int pop() {
        int word = MemIoImpl.peek16(regSP);
        regSP = (regSP + 2) & 0xffff;
        return word;
    }

    // PUSH
    private final void push(int valor) {
        int msb = (valor >>> 8) & 0xff;
        regSP--;
        MemIoImpl.poke8(regSP & 0xffff, msb);
        regSP = (regSP - 1) & 0xffff;
        MemIoImpl.poke8(regSP, valor & 0xff);
    }

    // versión rápida de PUSH para cuando hay que guardar registros
    private final void push(int msb, int lsb) {
        regSP--;
        MemIoImpl.poke8(regSP & 0xffff, msb);
        regSP = (regSP - 1) & 0xffff;
        MemIoImpl.poke8(regSP, lsb);
    }

    // LDI
    private final void ldi() {
        int work8 = MemIoImpl.peek8(getRegHL());
        MemIoImpl.poke8(getRegDE(), work8);
        MemIoImpl.contendedStates(getRegDE(), 2);
        incRegHL();
        incRegDE();
        decRegBC();
        work8 += regA;
        sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZ_MASK) | (work8 & BIT3_MASK);

        if( (work8 & ADDSUB_MASK) != 0 )
            sz5h3pnFlags |= BIT5_MASK;

        if( getRegBC() != 0 )
            sz5h3pnFlags |= PARITY_MASK;
    }

    // LDD
    private final void ldd() {
        int work8 = MemIoImpl.peek8(getRegHL());
        MemIoImpl.poke8(getRegDE(), work8);
        MemIoImpl.contendedStates(getRegDE(), 2);
        decRegHL();
        decRegDE();
        decRegBC();
        work8 += regA;
        sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZ_MASK) | (work8 & BIT3_MASK);

        if( (work8 & ADDSUB_MASK) != 0 )
            sz5h3pnFlags |= BIT5_MASK;

        if( getRegBC() != 0 )
            sz5h3pnFlags |= PARITY_MASK;
    }

    // CPI
    private final void cpi() {
        int regHL = getRegHL();
        int memHL = MemIoImpl.peek8(regHL);
        boolean carry = carryFlag; // lo guardo porque cp lo toca
        cp(memHL);
        MemIoImpl.contendedStates(regHL, 5);
        carryFlag = carry;
        incRegHL();
        decRegBC();
        memHL = regA - memHL - ((sz5h3pnFlags & HALFCARRY_MASK) != 0 ? 1 : 0);
        sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHN_MASK) | (memHL & BIT3_MASK);

        if( (memHL & ADDSUB_MASK) != 0 )
            sz5h3pnFlags |= BIT5_MASK;

        if( getRegBC() != 0 )
            sz5h3pnFlags |= PARITY_MASK;

        memptr = (memptr + 1) & 0xffff;
    }

    // CPD
    private final void cpd() {
        int regHL = getRegHL();
        int memHL = MemIoImpl.peek8(regHL);
        boolean carry = carryFlag; // lo guardo porque cp lo toca
        cp(memHL);
        MemIoImpl.contendedStates(regHL, 5);
        carryFlag = carry;
        decRegHL();
        decRegBC();
        memHL = regA - memHL - ((sz5h3pnFlags & HALFCARRY_MASK) != 0 ? 1 : 0);
        sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHN_MASK) | (memHL & BIT3_MASK);

        if( (memHL & ADDSUB_MASK) != 0 )
            sz5h3pnFlags |= BIT5_MASK;

        if( getRegBC() != 0 )
            sz5h3pnFlags |= PARITY_MASK;

        memptr = (memptr - 1) & 0xffff;
    }

    // INI
    private final void ini() {
        MemIoImpl.contendedStates(getPairIR(), 1);
        int work8 = MemIoImpl.inPort(getRegBC());
        MemIoImpl.poke8(getRegHL(), work8);

        memptr = (getRegBC() + 1) & 0xffff;
        regB--;
        regB &= 0xff;

        incRegHL();

        sz5h3pnFlags = sz53pn_addTable[regB];
        if (work8 > 0x7f)
            sz5h3pnFlags |= ADDSUB_MASK;

        carryFlag = false;
        int tmp = work8 + ((regC + 1) & 0xff);
        if( tmp > 0xff ) {
            sz5h3pnFlags |= HALFCARRY_MASK;
            carryFlag = true;
        }

        if( (sz53pn_addTable[((tmp & 0x07) ^ regB)] &
                PARITY_MASK) == PARITY_MASK )
            sz5h3pnFlags |= PARITY_MASK;
        else
            sz5h3pnFlags &= ~PARITY_MASK;
    }

    // IND
    private final void ind() {
        MemIoImpl.contendedStates(getPairIR(), 1);
        int work8 = MemIoImpl.inPort(getRegBC());
        MemIoImpl.poke8(getRegHL(), work8);

        memptr = (getRegBC() - 1) & 0xffff;
        regB--;
        regB &= 0xff;

        decRegHL();

        sz5h3pnFlags = sz53pn_addTable[regB];
        if (work8 > 0x7f)
            sz5h3pnFlags |= ADDSUB_MASK;

        carryFlag = false;
        int tmp = work8 + ((regC - 1) & 0xff);
        if( tmp > 0xff ) {
            sz5h3pnFlags |= HALFCARRY_MASK;
            carryFlag = true;
        }

        if( (sz53pn_addTable[((tmp & 0x07) ^ regB)] &
                PARITY_MASK) == PARITY_MASK )
            sz5h3pnFlags |= PARITY_MASK;
        else
            sz5h3pnFlags &= ~PARITY_MASK;
    }

    // OUTI
    private final void outi() {
        MemIoImpl.contendedStates(getPairIR(), 1);

        regB--;
        regB &= 0xff;
        memptr = (getRegBC() + 1) & 0xffff;

        int work8 = MemIoImpl.peek8(getRegHL());
        MemIoImpl.outPort(getRegBC(), work8);
        incRegHL();
        carryFlag = false;
        if (work8 > 0x7f)
            sz5h3pnFlags = sz53n_subTable[regB];
        else
            sz5h3pnFlags = sz53n_addTable[regB];

        if( (regL + work8) > 0xff ) {
            sz5h3pnFlags |= HALFCARRY_MASK;
            carryFlag = true;
        }

        if( (sz53pn_addTable[(((regL + work8) & 0x07) ^ regB)] &
                PARITY_MASK) == PARITY_MASK )
            sz5h3pnFlags |= PARITY_MASK;
    }

    // OUTD
    private final void outd() {
        MemIoImpl.contendedStates(getPairIR(), 1);
        
        regB--;
        regB &= 0xff;
        memptr = (getRegBC() - 1) & 0xffff;
        
        int work8 = MemIoImpl.peek8(getRegHL());
        MemIoImpl.outPort(getRegBC(), work8);
        decRegHL();
        carryFlag = false;
        if (work8 > 0x7f)
            sz5h3pnFlags = sz53n_subTable[regB];
        else
            sz5h3pnFlags = sz53n_addTable[regB];

        if( (regL + work8) > 0xff ) {
            sz5h3pnFlags |= HALFCARRY_MASK;
            carryFlag = true;
        }

        if( (sz53pn_addTable[(((regL + work8) & 0x07) ^ regB)] &
                PARITY_MASK) == PARITY_MASK )
            sz5h3pnFlags |= PARITY_MASK;
    }

    // EXX
    private final void exx() {
        int work8 = regB;
        regB = regBalt;
        regBalt = work8;

        work8 = regC;
        regC = regCalt;
        regCalt = work8;

        work8 = regD;
        regD = regDalt;
        regDalt = work8;

        work8 = regE;
        regE = regEalt;
        regEalt = work8;

        work8 = regH;
        regH = regHalt;
        regHalt = work8;

        work8 = regL;
        regL = regLalt;
        regLalt = work8;
    }

    // Pone a 1 el Flag Z si el bit b del registro
    // r es igual a 0
    /*
     * En contra de lo que afirma el Z80-Undocumented, los bits 3 y 5 toman
     * SIEMPRE el valor de los bits correspondientes del valor a comparar para
     * las instrucciones BIT n,r. Para BIT n,(HL) toman el valor del registro
     * escondido (memptr), y para las BIT n, (IX/IY+n) toman el valor de los
     * bits superiores de la dirección indicada por IX/IY+n.
     *
     * 04/12/08 Confirmado el comentario anterior:
     *          http://scratchpad.wikia.com/wiki/Z80
     */
    private final void bit(int mask, int reg) {
        boolean zeroFlag = (mask & reg) == 0;

        sz5h3pnFlags = sz53n_addTable[reg] & ~FLAG_SZP_MASK | HALFCARRY_MASK;

        if( zeroFlag )
            sz5h3pnFlags |= (PARITY_MASK | ZERO_MASK);

        if ( mask == SIGN_MASK && !zeroFlag )
            sz5h3pnFlags |= SIGN_MASK;
    }

    // Pone a 0 el bit b del registro r
    private final int res(int mask, int reg) {
        return (reg & ~mask);
    }

    // Pone a 1 el el bit b del registro r
    private final int set(int mask, int reg) {
        return (reg | mask);
    }

    //Interrupción
    /* Desglose de la interrupción, según el modo:
     * IM0:
     *      M1: 7 T-Estados -> reconocer INT y decSP
     *      M2: 3 T-Estados -> escribir byte alto y decSP
     *      M3: 3 T-Estados -> escribir byte bajo y salto a N
     * IM1:
     *      M1: 7 T-Estados -> reconocer INT y decSP
     *      M2: 3 T-Estados -> escribir byte alto PC y decSP
     *      M3: 3 T-Estados -> escribir byte bajo PC y PC=0x0038
     * IM2:
     *      M1: 7 T-Estados -> reconocer INT y decSP
     *      M2: 3 T-Estados -> escribir byte alto y decSP
     *      M3: 3 T-Estados -> escribir byte bajo
     *      M4: 3 T-Estados -> leer byte bajo del vector de INT
     *      M5: 3 T-Estados -> leer byte alto y saltar a la rutina de INT
     */
    public final void interruption() {

        //System.out.println(String.format("INT at %d T-States", tEstados));
//        int tmp = tEstados; // peek8 modifica los tEstados
        // Si estaba en un HALT esperando una INT, lo saca de la espera
        if (halted) {
            halted = false;
            regPC = (regPC + 1) & 0xffff;
        }

        tEstados += 7;

        regR++;
        ffIFF1 = false;
        ffIFF2 = false;
        push(regPC);  // el push añadirá 6 t-estados (+contended si toca)
        if (modeINT == IM2) {
            regPC = MemIoImpl.peek16((regI << 8) | 0xff); // +6 t-estados
        } else {
            regPC = 0x0038;
        }
        memptr = regPC;
        //System.out.println(String.format("Coste INT: %d", tEstados-tmp));
    }

    //Interrupción NMI, no utilizado por ahora
    /* Desglose de ciclos de máquina y T-Estados
     * M1: 5 T-Estados -> extraer opcode (pá ná, es tontería) y decSP
     * M2: 3 T-Estados -> escribe byte alto de PC y decSP
     * M3: 3 T-Estados -> escribe byte bajo de PC y PC=0x0066
     */
    private final void nmi() {
        // Esta lectura consigue dos cosas:
        //      1.- La lectura del opcode del M1 que se descarta
        //      2.- Si estaba en un HALT esperando una INT, lo saca de la espera
        MemIoImpl.fetchOpcode(regPC);
        tEstados++;
        if (halted) {
            halted = false;
            regPC = (regPC + 1) & 0xffff;
        }
        regR++;
        ffIFF1 = false;
        push(regPC);  // 3+3 t-estados + contended si procede
        regPC = memptr = 0x0066;
    }

    //Devuelve el número de estados T ejecutados en el ciclo
    public final int getTEstados() {
        return tEstados;
    }

    // Inicia los T-estados a un valor
    public final void setTEstados(int tstates) {
        tEstados = tstates;
    }

    // Añade los t-estados de retraso de la contended memory
    public final void addTEstados(int delay) {
        tEstados += delay;
    }

    public final void setExecDone(boolean notify) {
        execDone = notify;
    }

    /* Los tEstados transcurridos se calculan teniendo en cuenta el número de
     * ciclos de máquina reales que se ejecutan. Esa es la única forma de poder
     * simular la contended memory del Spectrum.
     */
    public final int execute(int statesLimit) {

        while (tEstados < statesLimit) {
            int tmp = tEstados;

            // Primero se comprueba NMI
            if (activeNMI) {
                activeNMI = false;
                nmi();
                continue;
            }

            // Ahora se comprueba si al final de la instrucción anterior se
            // encontró una interrupción enmascarable y, de ser así, se procesa.
            if (activeINT) {
                if (ffIFF1 && !pendingEI)
                    interruption();
            }

            regR++;
            opCode = MemIoImpl.fetchOpcode(regPC);
            regPC = (regPC + 1) & 0xffff;
            decodeOpcode(opCode);

            // Si está pendiente la activación de la interrupciones y el
            // código que se acaba de ejecutar no es el propio EI
            if (pendingEI && opCode != 0xFB) {
                pendingEI = false;
            }

            if (execDone)
                MemIoImpl.execDone(tEstados - tmp);

        } /* del while */
        return tEstados;
    }

    private final void decodeOpcode(int opCode ) {
        int work8, work16;
        byte salto;
        switch (opCode) {
            case 0x00:       /*NOP*/
                break;
            case 0x01:       /*LD BC,nn*/
                setRegBC(MemIoImpl.peek16(regPC));
                regPC = (regPC + 2) & 0xffff;
                break;
            case 0x02:       /*LD (BC),A*/
                memptr = getRegBC();
                MemIoImpl.poke8(memptr, regA);
                memptr = (regA << 8) | ((memptr + 1) & 0xff);
                break;
            case 0x03:       /*INC BC*/
                incRegBC();
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            case 0x04:       /*INC B*/
                regB = inc8(regB);
                break;
            case 0x05:       /*DEC B*/
                regB = dec8(regB);
                break;
            case 0x06:       /*LD B,n*/
                regB = MemIoImpl.peek8(regPC);
                regPC = (regPC + 1) & 0xffff;
                break;
            case 0x07:       /*RLCA*/
                carryFlag = (regA > 0x7f);
                regA <<= 1;
                if( carryFlag )
                    regA |= CARRY_MASK;
                regA &= 0xff;
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZP_MASK) | (regA & FLAG_53_MASK);
                break;
            case 0x08:       /*EX AF,AF'*/
                work8 = regA;
                regA = regAalt;
                regAalt = work8;
                work8 = getFlags();
                setFlags(flagFalt);
                flagFalt = work8;
                break;
            case 0x09:       /*ADD HL,BC*/
                setRegHL(add16(getRegHL(), getRegBC()));
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            case 0x0A:       /*LD A,(BC)*/
                memptr = getRegBC();
                regA = MemIoImpl.peek8(memptr);
                memptr = (memptr + 1) & 0xffff;
                break;
            case 0x0B:       /*DEC BC*/
                decRegBC();
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            case 0x0C:       /*INC C*/
                regC = inc8(regC);
                break;
            case 0x0D:       /*DEC C*/
                regC = dec8(regC);
                break;
            case 0x0E:       /*LD C,n*/
                regC = MemIoImpl.peek8(regPC);
                regPC = (regPC + 1) & 0xffff;
                break;
            case 0x0F:       /*RRCA*/
                carryFlag = (regA & CARRY_MASK) != 0;
                regA >>>= 1;
                if( carryFlag )
                    regA |= SIGN_MASK;
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZP_MASK) | (regA & FLAG_53_MASK);
                break;
            case 0x10:       /*DJNZ e*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                salto = (byte) MemIoImpl.peek8(regPC);
                regB--;
                regB &= 0xff;
                if (regB != 0) {
                    MemIoImpl.contendedStates(regPC, 5);
                    memptr = (regPC + salto + 1) & 0xffff;
                    regPC = memptr;
                }
                else
                    regPC = (regPC + 1) & 0xffff;
                break;
            case 0x11:       /*LD DE,nn*/
                setRegDE(MemIoImpl.peek16(regPC));
                regPC = (regPC + 2) & 0xffff;
                break;
            case 0x12:       /*LD (DE),A*/
                memptr = getRegDE();
                MemIoImpl.poke8(memptr, regA);
                memptr = (regA << 8) | ((memptr + 1) & 0xff);
                break;
            case 0x13: {     /*INC DE*/
                incRegDE();
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            }
            case 0x14: {     /*INC D*/
                regD = inc8(regD);
                break;
            }
            case 0x15: {     /*DEC D*/
                regD = dec8(regD);
                break;
            }
            case 0x16: {     /*LD D,n*/
                regD = MemIoImpl.peek8(regPC);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x17: {     /*RLA*/
                boolean oldCarry = carryFlag;
                carryFlag = (regA > 0x7f);
                regA <<= 1;
                if( oldCarry )
                    regA |= CARRY_MASK;
                regA &= 0xff;
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZP_MASK) | (regA & FLAG_53_MASK);
                break;
            }
            case 0x18: {     /*JR e*/
                salto = (byte) MemIoImpl.peek8(regPC);
                MemIoImpl.contendedStates(regPC, 5);
                regPC = (regPC + salto + 1) & 0xffff;
                memptr = regPC;
                break;
            }
            case 0x19: {     /*ADD HL,DE*/
                setRegHL(add16(getRegHL(), getRegDE()));
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x1A: {     /*LD A,(DE)*/
                memptr = getRegDE();
                regA = MemIoImpl.peek8(memptr);
                memptr = (memptr + 1) & 0xffff;
                break;
            }
            case 0x1B: {     /*DEC DE*/
                decRegDE();
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            }
            case 0x1C: {     /*INC E*/
                regE = inc8(regE);
                break;
            }
            case 0x1D: {     /*DEC E*/
                regE = dec8(regE);
                break;
            }
            case 0x1E: {     /*LD E,n*/
                regE = MemIoImpl.peek8(regPC);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x1F: {     /*RRA*/
                boolean oldCarry = carryFlag;
                carryFlag = (regA & CARRY_MASK) != 0;
                regA >>>= 1;
                if( oldCarry )
                    regA |= SIGN_MASK;
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZP_MASK) | (regA & FLAG_53_MASK);
                break;
            }
            case 0x20: {     /*JR NZ,e*/
                salto = (byte) MemIoImpl.peek8(regPC);
                if( (sz5h3pnFlags & ZERO_MASK) == 0 ) {
                    MemIoImpl.contendedStates(regPC, 5);
                    regPC += salto;
                }
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x21: {     /*LD HL,nn*/
                setRegHL(MemIoImpl.peek16(regPC));
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x22: {     /*LD (nn),HL*/
                memptr = MemIoImpl.peek16(regPC);
                MemIoImpl.poke16(memptr, getRegHL());
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x23: {     /*INC HL*/
                incRegHL();
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            }
            case 0x24: {     /*INC H*/
                regH = inc8(regH);
                break;
            }
            case 0x25: {     /*DEC H*/
                regH = dec8(regH);
                break;
            }
            case 0x26: {     /*LD H,n*/
                regH = MemIoImpl.peek8(regPC);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x27: {     /*DAA*/
                daa();
                break;
            }
            case 0x28: {     /*JR Z,e*/
                salto = (byte) MemIoImpl.peek8(regPC);
                if( (sz5h3pnFlags & ZERO_MASK) != 0 ) {
                    MemIoImpl.contendedStates(regPC, 5);
                    regPC += salto;
                }
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x29: {     /*ADD HL,HL*/
                work16 = getRegHL();
                setRegHL(add16(work16, work16));
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x2A: {     /*LD HL,(nn)*/
                memptr = MemIoImpl.peek16(regPC);
                setRegHL(MemIoImpl.peek16(memptr));
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x2B: {     /*DEC HL*/
                decRegHL();
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            }
            case 0x2C: {     /*INC L*/
                regL = inc8(regL);
                break;
            }
            case 0x2D: {     /*DEC L*/
                regL = dec8(regL);
                break;
            }
            case 0x2E: {     /*LD L,n*/
                regL = MemIoImpl.peek8(regPC);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x2F: {     /*CPL*/
                regA = (regA ^ 0xff) & 0xff;
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZP_MASK) | HALFCARRY_MASK |
                               (regA & FLAG_53_MASK) | ADDSUB_MASK;
                break;
            }
            case 0x30: {     /*JR NC,e*/
                salto = (byte) MemIoImpl.peek8(regPC);
                if (!carryFlag) {
                    MemIoImpl.contendedStates(regPC, 5);
                    regPC += salto;
                }
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x31: {     /*LD SP,nn*/
                regSP = MemIoImpl.peek16(regPC);
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x32: {     /*LD (nn),A*/
                memptr = MemIoImpl.peek16(regPC);
                MemIoImpl.poke8(memptr, regA);
                memptr = (regA << 8) | ((memptr + 1) & 0xff);
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x33: {     /*INC SP*/
                regSP = (regSP + 1) & 0xffff;
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            }
            case 0x34: {     /*INC (HL)*/
                work16 = getRegHL();
                work8 = inc8(MemIoImpl.peek8(work16));
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, work8);
                break;
            }
            case 0x35: {     /*DEC (HL)*/
                work16 = getRegHL();
                work8 = dec8(MemIoImpl.peek8(work16));
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, work8);
                break;
            }
            case 0x36: {     /*LD (HL),n*/
                MemIoImpl.poke8(getRegHL(), MemIoImpl.peek8(regPC));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x37: {     /*SCF*/
                carryFlag = true;
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZP_MASK) | (regA & FLAG_53_MASK);
                break;
            }
            case 0x38: {     /*JR C,e*/
                salto = (byte) MemIoImpl.peek8(regPC);
                if (carryFlag) {
                    MemIoImpl.contendedStates(regPC, 5);
                    regPC += salto;
                }
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x39: {     /*ADD HL,SP*/
                setRegHL(add16(getRegHL(), regSP));
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x3A: {     /*LD A,(nn)*/
                memptr = MemIoImpl.peek16(regPC);
                regA = MemIoImpl.peek8(memptr);
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x3B: {     /*DEC SP*/
                regSP = (regSP - 1) & 0xffff;
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            }
            case 0x3C: {     /*INC A*/
                regA = inc8(regA);
                break;
            }
            case 0x3D: {     /*DEC A*/
                regA = dec8(regA);
                break;
            }
            case 0x3E: {     /*LD A,n*/
                regA = MemIoImpl.peek8(regPC);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x3F: {     /*CCF*/
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZP_MASK) | (regA & FLAG_53_MASK);
                if( carryFlag )
                    sz5h3pnFlags |= HALFCARRY_MASK;
                carryFlag = !carryFlag;
                break;
            }
            case 0x40: {     /*LD B,B*/
                break;
            }
            case 0x41: {     /*LD B,C*/
                regB = regC;
                break;
            }
            case 0x42: {     /*LD B,D*/
                regB = regD;
                break;
            }
            case 0x43: {     /*LD B,E*/
                regB = regE;
                break;
            }
            case 0x44: {     /*LD B,H*/
                regB = regH;
                break;
            }
            case 0x45: {     /*LD B,L*/
                regB = regL;
                break;
            }
            case 0x46: {     /*LD B,(HL)*/
                regB = MemIoImpl.peek8(getRegHL());
                break;
            }
            case 0x47: {     /*LD B,A*/
                regB = regA;
                break;
            }
            case 0x48: {     /*LD C,B*/
                regC = regB;
                break;
            }
            case 0x49: {     /*LD C,C*/
                break;
            }
            case 0x4A: {     /*LD C,D*/
                regC = regD;
                break;
            }
            case 0x4B: {     /*LD C,E*/
                regC = regE;
                break;
            }
            case 0x4C: {     /*LD C,H*/
                regC = regH;
                break;
            }
            case 0x4D: {     /*LD C,L*/
                regC = regL;
                break;
            }
            case 0x4E: {     /*LD C,(HL)*/
                regC = MemIoImpl.peek8(getRegHL());
                break;
            }
            case 0x4F: {     /*LD C,A*/
                regC = regA;
                break;
            }
            case 0x50: {     /*LD D,B*/
                regD = regB;
                break;
            }
            case 0x51: {     /*LD D,C*/
                regD = regC;
                break;
            }
            case 0x52: {     /*LD D,D*/
                break;
            }
            case 0x53: {     /*LD D,E*/
                regD = regE;
                break;
            }
            case 0x54: {     /*LD D,H*/
                regD = regH;
                break;
            }
            case 0x55: {     /*LD D,L*/
                regD = regL;
                break;
            }
            case 0x56: {     /*LD D,(HL)*/
                regD = MemIoImpl.peek8(getRegHL());
                break;
            }
            case 0x57: {     /*LD D,A*/
                regD = regA;
                break;
            }
            case 0x58: {     /*LD E,B*/
                regE = regB;
                break;
            }
            case 0x59: {     /*LD E,C*/
                regE = regC;
                break;
            }
            case 0x5A: {     /*LD E,D*/
                regE = regD;
                break;
            }
            case 0x5B: {     /*LD E,E*/
                break;
            }
            case 0x5C: {     /*LD E,H*/
                regE = regH;
                break;
            }
            case 0x5D: {     /*LD E,L*/
                regE = regL;
                break;
            }
            case 0x5E: {     /*LD E,(HL)*/
                regE = MemIoImpl.peek8(getRegHL());
                break;
            }
            case 0x5F: {     /*LD E,A*/
                regE = regA;
                break;
            }
            case 0x60: {     /*LD H,B*/
                regH = regB;
                break;
            }
            case 0x61: {     /*LD H,C*/
                regH = regC;
                break;
            }
            case 0x62: {     /*LD H,D*/
                regH = regD;
                break;
            }
            case 0x63: {     /*LD H,E*/
                regH = regE;
                break;
            }
            case 0x64: {     /*LD H,H*/
                break;
            }
            case 0x65: {     /*LD H,L*/
                regH = regL;
                break;
            }
            case 0x66: {     /*LD H,(HL)*/
                regH = MemIoImpl.peek8(getRegHL());
                break;
            }
            case 0x67: {     /*LD H,A*/
                regH = regA;
                break;
            }
            case 0x68: {     /*LD L,B*/
                regL = regB;
                break;
            }
            case 0x69: {     /*LD L,C*/
                regL = regC;
                break;
            }
            case 0x6A: {     /*LD L,D*/
                regL = regD;
                break;
            }
            case 0x6B: {     /*LD L,E*/
                regL = regE;
                break;
            }
            case 0x6C: {     /*LD L,H*/
                regL = regH;
                break;
            }
            case 0x6D: {     /*LD L,L*/
                break;
            }
            case 0x6E: {     /*LD L,(HL)*/
                regL = MemIoImpl.peek8(getRegHL());
                break;
            }
            case 0x6F: {     /*LD L,A*/
                regL = regA;
                break;
            }
            case 0x70: {     /*LD (HL),B*/
                MemIoImpl.poke8(getRegHL(), regB);
                break;
            }
            case 0x71: {     /*LD (HL),C*/
                MemIoImpl.poke8(getRegHL(), regC);
                break;
            }
            case 0x72: {     /*LD (HL),D*/
                MemIoImpl.poke8(getRegHL(), regD);
                break;
            }
            case 0x73: {     /*LD (HL),E*/
                MemIoImpl.poke8(getRegHL(), regE);
                break;
            }
            case 0x74: {     /*LD (HL),H*/
                MemIoImpl.poke8(getRegHL(), regH);
                break;
            }
            case 0x75: {     /*LD (HL),L*/
                MemIoImpl.poke8(getRegHL(), regL);
                break;
            }
            case 0x76: {     /*HALT*/
                regPC = (regPC - 1) & 0xffff;
                halted = true;
                break;
            }
            case 0x77: {     /*LD (HL),A*/
                MemIoImpl.poke8(getRegHL(), regA);
                break;
            }
            case 0x78: {     /*LD A,B*/
                regA = regB;
                break;
            }
            case 0x79: {     /*LD A,C*/
                regA = regC;
                break;
            }
            case 0x7A: {     /*LD A,D*/
                regA = regD;
                break;
            }
            case 0x7B: {     /*LD A,E*/
                regA = regE;
                break;
            }
            case 0x7C: {     /*LD A,H*/
                regA = regH;
                break;
            }
            case 0x7D: {     /*LD A,L*/
                regA = regL;
                break;
            }
            case 0x7E: {     /*LD A,(HL)*/
                regA = MemIoImpl.peek8(getRegHL());
                break;
            }
            case 0x7F: {     /*LD A,A*/
                break;
            }
            case 0x80: {     /*ADD A,B*/
                add(regB);
                break;
            }
            case 0x81: {     /*ADD A,C*/
                add(regC);
                break;
            }
            case 0x82: {     /*ADD A,D*/
                add(regD);
                break;
            }
            case 0x83: {     /*ADD A,E*/
                add(regE);
                break;
            }
            case 0x84: {     /*ADD A,H*/
                add(regH);
                break;
            }
            case 0x85: {     /*ADD A,L*/
                add(regL);
                break;
            }
            case 0x86: {     /*ADD A,(HL)*/
                add(MemIoImpl.peek8(getRegHL()));
                break;
            }
            case 0x87: {     /*ADD A,A*/
                add(regA);
                break;
            }
            case 0x88: {     /*ADC A,B*/
                adc(regB);
                break;
            }
            case 0x89: {     /*ADC A,C*/
                adc(regC);
                break;
            }
            case 0x8A: {     /*ADC A,D*/
                adc(regD);
                break;
            }
            case 0x8B: {     /*ADC A,E*/
                adc(regE);
                break;
            }
            case 0x8C: {     /*ADC A,H*/
                adc(regH);
                break;
            }
            case 0x8D: {     /*ADC A,L*/
                adc(regL);
                break;
            }
            case 0x8E: {     /*ADC A,(HL)*/
                adc(MemIoImpl.peek8(getRegHL()));
                break;
            }
            case 0x8F: {     /*ADC A,A*/
                adc(regA);
                break;
            }
            case 0x90: {     /*SUB B*/
                sub(regB);
                break;
            }
            case 0x91: {     /*SUB C*/
                sub(regC);
                break;
            }
            case 0x92: {     /*SUB D*/
                sub(regD);
                break;
            }
            case 0x93: {     /*SUB E*/
                sub(regE);
                break;
            }
            case 0x94: {     /*SUB H*/
                sub(regH);
                break;
            }
            case 0x95: {     /*SUB L*/
                sub(regL);
                break;
            }
            case 0x96: {     /*SUB (HL)*/
                sub(MemIoImpl.peek8(getRegHL()));
                break;
            }
            case 0x97: {     /*SUB A*/
                sub(regA);
                break;
            }
            case 0x98: {     /*SBC A,B*/
                sbc(regB);
                break;
            }
            case 0x99: {     /*SBC A,C*/
                sbc(regC);
                break;
            }
            case 0x9A: {     /*SBC A,D*/
                sbc(regD);
                break;
            }
            case 0x9B: {     /*SBC A,E*/
                sbc(regE);
                break;
            }
            case 0x9C: {     /*SBC A,H*/
                sbc(regH);
                break;
            }
            case 0x9D: {     /*SBC A,L*/
                sbc(regL);
                break;
            }
            case 0x9E: {     /*SBC A,(HL)*/
                sbc(MemIoImpl.peek8(getRegHL()));
                break;
            }
            case 0x9F: {     /*SBC A,A*/
                sbc(regA);
                break;
            }
            case 0xA0: {     /*AND B*/
                and(regB);
                break;
            }
            case 0xA1: {     /*AND C*/
                and(regC);
                break;
            }
            case 0xA2: {     /*AND D*/
                and(regD);
                break;
            }
            case 0xA3: {     /*AND E*/
                and(regE);
                break;
            }
            case 0xA4: {     /*AND H*/
                and(regH);
                break;
            }
            case 0xA5: {     /*AND L*/
                and(regL);
                break;
            }
            case 0xA6: {     /*AND (HL)*/
                and(MemIoImpl.peek8(getRegHL()));
                break;
            }
            case 0xA7: {     /*AND A*/
                and(regA);
                break;
            }
            case 0xA8: {     /*XOR B*/
                xor(regB);
                break;
            }
            case 0xA9: {     /*XOR C*/
                xor(regC);
                break;
            }
            case 0xAA: {     /*XOR D*/
                xor(regD);
                break;
            }
            case 0xAB: {     /*XOR E*/
                xor(regE);
                break;
            }
            case 0xAC: {     /*XOR H*/
                xor(regH);
                break;
            }
            case 0xAD: {     /*XOR L*/
                xor(regL);
                break;
            }
            case 0xAE: {     /*XOR (HL)*/
                xor(MemIoImpl.peek8(getRegHL()));
                break;
            }
            case 0xAF: {     /*XOR A*/
                xor(regA);
                break;
            }
            case 0xB0: {     /*OR B*/
                or(regB);
                break;
            }
            case 0xB1: {     /*OR C*/
                or(regC);
                break;
            }
            case 0xB2: {     /*OR D*/
                or(regD);
                break;
            }
            case 0xB3: {     /*OR E*/
                or(regE);
                break;
            }
            case 0xB4: {     /*OR H*/
                or(regH);
                break;
            }
            case 0xB5: {     /*OR L*/
                or(regL);
                break;
            }
            case 0xB6: {     /*OR (HL)*/
                or(MemIoImpl.peek8(getRegHL()));
                break;
            }
            case 0xB7: {     /*OR A*/
                or(regA);
                break;
            }
            case 0xB8: {     /*CP B*/
                cp(regB);
                break;
            }
            case 0xB9: {     /*CP C*/
                cp(regC);
                break;
            }
            case 0xBA: {     /*CP D*/
                cp(regD);
                break;
            }
            case 0xBB: {     /*CP E*/
                cp(regE);
                break;
            }
            case 0xBC: {     /*CP H*/
                cp(regH);
                break;
            }
            case 0xBD: {     /*CP L*/
                cp(regL);
                break;
            }
            case 0xBE: {     /*CP (HL)*/
                cp(MemIoImpl.peek8(getRegHL()));
                break;
            }
            case 0xBF: {     /*CP A*/
                cp(regA);
                break;
            }
            case 0xC0: {     /*RET NZ*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                if ( (sz5h3pnFlags & ZERO_MASK) == 0 ) {
                    regPC = memptr = pop();
                }
                break;
            }
            case 0xC1: {     /*POP BC*/
                setRegBC(pop());
                break;
            }
            case 0xC2: {     /*JP NZ,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if( (sz5h3pnFlags & ZERO_MASK) == 0 ) {
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0xC3: {     /*JP nn*/
                memptr = regPC = MemIoImpl.peek16(regPC);
                break;
            }
            case 0xC4: {     /*CALL NZ,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( (sz5h3pnFlags & ZERO_MASK) == 0 ) {
                    MemIoImpl.contendedStates(regPC + 1, 1);
                    push(regPC + 2);
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0xC5: {     /*PUSH BC*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regB, regC);
                break;
            }
            case 0xC6: {     /*ADD A,n*/
                add(MemIoImpl.peek8(regPC));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xC7: {     /*RST 00H*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regPC);
                regPC = memptr = 0x00;
                break;
            }
            case 0xC8: {     /*RET Z*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                if ( (sz5h3pnFlags & ZERO_MASK) != 0 ) {
                    regPC = memptr = pop();
                }
                break;
            }
            case 0xC9: {     /*RET*/
                regPC = memptr = pop();
                break;
            }
            case 0xCA: {     /*JP Z,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( (sz5h3pnFlags & ZERO_MASK) != 0 ) {
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0xCB: {     /*Subconjunto de instrucciones*/
                decodeCB();
                break;
            }
            case 0xCC: {     /*CALL Z,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( (sz5h3pnFlags & ZERO_MASK) != 0 ) {
                    MemIoImpl.contendedStates(regPC + 1, 1);
                    push(regPC + 2);
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0xCD: {     /*CALL nn*/
                memptr = MemIoImpl.peek16(regPC);
                MemIoImpl.contendedStates(regPC + 1, 1);
                push(regPC + 2);
                regPC = memptr;
                break;
            }
            case 0xCE: {     /*ADC A,n*/
                adc(MemIoImpl.peek8(regPC));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xCF: {     /*RST 08H*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regPC);
                regPC = memptr = 0x08;
                break;
            }
            case 0xD0: {     /*RET NC*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                if (!carryFlag) {
                    regPC = memptr = pop();
                }
                break;
            }
            case 0xD1: {     /*POP DE*/
                setRegDE(pop());
                break;
            }
            case 0xD2: {     /*JP NC,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if (!carryFlag) {
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0xD3: {     /*OUT (n),A*/
                work8 = MemIoImpl.peek8(regPC);
                MemIoImpl.outPort((regA << 8) | work8, regA);
                memptr = (regA <<8 ) | (work8 + 1);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xD4: {     /*CALL NC,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if (!carryFlag) {
                    MemIoImpl.contendedStates(regPC + 1, 1);
                    push(regPC + 2);
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0xD5: {     /*PUSH DE*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regD, regE);
                break;
            }
            case 0xD6: {     /*SUB n*/
                sub(MemIoImpl.peek8(regPC));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xD7: {     /*RST 10H*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regPC);
                regPC = memptr = 0x10;
                break;
            }
            case 0xD8: {     /*RET C*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                if (carryFlag) {
                    regPC = memptr = pop();
                }
                break;
            }
            case 0xD9: {     /*EXX*/
                exx();
                break;
            }
            case 0xDA: {     /*JP C,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if (carryFlag) {
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0xDB: {     /*IN A,(n)*/
                work8 = MemIoImpl.peek8(regPC);
                memptr = (regA << 8) + work8 + 1;
                regA = MemIoImpl.inPort((regA << 8) | work8);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xDC: {     /*CALL C,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if (carryFlag) {
                    MemIoImpl.contendedStates(regPC + 1, 1);
                    push(regPC + 2);
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0xDD: {     /*Subconjunto de instrucciones*/
                regIX = decodeDDFD(regIX);
                break;
            }
            case 0xDE: {     /*SBC A,n*/
                sbc(MemIoImpl.peek8(regPC));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xDF: {     /*RST 18 H*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regPC);
                regPC = memptr = 0x18;
                break;
            }
            case 0xE0:       /*RET PO*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                if ( (sz5h3pnFlags & PARITY_MASK) == 0 ) {
                    regPC = memptr = pop();
                }
                break;
            case 0xE1:       /*POP HL*/
                setRegHL(pop());
                break;
            case 0xE2:       /*JP PO,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( (sz5h3pnFlags & PARITY_MASK) == 0 ) {
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            case 0xE3:       /*EX (SP),HL*/
                // Instrucción de ejecución sutil.
                work16 = getRegHL();
                setRegHL(MemIoImpl.peek16(regSP));
                MemIoImpl.contendedStates(regSP + 1, 1);
                // No se usa poke16 porque el Z80 escribe los bytes AL REVES
                MemIoImpl.poke8((regSP + 1) & 0xffff, (work16 >>> 8) & 0xff);
                MemIoImpl.poke8(regSP, work16 & 0xff);
                MemIoImpl.contendedStates(regSP, 2);
                memptr = getRegHL();
                break;
            case 0xE4:       /*CALL PO,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( (sz5h3pnFlags & PARITY_MASK) == 0 ) {
                    MemIoImpl.contendedStates(regPC + 1 , 1);
                    push(regPC + 2);
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            case 0xE5:       /*PUSH HL*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regH, regL);
                break;
            case 0xE6:       /*AND n*/
                and(MemIoImpl.peek8(regPC));
                regPC = (regPC + 1) & 0xffff;
                break;
            case 0xE7:       /*RST 20H*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regPC);
                regPC = memptr = 0x20;
                break;
            case 0xE8:       /*RET PE*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                if ( (sz5h3pnFlags & PARITY_MASK) != 0 ) {
                    regPC = memptr = pop();
                }
                break;
            case 0xE9:       /*JP (HL)*/
                regPC = getRegHL();
                break;
            case 0xEA:       /*JP PE,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( (sz5h3pnFlags & PARITY_MASK) != 0 ) {
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            case 0xEB:       /*EX DE,HL*/
                work8 = regH;
                regH = regD;
                regD = work8;
                work8 = regL;
                regL = regE;
                regE = work8;
                break;
            case 0xEC:       /*CALL PE,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( (sz5h3pnFlags & PARITY_MASK) != 0 ) {
                    MemIoImpl.contendedStates(regPC + 1, 1);
                    push(regPC + 2);
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            case 0xED:       /*Subconjunto de instrucciones*/
                decodeED();
                break;
            case 0xEE:       /*XOR n*/
                xor(MemIoImpl.peek8(regPC));
                regPC = (regPC + 1) & 0xffff;
                break;
            case 0xEF:       /*RST 28H*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regPC);
                regPC = memptr = 0x28;
                break;
            case 0xF0:       /*RET P*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                if ( sz5h3pnFlags < SIGN_MASK ) {
                    regPC = memptr = pop();
                }
                break;
            case 0xF1:       /*POP AF*/
                work16 = pop();
                regA = (work16 >>> 8);
                setFlags(work16 & 0xff);
                break;
            case 0xF2:       /*JP P,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( sz5h3pnFlags < SIGN_MASK ) {
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            case 0xF3:       /*DI*/
                ffIFF1 = false;
                ffIFF2 = false;
                break;
            case 0xF4:       /*CALL P,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( sz5h3pnFlags < SIGN_MASK ) {
                    MemIoImpl.contendedStates(regPC + 1, 1);
                    push(regPC + 2);
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            case 0xF5:       /*PUSH AF*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regA, getFlags());
                break;
            case 0xF6:       /*OR n*/
                or(MemIoImpl.peek8(regPC));
                regPC = (regPC + 1) & 0xffff;
                break;
            case 0xF7:       /*RST 30H*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regPC);
                regPC = memptr = 0x30;
                break;
            case 0xF8:       /*RET M*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                if ( sz5h3pnFlags > 0x7f ) {
                    regPC = memptr = pop();
                }
                break;
            case 0xF9:       /*LD SP,HL*/
                //System.out.println(String.format("PC: %04x\tt-states: %d",regPC-1, tEstados));
                regSP = getRegHL();
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            case 0xFA:       /*JP M,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( sz5h3pnFlags > 0x7f ) {
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            case 0xFB:       /*EI*/
                ffIFF1 = true;
                ffIFF2 = true;
                pendingEI = true;
                break;
            case 0xFC:       /*CALL M,nn*/
                memptr = MemIoImpl.peek16(regPC);
                if ( sz5h3pnFlags > 0x7f ) {
                    MemIoImpl.contendedStates(regPC + 1, 1);
                    push(regPC + 2);
                    regPC = memptr;
                    break;
                }
                regPC = (regPC + 2) & 0xffff;
                break;
            case 0xFD:       /*Subconjunto de instrucciones*/
                regIY = decodeDDFD(regIY);
                break;
            case 0xFE:       /*CP n*/
                cp(MemIoImpl.peek8(regPC));
                regPC = (regPC + 1) & 0xffff;
                break;
            case 0xFF:       /*RST 38H*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regPC);
                regPC = memptr = 0x38;
        } /* del switch( codigo ) */
    }

    //Subconjunto de instrucciones 0xCB
    private final void decodeCB() {
        int work8, work16;
        regR++;
        opCode = MemIoImpl.fetchOpcode(regPC);
        regPC = (regPC + 1) & 0xffff;
        switch (opCode) {
            case 0x00: {     /*RLC B*/
                regB = rlc(regB);
                break;
            }
            case 0x01: {     /*RLC C*/
                regC = rlc(regC);
                break;
            }
            case 0x02: {     /*RLC D*/
                regD = rlc(regD);
                break;
            }
            case 0x03: {     /*RLC E*/
                regE = rlc(regE);
                break;
            }
            case 0x04: {     /*RLC H*/
                regH = rlc(regH);
                break;
            }
            case 0x05: {     /*RLC L*/
                regL = rlc(regL);
                break;
            }
            case 0x06: {     /*RLC (HL)*/
                work16 = getRegHL();
                work8 = rlc(MemIoImpl.peek8(work16));
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, work8);
                break;
            }
            case 0x07: {     /*RLC A*/
                regA = rlc(regA);
                break;
            }
            case 0x08: {     /*RRC B*/
                regB = rrc(regB);
                break;
            }
            case 0x09: {     /*RRC C*/
                regC = rrc(regC);
                break;
            }
            case 0x0A: {     /*RRC D*/
                regD = rrc(regD);
                break;
            }
            case 0x0B: {     /*RRC E*/
                regE = rrc(regE);
                break;
            }
            case 0x0C: {     /*RRC H*/
                regH = rrc(regH);
                break;
            }
            case 0x0D: {     /*RRC L*/
                regL = rrc(regL);
                break;
            }
            case 0x0E: {     /*RRC (HL)*/
                work16 = getRegHL();
                work8 = rrc(MemIoImpl.peek8(work16));
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, work8);
                break;
            }
            case 0x0F: {     /*RRC A*/
                regA = rrc(regA);
                break;
            }
            case 0x10: {     /*RL B*/
                regB = rl(regB);
                break;
            }
            case 0x11: {     /*RL C*/
                regC = rl(regC);
                break;
            }
            case 0x12: {     /*RL D*/
                regD = rl(regD);
                break;
            }
            case 0x13: {     /*RL E*/
                regE = rl(regE);
                break;
            }
            case 0x14: {     /*RL H*/
                regH = rl(regH);
                break;
            }
            case 0x15: {     /*RL L*/
                regL = rl(regL);
                break;
            }
            case 0x16: {     /*RL (HL)*/
                work16 = getRegHL();
                work8 = rl(MemIoImpl.peek8(work16));
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, work8);
                break;
            }
            case 0x17: {     /*RL A*/
                regA = rl(regA);
                break;
            }
            case 0x18: {     /*RR B*/
                regB = rr(regB);
                break;
            }
            case 0x19: {     /*RR C*/
                regC = rr(regC);
                break;
            }
            case 0x1A: {     /*RR D*/
                regD = rr(regD);
                break;
            }
            case 0x1B: {     /*RR E*/
                regE = rr(regE);
                break;
            }
            case 0x1C: {     /*RR H*/
                regH = rr(regH);
                break;
            }
            case 0x1D: {     /*RR L*/
                regL = rr(regL);
                break;
            }
            case 0x1E: {     /*RR (HL)*/
                work16 = getRegHL();
                work8 = rr(MemIoImpl.peek8(work16));
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, work8);
                break;
            }
            case 0x1F: {     /*RR A*/
                regA = rr(regA);
                break;
            }
            case 0x20: {     /*SLA B*/
                regB = sla(regB);
                break;
            }
            case 0x21: {     /*SLA C*/
                regC = sla(regC);
                break;
            }
            case 0x22: {     /*SLA D*/
                regD = sla(regD);
                break;
            }
            case 0x23: {     /*SLA E*/
                regE = sla(regE);
                break;
            }
            case 0x24: {     /*SLA H*/
                regH = sla(regH);
                break;
            }
            case 0x25: {     /*SLA L*/
                regL = sla(regL);
                break;
            }
            case 0x26: {     /*SLA (HL)*/
                work16 = getRegHL();
                work8 = sla(MemIoImpl.peek8(work16));
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, work8);
                break;
            }
            case 0x27: {     /*SLA A*/
                regA = sla(regA);
                break;
            }
            case 0x28: {     /*SRA B*/
                regB = sra(regB);
                break;
            }
            case 0x29: {     /*SRA C*/
                regC = sra(regC);
                break;
            }
            case 0x2A: {     /*SRA D*/
                regD = sra(regD);
                break;
            }
            case 0x2B: {     /*SRA E*/
                regE = sra(regE);
                break;
            }
            case 0x2C: {     /*SRA H*/
                regH = sra(regH);
                break;
            }
            case 0x2D: {     /*SRA L*/
                regL = sra(regL);
                break;
            }
            case 0x2E: {     /*SRA (HL)*/
                work16 = getRegHL();
                work8 = sra(MemIoImpl.peek8(work16));
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, work8);
                break;
            }
            case 0x2F: {     /*SRA A*/
                regA = sra(regA);
                break;
            }
            case 0x30: {     /*SLL B*/
                regB = sll(regB);
                break;
            }
            case 0x31: {     /*SLL C*/
                regC = sll(regC);
                break;
            }
            case 0x32: {     /*SLL D*/
                regD = sll(regD);
                break;
            }
            case 0x33: {     /*SLL E*/
                regE = sll(regE);
                break;
            }
            case 0x34: {     /*SLL H*/
                regH = sll(regH);
                break;
            }
            case 0x35: {     /*SLL L*/
                regL = sll(regL);
                break;
            }
            case 0x36: {     /*SLL (HL)*/
                work16 = getRegHL();
                work8 = sll(MemIoImpl.peek8(work16));
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, work8);
                break;
            }
            case 0x37: {     /*SLL A*/
                regA = sll(regA);
                break;
            }
            case 0x38: {     /*SRL B*/
                regB = srl(regB);
                break;
            }
            case 0x39: {     /*SRL C*/
                regC = srl(regC);
                break;
            }
            case 0x3A: {     /*SRL D*/
                regD = srl(regD);
                break;
            }
            case 0x3B: {     /*SRL E*/
                regE = srl(regE);
                break;
            }
            case 0x3C: {     /*SRL H*/
                regH = srl(regH);
                break;
            }
            case 0x3D: {     /*SRL L*/
                regL = srl(regL);
                break;
            }
            case 0x3E: {     /*SRL (HL)*/
                work16 = getRegHL();
                work8 = srl(MemIoImpl.peek8(work16));
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, work8);
                break;
            }
            case 0x3F: {     /*SRL A*/
                regA = srl(regA);
                break;
            }
            case 0x40: {     /*BIT 0,B*/
                bit(0x01, regB);
                break;
            }
            case 0x41: {     /*BIT 0,C*/
                bit(0x01, regC);
                break;
            }
            case 0x42: {     /*BIT 0,D*/
                bit(0x01, regD);
                break;
            }
            case 0x43: {     /*BIT 0,E*/
                bit(0x01, regE);
                break;
            }
            case 0x44: {     /*BIT 0,H*/
                bit(0x01, regH);
                break;
            }
            case 0x45: {     /*BIT 0,L*/
                bit(0x01, regL);
                break;
            }
            case 0x46: {     /*BIT 0,(HL)*/
                work16 = getRegHL();
                bit(0x01, MemIoImpl.peek8(work16));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                    ((memptr >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(work16, 1);
                break;
            }
            case 0x47: {     /*BIT 0,A*/
                bit(0x01, regA);
                break;
            }
            case 0x48: {     /*BIT 1,B*/
                bit(0x02, regB);
                break;
            }
            case 0x49: {     /*BIT 1,C*/
                bit(0x02, regC);
                break;
            }
            case 0x4A: {     /*BIT 1,D*/
                bit(0x02, regD);
                break;
            }
            case 0x4B: {     /*BIT 1,E*/
                bit(0x02, regE);
                break;
            }
            case 0x4C: {     /*BIT 1,H*/
                bit(0x02, regH);
                break;
            }
            case 0x4D: {     /*BIT 1,L*/
                bit(0x02, regL);
                break;
            }
            case 0x4E: {     /*BIT 1,(HL)*/
                work16 = getRegHL();
                bit(0x02, MemIoImpl.peek8(work16));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                    ((memptr >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(work16, 1);
                break;
            }
            case 0x4F: {     /*BIT 1,A*/
                bit(0x02, regA);
                break;
            }
            case 0x50: {     /*BIT 2,B*/
                bit(0x04, regB);
                break;
            }
            case 0x51: {     /*BIT 2,C*/
                bit(0x04, regC);
                break;
            }
            case 0x52: {     /*BIT 2,D*/
                bit(0x04, regD);
                break;
            }
            case 0x53: {     /*BIT 2,E*/
                bit(0x04, regE);
                break;
            }
            case 0x54: {     /*BIT 2,H*/
                bit(0x04, regH);
                break;
            }
            case 0x55: {     /*BIT 2,L*/
                bit(0x04, regL);
                break;
            }
            case 0x56: {     /*BIT 2,(HL)*/
                work16 = getRegHL();
                bit(0x04, MemIoImpl.peek8(work16));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                    ((memptr >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(work16, 1);
                break;
            }
            case 0x57: {     /*BIT 2,A*/
                bit(0x04, regA);
                break;
            }
            case 0x58: {     /*BIT 3,B*/
                bit(0x08, regB);
                break;
            }
            case 0x59: {     /*BIT 3,C*/
                bit(0x08, regC);
                break;
            }
            case 0x5A: {     /*BIT 3,D*/
                bit(0x08, regD);
                break;
            }
            case 0x5B: {     /*BIT 3,E*/
                bit(0x08, regE);
                break;
            }
            case 0x5C: {     /*BIT 3,H*/
                bit(0x08, regH);
                break;
            }
            case 0x5D: {     /*BIT 3,L*/
                bit(0x08, regL);
                break;
            }
            case 0x5E: {     /*BIT 3,(HL)*/
                work16 = getRegHL();
                bit(0x08, MemIoImpl.peek8(work16));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                    ((memptr >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(work16, 1);
                break;
            }
            case 0x5F: {     /*BIT 3,A*/
                bit(0x08, regA);
                break;
            }
            case 0x60: {     /*BIT 4,B*/
                bit(0x10, regB);
                break;
            }
            case 0x61: {     /*BIT 4,C*/
                bit(0x10, regC);
                break;
            }
            case 0x62: {     /*BIT 4,D*/
                bit(0x10, regD);
                break;
            }
            case 0x63: {     /*BIT 4,E*/
                bit(0x10, regE);
                break;
            }
            case 0x64: {     /*BIT 4,H*/
                bit(0x10, regH);
                break;
            }
            case 0x65: {     /*BIT 4,L*/
                bit(0x10, regL);
                break;
            }
            case 0x66: {     /*BIT 4,(HL)*/
                work16 = getRegHL();
                bit(0x10, MemIoImpl.peek8(work16));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                    ((memptr >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(work16, 1);
                break;
            }
            case 0x67: {     /*BIT 4,A*/
                bit(0x10, regA);
                break;
            }
            case 0x68: {     /*BIT 5,B*/
                bit(0x20, regB);
                break;
            }
            case 0x69: {     /*BIT 5,C*/
                bit(0x20, regC);
                break;
            }
            case 0x6A: {     /*BIT 5,D*/
                bit(0x20, regD);
                break;
            }
            case 0x6B: {     /*BIT 5,E*/
                bit(0x20, regE);
                break;
            }
            case 0x6C: {     /*BIT 5,H*/
                bit(0x20, regH);
                break;
            }
            case 0x6D: {     /*BIT 5,L*/
                bit(0x20, regL);
                break;
            }
            case 0x6E: {     /*BIT 5,(HL)*/
                work16 = getRegHL();
                bit(0x20, MemIoImpl.peek8(work16));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                    ((memptr >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(work16, 1);
                break;
            }
            case 0x6F: {     /*BIT 5,A*/
                bit(0x20, regA);
                break;
            }
            case 0x70: {     /*BIT 6,B*/
                bit(0x40, regB);
                break;
            }
            case 0x71: {     /*BIT 6,C*/
                bit(0x40, regC);
                break;
            }
            case 0x72: {     /*BIT 6,D*/
                bit(0x40, regD);
                break;
            }
            case 0x73: {     /*BIT 6,E*/
                bit(0x40, regE);
                break;
            }
            case 0x74: {     /*BIT 6,H*/
                bit(0x40, regH);
                break;
            }
            case 0x75: {     /*BIT 6,L*/
                bit(0x40, regL);
                break;
            }
            case 0x76: {     /*BIT 6,(HL)*/
                work16 = getRegHL();
                bit(0x40, MemIoImpl.peek8(work16));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                    ((memptr >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(work16, 1);
                break;
            }
            case 0x77: {     /*BIT 6,A*/
                bit(0x40, regA);
                break;
            }
            case 0x78: {     /*BIT 7,B*/
                bit(0x80, regB);
                break;
            }
            case 0x79: {     /*BIT 7,C*/
                bit(0x80, regC);
                break;
            }
            case 0x7A: {     /*BIT 7,D*/
                bit(0x80, regD);
                break;
            }
            case 0x7B: {     /*BIT 7,E*/
                bit(0x80, regE);
                break;
            }
            case 0x7C: {     /*BIT 7,H*/
                bit(0x80, regH);
                break;
            }
            case 0x7D: {     /*BIT 7,L*/
                bit(0x80, regL);
                break;
            }
            case 0x7E: {     /*BIT 7,(HL)*/
                work16 = getRegHL();
                bit(0x80, MemIoImpl.peek8(work16));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                    ((memptr >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(work16, 1);
                break;
            }
            case 0x7F: {     /*BIT 7,A*/
                bit(0x80, regA);
                break;
            }
            case 0x80: {     /*RES 0,B*/
                regB = res(0x01, regB);
                break;
            }
            case 0x81: {     /*RES 0,C*/
                regC = res(0x01, regC);
                break;
            }
            case 0x82: {     /*RES 0,D*/
                regD = res(0x01, regD);
                break;
            }
            case 0x83: {     /*RES 0,E*/
                regE = res(0x01, regE);
                break;
            }
            case 0x84: {     /*RES 0,H*/
                regH = res(0x01, regH);
                break;
            }
            case 0x85: {     /*RES 0,L*/
                regL = res(0x01, regL);
                break;
            }
            case 0x86: {     /*RES 0,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, res(0x01, work8));
                break;
            }
            case 0x87: {     /*RES 0,A*/
                regA = res(0x01, regA);
                break;
            }
            case 0x88: {     /*RES 1,B*/
                regB = res(0x02, regB);
                break;
            }
            case 0x89: {     /*RES 1,C*/
                regC = res(0x02, regC);
                break;
            }
            case 0x8A: {     /*RES 1,D*/
                regD = res(0x02, regD);
                break;
            }
            case 0x8B: {     /*RES 1,E*/
                regE = res(0x02, regE);
                break;
            }
            case 0x8C: {     /*RES 1,H*/
                regH = res(0x02, regH);
                break;
            }
            case 0x8D: {     /*RES 1,L*/
                regL = res(0x02, regL);
                break;
            }
            case 0x8E: {     /*RES 1,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, res(0x02, work8));
                break;
            }
            case 0x8F: {     /*RES 1,A*/
                regA = res(0x02, regA);
                break;
            }
            case 0x90: {     /*RES 2,B*/
                regB = res(0x04, regB);
                break;
            }
            case 0x91: {     /*RES 2,C*/
                regC = res(0x04, regC);
                break;
            }
            case 0x92: {     /*RES 2,D*/
                regD = res(0x04, regD);
                break;
            }
            case 0x93: {     /*RES 2,E*/
                regE = res(0x04, regE);
                break;
            }
            case 0x94: {     /*RES 2,H*/
                regH = res(0x04, regH);
                break;
            }
            case 0x95: {     /*RES 2,L*/
                regL = res(0x04, regL);
                break;
            }
            case 0x96: {     /*RES 2,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, res(0x04, work8));
                break;
            }
            case 0x97: {     /*RES 2,A*/
                regA = res(0x04, regA);
                break;
            }
            case 0x98: {     /*RES 3,B*/
                regB = res(0x08, regB);
                break;
            }
            case 0x99: {     /*RES 3,C*/
                regC = res(0x08, regC);
                break;
            }
            case 0x9A: {     /*RES 3,D*/
                regD = res(0x08, regD);
                break;
            }
            case 0x9B: {     /*RES 3,E*/
                regE = res(0x08, regE);
                break;
            }
            case 0x9C: {     /*RES 3,H*/
                regH = res(0x08, regH);
                break;
            }
            case 0x9D: {     /*RES 3,L*/
                regL = res(0x08, regL);
                break;
            }
            case 0x9E: {     /*RES 3,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, res(0x08, work8));
                break;
            }
            case 0x9F: {     /*RES 3,A*/
                regA = res(0x08, regA);
                break;
            }
            case 0xA0: {     /*RES 4,B*/
                regB = res(0x10, regB);
                break;
            }
            case 0xA1: {     /*RES 4,C*/
                regC = res(0x10, regC);
                break;
            }
            case 0xA2: {     /*RES 4,D*/
                regD = res(0x10, regD);
                break;
            }
            case 0xA3: {     /*RES 4,E*/
                regE = res(0x10, regE);
                break;
            }
            case 0xA4: {     /*RES 4,H*/
                regH = res(0x10, regH);
                break;
            }
            case 0xA5: {     /*RES 4,L*/
                regL = res(0x10, regL);
                break;
            }
            case 0xA6: {     /*RES 4,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, res(0x10, work8));
                break;
            }
            case 0xA7: {     /*RES 4,A*/
                regA = res(0x10, regA);
                break;
            }
            case 0xA8: {     /*RES 5,B*/
                regB = res(0x20, regB);
                break;
            }
            case 0xA9: {     /*RES 5,C*/
                regC = res(0x20, regC);
                break;
            }
            case 0xAA: {     /*RES 5,D*/
                regD = res(0x20, regD);
                break;
            }
            case 0xAB: {     /*RES 5,E*/
                regE = res(0x20, regE);
                break;
            }
            case 0xAC: {     /*RES 5,H*/
                regH = res(0x20, regH);
                break;
            }
            case 0xAD: {     /*RES 5,L*/
                regL = res(0x20, regL);
                break;
            }
            case 0xAE: {     /*RES 5,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, res(0x20, work8));
                break;
            }
            case 0xAF: {     /*RES 5,A*/
                regA = res(0x20, regA);
                break;
            }
            case 0xB0: {     /*RES 6,B*/
                regB = res(0x40, regB);
                break;
            }
            case 0xB1: {     /*RES 6,C*/
                regC = res(0x40, regC);
                break;
            }
            case 0xB2: {     /*RES 6,D*/
                regD = res(0x40, regD);
                break;
            }
            case 0xB3: {     /*RES 6,E*/
                regE = res(0x40, regE);
                break;
            }
            case 0xB4: {     /*RES 6,H*/
                regH = res(0x40, regH);
                break;
            }
            case 0xB5: {     /*RES 6,L*/
                regL = res(0x40, regL);
                break;
            }
            case 0xB6: {     /*RES 6,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, res(0x40, work8));
                break;
            }
            case 0xB7: {     /*RES 6,A*/
                regA = res(0x40, regA);
                break;
            }
            case 0xB8: {     /*RES 7,B*/
                regB = res(0x80, regB);
                break;
            }
            case 0xB9: {     /*RES 7,C*/
                regC = res(0x80, regC);
                break;
            }
            case 0xBA: {     /*RES 7,D*/
                regD = res(0x80, regD);
                break;
            }
            case 0xBB: {     /*RES 7,E*/
                regE = res(0x80, regE);
                break;
            }
            case 0xBC: {     /*RES 7,H*/
                regH = res(0x80, regH);
                break;
            }
            case 0xBD: {     /*RES 7,L*/
                regL = res(0x80, regL);
                break;
            }
            case 0xBE: {     /*RES 7,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, res(0x80, work8));
                break;
            }
            case 0xBF: {     /*RES 7,A*/
                regA = res(0x80, regA);
                break;
            }
            case 0xC0: {     /*SET 0,B*/
                regB = set(0x01, regB);
                break;
            }
            case 0xC1: {     /*SET 0,C*/
                regC = set(0x01, regC);
                break;
            }
            case 0xC2: {     /*SET 0,D*/
                regD = set(0x01, regD);
                break;
            }
            case 0xC3: {     /*SET 0,E*/
                regE = set(0x01, regE);
                break;
            }
            case 0xC4: {     /*SET 0,H*/
                regH = set(0x01, regH);
                break;
            }
            case 0xC5: {     /*SET 0,L*/
                regL = set(0x01, regL);
                break;
            }
            case 0xC6: {     /*SET 0,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, set(0x01, work8));
                break;
            }
            case 0xC7: {     /*SET 0,A*/
                regA = set(0x01, regA);
                break;
            }
            case 0xC8: {     /*SET 1,B*/
                regB = set(0x02, regB);
                break;
            }
            case 0xC9: {     /*SET 1,C*/
                regC = set(0x02, regC);
                break;
            }
            case 0xCA: {     /*SET 1,D*/
                regD = set(0x02, regD);
                break;
            }
            case 0xCB: {     /*SET 1,E*/
                regE = set(0x02, regE);
                break;
            }
            case 0xCC: {     /*SET 1,H*/
                regH = set(0x02, regH);
                break;
            }
            case 0xCD: {     /*SET 1,L*/
                regL = set(0x02, regL);
                break;
            }
            case 0xCE: {     /*SET 1,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, set(0x02, work8));
                break;
            }
            case 0xCF: {     /*SET 1,A*/
                regA = set(0x02, regA);
                break;
            }
            case 0xD0: {     /*SET 2,B*/
                regB = set(0x04, regB);
                break;
            }
            case 0xD1: {     /*SET 2,C*/
                regC = set(0x04, regC);
                break;
            }
            case 0xD2: {     /*SET 2,D*/
                regD = set(0x04, regD);
                break;
            }
            case 0xD3: {     /*SET 2,E*/
                regE = set(0x04, regE);
                break;
            }
            case 0xD4: {     /*SET 2,H*/
                regH = set(0x04, regH);
                break;
            }
            case 0xD5: {     /*SET 2,L*/
                regL = set(0x04, regL);
                break;
            }
            case 0xD6: {     /*SET 2,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, set(0x04, work8));
                break;
            }
            case 0xD7: {     /*SET 2,A*/
                regA = set(0x04, regA);
                break;
            }
            case 0xD8: {     /*SET 3,B*/
                regB = set(0x08, regB);
                break;
            }
            case 0xD9: {     /*SET 3,C*/
                regC = set(0x08, regC);
                break;
            }
            case 0xDA: {     /*SET 3,D*/
                regD = set(0x08, regD);
                break;
            }
            case 0xDB: {     /*SET 3,E*/
                regE = set(0x08, regE);
                break;
            }
            case 0xDC: {     /*SET 3,H*/
                regH = set(0x08, regH);
                break;
            }
            case 0xDD: {     /*SET 3,L*/
                regL = set(0x08, regL);
                break;
            }
            case 0xDE: {     /*SET 3,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, set(0x08, work8));
                break;
            }
            case 0xDF: {     /*SET 3,A*/
                regA = set(0x08, regA);
                break;
            }
            case 0xE0: {     /*SET 4,B*/
                regB = set(0x10, regB);
                break;
            }
            case 0xE1: {     /*SET 4,C*/
                regC = set(0x10, regC);
                break;
            }
            case 0xE2: {     /*SET 4,D*/
                regD = set(0x10, regD);
                break;
            }
            case 0xE3: {     /*SET 4,E*/
                regE = set(0x10, regE);
                break;
            }
            case 0xE4: {     /*SET 4,H*/
                regH = set(0x10, regH);
                break;
            }
            case 0xE5: {     /*SET 4,L*/
                regL = set(0x10, regL);
                break;
            }
            case 0xE6: {     /*SET 4,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, set(0x10, work8));
                break;
            }
            case 0xE7: {     /*SET 4,A*/
                regA = set(0x10, regA);
                break;
            }
            case 0xE8: {     /*SET 5,B*/
                regB = set(0x20, regB);
                break;
            }
            case 0xE9: {     /*SET 5,C*/
                regC = set(0x20, regC);
                break;
            }
            case 0xEA: {     /*SET 5,D*/
                regD = set(0x20, regD);
                break;
            }
            case 0xEB: {     /*SET 5,E*/
                regE = set(0x20, regE);
                break;
            }
            case 0xEC: {     /*SET 5,H*/
                regH = set(0x20, regH);
                break;
            }
            case 0xED: {     /*SET 5,L*/
                regL = set(0x20, regL);
                break;
            }
            case 0xEE: {     /*SET 5,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, set(0x20, work8));
                break;
            }
            case 0xEF: {     /*SET 5,A*/
                regA = set(0x20, regA);
                break;
            }
            case 0xF0: {     /*SET 6,B*/
                regB = set(0x40, regB);
                break;
            }
            case 0xF1: {     /*SET 6,C*/
                regC = set(0x40, regC);
                break;
            }
            case 0xF2: {     /*SET 6,D*/
                regD = set(0x40, regD);
                break;
            }
            case 0xF3: {     /*SET 6,E*/
                regE = set(0x40, regE);
                break;
            }
            case 0xF4: {     /*SET 6,H*/
                regH = set(0x40, regH);
                break;
            }
            case 0xF5: {     /*SET 6,L*/
                regL = set(0x40, regL);
                break;
            }
            case 0xF6: {     /*SET 6,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, set(0x40, work8));
                break;
            }
            case 0xF7: {     /*SET 6,A*/
                regA = set(0x40, regA);
                break;
            }
            case 0xF8: {     /*SET 7,B*/
                regB = set(0x80, regB);
                break;
            }
            case 0xF9: {     /*SET 7,C*/
                regC = set(0x80, regC);
                break;
            }
            case 0xFA: {     /*SET 7,D*/
                regD = set(0x80, regD);
                break;
            }
            case 0xFB: {     /*SET 7,E*/
                regE = set(0x80, regE);
                break;
            }
            case 0xFC: {     /*SET 7,H*/
                regH = set(0x80, regH);
                break;
            }
            case 0xFD: {     /*SET 7,L*/
                regL = set(0x80, regL);
                break;
            }
            case 0xFE: {     /*SET 7,(HL)*/
                work16 = getRegHL();
                work8 = MemIoImpl.peek8(work16);
                MemIoImpl.contendedStates(work16, 1);
                MemIoImpl.poke8(work16, set(0x80, work8));
                break;
            }
            case 0xFF: {     /*SET 7,A*/
                regA = set(0x80, regA);
                break;
            }
            default: {
//                System.out.println("Error instrucción CB " + Integer.toHexString(opCode));
                break;
            }
        }
    }

    //Subconjunto de instrucciones 0xDD / 0xFD
    /*
     * Hay que tener en cuenta el manejo de secuencias códigos DD/FD que no
     * hacen nada. Según el apartado 3.7 del documento
     * [http://www.myquest.nl/z80undocumented/z80-documented-v0.91.pdf]
     * secuencias de códigos como FD DD 00 21 00 10 NOP NOP NOP LD HL,1000h
     * activan IY con el primer FD, IX con el segundo DD y vuelven al
     * registro HL con el código NOP. Es decir, si detrás del código DD/FD no
     * viene una instrucción que maneje el registro HL, el código DD/FD
     * "se olvida" y hay que reprocesar la instrucción como si nunca se
     * hubiera visto el prefijo (salvo por los 4 t-estados que ha costado).
     * Naturalmente, en una serie repetida de DDFD hay que comprobar las
     * interrupciones entre cada prefijo.
     */
    private final int decodeDDFD(int regIXY) {
        int work8 = tEstados;
        regR++;
        opCode = MemIoImpl.fetchOpcode(regPC);
        regPC = (regPC + 1) & 0xffff;
        switch (opCode) {
            case 0x09: {     /* ADD IX,BC */
                regIXY = add16(regIXY, getRegBC());
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x19: {     /* ADD IX,DE */
                regIXY = add16(regIXY, getRegDE());
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x21: {     /*LD IX,nn*/
                regIXY = MemIoImpl.peek16(regPC);
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x22: {     /*LD (nn),IX*/
                memptr = MemIoImpl.peek16(regPC);
                MemIoImpl.poke16(memptr, regIXY);
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x23: {     /*INC IX*/
                regIXY = (regIXY + 1) & 0xffff;
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            }
            case 0x24: {     /*INC IXh*/
                regIXY = (inc8(regIXY >>> 8) << 8) | (regIXY & 0xff);
                break;
            }
            case 0x25: {     /*DEC IXh*/
                regIXY = (dec8(regIXY >>> 8) << 8) | (regIXY & 0xff);
                break;
            }
            case 0x26: {     /*LD IXh,n*/
                regIXY = (MemIoImpl.peek8(regPC) << 8) | (regIXY & 0xff);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x29: {     /*ADD IX,IX*/
                regIXY = add16(regIXY, regIXY);
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x2A: {     /*LD IX,(nn)*/
                memptr = MemIoImpl.peek16(regPC);
                regIXY = MemIoImpl.peek16(memptr);
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x2B: {     /*DEC IX*/
                regIXY = (regIXY - 1) & 0xffff;
                MemIoImpl.contendedStates(getPairIR(), 2);
                break;
            }
            case 0x2C: {     /*INC IXl*/
                regIXY = (regIXY & 0xff00) | inc8(regIXY & 0xff);
                break;
            }
            case 0x2D: {     /*DEC IXl*/
                regIXY = (regIXY & 0xff00) | dec8(regIXY & 0xff);
                break;
            }
            case 0x2E: {     /*LD IXl,n*/
                regIXY = (regIXY & 0xff00) | MemIoImpl.peek8(regPC);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x34:       /*INC (IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                work8 = MemIoImpl.peek8(memptr);
                MemIoImpl.contendedStates(memptr, 1);
                MemIoImpl.poke8(memptr, inc8(work8));
                regPC = (regPC + 1) & 0xffff;
                break;
            case 0x35: {     /*DEC (IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                work8 = MemIoImpl.peek8(memptr);
                MemIoImpl.contendedStates(memptr, 1);
                MemIoImpl.poke8(memptr, dec8(work8));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x36: {     /*LD (IX+d),n*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                regPC = (regPC + 1) & 0xffff;
                work8 = MemIoImpl.peek8(regPC);
                MemIoImpl.contendedStates(regPC, 2);
                regPC = (regPC + 1) & 0xffff;
                MemIoImpl.poke8(memptr, work8);
                break;
            }
            case 0x39: {     /*ADD IX,SP*/
                regIXY = add16(regIXY, regSP);
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x44: {     /*LD B,IXh*/
                regB = regIXY >>> 8;
                break;
            }
            case 0x45: {     /*LD B,IXl*/
                regB = regIXY & 0xff;
                break;
            }
            case 0x46: {     /*LD B,(IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                regB = MemIoImpl.peek8(memptr);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x4C: {     /*LD C,IXh*/
                regC = regIXY >>> 8;
                break;
            }
            case 0x4D: {     /*LD C,IXl*/
                regC = regIXY & 0xff;
                break;
            }
            case 0x4E: {     /*LD C,(IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                regC = MemIoImpl.peek8(memptr);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x54: {     /*LD D,IXh*/
                regD = regIXY >>> 8;
                break;
            }
            case 0x55: {     /*LD D,IXl*/
                regD = regIXY & 0xff;
                break;
            }
            case 0x56: {     /*LD D,(IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                regD = MemIoImpl.peek8(memptr);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x5C: {     /*LD E,IXh*/
                regE = regIXY >>> 8;
                break;
            }
            case 0x5D: {     /*LD E,IXl*/
                regE = regIXY & 0xff;
                break;
            }
            case 0x5E: {     /*LD E,(IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                regE = MemIoImpl.peek8(memptr);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x60: {     /*LD IXh,B*/
                regIXY = (regIXY & 0x00ff) | (regB << 8);
                break;
            }
            case 0x61: {     /*LD IXh,C*/
                regIXY = (regIXY & 0x00ff) | (regC << 8);
                break;
            }
            case 0x62: {     /*LD IXh,D*/
                regIXY = (regIXY & 0x00ff) | (regD << 8);
                break;
            }
            case 0x63: {     /*LD IXh,E*/
                regIXY = (regIXY & 0x00ff) | (regE << 8);
                break;
            }
            case 0x64: {     /*LD IXh,IXh*/
                break;
            }
            case 0x65: {     /*LD IXh,IXl*/
                work8 = regIXY;
                regIXY = (regIXY & 0x00ff) | ((regIXY & 0xff) << 8);
                break;
            }
            case 0x66: {     /*LD H,(IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                regH = MemIoImpl.peek8(memptr);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x67: {     /*LD IXh,A*/
                regIXY = (regIXY & 0x00ff) | (regA << 8);
                break;
            }
            case 0x68: {     /*LD IXl,B*/
                regIXY = (regIXY & 0xff00) | regB;
                break;
            }
            case 0x69: {     /*LD IXl,C*/
                regIXY = (regIXY & 0xff00) | regC;
                break;
            }
            case 0x6A: {     /*LD IXl,D*/
                regIXY = (regIXY & 0xff00) | regD;
                break;
            }
            case 0x6B: {     /*LD IXl,E*/
                regIXY = (regIXY & 0xff00) | regE;
                break;
            }
            case 0x6C: {     /*LD IXl,IXh*/
                work8 = regIXY;
                regIXY = (regIXY & 0xff00) | (regIXY >>> 8);
                break;
            }
            case 0x6D: {     /*LD IXl,IXl*/
                break;
            }
            case 0x6E: {     /*LD L,(IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                regL = MemIoImpl.peek8(memptr);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x6F: {     /*LD IXl,A*/
                regIXY = (regIXY & 0xff00) | regA;
                break;
            }
            case 0x70: {     /*LD (IX+d),B*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                MemIoImpl.poke8(memptr, regB);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x71: {     /*LD (IX+d),C*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                MemIoImpl.poke8(memptr, regC);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x72: {     /*LD (IX+d),D*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                MemIoImpl.poke8(memptr, regD);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x73: {     /*LD (IX+d),E*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                MemIoImpl.poke8(memptr, regE);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x74: {     /*LD (IX+d),H*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                MemIoImpl.poke8(memptr, regH);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x75: {     /*LD (IX+d),L*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                MemIoImpl.poke8(memptr, regL);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x77: {     /*LD (IX+d),A*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                MemIoImpl.poke8(memptr, regA);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x7C: {     /*LD A,IXh*/
                regA = regIXY >>> 8;
                break;
            }
            case 0x7D: {     /*LD A,IXl*/
                regA = regIXY & 0xff;
                break;
            }
            case 0x7E: {     /*LD A,(IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                regA = MemIoImpl.peek8(memptr);
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x84: {     /*ADD A,IXh*/
                add(regIXY >>> 8);
                break;
            }
            case 0x85: {     /*ADD A,IXl*/
                add(regIXY & 0xff);
                break;
            }
            case 0x86: {     /*ADD A,(IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                add(MemIoImpl.peek8(memptr));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x8C: {     /*ADC A,IXh*/
                adc(regIXY >>> 8);
                break;
            }
            case 0x8D: {     /*ADC A,IXl*/
                adc(regIXY & 0xff);
                break;
            }
            case 0x8E: {     /*ADC A,(IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                adc(MemIoImpl.peek8(memptr));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x94: {     /*SUB IXh*/
                sub(regIXY >>> 8);
                break;
            }
            case 0x95: {     /*SUB IXl*/
                sub(regIXY & 0xff);
                break;
            }
            case 0x96: {     /*SUB (IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                sub(MemIoImpl.peek8(memptr));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0x9C: {     /*SBC A,IXh*/
                sbc(regIXY >>> 8);
                break;
            }
            case 0x9D: {     /*SBC A,IXl*/
                sbc(regIXY & 0xff);
                break;
            }
            case 0x9E: {     /*SBC A,(IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                sbc(MemIoImpl.peek8(memptr));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xA4: {     /*AND IXh*/
                and(regIXY >>> 8);
                break;
            }
            case 0xA5: {     /*AND IXl*/
                and(regIXY & 0xff);
                break;
            }
            case 0xA6: {     /*AND (IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                and(MemIoImpl.peek8(memptr));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xAC: {     /*XOR IXh*/
                xor(regIXY >>> 8);
                break;
            }
            case 0xAD: {     /*XOR IXl*/
                xor(regIXY & 0xff);
                break;
            }
            case 0xAE: {     /*XOR (IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                xor(MemIoImpl.peek8(memptr));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xB4: {     /*OR IXh*/
                or(regIXY >>> 8);
                break;
            }
            case 0xB5: {     /*OR IXl*/
                or(regIXY & 0xff);
                break;
            }
            case 0xB6: {     /*OR (IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                or(MemIoImpl.peek8(memptr));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xBC: {     /*CP IXh*/
                cp(regIXY >>> 8);
                break;
            }
            case 0xBD: {     /*CP IXl*/
                cp(regIXY & 0xff);
                break;
            }
            case 0xBE: {     /*CP (IX+d)*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                MemIoImpl.contendedStates(regPC, 5);
                cp(MemIoImpl.peek8(memptr));
                regPC = (regPC + 1) & 0xffff;
                break;
            }
            case 0xCB: {     /*Subconjunto de instrucciones*/
                memptr = (regIXY + (byte) MemIoImpl.peek8(regPC)) & 0xffff;
                regPC = (regPC + 1) & 0xffff;
                opCode = MemIoImpl.peek8(regPC);
                MemIoImpl.contendedStates(regPC, 2);
                regPC = (regPC + 1) & 0xffff;
                if( opCode < 0x80 ) {
                    decodeDDFDCBto7F(opCode, memptr);
                } else {
                    decodeDDFDCBtoFF(opCode, memptr);
                }
                break;
            }
            case 0xE1: {     /*POP IX*/
                regIXY = pop();
                break;
            }
            case 0xE3: {     /*EX (SP),IX*/
                // Instrucción de ejecución sutil como pocas... atento al dato.
                int work16 = regIXY;
                regIXY = MemIoImpl.peek16(regSP);
                MemIoImpl.contendedStates(regSP + 1, 1);
                MemIoImpl.poke8((regSP + 1) & 0xffff, (work16 >>> 8) & 0xff);
                MemIoImpl.poke8(regSP, work16 & 0xff);
                MemIoImpl.contendedStates(regSP, 2);
                memptr = regIXY;
                break;
            }
            case 0xE5: {     /*PUSH IX*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                push(regIXY);
                break;
            }
            case 0xE9: {     /*JP (IX)*/
                regPC = regIXY;
                break;
            }
            case 0xF9: {     /*LD SP,IX*/
                regSP = regIXY;
                MemIoImpl.contendedStates(regPC, 2);
                break;
            }
            default: {
                // Código no válido. Deshacemos la faena para reprocesar la
                // instrucción. Sin esto, además de emular mal, falla el test
                // ld <bcdexya>,<bcdexya> de ZEXALL.
                tEstados = work8;
                regR--;
                regPC = (regPC - 1) & 0xffff;
                //System.out.println("Error instrucción DD/FD" + Integer.toHexString(opCode));
                break;
            }
        }
        return regIXY;
    }

    //Subconjunto de instrucciones 0xDDCB desde el código 0x00 hasta el 0x7F
    private final void decodeDDFDCBto7F(int opCode, int address) {
        int work8;
        switch (opCode) {
            case 0x00: {     /*RLC (IX+d),B*/
                regB = rlc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x01: {     /*RLC (IX+d),C*/
                regC = rlc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x02: {     /*RLC (IX+d),D*/
                regD = rlc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x03: {     /*RLC (IX+d),E*/
                regE = rlc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x04: {     /*RLC (IX+d),H*/
                regH = rlc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x05: {     /*RLC (IX+d),L*/
                regL = rlc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x06: {     /*RLC (IX+d)*/
                work8 = rlc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, work8);
                break;
            }
            case 0x07: {     /*RLC (IX+d),A*/
                regA = rlc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x08: {     /*RRC (IX+d),B*/
                regB = rrc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x09: {     /*RRC (IX+d),C*/
                regC = rrc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x0A: {     /*RRC (IX+d),D*/
                regD = rrc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x0B: {     /*RRC (IX+d),E*/
                regE = rrc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x0C: {     /*RRC (IX+d),H*/
                regH = rrc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x0D: {     /*RRC (IX+d),L*/
                regL = rrc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x0E: {     /*RRC (IX+d)*/
                work8 = rrc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, work8);
                break;
            }
            case 0x0F: {     /*RRC (IX+d),A*/
                regA = rrc(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x10: {     /*RL (IX+d),B*/
                regB = rl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x11: {     /*RL (IX+d),C*/
                regC = rl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x12: {     /*RL (IX+d),D*/
                regD = rl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x13: {     /*RL (IX+d),E*/
                regE = rl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x14: {     /*RL (IX+d),H*/
                regH = rl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x15: {     /*RL (IX+d),L*/
                regL = rl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x16: {     /*RL (IX+d)*/
                work8 = rl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, work8);
                break;
            }
            case 0x17: {     /*RL (IX+d),A*/
                regA = rl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x18: {     /*RR (IX+d),B*/
                regB = rr(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x19: {     /*RR (IX+d),C*/
                regC = rr(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x1A: {     /*RR (IX+d),D*/
                regD = rr(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x1B: {     /*RR (IX+d),E*/
                regE = rr(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x1C: {     /*RR (IX+d),H*/
                regH = rr(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x1D: {     /*RR (IX+d),L*/
                regL = rr(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x1E: {     /*RR (IX+d)*/
                work8 = rr(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, work8);
                break;
            }
            case 0x1F: {     /*RR (IX+d),A*/
                regA = rr(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x20: {     /*SLA (IX+d),B*/
                regB = sla(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x21: {     /*SLA (IX+d),C*/
                regC = sla(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x22: {     /*SLA (IX+d),D*/
                regD = sla(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x23: {     /*SLA (IX+d),E*/
                regE = sla(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x24: {     /*SLA (IX+d),H*/
                regH = sla(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x25: {     /*SLA (IX+d),L*/
                regL = sla(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x26: {     /*SLA (IX+d)*/
                work8 = sla(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, work8);
                break;
            }
            case 0x27: {     /*SLA (IX+d),A*/
                regA = sla(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x28: {     /*SRA (IX+d),B*/
                regB = sra(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x29: {     /*SRA (IX+d),C*/
                regC = sra(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x2A: {     /*SRA (IX+d),D*/
                regD = sra(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x2B: {     /*SRA (IX+d),E*/
                regE = sra(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x2C: {     /*SRA (IX+d),H*/
                regH = sra(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x2D: {     /*SRA (IX+d),L*/
                regL = sra(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x2E: {     /*SRA (IX+d)*/
                work8 = sra(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, work8);
                break;
            }
            case 0x2F: {     /*SRA (IX+d),A*/
                regA = sra(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x30: {     /*SLL (IX+d),B*/
                regB = sll(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x31: {     /*SLL (IX+d),C*/
                regC = sll(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x32: {     /*SLL (IX+d),D*/
                regD = sll(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x33: {     /*SLL (IX+d),E*/
                regE = sll(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x34: {     /*SLL (IX+d),H*/
                regH = sll(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x35: {     /*SLL (IX+d),L*/
                regL = sll(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x36: {     /*SLL (IX+d)*/
                work8 = sll(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, work8);
                break;
            }
            case 0x37: {     /*SLL (IX+d),A*/
                regA = sll(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x38: {     /*SRL (IX+d),B*/
                regB = srl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x39: {     /*SRL (IX+d),C*/
                regC = srl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x3A: {     /*SRL (IX+d),D*/
                regD = srl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x3B: {     /*SRL (IX+d),E*/
                regE = srl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x3C: {     /*SRL (IX+d),H*/
                regH = srl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x3D: {     /*SRL (IX+d),L*/
                regL = srl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x3E: {     /*SRL (IX+d)*/
                work8 = srl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, work8);
                break;
            }
            case 0x3F: {     /*SRL (IX+d),A*/
                regA = srl(MemIoImpl.peek8(address));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x40:
            case 0x41:
            case 0x42:
            case 0x43:
            case 0x44:
            case 0x45:
            case 0x46:
            case 0x47: {     /*BIT 0,(IX+d)*/
                bit(0x01, MemIoImpl.peek8(address));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                        ((address >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(address, 1);
                break;
            }
            case 0x48:
            case 0x49:
            case 0x4A:
            case 0x4B:
            case 0x4C:
            case 0x4D:
            case 0x4E:
            case 0x4F: {     /*BIT 1,(IX+d)*/
                bit(0x02, MemIoImpl.peek8(address));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                        ((address >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(address, 1);
                break;
            }
            case 0x50:
            case 0x51:
            case 0x52:
            case 0x53:
            case 0x54:
            case 0x55:
            case 0x56:
            case 0x57: {     /*BIT 2,(IX+d)*/
                bit(0x04, MemIoImpl.peek8(address));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                        ((address >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(address, 1);
                break;
            }
            case 0x58:
            case 0x59:
            case 0x5A:
            case 0x5B:
            case 0x5C:
            case 0x5D:
            case 0x5E:
            case 0x5F: {     /*BIT 3,(IX+d)*/
                bit(0x08, MemIoImpl.peek8(address));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                        ((address >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(address, 1);
                break;
            }
            case 0x60:
            case 0x61:
            case 0x62:
            case 0x63:
            case 0x64:
            case 0x65:
            case 0x66:
            case 0x67: {     /*BIT 4,(IX+d)*/
                bit(0x10, MemIoImpl.peek8(address));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                        ((address >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(address, 1);
                break;
            }
            case 0x68:
            case 0x69:
            case 0x6A:
            case 0x6B:
            case 0x6C:
            case 0x6D:
            case 0x6E:
            case 0x6F: {     /*BIT 5,(IX+d)*/
                bit(0x20, MemIoImpl.peek8(address));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                        ((address >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(address, 1);
                break;
            }
            case 0x70:
            case 0x71:
            case 0x72:
            case 0x73:
            case 0x74:
            case 0x75:
            case 0x76:
            case 0x77: {     /*BIT 6,(IX+d)*/
                bit(0x40, MemIoImpl.peek8(address));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                        ((address >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(address, 1);
                break;
            }
            case 0x78:
            case 0x79:
            case 0x7A:
            case 0x7B:
            case 0x7C:
            case 0x7D:
            case 0x7E:
            case 0x7F: {     /*BIT 7,(IX+d)*/
                bit(0x80, MemIoImpl.peek8(address));
                sz5h3pnFlags = (sz5h3pnFlags & FLAG_SZHP_MASK) |
                        ((address >>> 8) & FLAG_53_MASK);
                MemIoImpl.contendedStates(address, 1);
                break;
            }
        }
    }

    //Subconjunto de instrucciones 0xDDCB desde el código 0x80 hasta el 0xFF
    private final void decodeDDFDCBtoFF(int opCode, int address) {
        int work8;
        switch (opCode) {
            case 0x80: {     /*RES 0,(IX+d),B*/
                regB = res(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x81: {     /*RES 0,(IX+d),C*/
                regC = res(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x82: {     /*RES 0,(IX+d),D*/
                regD = res(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x83: {     /*RES 0,(IX+d),E*/
                regE = res(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x84: {     /*RES 0,(IX+d),H*/
                regH = res(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x85: {     /*RES 0,(IX+d),L*/
                regL = res(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x86: {     /*RES 0,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, res(0x01, work8));
                break;
            }
            case 0x87: {     /*RES 0,(IX+d),A*/
                regA = res(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x88: {     /*RES 1,(IX+d),B*/
                regB = res(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x89: {     /*RES 1,(IX+d),C*/
                regC = res(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x8A: {     /*RES 1,(IX+d),D*/
                regD = res(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x8B: {     /*RES 1,(IX+d),E*/
                regE = res(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x8C: {     /*RES 1,(IX+d),H*/
                regH = res(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x8D: {     /*RES 1,(IX+d),L*/
                regL = res(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x8E: {     /*RES 1,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, res(0x02, work8));
                break;
            }
            case 0x8F: {     /*RES 1,(IX+d),A*/
                regA = res(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x90: {     /*RES 2,(IX+d),B*/
                regB = res(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x91: {     /*RES 2,(IX+d),C*/
                regC = res(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x92: {     /*RES 2,(IX+d),D*/
                regD = res(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x93: {     /*RES 2,(IX+d),E*/
                regE = res(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x94: {     /*RES 2,(IX+d),H*/
                regH = res(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x95: {     /*RES 2,(IX+d),L*/
                regL = res(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x96: {     /*RES 2,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, res(0x04, work8));
                break;
            }
            case 0x97: {     /*RES 2,(IX+d),A*/
                regA = res(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0x98: {     /*RES 3,(IX+d),B*/
                regB = res(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0x99: {     /*RES 3,(IX+d),C*/
                regC = res(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0x9A: {     /*RES 3,(IX+d),D*/
                regD = res(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0x9B: {     /*RES 3,(IX+d),E*/
                regE = res(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0x9C: {     /*RES 3,(IX+d),H*/
                regH = res(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0x9D: {     /*RES 3,(IX+d),L*/
                regL = res(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0x9E: {     /*RES 3,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, res(0x08, work8));
                break;
            }
            case 0x9F: {     /*RES 3,(IX+d),A*/
                regA = res(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xA0: {     /*RES 4,(IX+d),B*/
                regB = res(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xA1: {     /*RES 4,(IX+d),C*/
                regC = res(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xA2: {     /*RES 4,(IX+d),D*/
                regD = res(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xA3: {     /*RES 4,(IX+d),E*/
                regE = res(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xA4: {     /*RES 4,(IX+d),H*/
                regH = res(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xA5: {     /*RES 4,(IX+d),L*/
                regL = res(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xA6: {     /*RES 4,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, res(0x10, work8));
                break;
            }
            case 0xA7: {     /*RES 4,(IX+d),A*/
                regA = res(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xA8: {     /*RES 5,(IX+d),B*/
                regB = res(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xA9: {     /*RES 5,(IX+d),C*/
                regC = res(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xAA: {     /*RES 5,(IX+d),D*/
                regD = res(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xAB: {     /*RES 5,(IX+d),E*/
                regE = res(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xAC: {     /*RES 5,(IX+d),H*/
                regH = res(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xAD: {     /*RES 5,(IX+d),L*/
                regL = res(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xAE: {     /*RES 5,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, res(0x20, work8));
                break;
            }
            case 0xAF: {     /*RES 5,(IX+d),A*/
                regA = res(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xB0: {     /*RES 6,(IX+d),B*/
                regB = res(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xB1: {     /*RES 6,(IX+d),C*/
                regC = res(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xB2: {     /*RES 6,(IX+d),D*/
                regD = res(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xB3: {     /*RES 6,(IX+d),E*/
                regE = res(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xB4: {     /*RES 6,(IX+d),H*/
                regH = res(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xB5: {     /*RES 6,(IX+d),L*/
                regL = res(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xB6: {     /*RES 6,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, res(0x40, work8));
                break;
            }
            case 0xB7: {     /*RES 6,(IX+d),A*/
                regA = res(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xB8: {     /*RES 7,(IX+d),B*/
                regB = res(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xB9: {     /*RES 7,(IX+d),C*/
                regC = res(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xBA: {     /*RES 7,(IX+d),D*/
                regD = res(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xBB: {     /*RES 7,(IX+d),E*/
                regE = res(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xBC: {     /*RES 7,(IX+d),H*/
                regH = res(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xBD: {     /*RES 7,(IX+d),L*/
                regL = res(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xBE: {     /*RES 7,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, res(0x80, work8));
                break;
            }
            case 0xBF: {     /*RES 7,(IX+d),A*/
                regA = res(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
             case 0xC0: {     /*SET 0,(IX+d),B*/
                regB = set(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xC1: {     /*SET 0,(IX+d),C*/
                regC = set(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xC2: {     /*SET 0,(IX+d),D*/
                regD = set(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xC3: {     /*SET 0,(IX+d),E*/
                regE = set(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xC4: {     /*SET 0,(IX+d),H*/
                regH = set(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xC5: {     /*SET 0,(IX+d),L*/
                regL = set(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xC6: {     /*SET 0,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, set(0x01, work8));
                break;
            }
            case 0xC7: {     /*SET 0,(IX+d),A*/
                regA = set(0x01, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xC8: {     /*SET 1,(IX+d),B*/
                regB = set(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xC9: {     /*SET 1,(IX+d),C*/
                regC = set(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xCA: {     /*SET 1,(IX+d),D*/
                regD = set(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xCB: {     /*SET 1,(IX+d),E*/
                regE = set(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xCC: {     /*SET 1,(IX+d),H*/
                regH = set(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xCD: {     /*SET 1,(IX+d),L*/
                regL = set(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xCE: {     /*SET 1,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, set(0x02, work8));
                break;
            }
            case 0xCF: {     /*SET 1,(IX+d),A*/
                regA = set(0x02, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xD0: {     /*SET 2,(IX+d),B*/
                regB = set(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xD1: {     /*SET 2,(IX+d),C*/
                regC = set(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xD2: {     /*SET 2,(IX+d),D*/
                regD = set(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xD3: {     /*SET 2,(IX+d),E*/
                regE = set(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xD4: {     /*SET 2,(IX+d),H*/
                regH = set(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xD5: {     /*SET 2,(IX+d),L*/
                regL = set(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xD6: {     /*SET 2,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, set(0x04, work8));
                break;
            }
            case 0xD7: {     /*SET 2,(IX+d),A*/
                regA = set(0x04, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xD8: {     /*SET 3,(IX+d),B*/
                regB = set(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xD9: {     /*SET 3,(IX+d),C*/
                regC = set(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xDA: {     /*SET 3,(IX+d),D*/
                regD = set(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xDB: {     /*SET 3,(IX+d),E*/
                regE = set(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xDC: {     /*SET 3,(IX+d),H*/
                regH = set(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xDD: {     /*SET 3,(IX+d),L*/
                regL = set(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xDE: {     /*SET 3,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, set(0x08, work8));
                break;
            }
            case 0xDF: {     /*SET 3,(IX+d),A*/
                regA = set(0x08, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xE0: {     /*SET 4,(IX+d),B*/
                regB = set(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xE1: {     /*SET 4,(IX+d),C*/
                regC = set(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xE2: {     /*SET 4,(IX+d),D*/
                regD = set(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xE3: {     /*SET 4,(IX+d),E*/
                regE = set(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xE4: {     /*SET 4,(IX+d),H*/
                regH = set(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xE5: {     /*SET 4,(IX+d),L*/
                regL = set(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xE6: {     /*SET 4,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, set(0x10, work8));
                break;
            }
            case 0xE7: {     /*SET 4,(IX+d),A*/
                regA = set(0x10, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xE8: {     /*SET 5,(IX+d),B*/
                regB = set(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xE9: {     /*SET 5,(IX+d),C*/
                regC = set(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xEA: {     /*SET 5,(IX+d),D*/
                regD = set(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xEB: {     /*SET 5,(IX+d),E*/
                regE = set(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xEC: {     /*SET 5,(IX+d),H*/
                regH = set(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xED: {     /*SET 5,(IX+d),L*/
                regL = set(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xEE: {     /*SET 5,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, set(0x20, work8));
                break;
            }
            case 0xEF: {     /*SET 5,(IX+d),A*/
                regA = set(0x20, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xF0: {     /*SET 6,(IX+d),B*/
                regB = set(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xF1: {     /*SET 6,(IX+d),C*/
                regC = set(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xF2: {     /*SET 6,(IX+d),D*/
                regD = set(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xF3: {     /*SET 6,(IX+d),E*/
                regE = set(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xF4: {     /*SET 6,(IX+d),H*/
                regH = set(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xF5: {     /*SET 6,(IX+d),L*/
                regL = set(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xF6: {     /*SET 6,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, set(0x40, work8));
                break;
            }
            case 0xF7: {     /*SET 6,(IX+d),A*/
                regA = set(0x40, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
            case 0xF8: {     /*SET 7,(IX+d),B*/
                regB = set(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regB);
                break;
            }
            case 0xF9: {     /*SET 7,(IX+d),C*/
                regC = set(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regC);
                break;
            }
            case 0xFA: {     /*SET 7,(IX+d),D*/
                regD = set(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regD);
                break;
            }
            case 0xFB: {     /*SET 7,(IX+d),E*/
                regE = set(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regE);
                break;
            }
            case 0xFC: {     /*SET 7,(IX+d),H*/
                regH = set(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regH);
                break;
            }
            case 0xFD: {     /*SET 7,(IX+d),L*/
                regL = set(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regL);
                break;
            }
            case 0xFE: {     /*SET 7,(IX+d)*/
                work8 = MemIoImpl.peek8(address);
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, set(0x80, work8));
                break;
            }
            case 0xFF: {     /*SET 7,(IX+d),A*/
                regA = set(0x80, (MemIoImpl.peek8(address)));
                MemIoImpl.contendedStates(address, 1);
                MemIoImpl.poke8(address, regA);
                break;
            }
        }
    }

    //Subconjunto de instrucciones 0xED
    private final void decodeED() {
        regR++;
        opCode = MemIoImpl.fetchOpcode(regPC);
        regPC = (regPC + 1) & 0xffff;
        switch (opCode) {
            case 0x40: {     /*IN B,(C)*/
                regB = MemIoImpl.inPort(getRegBC());
                sz5h3pnFlags = sz53pn_addTable[regB];
                break;
            }
            case 0x41: {     /*OUT (C),B*/
                MemIoImpl.outPort(getRegBC(), regB);
                break;
            }
            case 0x42: {     /*SBC HL,BC*/
                sbc16(getRegBC());
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x43: {     /*LD (nn),BC*/
                memptr = MemIoImpl.peek16(regPC);
                MemIoImpl.poke16(memptr, getRegBC());
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x44:
            case 0x4C:
            case 0x54:
            case 0x5C:
            case 0x64:
            case 0x6C:
            case 0x74:
            case 0x7C: {     /*NEG*/
                int aux = regA;
                regA = 0;
                sub(aux);
                break;
            }
            case 0x45:
            case 0x55:
            case 0x5D:
            case 0x65:
            case 0x6D:
            case 0x75:
            case 0x7D: {     /*RETN*/
                ffIFF1 = ffIFF2;
                regPC = memptr = pop();
                break;
            }
            case 0x46:
            case 0x4E:
            case 0x66:
            case 0x6E: {     /*IM 0*/
                setIM(IM0);
                break;
            }
            case 0x47: {     /*LD I,A*/
                /*
                 * El contended-tstate se produce con el contenido de I *antes*
                 * de ser copiado el del registro A. Detalle importante.
                 */
                MemIoImpl.contendedStates(getPairIR(), 1);
                regI = regA;
                break;
            }
            case 0x48: {     /*IN C,(C)*/
                regC = MemIoImpl.inPort(getRegBC());
                sz5h3pnFlags = sz53pn_addTable[regC];
                break;
            }
            case 0x49: {     /*OUT (C),C*/
                MemIoImpl.outPort(getRegBC(), regC);
                break;
            }
            case 0x4A: {     /*ADC HL,BC*/
                adc16(getRegBC());
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x4B: {     /*LD BC,(nn)*/
                memptr = MemIoImpl.peek16(regPC);
                setRegBC(MemIoImpl.peek16(memptr));
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x4D: {     /*RETI*/
                regPC = memptr = pop();
                break;
            }
            case 0x4F: {     /*LD R,A*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                setRegR(regA);
                break;
            }
            case 0x50: {     /*IN D,(C)*/
                regD = MemIoImpl.inPort(getRegBC());
                sz5h3pnFlags = sz53pn_addTable[regD];
                break;
            }
            case 0x51: {     /*OUT (C),D*/
                MemIoImpl.outPort(getRegBC(), regD);
                break;
            }
            case 0x52: {     /*SBC HL,DE*/
                sbc16(getRegDE());
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x53: {     /*LD (nn),DE*/
                memptr = MemIoImpl.peek16(regPC);
                MemIoImpl.poke16(memptr, getRegDE());
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x56:
            case 0x76: {     /*IM 1*/
                setIM(IM1);
                break;
            }
            case 0x57: {     /*LD A,I*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                regA = regI;
                sz5h3pnFlags = sz53n_addTable[regA];
                if( ffIFF2 )
                    sz5h3pnFlags |= PARITY_MASK;
                break;
            }
            case 0x58: {     /*IN E,(C)*/
                regE = MemIoImpl.inPort(getRegBC());
                sz5h3pnFlags = sz53pn_addTable[regE];
                break;
            }
            case 0x59: {     /*OUT (C),E*/
                MemIoImpl.outPort(getRegBC(), regE);
                break;
            }
            case 0x5A: {     /*ADC HL,DE*/
                adc16(getRegDE());
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x5B: {     /*LD DE,(nn)*/
                memptr = MemIoImpl.peek16(regPC);
                setRegDE(MemIoImpl.peek16(memptr));
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;;
                break;
            }
            case 0x5E:
            case 0x7E: {     /*IM 2*/
                setIM(IM2);
                break;
            }
            case 0x5F: {     /*LD A,R*/
                MemIoImpl.contendedStates(getPairIR(), 1);
                regA = getRegR();
                sz5h3pnFlags = sz53n_addTable[regA];
                if( ffIFF2 )
                    sz5h3pnFlags |= PARITY_MASK;
                break;
            }
            case 0x60: {     /*IN H,(C)*/
                regH = MemIoImpl.inPort(getRegBC());
                sz5h3pnFlags = sz53pn_addTable[regH];
                break;
            }
            case 0x61: {     /*OUT (C),H*/
                MemIoImpl.outPort(getRegBC(), regH);
                break;
            }
            case 0x62: {     /*SBC HL,HL*/
                sbc16(getRegHL());
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x63: {     /*LD (nn),HL*/
                memptr = MemIoImpl.peek16(regPC);
                MemIoImpl.poke16(memptr, getRegHL());
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x67: {     /*RRD*/
                rrd();
                break;
            }
            case 0x68: {     /*IN L,(C)*/
                regL = MemIoImpl.inPort(getRegBC());
                sz5h3pnFlags = sz53pn_addTable[regL];
                break;
            }
            case 0x69: {     /*OUT (C),L*/
                MemIoImpl.outPort(getRegBC(), regL);
                break;
            }
            case 0x6A: {     /*ADC HL,HL*/
                adc16(getRegHL());
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x6B: {     /*LD HL,(nn)*/
                memptr = MemIoImpl.peek16(regPC);
                setRegHL(MemIoImpl.peek16(memptr));
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x6F: {     /*RLD*/
                rld();
                break;
            }
            case 0x70: {     /*IN (C)*/
                int inPort = MemIoImpl.inPort(getRegBC());
                sz5h3pnFlags = sz53pn_addTable[inPort];
                break;
            }
            case 0x71: {     /*OUT (C),0*/
                MemIoImpl.outPort(getRegBC(), 0x00);
                break;
            }
            case 0x72: {     /*SBC HL,SP*/
                sbc16(regSP);
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x73: {     /*LD (nn),SP*/
                memptr = MemIoImpl.peek16(regPC);
                MemIoImpl.poke16(memptr, regSP);
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0x78: {     /*IN A,(C)*/
                regA = MemIoImpl.inPort(getRegBC());
                sz5h3pnFlags = sz53pn_addTable[regA];
                memptr = (getRegBC() + 1) & 0xffff;
                break;
            }
            case 0x79: {     /*OUT (C),A*/
                memptr = getRegBC();
                MemIoImpl.outPort(memptr, regA);
                memptr = (memptr + 1) & 0xffff;
                break;
            }
            case 0x7A: {     /*ADC HL,SP*/
                adc16(regSP);
                MemIoImpl.contendedStates(getPairIR(), 7);
                break;
            }
            case 0x7B: {     /*LD SP,(nn)*/
                memptr = MemIoImpl.peek16(regPC);
                regSP = MemIoImpl.peek16(memptr);
                memptr = (memptr + 1) & 0xffff;
                regPC = (regPC + 2) & 0xffff;
                break;
            }
            case 0xA0: {     /*LDI*/
                ldi();
                break;
            }
            case 0xA1: {     /*CPI*/
                cpi();
                break;
            }
            case 0xA2: {     /*INI*/
                ini();
                break;
            }
            case 0xA3: {     /*OUTI*/
                outi();
                break;
            }
            case 0xA8: {     /*LDD*/
                ldd();
                break;
            }
            case 0xA9: {     /*CPD*/
                cpd();
                break;
            }
            case 0xAA: {     /*IND*/
                ind();
                break;
            }
            case 0xAB: {     /*OUTD*/
                outd();
                break;
            }
            case 0xB0: {     /*LDIR*/
                ldi();
                if ( (sz5h3pnFlags & PARITY_MASK) == PARITY_MASK ) {
                    regPC = (regPC - 2) & 0xffff;
                    memptr = (regPC + 1) & 0xffff;
                    MemIoImpl.contendedStates(getRegDE()-1, 5);
                }
                break;
            }
            case 0xB1: {     /*CPIR*/
                cpi();
                if( (sz5h3pnFlags & PARITY_MASK) == PARITY_MASK &&
                       (sz5h3pnFlags & ZERO_MASK) == 0 ) {
                    regPC = (regPC - 2) & 0xffff;
                    memptr = (regPC + 1) & 0xffff;
                    MemIoImpl.contendedStates(getRegHL()-1, 5);
                }
                break;
            }
            case 0xB2: {     /*INIR*/
                ini();
                if (regB != 0) {
                    regPC = (regPC - 2) & 0xffff;
                    MemIoImpl.contendedStates(getRegHL()-1, 5);
                }
                break;
            }
            case 0xB3: {     /*OTIR*/
                outi();
                if (regB != 0) {
                    regPC = (regPC - 2) & 0xffff;
                    MemIoImpl.contendedStates(getRegBC(), 5);
                }
                break;
            }
            case 0xB8: {     /*LDDR*/
                ldd();
                if ( (sz5h3pnFlags & PARITY_MASK) == PARITY_MASK ) {
                    regPC = (regPC - 2) & 0xffff;
                    memptr = (regPC + 1) & 0xffff;
                    MemIoImpl.contendedStates(getRegDE()+1, 5);
                }
                break;
            }
            case 0xB9: {     /*CPDR*/
                cpd();
                if ( (sz5h3pnFlags & PARITY_MASK) == PARITY_MASK &&
                        (sz5h3pnFlags & ZERO_MASK) == 0 ) {
                    regPC = (regPC - 2) & 0xffff;
                    memptr = (regPC + 1) & 0xffff;
                    MemIoImpl.contendedStates(getRegHL()+1, 5);
                }
                break;
            }
            case 0xBA: {     /*INDR*/
                ind();
                if (regB != 0) {
                    regPC = (regPC - 2) & 0xffff;
                    MemIoImpl.contendedStates(getRegHL()+1, 5);
                }
                break;
            }
            case 0xBB: {     /*OTDR*/
                outd();
                if (regB != 0) {
                    regPC = (regPC - 2) & 0xffff;
                    MemIoImpl.contendedStates(getRegBC(), 5);
                }
                break;
            }
            default: {
//                System.out.println("Error instrucción ED " + Integer.toHexString(opCode));
                break;
            }
        }
    }
}