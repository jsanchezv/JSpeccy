/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import snapshots.AY8912State;

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
    private boolean Continue, Attack, Hold, Alternate;
    // Envelope amplitude
    private int amplitudeEnv;
    private int maxAmplitude;
    // AY register index
    private int addressLatch;
    // AY register set
    private final int regAY[] = new int[16];
    // Output signal levels (thanks to Matthew Westcott)
    // http://groups.google.com/group/comp.sys.sinclair/browse_thread/thread/
    // fb3091da4c4caf26/d5959a800cda0b5e?lnk=gst&q=Matthew+Westcott#d5959a800cda0b5e
    private static final double volumeRate[] = {
        0.0000, 0.0137, 0.0205, 0.0291, 0.0423, 0.0618, 0.0847, 0.1369,
        0.1691, 0.2647, 0.3527, 0.4499, 0.5704, 0.6873, 0.8482, 1.0000
    };
    // Real (for the soundcard) volume levels
    private final int[] volumeLevel = new int[16];
    private int[] bufA;
    private int[] bufB;
    private int[] bufC;
    private int pbuf;
    private int FREQ;
    private long step, stepCounter;
    private double stepRate;
    // Tone channel levels
    private boolean toneA, toneB, toneC, toneN;
    private boolean disableToneA, disableToneB, disableToneC;
    private boolean disableNoiseA, disableNoiseB, disableNoiseC;
    private boolean envA, envB, envC;
    private int volumeA, volumeB, volumeC;
