/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

import java.util.Arrays;

/**
 *
 * @author jsanchez
 */
public class AY8912 {

    private static final int FineToneA = 0;
    private static final int CoarseToneA = 1;
    private static final int FineToneB = 2;
    private static final int CoarseToneB = 3;
    private static final int FineToneC = 4;
    private static final int CoarseToneC = 5;
    private static final int NoisePeriod = 6;
    private static final int AYEnable = 7;
    private static final int AmplitudeA = 8;
    private static final int AmplitudeB = 9;
    private static final int AmplitudeC = 10;
    private static final int FineEnvelope = 11;
    private static final int CoarseEnvelope = 12;
    private static final int EnvelopeShapeCycle = 13;
    private static final int IOPortA = 14;
    private static final int IOPortB = 15;

     // Other definitions
    private static final int ENABLE_A = 0x01;
    private static final int ENABLE_B = 0x02;
    private static final int ENABLE_C = 0x04;
    private static final int NOISE_A = 0x08;
    private static final int NOISE_B = 0x10;
    private static final int NOISE_C = 0x20;
    
    // Host clock frequency
    private int clockFreq;
    // AY clock cycle
    private int ayCycle;
    // Channel periods
    private int periodA, periodB, periodC;
    // Channel period counters
    private int counterA, counterB, counterC;
    // Channel amplitudes
    private int amplitudeA, amplitudeB, amplitudeC;
    // Noise counter
    private int noiseCounter;
    private int envelopePeriod, envelopeCounter;

    /* Envelope shape cycles:
       C AtAlH
       0 0 x x  \_______

       0 1 x x  /|______

       1 0 0 0  \|\|\|\|

       1 0 0 1  \_______

       1 0 1 0  \/\/\/\/
                  ______
       1 0 1 1  \|

       1 1 0 0  /|/|/|/|
                 _______
       1 1 0 1  /

       1 1 1 0  /\/\/\/\

       1 1 1 1  /|______
     
       The envelope counter on the AY-3-8910 has 16 steps. 
     */
    private int envelopeShape;
    private int maxAmplitude;
    
    // AY register index
    private int registerAddress;
    // AY register set
    private int regAY[] = new int[16];
    // Output signal levels (thanks to Matthew Westcott)
    // http://groups.google.com/group/comp.sys.sinclair/browse_thread/thread/
    // fb3091da4c4caf26/d5959a800cda0b5e?lnk=gst&q=Matthew+Westcott#d5959a800cda0b5e
    private static final double volumeRate[] = {
        0.0000, 0.0137, 0.0205, 0.0291, 0.0423, 0.0618, 0.0847, 0.1369,
        0.1691, 0.2647, 0.3527, 0.4499, 0.5704, 0.6873, 0.8482, 1.0000
    };
    // Real (for the soundcard) volume levels
    private int volumeLevel[] = new int[16];
    // Channel audio buffer (110840 / 50 = 2216'8) samples per frame
    private int[] bufA = new int[2220];
    private int[] bufB = new int[2220];
    private int[] bufC = new int[2220];
    private int pbufA, pbufB, pbufC;

    // Output channel levels
    private boolean outA, outB, outC, outN;

    private int audiotstates;
    private float spf;

    AY8912(int clock) {
        clockFreq = clock;
        ayCycle = clockFreq >>> 5;
        periodA = periodB = periodC = 0;
        counterA = counterB = counterC = 0;
        amplitudeA = amplitudeB = amplitudeC = 0;
        noiseCounter = 0;
        envelopePeriod = envelopeShape = 0;
        maxAmplitude = 8000;
        registerAddress = 0;
        Arrays.fill(regAY, 0);
        for (int idx = 0; idx < volumeLevel.length; idx++) {
            volumeLevel[idx] = (int)(maxAmplitude * volumeRate[idx]);
//            System.out.println(String.format("volumeLevel[%d]: %d", idx, volumeLevel[idx]));
        }
        Arrays.fill(bufA, 0);
        Arrays.fill(bufB, 0);
        Arrays.fill(bufC, 0);
        pbufA = pbufB = pbufC = 0;
        outA = outB = outC = outN = false;
    }

    public int getRegisterAddress() {
        return registerAddress;
    }

    public void setRegisterAddress(int value) {
        registerAddress = value & 0x0f;
    }

