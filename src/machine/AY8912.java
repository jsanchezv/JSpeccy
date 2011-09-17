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
public final class AY8912 {

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
    private static final int ENVELOPE = 0x10;
    // Envelope shape bits
    private static final int HOLD = 0x01;
    private static final int ALTERNATE = 0x02;
    private static final int ATTACK = 0x04;
    private static final int CONTINUE = 0x08;
    // Host clock frequency
    private int clockFreq;
    // AY clock cycle
    private int ayCycle;
    // Channel periods
    private int periodA, periodB, periodC, periodN;
    // Channel half periods
    private int hPeriodA, hPeriodB, hPeriodC;
    // Channel period counters
    private int counterA, counterB, counterC;
    // Channel amplitudes
    private int amplitudeA, amplitudeB, amplitudeC;
    // Noise counter & noise seed
    private int noiseCounter, rng;
    private int envelopePeriod, envelopeCounter, envIncr;

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
    private int envelopeShape, shapeCounter, shapeCycle, shapePeriod;
    private boolean Continue, Attack;
    // Envelope amplitude
    private int amplitudeEnv;
    private int maxAmplitude;
    // AY register index
    private int addressLatch;
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
    private int[] bufA = new int[2217];
    private int[] bufB = new int[2217];
    private int[] bufC = new int[2217];
    private int pbuf;
    // Precalculate sample positions
    private int[] samplePos = new int[970];
    // Tone channel levels
    private boolean toneA, toneB, toneC, toneN;
    private int audiotstates, ticks;

    AY8912(int clock) {
        clockFreq = clock;
        ayCycle = clockFreq >>> 5;
        maxAmplitude = 8000;
        for (int idx = 0; idx < volumeLevel.length; idx++) {
            volumeLevel[idx] = (int) (maxAmplitude * volumeRate[idx]);
//            System.out.println(String.format("volumeLevel[%d]: %d", idx, volumeLevel[idx]));
        }
        for (int pos = 0; pos < samplePos.length; pos++) {
            samplePos[pos] = (int) (pos * 2.3f + 0.5f);
        }
        reset();
    }

    public int getAddressLatch() {
        return addressLatch;
    }

    public void setAddressLatch(int value) {
        addressLatch = value & 0x0f;
    }

    public int readRegister() {
        if (addressLatch >= 14
            && (regAY[AYEnable] >> addressLatch - 8 & 1) == 0) {
//            System.out.println(String.format("getAYRegister %d: %02X",
//                registerLatch, 0xFF));
            return 0xFF;
        }

//        if (addressLatch < 14)
//            System.out.println(String.format("getAYRegister %d: %02X",
//                addressLatch, regAY[addressLatch]));
        return regAY[addressLatch];
    }

