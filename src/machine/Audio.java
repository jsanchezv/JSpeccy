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


class Audio
{
	byte buf[] = new byte[4096];
	int bufp;

	long div;
	int idiv, mul;
	int acct;
	int accv0, accv1, level;
    public int audiotstates;
    private int out1, out2;
    private double timeRem, spf;

	void open(int hz)
	{
		div = hz;
		acct = hz;
		idiv = (1<<30) / hz;
        timeRem = out1 = out2 = 0;
        spf = ((double)hz / (double)FREQ);
	}

	void step(int time, int volume)
	{
		time = acct - mul * time;

		if (time < 0) {
			int p = bufp, v = accv0;
			buf[p++] = (byte)v;
			buf[p++] = (byte)(v>>8);
			v = accv1; accv1 = level;
loop:
			for(;;) {
				if(p == buf.length)
					p = flush(p);
				if((time += div)>=0)
					break;
				buf[p++] = (byte)v;
				buf[p++] = (byte)(v>>8);
				v = level;
				if(p == buf.length)
					continue;
				if((time += div)>=0)
					break;
				byte l=(byte)v;
				byte h=(byte)(v>>8);
				for(;;) {
					buf[p++] = l;
					buf[p++] = h;
					if(p == buf.length)
						continue loop;
					if((time += div)>=0)
						break loop;
				}
			}
			accv0 = v;
			bufp = p;
		}

		// 0 <= t < div

		acct = time;
		int newVolume = level + volume;
		if((short)newVolume != newVolume) {
			newVolume = (short)(newVolume>>31 ^ 0x7FFF);
			volume = newVolume - level;
		}
		level = newVolume;

		int x = idiv*time >> 22;
		int xx = x*x >> 9;
		accv0 += volume*xx >> 8;
		xx = 128 - xx + x;
		accv1 += volume*xx >> 8;
	}

     synchronized void updateAudio(int tstates, int value) {

        tstates = tstates - audiotstates;
        audiotstates += tstates;
        double time = tstates;
        time += timeRem;

//        System.out.println(String.format("tstates: %d audiotstates: %d nSamples: %d Rem: %d",
//                tstates, audiotstates, nSamples, tstatesRem));

        while( time > spf ) {
            if( bufp == buf.length )
                bufp = flush(bufp);
            if( (value & 0x10) != 0 ) {
                level = (0x4000 + out1 + out2) / 3;
            } else {
                level = (out1 + out2) / 3;
            }
            buf[bufp++] = (byte) level;
            buf[bufp++] = (byte) (level >>> 8);
            out2 = out1;
            out1 = level;
            time -= spf;
        }
        timeRem = time;

//        if( (value & 0x10) != 0 )
//            level = (int)(tstatesRem * rel);
//        else
//            level = 0;
    }


	static final int FREQ = 48000;
	SourceDataLine line;
    DataLine.Info infoDataLine;

	Audio() {
		try {
			mul = FREQ;
			AudioFormat fmt
			  = new AudioFormat(FREQ, 16, 1, true, false);
			System.out.println(fmt);
            infoDataLine = new DataLine.Info(SourceDataLine.class, fmt);
			SourceDataLine l = (SourceDataLine)AudioSystem.getLine(infoDataLine);
			l.open(fmt, 4096);
			l.start();
			line = l;
//            System.out.println(String.format("maxBufferSize: %d minBufferSize: %d",
//                infoDataLine.getMaxBufferSize(), infoDataLine.getMinBufferSize()));
            audiotstates = 0;
            timeRem = 0.0;
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	synchronized int flush(int p) {
		SourceDataLine l = line;
		if(l!=null)
			l.write(buf, 0, p);
		return 0;
	}

	synchronized void close() {
		SourceDataLine l = line;
		if(l!=null) {
			line = null;
			l.stop();
			l.close();
		}
	}
}
