/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

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
    // Host clock frequency
    private int clockFreq;
    // AY clock cycle
    private int ayCycle;
    // Channel periods
    private int periodA, periodB, periodC, periodN;
    // Channel period counters
    private int counterA, counterB, counterC;
    // Channel amplitudes
    private int amplitudeA, amplitudeB, amplitudeC;
    // Noise counter & noise seed
    private int counterN, rng;
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
    private int[] chanA = new int[4440];
    private int[] chanB = new int[4440];
    private int[] chanC = new int[4440];
    private int pbuf;
    // Precalculate sample positions
    private int[] samplePos = new int[965];
    private final double Divider = 4.6164;
    // Tone channel levels
    private boolean toneA, toneB, toneC, toneN;
    private boolean disableToneA, disableToneB, disableToneC;
    private boolean disableNoiseA, disableNoiseB, disableNoiseC;
    private boolean envA, envB, envC;
    private int audiotstates;

    AY8912(int clock) {
        clockFreq = clock;
        maxAmplitude = 7000;
        for (int idx = 0; idx < volumeLevel.length; idx++) {
            volumeLevel[idx] = (int) (maxAmplitude * volumeRate[idx]);
//            System.out.println(String.format("volumeLevel[%d]: %d",
//                    idx, volumeLevel[idx]));
        }
        for (int pos = 0; pos < samplePos.length; pos++) {
            samplePos[pos] = (int) (pos * Divider + 0.24f);
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

    public void writeRegister(int value, int tstates) {

        regAY[addressLatch] = value & 0xff;

        switch (addressLatch) {
            case FineToneA:
            case CoarseToneA:
                regAY[CoarseToneA] &= 0x0f;
                periodA = regAY[CoarseToneA] * 256 + regAY[FineToneA];
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
                envelopePeriod <<= 1;
//                System.out.println(String.format("envPeriod: %d", envelopePeriod));
                break;
            case EnvelopeShapeCycle:
                regAY[addressLatch] &= 0x0f;
//                System.out.println(String.format("envShape: %02x", value & 0x0f));
                envelopeCounter = 0;
                if ((value & ATTACK) != 0) {
                    amplitudeEnv = 0;
                    envIncr = 1;
                    Attack = true;
                } else {
                    amplitudeEnv = 15;
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
                        amplitudeEnv += envIncr;
                    }

                    if (amplitudeEnv < 0 || amplitudeEnv > 15) {
                        if ((regAY[EnvelopeShapeCycle] & CONTINUE) == 0) {
                            amplitudeEnv = 0;
                            Continue = true;
                        } else {
                            if ((regAY[EnvelopeShapeCycle] & ALTERNATE) != 0) {
                                envIncr *= -1;
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
                }
            }

//            System.out.println(String.format("volA: %d, volB: %d, volC: %d", volA, volB, volC));

            outA = (toneA || disableToneA) && (toneN || disableNoiseA);
            outB = (toneB || disableToneB) && (toneN || disableNoiseB);
            outC = (toneC || disableToneC) && (toneN || disableNoiseC);

            if (envA) {
                chanA[pbuf] = outA ? volumeLevel[amplitudeEnv] : -volumeLevel[amplitudeEnv];
            } else {
                chanA[pbuf] = outA ? amplitudeA : -amplitudeA;
            }

            if (envB) {
                chanB[pbuf] = outB ? volumeLevel[amplitudeEnv] : -volumeLevel[amplitudeEnv];
            } else {
                chanB[pbuf] = outB ? amplitudeB : -amplitudeB;
            }

            if (envC) {
                chanC[pbuf] = outC ? volumeLevel[amplitudeEnv] : -volumeLevel[amplitudeEnv];
            } else {
                chanC[pbuf] = outC ? amplitudeC : -amplitudeC;
            }
            pbuf++;
        }
//        System.out.println(String.format("updateAY: resto de %d tstates", remain));
    }

    public void endFrame() {
//        System.out.println(String.format("endFrame: ticks = %d", ticks));
//        double timePos;
        int pos0, pos1, pos2;
        for (int sample = 0; sample < 961; sample++) {
//            timePos = sample * Divider;
//            pos = (int) timePos;
//            timePos -= pos;
//
//            if (chanA[pos + 1] - chanA[pos] == 0) {
//                bufA[sample] = chanA[pos];
//            } else {
//                bufA[sample] = (int)(chanA[pos] * (1-timePos) + chanA[pos+1] * timePos);
//            }
//
//            if (chanB[pos + 1] - chanB[pos] == 0) {
//                bufB[sample] = chanB[pos];
//            } else {
//                bufB[sample] = (int)(chanB[pos] * (1-timePos) + chanB[pos+1] * timePos);
//            }
//
//            if (chanC[pos + 1] - chanC[pos] == 0) {
//                bufC[sample] = chanC[pos];
//            } else {
//                bufC[sample] = (int)(chanC[pos] * (1-timePos) + chanC[pos+1] * timePos);
//            }
            pos0 = samplePos[sample];
            pos1 = pos0 + 1;
            pos2 = pos0 + 2;
            bufA[sample] = (chanA[pos0] + chanA[pos1] + chanA[pos2]) / 3;
            bufB[sample] = (chanB[pos0] + chanB[pos1] + chanB[pos2]) / 3;
            bufC[sample] = (chanC[pos0] + chanC[pos1] + chanC[pos2]) / 3;
        }
        pbuf = 0;
        audiotstates -= Spectrum.FRAMES128k;
//        if (audiotstates < 0) {
//            System.out.println("audiotstates < 0!");
//            audiotstates = 0;
//        }
    }

    public void reset() {
        periodA = periodB = periodC = periodN = 1;
        counterA = counterB = counterC = counterN = 0;
        amplitudeA = amplitudeB = amplitudeC = amplitudeEnv = 0;
        envelopePeriod = 0;
        addressLatch = 0;
        audiotstates = pbuf = 0;
        toneA = toneB = toneC = toneN = true;
        rng = 1;
        regAY[Mixer] = 0xff;
        Continue = false;
        Attack = true;
        amplitudeEnv = 0;
        envIncr = 1;
    }

    public void setBufferChannels(int[] bChanA, int[] bChanB, int[] bChanC) {
        bufA = bChanA;
        bufB = bChanB;
        bufC = bChanC;
    }

    public int getSampleCount() {
        return (int)(pbuf / Divider);
    }
}
