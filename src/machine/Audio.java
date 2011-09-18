/*
 *	Audio.java
 *
 *  2009-2010 José Luis Sánchez
 *
 *  with parts from:
 *	Copyright 2007-2008 Jan Bobrowski <jb@wizard.ae.krakow.pl>
 *
 *	This program is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	version 2 as published by the Free Software Foundation.
 */
package machine;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import configuration.AY8912Type;

class Audio {
    static final int FREQ = 44100;
    private SourceDataLine line;
    private DataLine.Info infoDataLine;
    private AudioFormat fmt;
    private SourceDataLine sdl;
    // Buffer de sonido para el frame actual, hay más espacio del necesario.
    private final byte[] buf = new byte[4096];
    private final int[] beeper = new int[2048];
    // Un frame completo lleno de ceros para enviarlo como aperitivo.
    private final byte[] nullbuf = new byte[3840];
    // Buffer de sonido para el AY
    private final int[] ayBufA = new int[2048];
    private final int[] ayBufB = new int[2048];
    private final int[] ayBufC = new int[2048];
    private int bufp;
    private int level;
    private int audiotstates;
    private int soundMode;
    private float timeRem, spf;
//    private AY8912 ay;
    private MachineTypes spectrumModel;
    private boolean enabledAY;
    private AY8912Type settings;

    Audio(AY8912Type ayConf) {
       settings = ayConf;
       line = null;
       java.util.Arrays.fill(nullbuf, (byte) 0);
    }
    
    synchronized void open(MachineTypes model, AY8912 ay8912, boolean hasAY) {
        if (line == null) {
            try {
                soundMode = settings.getSoundMode();
                if (soundMode < 0 || soundMode > 3)
                    soundMode = 0;
//                System.out.println("Selected soundMode " +  soundMode);

                int channels =  soundMode > 0 ? 2 : 1;
                fmt = new AudioFormat(FREQ, 16, channels, true, false);
//                System.out.println(fmt);
                infoDataLine = new DataLine.Info(SourceDataLine.class, fmt);
                sdl = (SourceDataLine) AudioSystem.getLine(infoDataLine);
            } catch (Exception excpt) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, excpt);
            }
            spectrumModel = model;
            enabledAY = hasAY;
            timeRem = (float) 0.0;
            spf = (float) spectrumModel.getTstatesFrame() / (FREQ / 50);
            audiotstates = bufp = level = 0;
            if (soundMode > 0) {
                ay8912.setMaxAmplitude(21500); // 11000
            } else {
                ay8912.setMaxAmplitude(16300); // 7000
            }

            try {
                sdl.open(fmt, nullbuf.length * 2); // Espacio para dos frames
                // No se llama al método start hasta tener el primer buffer
//                sdl.start();
                line = sdl;
            } catch (LineUnavailableException ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }

            ay8912.setBufferChannels(ayBufA, ayBufB, ayBufC);
            ay8912.setAudioFreq(FREQ);
            ay8912.setSpectrumModel(spectrumModel);
            ay8912.reset();
        }
    }

    synchronized void close() {
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
    }

    synchronized void updateAudio(int tstates, int value) {
        tstates = tstates - audiotstates;
        audiotstates += tstates;
        float time = tstates;
//        System.out.println(String.format("updateAudio: value = %d", value));

        synchronized (buf) {
            if (time + timeRem > spf) {
                level += ((spf - timeRem) / spf) * value;
                time -= spf - timeRem;
                beeper[bufp++] = level;
            } else {
                timeRem += time;
                level += (time / spf) * value;
                return;
            }

            while (time > spf) {
                beeper[bufp++] = value;
                time -= spf;
            }
        }

        // calculamos el nivel de sonido de la parte residual de la muestra
        // para el próximo cambio de estado
        level = (int) (value * (time / spf));
        timeRem = time;
//        System.out.println(String.format("tstates: %d speaker: %04x timeRem: %f level: %d",
//            tstates, (short)value, timeRem, (short)level));
    }

    private synchronized void flushBuffer(int len) {
        if (line != null) {
            synchronized (buf) {
                // Si al ir a escribir el frame la línea no está funcionando,
                // iniciar el envío y estrenar el sonido con un magnífico frame
                // lleno de ceros.
                if (!line.isRunning()) {
                    line.start();
                    line.write(nullbuf, 0, nullbuf.length); // y una magdalena
                }
                line.write(buf, 0, len);
            }
        }
    }

    public void flush() {
        bufp = level = 0;
        timeRem = 0.0f;
        if (line != null)
            line.flush();
    }

    public void endFrame() {

        if (bufp == 0)
            return;

        int ptr = 0;

        switch (soundMode) {
            case 1: // Stereo ABC
//                System.out.println("ABC");
                ptr = endFrameStereoABC();
                break;
            case 2: // Stereo ACB
//                System.out.println("ACB");
                ptr = endFrameStereoACB();
                break;
            case 3:
//                System.out.println("BAC");
                ptr = endFrameStereoBAC();
                break;
            default:
//                System.out.println("Mono");
                ptr = endFrameMono();
        }

        flushBuffer(ptr);
        bufp = 0;
        audiotstates -= spectrumModel.tstatesFrame;
    }

