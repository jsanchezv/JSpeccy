/*
 *	Audio.java
 *
 *  2009-2021 José Luis Sánchez jspeccy@gmail.com
 *
 *	This program is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	version 2 as published by the Free Software Foundation.
 */
package machine;

import configuration.AY8912Type;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Audio {
    private int samplingFrequency;
    private SourceDataLine line;
    private DataLine.Info infoDataLine;
    private AudioFormat fmt;
    // Buffer de sonido para 1 frame a 48 Khz estéreo, hay más espacio del necesario.
    private final byte[] buf = new byte[4096];
    private final int[] beeper = new int[1024];
    // Buffer de sonido para el AY
    private final int[] ayBufA = new int[1024];
    private final int[] ayBufB = new int[1024];
    private final int[] ayBufC = new int[1024];
    private int ptrBeeper, ptrBuf;
    private int level, lastLevel;
    private int audiotstates;
    private int samplesPerFrame, frameSize;
    private int soundMode, channels;
    private long timeRem, step;
    private MachineTypes spectrumModel;
    private boolean enabledAY;
    private final AY8912Type settings;
    private AY8912 ay;

    Audio(AY8912Type ayConf) {
       settings = ayConf;
       line = null;
    }

    synchronized void open(MachineTypes model, AY8912 ay8912, boolean hasAY, int freq) {
        samplingFrequency = freq;

        soundMode = settings.getSoundMode();
        if (soundMode < 0 || soundMode > 3)
            soundMode = 0;

        channels = soundMode > 0 ? 2 : 1;

        if (line == null) {
            try {
                fmt = new AudioFormat(samplingFrequency, 16, channels, true, false);
//                System.out.println(fmt);
                infoDataLine = new DataLine.Info(SourceDataLine.class, fmt);
                line = (SourceDataLine) AudioSystem.getLine(infoDataLine);
            } catch (LineUnavailableException excpt) {
                log.info("Unavailable Line: ", excpt);
            }

            enabledAY = hasAY;
            timeRem = 0;
            samplesPerFrame = samplingFrequency / 50;
            frameSize = samplesPerFrame * 2 * channels;
//            System.out.println(String.format("FREQ = %d, samples = %d, frameSize = %d",
//                    samplingFrequency, samplesPerFrame, frameSize));

            if (model != spectrumModel) {
                spectrumModel = model;
                ay8912.setSpectrumModel(spectrumModel);
            }

            step = (long)(((double)spectrumModel.tstatesFrame / (double)samplesPerFrame) * 100000.0);
            audiotstates = ptrBeeper = ptrBuf = 0;
            level = lastLevel = 0;

            ay8912.setMaxAmplitude(soundMode == 0 ? 10900 : 16350);
            switch (soundMode) {
                case 2: // Stereo ACB
                    ay8912.setBufferChannels(ayBufA, ayBufC, ayBufB);
                    break;
                case 3: // Stereo BAC
                    ay8912.setBufferChannels(ayBufB, ayBufA, ayBufC);
                    break;
                default: // Stereo ABC or Mono
                    ay8912.setBufferChannels(ayBufA, ayBufB, ayBufC);
            }

            if (enabledAY) {
                ay8912.setAudioFreq(samplingFrequency);
                ay8912.startPlay();
                ay = ay8912;
            }

            /*
             * Aunque en Linux "parece" que se reserva un frame, internamente el sistema (¿ALSA?)
             * crea el doble de espacio del solicitado. Eso provoca que el método available() se
             * comporte de manera errática, de modo que mejor lo evitamos.
             */
            try {
                if (System.getProperty("os.name").contains("Linux")) {
                    line.open(fmt, frameSize);
                } else {
                    line.open(fmt, frameSize * 2);
                }
                line.start();
            } catch (LineUnavailableException ex) {
                log.info("Unavailable Line: ", ex);
            }
        }
    }

    synchronized void close() {
        if (line != null) {
            line.flush();
            line.stop();
            line.close();
            line = null;
        }
    }

    synchronized void updateAudio(int tstates, int value) {
        tstates -= audiotstates;
        audiotstates += tstates;
        long time = tstates * 100000L;

        synchronized (beeper) {
            // Si timeRem != 0, es que hay que completar una muestra de audio
            if (timeRem > 0) {
                long diff = step - timeRem;
                if (time >= diff) {
                    // el tiempo transcurrido es suficiente para completarla
                    time -= diff;
                    if (value != 0) {
                        level += ((float) diff / (float) step) * value;
                    }
                    lastLevel += (level - lastLevel) >> 1;
                    beeper[ptrBeeper++] = lastLevel;
                } else {
                    // el tiempo transcurrido no basta para completar la muestra
                    timeRem += time;
                    if (value != 0) {
                        level += ((float) time / (float) step) * value;
                    }
                    return;
                }
            }

            // se añaden muestras completas mientras se pueda
            while (time >= step) {
                lastLevel += (value - lastLevel) >> 1;
                beeper[ptrBeeper++] = lastLevel;
                time -= step;
            }
        }

        // calculamos el nivel de sonido de la parte residual del tiempo restante
        // para el próximo cambio de estado.
        timeRem = time;
        if (value != 0 && time > 0) {
            level = (int)(((float) time / (float) step) * value);
        } else {
            level = 0;
        }
    }

    synchronized public void flush() {
        level = lastLevel = ptrBeeper = 0;
        timeRem = 0;
        if (line != null)
            line.flush();
    }

    synchronized public void sendAudioFrame() {
        if (line != null)
            line.write(buf, 0, frameSize);
    }

    synchronized public void endFrame() {

        if (ptrBeeper == 0)
            return;

//        System.out.println(ptrBeeper + ", " + ay.getSampleCount());

        if (soundMode == 0) {
            endFrameMono();
        }
        else {
            endFrameStereo();
        }

        ptrBuf = 0;

        if (enabledAY) {
            ay.endFrame();
        }

        audiotstates = ptrBeeper = 0;
    }

    private void endFrameMono() {

        // El código está repetido, lo que es correcto. Si no se hace así habría
        // que meter la comprobación de enabledAY dentro del bucle, lo que
        // haría que en lugar de comprobarse una vez, se comprobara ciento.
        if (enabledAY) {
            for (int idx = 0; idx < samplesPerFrame; idx++) {
                int sample = beeper[idx] + ayBufA[idx] + ayBufB[idx] + ayBufC[idx];
                buf[ptrBuf++] = (byte) sample;
                buf[ptrBuf++] = (byte)(sample >>> 8);
            }
        } else {
            for (int idx = 0; idx < samplesPerFrame; idx++) {
                buf[ptrBuf++] = (byte) beeper[idx];
                buf[ptrBuf++] = (byte) (beeper[idx] >>> 8);
            }
        }
    }

    private void endFrameStereo() {


        // El código está repetido, lo que es correcto. Si no se hace así habría
        // que meter la comprobación de enabledAY dentro del bucle, lo que
        // haría que en lugar de comprobarse una vez, se comprobara ciento.
        if (enabledAY) {
            int sampleL, sampleR, center, side;
            for (int idx = 0; idx < samplesPerFrame; idx++) {
                center = (int)(ayBufB[idx] * 0.7);
                side = (int)(ayBufC[idx] * 0.3);
                sampleL = beeper[idx] + ayBufA[idx] + center + side;
                side = (int)(ayBufA[idx] * 0.3);
                sampleR = beeper[idx] + side + center + ayBufC[idx];
                buf[ptrBuf++] = (byte) sampleL;
                buf[ptrBuf++] = (byte)(sampleL >>> 8);
                buf[ptrBuf++] = (byte) sampleR;
                buf[ptrBuf++] = (byte)(sampleR >>> 8);
            }
        } else {
            byte lsb, msb;
            for (int idx = 0; idx < samplesPerFrame; idx++) {
                lsb = (byte) beeper[idx];
                msb = (byte) (beeper[idx] >>> 8);
                buf[ptrBuf++] = lsb;
                buf[ptrBuf++] = msb;
                buf[ptrBuf++] = lsb;
                buf[ptrBuf++] = msb;
            }
        }
    }

    public void reset() {
        audiotstates = 0;
        level = lastLevel = ptrBeeper = 0;
        timeRem = 0;
        Arrays.fill(buf, (byte)0);
        Arrays.fill(beeper, 0);
    }
}
