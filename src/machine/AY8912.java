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
    private static final int Mixer = 7;
    private static final int AmplitudeA = 8;
    private static final int AmplitudeB = 9;
    private static final int AmplitudeC = 10;
    private static final int FineEnvelope = 11;
    private static final int CoarseEnvelope = 12;
    private static final int EnvelopeShapeCycle = 13;
    private static final int IOPortA = 14;
    private static final int IOPortB = 15;
    // Other definitions
    private static final int TONE_A = 0x01;
    private static final int TONE_B = 0x02;
    private static final int TONE_C = 0x04;
    private static final int NOISE_A = 0x08;
    private static final int NOISE_B = 0x10;
    private static final int NOISE_C = 0x20;
    private static final int ENVELOPE = 0x10;
    // Envelope shape bits
    private static final int HOLD = 0x01;
    private static final int ALTERNATE = 0x02;
    private static final int ATTACK = 0x04;
    private static final int CONTINUE = 0x08;
    // Channel periods
    private int periodA, periodB, periodC, periodN;
    // Channel period counters
    private int counterA, counterB, counterC;
    // Channel amplitudes
    private int amplitudeA, amplitudeB, amplitudeC;
    // Noise counter & noise seed
    private int counterN, rng;
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
    private int[] volumeLevel = new int[16];
    private int[] bufA;
    private int[] bufB;
    private int[] bufC;
    private int pbuf;
    private int FREQ;
    // Precalculate sample positions