    public int readRegister() {
        if (registerAddress >= 14 &&
                (regAY[AYEnable] >> registerAddress - 8 & 1) == 0) {
//            System.out.println(String.format("getAYRegister %d: %02X",
//                registerLatch, 0xFF));
            return 0xFF;
        }

        if (registerAddress < 14)
            System.out.println(String.format("getAYRegister %d: %02X",
                registerAddress, regAY[registerAddress]));
        return regAY[registerAddress];
    }

    public void writeRegister(int value) {

        regAY[registerAddress] = value & 0xff;

        switch(registerAddress) {
            case FineToneA:
            case CoarseToneA:
                regAY[CoarseToneA] = value & 0x0f;
                periodA = regAY[CoarseToneA] * 256 + regAY[FineToneA];
                if (periodA == 0) {
                    periodA = 1;
                }
                break;
            case FineToneB:
            case CoarseToneB:
                regAY[CoarseToneB] = value & 0x0f;
                periodB = regAY[CoarseToneB] * 256 + regAY[FineToneB];
                if (periodB == 0) {
                    periodB = 1;
                }
                break;
            case FineToneC:
            case CoarseToneC:
                regAY[CoarseToneC] = value & 0x0f;
                periodC = regAY[CoarseToneC] * 256 + regAY[FineToneC];
                if (periodC == 0) {
                    periodC = 1;
                }
                break;
            case NoisePeriod:
                regAY[registerAddress] &= 0x1f;
                if (regAY[registerAddress] == 0) {
                    regAY[registerAddress] = 1;
                }
                break;
            case AYEnable:
                break;
            case AmplitudeA:
                regAY[registerAddress] &= 0x1f;
                amplitudeA = value & 0x1f;
                break;
            case AmplitudeB:
                regAY[registerAddress] &= 0x1f;
                amplitudeB = value & 0x1f;
                break;
            case AmplitudeC:
                regAY[registerAddress] &= 0x1f;
                amplitudeC = value & 0x1f;
                break;
            case FineEnvelope:
            case CoarseEnvelope:
                envelopePeriod = regAY[CoarseEnvelope] * 256 + regAY[FineEnvelope];
                if (envelopePeriod == 0)
                    envelopePeriod = 1;
                break;
            case EnvelopeShapeCycle:
                regAY[registerAddress] = value & 0x0f;
                envelopeCounter = 0;
        }
        if (registerAddress < 14)
            System.out.println(String.format("setAYRegister %d: %02X",
                registerAddress, regAY[registerAddress]));
    }

    public void updateAY(int tstates) {
//        System.out.println(String.format("updateAY: tstates = %d", tstates));
//        states = tstates - audiotstates;
//        audiotstates += tstates;
//        float time = tstates;

//        tstates += timeRem;
        if (tstates < 32) { // 32 CPU clocks == 1 AY clock
            return;
        }

        while ((tstates - audiotstates) > 32) {
            audiotstates += 32;
            counterA++;
            counterB++;
            counterC++;
            if ((regAY[AYEnable] & ENABLE_A) != 0) {
                outA = true;
            } else {
                if (regAY[AmplitudeA] == 0) {
                    bufA[pbufA++] = 0;
                    bufA[pbufA++] = 0;
                } else {
                    bufA[pbufA] = volumeLevel[amplitudeA];
                    bufA[pbufA + 1] = -volumeLevel[amplitudeA];;
                    pbufA += 2;
                }
            }

            if ((regAY[AYEnable] & ENABLE_B) != 0) {
                outB = true;
            } else {
                if (regAY[AmplitudeB] == 0) {
                    bufB[pbufB++] = 0;
                    bufB[pbufB++] = 0;
                } else {
                    bufB[pbufB] = volumeLevel[amplitudeB];
                    bufB[pbufB + 1] = -volumeLevel[amplitudeB];
                    pbufB += 2;
                }
            }

            if ((regAY[AYEnable] & ENABLE_C) != 0) {
                outC = true;
            } else {
                if (regAY[AmplitudeC] == 0) {
                    bufC[pbufC++] = 0;
                    bufC[pbufC++] = 0;
                } else {
                    bufC[pbufC] = volumeLevel[amplitudeC];
                    bufC[pbufC + 1] = -volumeLevel[amplitudeC];
                    pbufC += 2;
                }
            }
        }
    }
}
