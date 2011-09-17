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
//    private enum AYReg { FineToneA, CoarseToneA,
//        FineToneB, CoarseToneB, FineToneC, CoarseToneC,
//        NoisePeriod, Enable, AmplitudeA, AmplitudeB, AmplitudeC,
//        FineToneEnvelope, CoarseToneEnvelope, EnvelopeShapeCycle,
//        IOPortA, IOPortB };
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
    private int envelopePeriod;

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
    private int registerLatch;
    // AY register set
    private int regAY[] = new int[16];
    // Output signal levels
    private static final double volumeRate[] = {
        0.0000, 0.0138, 0.0205, 0.0291, 0.0423, 0.0618, 0.0847, 0.1369,
        0.1691, 0.2647, 0.3527, 0.4499, 0.5704, 0.6873, 0.8482, 1.0000
    };
    // Real (for the soundcard) volume levels
    private int volumeLevel[] = new int[16];
    // Channel audio buffer
    private int[] bufA = new int[2048];
    private int[] bufB = new int[2048];
    private int[] bufC = new int[2048];
    private int pbufA, pbufB, pbufC;

    // Output channel levels
    private int outA, outB, outC, outN;

    public int audiotstates;
    private float timeRem, spf;

    AY8912(int clock) {
        clockFreq = clock;
        ayCycle = clockFreq >>> 5;
        periodA = periodB = periodC = 0;
        counterA = counterB = counterC = 0;
        amplitudeA = amplitudeB = amplitudeC = 0;
        noiseCounter = 0;
        envelopePeriod = envelopeShape = 0;
        maxAmplitude = 8000;
        registerLatch = 0;
        Arrays.fill(regAY, 0);
        for (int idx = 0; idx < volumeLevel.length; idx++) {
            volumeLevel[idx] = (int)(maxAmplitude * volumeRate[idx]);
//            System.out.println(String.format("volumeLevel[%d]: %d", idx, volumeLevel[idx]));
        }
        Arrays.fill(bufA, 0);
        Arrays.fill(bufB, 0);
        Arrays.fill(bufC, 0);
        pbufA = pbufB = pbufC = 0;
        outA = outB = outC = 0;
    }

    public int getIndexRegister() {
        return registerLatch;
    }

    public void setIndexRegister(int value) {
        registerLatch = value & 0x0f;
    }

    public int getAYRegister() {
        if (registerLatch >= 14 &&
                (regAY[AYEnable] >> registerLatch - 8 & 1) == 0) {
//            System.out.println(String.format("getAYRegister %d: %d",
//                indexRegister, 0xFF));
            return 0xFF;
        }

//        System.out.println(String.format("getAYRegister %d: %d",
//            indexRegister, ayRegs[indexRegister]));
        return regAY[registerLatch];
    }

    public void setAYRegister(int value) {
        switch(registerLatch) {
            case FineToneA:
                regAY[registerLatch] = value & 0xff;
                periodA = (regAY[CoarseToneA] << 8) | regAY[FineToneA];
                break;
            case CoarseToneA:
                regAY[registerLatch] = value & 0x0f;
                periodA = (regAY[CoarseToneA] << 8) | regAY[FineToneA];
                break;
            case FineToneB:
                regAY[registerLatch] = value & 0xff;
                periodB = (regAY[CoarseToneB] << 8) | regAY[FineToneB];
                break;
            case CoarseToneB:
                regAY[registerLatch] = value & 0x0f;
                periodB = (regAY[CoarseToneB] << 8) | regAY[FineToneB];
                break;
            case FineToneC:
                regAY[registerLatch] = value & 0xff;
                periodC = (regAY[CoarseToneC] << 8) | regAY[FineToneC];
                break;
            case CoarseToneC:
                regAY[registerLatch] = value & 0x0f;
                periodC = (regAY[CoarseToneC] << 8) | regAY[FineToneC];
                break;
            case NoisePeriod:
                regAY[registerLatch] = value & 0x1f;
                break;
            case AYEnable:
                regAY[registerLatch] = value & 0xff;
                break;
            case AmplitudeA:
            case AmplitudeB:
            case AmplitudeC:
                regAY[registerLatch] = value & 0x1f;
                break;
            case FineEnvelope:
            case CoarseEnvelope:
                regAY[registerLatch] = value & 0xff;
                envelopePeriod = (regAY[CoarseEnvelope] << 16) | regAY[FineEnvelope];
        }
//        System.out.println(String.format("setAYRegister %d: %d",
//            indexRegister, ayRegs[indexRegister]));
    }

    public void updateAY(int tstates) {
        tstates = tstates - audiotstates;
        audiotstates += tstates;
        float time = tstates;

        if (time + timeRem < 32) { //32 CPU clocks == 1 AY clock
            timeRem += time;
            return;
        }

        time += timeRem;

        if ((regAY[AYEnable] & ENABLE_A) != 0) {
            counterA += time;
            outA = volumeLevel[15];
        } else if (regAY[AmplitudeA] == 0) {
           counterA += time;
        }

        if ((regAY[AYEnable] & ENABLE_B) != 0) {
            counterB += time;
            outB = volumeLevel[15];
        } else if (regAY[AmplitudeB] == 0) {
           counterB += time;
        }

        if ((regAY[AYEnable] & ENABLE_C) != 0) {
            counterC += time;
            outC = volumeLevel[15];
        } else if (regAY[AmplitudeC] == 0) {
           counterC += time;
        }

        while (time > 32) {

        }
    }
}