    public void writeRegister(int value) {

        regAY[addressLatch] = value & 0xff;

        switch (addressLatch) {
            case FineToneA:
            case CoarseToneA:
                regAY[CoarseToneA] = value & 0x0f;
                periodA = regAY[CoarseToneA] * 256 + regAY[FineToneA];
                if (periodA == 0) {
                    periodA = 2;
                }
                hPeriodA = periodA >>> 1;
                break;
            case FineToneB:
            case CoarseToneB:
                regAY[CoarseToneB] = value & 0x0f;
                periodB = regAY[CoarseToneB] * 256 + regAY[FineToneB];
                if (periodB == 0) {
                    periodB = 2;
                }
                hPeriodB = periodB >>> 1;
                break;
            case FineToneC:
            case CoarseToneC:
                regAY[CoarseToneC] = value & 0x0f;
                periodC = regAY[CoarseToneC] * 256 + regAY[FineToneC];
                if (periodC == 0) {
                    periodC = 2;
                }
                hPeriodC = periodC >>> 1;
                break;
            case NoisePeriod:
                regAY[addressLatch] &= 0x1f;
                if (regAY[addressLatch] == 0) {
                    regAY[addressLatch] = 1;
                }
                break;
            case AYEnable:
                System.out.println(String.format("Enable Register: %02X",
                    regAY[addressLatch]));
                break;
            case AmplitudeA:
                regAY[addressLatch] &= 0x1f;
                amplitudeA = value & 0x0f;
                if ((value & 0x10) != 0)
                    System.out.println("Envelope Chan A");
                break;
            case AmplitudeB:
                regAY[addressLatch] &= 0x1f;
                amplitudeB = value & 0x0f;
                if ((value & 0x10) != 0)
                    System.out.println("Envelope Chan C");
                break;
            case AmplitudeC:
                regAY[addressLatch] &= 0x1f;
                amplitudeC = value & 0x0f;
                if ((value & 0x10) != 0)
                    System.out.println("Envelope Chan C");
                break;
            case FineEnvelope:
            case CoarseEnvelope:
                envelopePeriod = regAY[CoarseEnvelope] * 256 + regAY[FineEnvelope];
                if (envelopePeriod == 0) {
                    envelopePeriod = 1;
                }
                shapePeriod = envelopePeriod;
                envelopePeriod <<= 4;

//                System.out.println(String.format("envPeriod: %d, shapePeriod: %d",
//                    envelopePeriod, shapePeriod));
                break;
            case EnvelopeShapeCycle:
                regAY[addressLatch] = value & 0x0f;
                shapeCounter = 0;
                if ((value & ATTACK) != 0) {
                    amplitudeEnv = -1;
                    envIncr = 1;
                    Attack = true;
                } else {
                    amplitudeEnv = 16;
                    envIncr = -1;
                    Attack = false;
                }
                Continue = false;
        }
//        if (addressLatch < 14)
//            System.out.println(String.format("setAYRegister %d: %02X",
//                addressLatch, regAY[addressLatch]));
    }

    public void updateAY(int tstates) {
        boolean outA, outB, outC;
        boolean enableA, enableB, enableC;
        boolean noiseA, noiseB, noiseC;
        boolean envA, envB, envC;

        enableA = (regAY[AYEnable] & ENABLE_A) != 0;
        enableB = (regAY[AYEnable] & ENABLE_B) != 0;
        enableC = (regAY[AYEnable] & ENABLE_C) != 0;
        noiseA = (regAY[AYEnable] & NOISE_A) != 0;
        noiseB = (regAY[AYEnable] & NOISE_B) != 0;
        noiseC = (regAY[AYEnable] & NOISE_C) != 0;
        envA = (regAY[AmplitudeA] & ENVELOPE) != 0;
        envB = (regAY[AmplitudeB] & ENVELOPE) != 0;
        envC = (regAY[AmplitudeC] & ENVELOPE) != 0;

//        System.out.println(String.format("updateAY: tstates = %d", tstates));
//        states = tstates - audiotstates;
//        audiotstates += tstates;
//        float time = tstates;

//        tstates += timeRem;
//        if (tstates < 32) { // 32 CPU clocks == 1 AY clock
//            return;
//        }

        while ((tstates - audiotstates) > 32) {
            audiotstates += 32;
            ticks++;
            if (++counterA >= periodA) {
                toneA = true;
                counterA = 0;
            }
            if (toneA && counterA > hPeriodA) {
                toneA = false;
            }

            if (++counterB >= periodB) {
                toneB = true;
                counterB = 0;
            }
            if (toneB && counterB > hPeriodB) {
                toneB = false;
            }

            if (++counterC >= periodC) {
                toneC = true;
                counterC = 0;
            }
            if (toneC && counterC > hPeriodC) {
                toneC = false;
            }

            if (++noiseCounter >= periodN) {
                noiseCounter = 0;

                // Borrowed from breemlib. Thanks to his authors. :)
                /* Is noise output going to change? */
                if (((rng + 1) & 2) != 0) /* (bit0^bit1)? */ {
                    toneN = !toneN;
                }

                /* The Random Number Generator of the 8910 is a 17-bit shift */
                /* register. The input to the shift register is bit0 XOR bit3 */
                /* (bit0 is the output). This was verified on AY-3-8910 and YM2149 chips. */

                /* The following is a fast way to compute bit17 = bit0^bit3. */
                /* Instead of doing all the logic operations, we only check */
                /* bit0, relying on the fact that after three shifts of the */
                /* register, what now is bit3 will become bit0, and will */
                /* invert, if necessary, bit14, which previously was bit17. */
                if ((rng & 1) != 0) {
                    rng ^= 0x24000; /* This version is called the "Galois configuration". */
                }
                rng >>= 1;
            }

            outA = (toneA || enableA) && (toneN || noiseA);
            outB = (toneB || enableB) && (toneN || noiseB);
            outC = (toneC || enableC) && (toneN || noiseC);

            if (envA || envB || envC) {
                if (++envelopeCounter >= envelopePeriod) {
                    envelopeCounter = 0;
                    if ((regAY[EnvelopeShapeCycle] & ATTACK) != 0) {
                        amplitudeEnv = -1;
                        envIncr = 1;
                        Attack = true;
                    } else {
                        amplitudeEnv = 16;
                        envIncr = -1;
                        Attack = false;
                    }
                    Continue = false;
//                    System.out.println(String.format("updateAY: ticks = %d", ticks));
                }

                if (++shapeCounter >= shapePeriod) {
                    shapeCounter = 0;
                    if (!Continue) {
                        amplitudeEnv += envIncr;
                    }
                    if (amplitudeEnv < 0 || amplitudeEnv > 15) {
                        if ((regAY[EnvelopeShapeCycle] & CONTINUE) == 0) {
                            amplitudeEnv = 0;
                            Continue = true;
                        }

                        if ((regAY[EnvelopeShapeCycle] & ALTERNATE) != 0) {
                            envIncr *= -1;
                            Attack = !Attack;
                        }

                        if ((regAY[EnvelopeShapeCycle] & HOLD) != 0) {
                            if (Attack) {
                                amplitudeEnv = 15;
                            } else {
                                amplitudeEnv = 0;
                            }
                        }
                        amplitudeEnv &= 0x0f;
                    }
                }
            }

            if (envA) {
                bufA[pbuf] = outA ? volumeLevel[amplitudeEnv] : -volumeLevel[amplitudeEnv];
            } else {
                bufA[pbuf] = outA ? volumeLevel[amplitudeA] : -volumeLevel[amplitudeA];
            }

            if (envB) {
                bufB[pbuf] = outB ? volumeLevel[amplitudeEnv] : -volumeLevel[amplitudeEnv];
            } else {
                bufB[pbuf] = outB ? volumeLevel[amplitudeB] : -volumeLevel[amplitudeB];
            }

            if (envC) {
                bufC[pbuf] = outC ? volumeLevel[amplitudeEnv] : -volumeLevel[amplitudeEnv];
            } else {
                bufC[pbuf] = outC ? volumeLevel[amplitudeC] : -volumeLevel[amplitudeC];
            }
            pbuf++;
        }
    }