//    private int lastA, lastB, lastC;
    private int audiotstates, samplesPerFrame;
    private MachineTypes spectrumModel;

    AY8912() {
        maxAmplitude = 10900;
        for (int idx = 0; idx < volumeLevel.length; idx++) {
            volumeLevel[idx] = (int) (maxAmplitude * volumeRate[idx]);
//            System.out.println(String.format("volumeLevel[%d]: %d",
//                    idx, volumeLevel[idx]));
        }
    }

    public AY8912State getAY8912State() {
        AY8912State state = new AY8912State();
        state.setAddressLatch(addressLatch);
        int regs[] = new int[16];
        System.arraycopy(regAY, 0, regs, 0, regAY.length);
        state.setRegAY(regs);
        return state;
    }

    public void setAY8912State(AY8912State state) {

        int[] ay = state.getRegAY();
        for (int reg = 0; reg < 16; reg++) {
            addressLatch = reg;
            writeRegister(ay[reg]);
        }

        addressLatch = state.getAddressLatch();
    }

    public void setSpectrumModel(MachineTypes model) {
        if (spectrumModel != model) {
            spectrumModel = model;
            reset();
        }

        if (samplesPerFrame != 0) {
            double tmp = (double)spectrumModel.tstatesFrame / (double)samplesPerFrame;
            step = (long)(tmp * 100000.0);
            stepRate = tmp / 16.0;
//            System.out.println(String.format("step = %d, stepRate = %f", step, stepRate));
        }
    }

    public void setMaxAmplitude(int amplitude) {
        maxAmplitude = amplitude;
        for (int idx = 0; idx < volumeLevel.length; idx++) {
            volumeLevel[idx] = (int) (maxAmplitude * volumeRate[idx]);
//            System.out.println(String.format("volumeLevel[%d]: %d",
//                idx, volumeLevel[idx]));
        }
    }

    public void setAudioFreq(int freq) {
        FREQ = freq;
        samplesPerFrame = FREQ / 50;

        double tmp = (double)spectrumModel.tstatesFrame / (double)samplesPerFrame;
        step = (long)(tmp * 100000.0);
        stepRate = tmp / 16.0;

//        System.out.println(String.format("step = %d, stepRate = %f", step, stepRate));
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

        switch (addressLatch) {
            case FineToneA:
            case CoarseToneA:
                regAY[addressLatch] = value & (addressLatch == FineToneA ? 0xff : 0x0f);
                periodA = (regAY[CoarseToneA] << 8) | regAY[FineToneA];
                break;
            case FineToneB:
            case CoarseToneB:
                regAY[addressLatch] = value & (addressLatch == FineToneB ? 0xff : 0x0f);
                periodB = (regAY[CoarseToneB] << 8) | regAY[FineToneB];
                break;
            case FineToneC:
            case CoarseToneC:
                regAY[addressLatch] = value & (addressLatch == FineToneC ? 0xff : 0x0f);
                periodC = (regAY[CoarseToneC] << 8) | regAY[FineToneC];
                break;
            case NoisePeriod:
                regAY[NoisePeriod] = value & 0x1f;
                break;
            case Mixer:
                regAY[Mixer] = value & 0xff;
                disableToneA = (value & TONE_A) != 0;
                disableToneB = (value & TONE_B) != 0;
                disableToneC = (value & TONE_C) != 0;
                disableNoiseA = (value & NOISE_A) != 0;
                disableNoiseB = (value & NOISE_B) != 0;
                disableNoiseC = (value & NOISE_C) != 0;
                break;
            case AmplitudeA:
                regAY[AmplitudeA] = value & 0x1f;
                envA = (value & ENVELOPE) != 0;
                if (envA) {
                    amplitudeA = volumeLevel[amplitudeEnv];
                } else {
                    amplitudeA = volumeLevel[value & 0x0f];
                }
                break;
            case AmplitudeB:
                regAY[AmplitudeB] = value & 0x1f;
                envB = (value & ENVELOPE) != 0;
                if (envB) {
                    amplitudeB = volumeLevel[amplitudeEnv];
                } else {
                    amplitudeB = volumeLevel[value & 0x0f];
                }
                break;
            case AmplitudeC:
                regAY[AmplitudeC] = value & 0x1f;
                envC = (value & ENVELOPE) != 0;
                if (envC) {
                    amplitudeC = volumeLevel[amplitudeEnv];
                } else {
                    amplitudeC = volumeLevel[value & 0x0f];
                }
                break;
            case FineEnvelope:
            case CoarseEnvelope:
                regAY[addressLatch] = value & 0xff;
                envelopePeriod = (regAY[CoarseEnvelope] << 8) | regAY[FineEnvelope];
                if (envelopePeriod == 0)
                    envelopePeriod = 2;  // envelopePeriod = 1; envelopePeriod <<= 1;
                else
                    envelopePeriod <<= 1;
//                System.out.println(String.format("envPeriod: %d", envelopePeriod));
                break;
            case EnvelopeShapeCycle:
                regAY[EnvelopeShapeCycle] = value & 0x0f;
                Hold = (value & HOLD) != 0;
                Alternate = (value & ALTERNATE) != 0;
                Attack = (value & ATTACK) != 0;
                amplitudeEnv = Attack ? 0 : 15;
                Continue = true;
                envelopeCounter = 0;
//                if (envA || envB || envC)
//                    System.out.println(String.format("envShape: %02x, amplitudeEnv: %d", value & 0x0f, amplitudeEnv));

                if (envA) {
                    amplitudeA = volumeLevel[amplitudeEnv];
                }

                if (envB) {
                    amplitudeB = volumeLevel[amplitudeEnv];
                }

                if (envC) {
                    amplitudeC = volumeLevel[amplitudeEnv];
                }
                break;
            case IOPortA:
            case IOPortB:
                regAY[addressLatch] = value & 0xff;
//                System.out.println(String.format("Write Reg: %d with %d",
//                    addressLatch, value));
        }
    }

