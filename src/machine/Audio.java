/*
 *	Audio.java
 *
 *	Copyright 2007-2008 Jan Bobrowski <jb@wizard.ae.krakow.pl>
 *
 *	This program is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	version 2 as published by the Free Software Foundation.
 */
package machine;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

class Audio {
    static final int FREQ = 22050;
    SourceDataLine line;
    DataLine.Info infoDataLine;
    byte buf[] = new byte[4096];
    int bufp;
    int level;
    public int audiotstates;
    private float timeRem, spf;

    Audio() {
        try {
            AudioFormat fmt = new AudioFormat(FREQ, 16, 1, true, false);
            System.out.println(fmt);
            infoDataLine = new DataLine.Info(SourceDataLine.class, fmt);
            SourceDataLine sdl = (SourceDataLine) AudioSystem.getLine(infoDataLine);
            sdl.open(fmt, buf.length * 2);
            sdl.start();
            line = sdl;
//            System.out.println(String.format("maxBufferSize: %d minBufferSize: %d",
//                infoDataLine.getMaxBufferSize(), infoDataLine.getMinBufferSize()));
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    
    synchronized void open(int hz) {
        timeRem = (float) 0.0;
        spf = (float) 69888 / ((float) FREQ / (float) 50);
        level = 0;
        audiotstates = 0;
    }

    synchronized void updateAudio(int tstates, int value) {
        tstates = tstates - audiotstates;
        audiotstates += tstates;
        float time = tstates;

        if (time + timeRem >= spf) {
            level += ((spf - timeRem) / spf) * value;
            time -= spf - timeRem;
            buf[bufp++] = (byte) level;
            buf[bufp++] = (byte) (level >> 8);
            if (bufp == buf.length) {
                bufp = flush(bufp);
            }
        } else {
            timeRem += time;
            level += (time / spf) * value;
            return;
        }

        byte lsb = (byte) value;
        byte msb = (byte) (value >> 8);
        while (time > spf) {
            buf[bufp++] = lsb;
            buf[bufp++] = msb;
            if (bufp == buf.length) {
                bufp = flush(bufp);
            }
            time -= spf;
        }

        // calculamos el nivel de sonido de la parte residual de la muestra
        // para el próximo cambio de estado
        level = (int) (value * (time / spf));
        timeRem = time;
//        System.out.println(String.format("tstates: %d speaker: %04x timeRem: %f level: %d",
//            tstates, (short)value, timeRem, (short)level));
    }

    synchronized int flush(int ptr) {
        if (line != null) {
            line.write(buf, 0, ptr);
        }
        return 0;
    }

    synchronized void close() {
        if (line != null) {
            line = null;
            line.stop();
            line.close();
        }
    }
}