//    private void filterBeeper() {
//        int tmp = beeper[1];
//        for (int sample = 1; sample < bufp; sample++) {
//            beeper[sample] = (beeper[sample - 1] + tmp) / 2;
//            tmp = beeper[sample + 1];
//        }
//    }

    private int endFrameMono() {

        int ptr = 0;
//        filterBeeper();
//        int ayCnt = ay.getSampleCount();
//        System.out.println(String.format("BeeperChg %d", beeperChg));

        // El código está repetido, lo que es correcto. Si no se hace así habría
        // que meter la comprobación de enabledAY dentro del bucle, lo que
        // haría que en lugar de comprobarse una vez, se comprobara ciento.
        if (enabledAY) {
            int sample = 0;
            for (int idx = 0; idx < bufp; idx++) {
                sample = -32760 + (beeper[idx] + ayBufA[idx] + ayBufB[idx] + ayBufC[idx]);
                buf[ptr++] = (byte) sample;
                buf[ptr++] = (byte)(sample >>> 8);
            }
            // Si el frame se ha quedado corto de una punta, rellenarlo
            // Copiamos el último sample del beeper y el último sample actualizado del AY
//            if (ptr == 1918) {
//                sample = -32760 + (beeper[958] + ayBufA[959] + ayBufB[959] + ayBufC[959]);
//                buf[ptr++] = (byte) sample;
//                buf[ptr++] = (byte)(sample >>> 8);
//            }
        } else {
            for (int idx = 0; idx < bufp; idx++) {
                beeper[idx] = -32760 + beeper[idx];
                buf[ptr++] = (byte) beeper[idx];
                buf[ptr++] = (byte) (beeper[idx] >>> 8);
            }
            // Si el frame se ha quedado corto de una punta, rellenarlo
            // Copiamos el último sample del beeper
//            if (ptr == 1918) {
//                buf[ptr++] = (byte) beeper[958];
//                buf[ptr++] = (byte) (beeper[958] >>> 8);
//            }
        }
        return ptr;
    }

    private int endFrameStereoABC() {

        int ptr = 0;
//        int ayCnt = ay.getSampleCount();
//        System.out.println(String.format("BeeperChg %d", beeperChg));

        // El código está repetido, lo que es correcto. Si no se hace así habría
        // que meter la comprobación de enabledAY dentro del bucle, lo que
        // haría que en lugar de comprobarse una vez, se comprobara ciento.
        byte lsb, msb;
        if (enabledAY) {
            int sampleL = 0, sampleR = 0, center = 0;
            for (int idx = 0; idx < bufp; idx++) {
                center = (int)(ayBufB[idx] * 0.7);
                sampleL = -32760 +(beeper[idx] + ayBufA[idx] + center + ayBufC[idx] / 3);
                sampleR = -32760 + (beeper[idx] + ayBufA[idx] / 3 + center + ayBufC[idx]);
                buf[ptr++] = (byte) sampleL;
                buf[ptr++] = (byte)(sampleL >>> 8);
                buf[ptr++] = (byte) sampleR;
                buf[ptr++] = (byte)(sampleR >>> 8);
            }
            // Si el frame se ha quedado corto de una punta, rellenarlo
            // Copiamos el último sample del beeper y el último sample actualizado del AY
//            if (ptr == 3836) {
//                center = (int)(ayBufB[959] * 0.7);
//                sampleL = beeper[958] + ayBufA[959] + center + ayBufC[959] / 3;
//                sampleR = beeper[958] + ayBufA[959] / 3 + center + ayBufC[959];
//                buf[ptr++] = (byte) sampleL;
//                buf[ptr++] = (byte)(sampleL >>> 8);
//                buf[ptr++] = (byte) sampleR;
//                buf[ptr++] = (byte)(sampleR >>> 8);
//            }
        } else {
            for (int idx = 0; idx < bufp; idx++) {
                beeper[idx] = -32760 + beeper[idx];
                lsb = (byte) beeper[idx];
                msb = (byte) (beeper[idx] >>> 8);
                buf[ptr++] = lsb;
                buf[ptr++] = msb;
                buf[ptr++] = lsb;
                buf[ptr++] = msb;
            }
            // Si el frame se ha quedado corto de una punta, rellenarlo
            // Copiamos el último sample del beeper
//            if (ptr == 3836) {
//                lsb = (byte) beeper[958];
//                msb = (byte) (beeper[958] >>> 8);
//                buf[ptr++] = lsb;
//                buf[ptr++] = msb;
//                buf[ptr++] = lsb;
//                buf[ptr++] = msb;
//            }
        }
        return ptr;
    }

    private int endFrameStereoACB() {

        int ptr = 0;
//        int ayCnt = ay.getSampleCount();
//        System.out.println(String.format("BeeperChg %d", beeperChg));

        // El código está repetido, lo que es correcto. Si no se hace así habría
        // que meter la comprobación de enabledAY dentro del bucle, lo que
        // haría que en lugar de comprobarse una vez, se comprobara ciento.
        byte lsb, msb;
        if (enabledAY) {
            int sampleL = 0, sampleR = 0, center = 0;
            for (int idx = 0; idx < bufp; idx++) {
                center = (int)(ayBufC[idx] * 0.7);
                sampleL = -32760 + (beeper[idx] + ayBufA[idx] + center + ayBufB[idx] / 3);
                sampleR = -32760 + (beeper[idx] + ayBufA[idx] / 3 + center + ayBufB[idx]);
                buf[ptr++] = (byte) sampleL;
                buf[ptr++] = (byte)(sampleL >>> 8);
                buf[ptr++] = (byte) sampleR;
                buf[ptr++] = (byte)(sampleR >>> 8);
            }
            // Si el frame se ha quedado corto de una punta, rellenarlo
            // Copiamos el último sample del beeper y el último sample actualizado del AY
//            if (ptr == 3836) {
//                center = (int)(ayBufC[959] * 0.7);
//                sampleL = beeper[958] + ayBufA[959] + center + ayBufB[959] / 3;
//                sampleR = beeper[958] + ayBufA[959] / 3 + center + ayBufB[959];
//                buf[ptr++] = (byte) sampleL;
//                buf[ptr++] = (byte)(sampleL >>> 8);
//                buf[ptr++] = (byte) sampleR;
//                buf[ptr++] = (byte)(sampleR >>> 8);
//            }
        } else {
            for (int idx = 0; idx < bufp; idx++) {
                beeper[idx] = -32760 + beeper[idx];
                lsb = (byte) beeper[idx];
                msb = (byte) (beeper[idx] >>> 8);
                buf[ptr++] = lsb;
                buf[ptr++] = msb;
                buf[ptr++] = lsb;
                buf[ptr++] = msb;
            }
            // Si el frame se ha quedado corto de una punta, rellenarlo
            // Copiamos el último sample del beeper
//            if (ptr == 3836) {
//                lsb = (byte) beeper[958];
//                msb = (byte) (beeper[958] >>> 8);
//                buf[ptr++] = lsb;
//                buf[ptr++] = msb;
//                buf[ptr++] = lsb;
//                buf[ptr++] = msb;
//            }
        }
        return ptr;
    }

    private int endFrameStereoBAC() {

        int ptr = 0;
//        int ayCnt = ay.getSampleCount();
//        System.out.println(String.format("BeeperChg %d", beeperChg));

        // El código está repetido, lo que es correcto. Si no se hace así habría
        // que meter la comprobación de enabledAY dentro del bucle, lo que
        // haría que en lugar de comprobarse una vez, se comprobara ciento.
        byte lsb, msb;
        if (enabledAY) {
            int sampleL = 0, sampleR = 0, center = 0;
            for (int idx = 0; idx < bufp; idx++) {
                center = (int)(ayBufA[idx] * 0.7);
                sampleL = -32760 + (beeper[idx] + ayBufB[idx] + center + ayBufC[idx] / 3);
                sampleR = -32760 + (beeper[idx] + ayBufB[idx] / 3 + center + ayBufC[idx]);
                buf[ptr++] = (byte) sampleL;
                buf[ptr++] = (byte)(sampleL >>> 8);
                buf[ptr++] = (byte) sampleR;
                buf[ptr++] = (byte)(sampleR >>> 8);
            }
            // Si el frame se ha quedado corto de una punta, rellenarlo
            // Copiamos el último sample del beeper y el último sample actualizado del AY
//            if (ptr == 3836) {
//                center = (int)(ayBufA[959] * 0.7);
//                sampleL = beeper[958] + ayBufB[959] + center + ayBufC[959] / 3;
//                sampleR = beeper[958] + ayBufB[959] / 3 + center + ayBufC[959];
//                buf[ptr++] = (byte) sampleL;
//                buf[ptr++] = (byte)(sampleL >>> 8);
//                buf[ptr++] = (byte) sampleR;
//                buf[ptr++] = (byte)(sampleR >>> 8);
//            }
        } else {
            for (int idx = 0; idx < bufp; idx++) {
                beeper[idx] = -32760 + beeper[idx];
                lsb = (byte) beeper[idx];
                msb = (byte) (beeper[idx] >>> 8);
                buf[ptr++] = lsb;
                buf[ptr++] = msb;
                buf[ptr++] = lsb;
                buf[ptr++] = msb;
            }
            // Si el frame se ha quedado corto de una punta, rellenarlo
            // Copiamos el último sample del beeper
//            if (ptr == 3836) {
//                lsb = (byte) beeper[958];
//                msb = (byte) (beeper[958] >>> 8);
//                buf[ptr++] = lsb;
//                buf[ptr++] = msb;
//                buf[ptr++] = lsb;
//                buf[ptr++] = msb;
//            }
        }
        return ptr;
    }

    public void reset() {
        audiotstates = 0;
        bufp = 0;
        java.util.Arrays.fill(ayBufA, (byte)0);
        java.util.Arrays.fill(ayBufB, (byte)0);
        java.util.Arrays.fill(ayBufC, (byte)0);
    }
}