    public int getSampleABC(int nSample) {
//        float pos = nSample * 2.3f + 0.5f;
        int idx = samplePos[nSample];

//        int chnA = (bufA[idx]/2 + bufA[idx+1] + bufA[idx+2]/2) / 2;
//        int chnB = (bufB[idx]/2 + bufB[idx+1] + bufB[idx+2]/2) / 2;
//        int chnC = (bufC[idx]/2 + bufC[idx+1] + bufC[idx+2]/2) / 2;
//        int chnA = (bufA[idx] + bufA[idx+1]) / 2;
//        int chnB = (bufB[idx] + bufB[idx+1]) / 2;
//        int chnC = (bufC[idx]+ bufC[idx+1]) / 2;
//        return chnA + chnB + chnC;

        return bufA[idx] + bufB[idx] + bufC[idx];
    }

    public void endFrame() {
//        System.out.println(String.format("endFrame: ticks = %d", ticks));
        pbuf = ticks = 0;
        audiotstates -= Spectrum.FRAMES128k;
    }

    public void reset() {
        periodA = periodB = periodC = 0;
        counterA = counterB = counterC = 0;
        amplitudeA = amplitudeB = amplitudeC = 0;
        noiseCounter = 0;
        envelopePeriod = envelopeShape = 0;
        addressLatch = 0;
        Arrays.fill(regAY, 0);
        Arrays.fill(bufA, 0);
        Arrays.fill(bufB, 0);
        Arrays.fill(bufC, 0);
        audiotstates = pbuf = 0;
        toneA = toneB = toneC = toneN = true;
        rng = 1;
    }
}