//    private int[] samplePos = new int[965];
    private double step, stepCounter;
    // Tone channel levels
    private boolean toneA, toneB, toneC, toneN;
    private boolean disableToneA, disableToneB, disableToneC;
    private boolean disableNoiseA, disableNoiseB, disableNoiseC;
    private boolean envA, envB, envC;
    private int volumeA, volumeB, volumeC;
    private int audiotstates, samplesPerFrame;
    private MachineTypes spectrumModel;

    AY8912() {
        maxAmplitude = 16384;
        for (int idx = 0; idx < volumeLevel.length; idx++) {
            volumeLevel[idx] = (int) (maxAmplitude * volumeRate[idx]);
//            System.out.println(String.format("volumeLevel[%d]: %d",
//                    idx, volumeLevel[idx]));
        }
    }

    public void setSpectrumModel(MachineTypes model) {
        spectrumModel = model;
        step = (double) spectrumModel.tstatesFrame / samplesPerFrame;
//        divider = (double)spectrumModel.tstatesFrame / ((FREQ / 50) * 16);
//        for (int pos = 0; pos < samplePos.length; pos++) {
//            samplePos[pos] = (int) (pos * divider);
//        }
        reset();
    }

    public void setMaxAmplitude(int amplitude) {
        maxAmplitude = amplitude;
        for (int idx = 0; idx < volumeLevel.length; idx++) {
            volumeLevel[idx] = (int) (maxAmplitude * volumeRate[idx]);
        }
    }

    public void setAudioFreq(int freq) {
        FREQ = freq;
        samplesPerFrame = FREQ / 50;
        step = (double) spectrumModel.tstatesFrame / samplesPerFrame;
    }

    public int getAddressLatch() {
        return addressLatch;
    }

    public void setAddressLatch(int value) {
        addressLatch = value & 0x0f;
    }

    public int readRegister() {
        if (addressLatch >= 14
            && (regAY[Mixer] >> addressLatch - 8 & 1) == 0) {
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
                regAY[CoarseToneA] &= 0x0f;
                periodA = regAY[CoarseToneA] * 256 + regAY[FineToneA];
//                System.out.println("PeriodA: " + periodA);
                break;
            case FineToneB:
            case CoarseToneB:
                regAY[CoarseToneB] &= 0x0f;
                periodB = regAY[CoarseToneB] * 256 + regAY[FineToneB];
                break;
            case FineToneC:
            case CoarseToneC:
                regAY[CoarseToneC] &= 0x0f;
                periodC = regAY[CoarseToneC] * 256 + regAY[FineToneC];
                break;
            case NoisePeriod:
                regAY[addressLatch] &= 0x1f;
                periodN = value & 0x1f;
                if (periodN == 0) {
                    periodN = 1;
                }
                periodN <<= 1;
//                System.out.println(String.format("Noise Period: %d", periodN));
                break;
            case Mixer:
                disableToneA = (regAY[Mixer] & TONE_A) != 0;
                disableToneB = (regAY[Mixer] & TONE_B) != 0;
                disableToneC = (regAY[Mixer] & TONE_C) != 0;
                disableNoiseA = (regAY[Mixer] & NOISE_A) != 0;
                disableNoiseB = (regAY[Mixer] & NOISE_B) != 0;
                disableNoiseC = (regAY[Mixer] & NOISE_C) != 0;
//                System.out.println(String.format("Enable Register: %02X",
//                    regAY[addressLatch]));
                break;
            case AmplitudeA:
                regAY[addressLatch] &= 0x1f;
                amplitudeA = volumeLevel[value & 0x0f];
                envA = (regAY[AmplitudeA] & ENVELOPE) != 0;
//                if (envA) {
//                    System.out.println("Envelope Chan A");
//                } else {
//                    System.out.println("amplitudeA: " + (value & 0x0f));
//                }
                break;
            case AmplitudeB:
                regAY[addressLatch] &= 0x1f;
                amplitudeB = volumeLevel[value & 0x0f];
                envB = (regAY[AmplitudeB] & ENVELOPE) != 0;
//                if (envB) {
//                    System.out.println("Envelope Chan B");
//                }
                break;
            case AmplitudeC:
                regAY[addressLatch] &= 0x1f;
                amplitudeC = volumeLevel[value & 0x0f];
                envC = (regAY[AmplitudeC] & ENVELOPE) != 0;
//                if (envC) {
//                    System.out.println("Envelope Chan C");
//                }
                break;
            case FineEnvelope:
            case CoarseEnvelope:
                envelopePeriod = regAY[CoarseEnvelope] * 256 + regAY[FineEnvelope];
                if (envelopePeriod == 0) {
                    envelopePeriod = 1;
                }
//                System.out.println(String.format("envPeriod: %d", envelopePeriod));
                envelopePeriod <<= 1;
                break;
            case EnvelopeShapeCycle:
                regAY[addressLatch] &= 0x0f;
//                System.out.println(String.format("envShape: %02x", value & 0x0f));
                envelopeCounter = 0;
                if ((value & ATTACK) != 0) {
                    amplitudeEnv = 0;
                    Attack = true;
                } else {
                    amplitudeEnv = 15;
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
//        int volA, volB, volC;

//        System.out.println(String.format("updateAY: tstates = %d", tstates));

        while (audiotstates < tstates) {
            audiotstates += 16;

            if (++counterA >= periodA) {
                toneA = !toneA;
                counterA = 0;
            }

            if (++counterB >= periodB) {
                toneB = !toneB;
                counterB = 0;
            }

            if (++counterC >= periodC) {
                toneC = !toneC;
                counterC = 0;
            }

            if (++counterN >= 0) {
                counterN = 0;

                /* The Random Number Generator of the 8910 is a 17-bit shift */
                /* register. The input to the shift register is bit0 XOR bit3 */
                /* (bit0 is the output). This was verified on AY-3-8910 and YM2149 chips. */
                toneN = (rng & 0x01) != 0;
                if (toneN) {
                    rng ^= 0x28000;
                }
                rng >>>= 1;
            }

            if (envA || envB || envC) {
                if (++envelopeCounter >= envelopePeriod) {
                    envelopeCounter = 0;

                    if (!Continue) {
                        if (Attack) {
                            amplitudeEnv++;
                        } else {
                            amplitudeEnv--;
                        }
                    }

                    if (amplitudeEnv < 0 || amplitudeEnv > 15) {
                        if ((regAY[EnvelopeShapeCycle] & CONTINUE) == 0) {
                            amplitudeEnv = 0;
                            Continue = true;
                        } else {
                            if ((regAY[EnvelopeShapeCycle] & ALTERNATE) != 0) {
                                Attack = !Attack;
                            }

                            if ((regAY[EnvelopeShapeCycle] & HOLD) != 0) {
                                amplitudeEnv = Attack ? 15 : 0;
                                Continue = true;
                            } else {
                                amplitudeEnv = Attack ? 0 : 15;
                            }
                        }
                    }

                    if (envA) {
                        amplitudeA = volumeLevel[amplitudeEnv];
                    }

                    if (envB) {
                        amplitudeB = volumeLevel[amplitudeEnv];
                    }

                    if (envC) {
                        amplitudeC = volumeLevel[amplitudeEnv];
                    }

//                if (envA || envB || envC)
//                System.out.println(String.format("AmplitudeEnv = %d, %d",
//                        amplitudeEnv, volumeLevel[amplitudeEnv]));
                }
            }

//            System.out.println(String.format("volA: %d, volB: %d, volC: %d", volA, volB, volC));

//            outA = (toneA || disableToneA) && (toneN || disableNoiseA);
//            outB = (toneB || disableToneB) && (toneN || disableNoiseB);
//            outC = (toneC || disableToneC) && (toneN || disableNoiseC);
//
//            volA = outA ? amplitudeA : 0;
//            volumeA = (int) (volumeA / 5 + volA * 0.8);
//            volumeA = (int) (volumeA * 0.6 + volA * 0.4);
//            volumeA = (volumeA >> 1) + (volA >> 1);
//            volumeA = (volumeA + volA) >>> 1;

//            volB = outB ? amplitudeB : 0;
//            volumeB = (int) (volumeB / 5 + volB * 0.8);
//            volumeB = (int) (volumeB * 0.6 + volB * 0.4);
//            volumeB = (volumeB >> 1) + (volB >> 1);
//            volumeB = (volumeB + volB) >>> 1;

//            volC = outC ? amplitudeC : 0;
//            volumeC = (int) (volumeC / 5 + volC * 0.8);
//            volumeC = (int) (volumeC * 0.6 + volC * 0.4);
//            volumeC = (volumeC >> 1) + (volC >> 1);
//            volumeC = (volumeC + volC) >>> 1;

            stepCounter += 16.0;
            if (stepCounter >= step) {
                outA = (toneA || disableToneA) && (toneN || disableNoiseA);
                outB = (toneB || disableToneB) && (toneN || disableNoiseB);
                outC = (toneC || disableToneC) && (toneN || disableNoiseC);
//                volA = outA ? amplitudeA : 0;
//                volB = outB ? amplitudeB : 0;
//                volC = outC ? amplitudeC : 0;
//                volumeA = (int) (volumeA * 0.4 + volA * 0.6);
//                volumeB = (int) (volumeB * 0.4 + volB * 0.6);
//                volumeC = (int) (volumeC * 0.4 + volC * 0.6);
                volumeA = (volumeA + (outA ? amplitudeA : 0)) >>> 1;
                volumeB = (volumeB + (outB ? amplitudeB : 0)) >>> 1;
                volumeC = (volumeC + (outC ? amplitudeC : 0)) >>> 1;
                bufA[pbuf] = volumeA;
                bufB[pbuf] = volumeB;
                bufC[pbuf] = volumeC;
                pbuf++;
                stepCounter -= step;
            }
        }
//        System.out.println(String.format("updateAY: resto de %d tstates", remain));
    }

    public void endFrame() {
        if (pbuf == 0)
            return;

//        System.out.println("# samples: " + getSampleCount());
//        System.out.println(String.format("# AY samples: %d", pbuf));
        if (pbuf == samplesPerFrame - 1) {
//            System.out.println(String.format("El buffer del AY solo tenía %d samples",
//                pbuf));
            bufA[pbuf] = bufA[pbuf - 1];
            bufB[pbuf] = bufB[pbuf - 1];
            bufC[pbuf] = bufC[pbuf - 1];
        }
        pbuf = 0;
        audiotstates -= spectrumModel.tstatesFrame;
    }

    public void reset() {
        periodA = periodB = periodC = periodN = 1;
        counterA = counterB = counterC = counterN = 0;
        amplitudeA = amplitudeB = amplitudeC = amplitudeEnv = 0;
        volumeA = volumeB = volumeC = 0;
        envelopePeriod = 0;
        addressLatch = 0;
        toneA = toneB = toneC = toneN = false;
        rng = 1;
        Arrays.fill(regAY, 0);
        regAY[Mixer] = 0xff;
        Continue = false;
        Attack = true;      
    }

    public void startFrame() {
        audiotstates = pbuf = 0;
        stepCounter = 0.0;

        if (bufA != null) {
            Arrays.fill(bufA, 0);
        }
        if (bufB != null) {
            Arrays.fill(bufB, 0);
        }
        if (bufC != null) {
            Arrays.fill(bufC, 0);
        }
    }

    public void setBufferChannels(int[] bChanA, int[] bChanB, int[] bChanC) {
        bufA = bChanA;
        bufB = bChanB;
        bufC = bChanC;
    }

    public int getSampleCount() {
        return pbuf;
    }
}
