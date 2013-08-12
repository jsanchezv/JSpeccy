/*
 *	Audio.java
 *
 *  2009-2013 José Luis Sánchez zx81@ono.com
 *
 *	This program is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	version 2 as published by the Free Software Foundation.
 */
package machine;

import configuration.AY8912Type;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.*;

class Audio {
    private int samplingFrequency;
    private SourceDataLine line;
    private DataLine.Info infoDataLine;
    private AudioFormat fmt;
    // Buffer de sonido para el frame actual, hay más espacio del necesario.
    private final byte[] buf = new byte[4096];
    private final int[] beeper = new int[1024];
    // Buffer de sonido para el AY
    private final int[] ayBufA = new int[1024];
    private final int[] ayBufB = new int[1024];
    private final int[] ayBufC = new int[1024];
    private int bufp;
    private int level;
    private int audiotstates;
    private int samplesPerFrame, frameSize, bufferSize;
    private int soundMode;
    private long timeRem, step;
    private MachineTypes spectrumModel;
    private boolean enabledAY;
    private AY8912Type settings;
    private AY8912 ay;

    Audio(AY8912Type ayConf) {
       settings = ayConf;
       line = null;
       samplingFrequency = 32000;
    }
    
    synchronized void open(MachineTypes model, AY8912 ay8912, boolean hasAY, int freq) {
        samplingFrequency = freq;
        
        soundMode = settings.getSoundMode();
        if (soundMode < 0 || soundMode > 3)
            soundMode = 0;
//              System.out.println("Selected soundMode " +  soundMode);

        int channels =  soundMode > 0 ? 2 : 1;

        if (line == null) {
            try {
                fmt = new AudioFormat(samplingFrequency, 16, channels, true, false);
//                System.out.println(fmt);
                infoDataLine = new DataLine.Info(SourceDataLine.class, fmt);
                line = (SourceDataLine) AudioSystem.getLine(infoDataLine);
            } catch (Exception excpt) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, excpt);
            }

            enabledAY = hasAY;
            timeRem = 0;
            samplesPerFrame = samplingFrequency / 50;
            frameSize = samplesPerFrame * 2 * channels;
//            System.out.println("framesize: " + frameSize);
            if (model != spectrumModel) {
                spectrumModel = model;
                ay8912.setSpectrumModel(spectrumModel);
            }

            step = (long)(((double)spectrumModel.tstatesFrame / (double)samplesPerFrame) * 100000.0);
            audiotstates = bufp = 0;
            level = 0;
            
            ay8912.setMaxAmplitude(soundMode == 0 ? 8192 : 12288);
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
            ay8912.setAudioFreq(samplingFrequency);
            ay8912.startPlay();
            ay = ay8912;

            try {
                line.open(fmt);
                bufferSize = line.getBufferSize();
                line.start();
            } catch (LineUnavailableException ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
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
        tstates = tstates - audiotstates;
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
                    beeper[bufp++] = (int) level;
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
                beeper[bufp++] = value;
                time -= step;
            }
        }

        // calculamos el nivel de sonido de la parte residual del tiempo restante
        // para el próximo cambio de estado.
        timeRem = time;
        if (value != 0) {
            level = (int)(((float) time / (float) step) * value);
        } else {
            level = 0;
        }
    }

    private void flushBuffer(int len) {

        if (line != null) {

            synchronized (buf) {
                line.write(buf, 0, len);
                // Deberían haber siempre 2 frames de sonido adelantados. Si no los hay
                // rellena en previsión de que falten. Es más molesto el efecto del buffer
                // vacío que la repetición de un frame.
                if (available() > bufferSize - (frameSize << 1)) {
                    System.out.println("UNDERRUN!");
                    line.write(buf, 0, len);
                    line.write(buf, 0, len);
                    bufp = 0;
                }
            }
        }
    }

    synchronized public void flush() {
        level = bufp = 0;
        timeRem = 0;
        if (line != null)
            line.flush();
    }

    synchronized public void endFrame() {

        if (bufp == 0)
            return;

        int ptr = 0;
        
        if (soundMode == 0) {
            ptr = endFrameMono(bufp);
        }
        else {
            ptr = endFrameStereo(bufp);
        }

        flushBuffer(ptr);
        
        if (enabledAY) {
            ay.endFrame();
        }
        
        bufp = 0;

        audiotstates -= spectrumModel.tstatesFrame;
    }

    private int endFrameMono(int nSamples) {

        int ptr = 0;

        // El código está repetido, lo que es correcto. Si no se hace así habría
        // que meter la comprobación de enabledAY dentro del bucle, lo que
        // haría que en lugar de comprobarse una vez, se comprobara ciento.
        if (enabledAY) {
            for (int idx = 0; idx < nSamples; idx++) {
                int sample = beeper[idx] + ayBufA[idx] + ayBufB[idx] + ayBufC[idx];
                buf[ptr++] = (byte) sample;
                buf[ptr++] = (byte)(sample >>> 8);
            }
        } else {
            for (int idx = 0; idx < nSamples; idx++) {
                buf[ptr++] = (byte) beeper[idx];
                buf[ptr++] = (byte) (beeper[idx] >>> 8);
            }
        }
        return ptr;
    }

    private int endFrameStereo(int nSamples) {

        int ptr = 0;

        // El código está repetido, lo que es correcto. Si no se hace así habría
        // que meter la comprobación de enabledAY dentro del bucle, lo que
        // haría que en lugar de comprobarse una vez, se comprobara ciento.
        if (enabledAY) {
            int sampleL, sampleR, center, side;
            for (int idx = 0; idx < nSamples; idx++) {
                center = (int)(ayBufB[idx] * 0.7);
                side = (int)(ayBufC[idx] * 0.3);
                sampleL = beeper[idx] + ayBufA[idx] + center + side;
                side = (int)(ayBufA[idx] * 0.3);
                sampleR = beeper[idx] + side + center + ayBufC[idx];
                buf[ptr++] = (byte) sampleL;
                buf[ptr++] = (byte)(sampleL >>> 8);
                buf[ptr++] = (byte) sampleR;
                buf[ptr++] = (byte)(sampleR >>> 8);
            }
        } else {
            byte lsb, msb;
            for (int idx = 0; idx < nSamples; idx++) {
                lsb = (byte) beeper[idx];
                msb = (byte) (beeper[idx] >>> 8);
                buf[ptr++] = lsb;
                buf[ptr++] = msb;
                buf[ptr++] = lsb;
                buf[ptr++] = msb;
            }
        }
        return ptr;
    }

    public void reset() {
        audiotstates = 0;
        level = bufp = 0;
        timeRem = 0;
        java.util.Arrays.fill(beeper, 0);
    }
    
    // El método available() de SourceDataLine es un caos. En Linux devuelve bufferSize + el
    // número de bytes que tiene disponibles. En Windows y OSX, de manera más lógica,
    // devuelve el número de bytes que le quedan libres en el buffer.
    // Huele sospechosamente a bug en la parte específica de Linux (con PulseAudio).
    public int available() {
        int available = line.available();
        if (available > bufferSize)
            available -= bufferSize;
        
//        System.out.println(available);
        return available;
    }
}