//    private int ayClock;
    public void updateAY(int tstates) {

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

            if (++counterN >= periodN) {
                counterN = 0;
                // Changes to R6 take effect only when internal counter reaches 0
                periodN = regAY[NoisePeriod];
                if (periodN == 0) {
                    periodN = 1;
                }
                periodN <<= 1;

                // Code borrowed from MAME sources
                /* Is noise output going to change? */
                if (((rng + 1) & 0x02) != 0) { /* (bit0^bit1)? */
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
                if ((rng & 0x01) != 0) {
                    rng ^= 0x24000; /* This version is called the "Galois configuration". */
                }
                rng >>>= 1;
                // End of code borrowed from MAME sources
            }

            if (Continue && ++envelopeCounter >= envelopePeriod) {
                envelopeCounter = 0;

                if (Attack) {
                    amplitudeEnv++;
                } else {
                    amplitudeEnv--;
                }

                if ((amplitudeEnv & 0x10) != 0) {
                    if ((regAY[EnvelopeShapeCycle] & CONTINUE) == 0) {
                        amplitudeEnv = 0;
                        Continue = false;
                    } else {
                        if (Alternate) {
                            Attack = !Attack;
                        }

                        if (Hold) {
                            amplitudeEnv = Attack ? 15 : 0;
                            Continue = false;
                        } else {
                            amplitudeEnv = Attack ? 0 : 15;
                        }
                    }
                }

//                if (envA || envB || envC)
//                    System.out.println(String.format("amplitudeEnv: %d", amplitudeEnv));

                if (envA) {
                    amplitudeA = volumeLevel[amplitudeEnv];
                }

                if (envB) {
                    amplitudeB = volumeLevel[amplitudeEnv];
                }

                if (envC) {
                    amplitudeC = volumeLevel[amplitudeEnv];
                }
            }

            // Los valores oscilan entre 0..+VOL cuando el tono == 1 y
            // es cero cuando tono == 0. Es incorrecto hacerlo oscilar
            // entre -VOL...+VOL, ya que entonces no se reproducen efectos
            // como la voz digitalizada de Robocop.
            long diff = step - stepCounter;
            stepCounter += 1600000L;
            if (diff > 1600000L) {
                if ((toneA || disableToneA) && (toneN || disableNoiseA)) {
                    volumeA += amplitudeA;
                }

                if ((toneB || disableToneB) && (toneN || disableNoiseB)) {
                    volumeB += amplitudeB;
                }

                if ((toneC || disableToneC) && (toneN || disableNoiseC)) {
                    volumeC += amplitudeC;
                }

//                System.out.println(String.format("> stepCounter = %f, percent = %f, volumeA = %f, ampA = %f",
//                    stepCounter, percent, volumeA, amplitudeA));
            } else {
                double percent = (double)diff / 1600000.0;
                int lastA = 0, lastB = 0, lastC = 0;
                if ((toneA || disableToneA) && (toneN || disableNoiseA)) {
                    lastA = (int) (amplitudeA * percent);
                }

                if ((toneB || disableToneB) && (toneN || disableNoiseB)) {
                    lastB = (int) (amplitudeB * percent);
                }

                if ((toneC || disableToneC) && (toneN || disableNoiseC)) {
                    lastC = (int) (amplitudeC * percent);
                }

//                System.out.println(String.format("< stepCounter = %f, percent = %f, volumeA = %f, lastA = %f",
//                    stepCounter, percent, volumeA + lastA, lastA));

                stepCounter -= step;
                bufA[pbuf] = (int) ((volumeA + lastA) / stepRate);
                bufB[pbuf] = (int) ((volumeB + lastB) / stepRate);
                bufC[pbuf] = (int) ((volumeC + lastC) / stepRate);
                pbuf++;

                volumeA = lastA > 0 ? amplitudeA - lastA : 0;
                volumeB = lastB > 0 ? amplitudeB - lastB : 0;
                volumeC = lastC > 0 ? amplitudeC - lastC : 0;
            }
        }
    }

    public void endFrame() {
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
        Continue = Attack = false;
        startPlay();
    }

    public void startPlay() {
        audiotstates = pbuf = 0;
        stepCounter = 0;

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