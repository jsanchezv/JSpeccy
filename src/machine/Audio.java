/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 *
 * @author jsanchez
 */
public class Audio {

    private byte buf[] = new byte[480];
    public int bufp;
    private int fromTstates, tstatesRem, oldFrame;
    public int audiotstates;
    static final int FREQ = 48000;
    SourceDataLine line;

    Audio() {
        try {
//            mul = FREQ;
            AudioFormat fmt = new AudioFormat(FREQ, 16, 1, true, false);
            System.out.println(fmt);
            SourceDataLine lineOut = (SourceDataLine) AudioSystem.getLine(
                    new DataLine.Info(SourceDataLine.class, fmt));
            lineOut.open(fmt, 4096);
            lineOut.start();
            line = lineOut;
            bufp = 0;
            fromTstates = tstatesRem = oldFrame = audiotstates = 0;
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    synchronized void updateAudio(int tstates, int value) {
        byte POS_SIGNAL_MSB = (byte)0x40;
        byte POS_SIGNAL_LSB = (byte)0x00;
        byte NEG_SIGNAL_MSB = (byte)0xC0;
        byte NEG_SIGNAL_LSB = (byte)0x00;

        int time = tstates - audiotstates;
        audiotstates += time;
        //if( time / 72 < 5 )
            time += tstatesRem;
        int nSamples = time / 73;
        tstatesRem = time % 73;
//        System.out.println(String.format("tstates: %d audiotstates: %d nSamples: %d Rem: %d",
//                tstates, audiotstates, nSamples, tstatesRem));
        if( bufp == buf.length )
            bufp = flush(bufp);
        for( int count = 0; count < nSamples; count++ ) {
            if( (value & 0x10) != 0 ) {
                buf[bufp++] = POS_SIGNAL_LSB;
                buf[bufp++] = POS_SIGNAL_MSB;
            } else {
                buf[bufp++] = 0; //NEG_SIGNAL_LSB;
                buf[bufp++] = 0; //NEG_SIGNAL_MSB;
            }
            if( bufp == buf.length )
                bufp = flush(bufp);
        }
    }

    synchronized void generateSample(int nFrame, int tstates, int value) {
        byte POS_SIGNAL_MSB = (byte)0x7f;
        byte POS_SIGNAL_LSB = (byte)0xf8;
        byte NEG_SIGNAL_MSB = (byte)0x80;
        byte NEG_SIGNAL_LSB = (byte)0x08;

//        if( (value & 0x10) != 0 ) {
//            fromTstates = tstates;
//            return;
//        }

        if( nFrame - oldFrame > 1 ) {
            fromTstates = tstates;
            oldFrame = nFrame;
            tstatesRem = 0;
            if( bufp > 0 )
                bufp = flush(bufp);
            return;
        }
        
        int longSample = (tstates < fromTstates ? (tstates + (69888 - fromTstates)) :
                                                (tstates - fromTstates)) + tstatesRem;

//        if( tstatesRem > 0 ) {
////            int sample;
//            int mix = 72 - tstatesRem;
//            if( mix > tstatesRem ) {
//                if( (value & 0x10) != 0 ) {
////                    sample = mix * 455 + tstatesRem * -455;
//                    buf[bufp++] = POS_SIGNAL_LSB;
//                    buf[bufp++] = POS_SIGNAL_MSB;
//                } else {
////                    sample = mix * -455 + tstatesRem * 455;
//                    buf[bufp++] = NEG_SIGNAL_LSB;
//                    buf[bufp++] = NEG_SIGNAL_MSB;
//                }
//            } else {
//                if( (value & 0x10) != 0 ) {
////                    sample = mix * -455 + tstatesRem * 455;
//                    buf[bufp++] = NEG_SIGNAL_LSB;
//                    buf[bufp++] = NEG_SIGNAL_MSB;
//                } else {
////                    sample = mix * 455 + tstatesRem * -455;
//                    buf[bufp++] = POS_SIGNAL_LSB;
//                    buf[bufp++] = POS_SIGNAL_MSB;
//                }
//            }
////            buf[bufp++] = (byte) sample;
////            buf[bufp++] = (byte) (sample >>> 8);
//            if( longSample > mix)
//                longSample -= mix;
//            else
//                longSample += tstatesRem;
//        }
        int nSamples = longSample / 72;
        tstatesRem = longSample % 72;
//        if( tstatesRem > 35 ) {
//            nSamples++;
//            //tstatesRem = 0;
//        }

//        nSamples <<= 1;
        for( int count = 0; count < nSamples; count++ ) {
            if( (value & 0x10) != 0 ) {
                buf[bufp++] = POS_SIGNAL_LSB;
                buf[bufp++] = POS_SIGNAL_MSB;
            } else {
                buf[bufp++] = NEG_SIGNAL_LSB;
                buf[bufp++] = NEG_SIGNAL_MSB;
            }
            if( bufp == buf.length )
                bufp = flush(bufp);
        }
        //bufp = flush(bufp);
//        System.out.println(String.format("longSample: %d\tnSamples: %d\tRemain: %d",
//                longSample, nSamples, tstatesRem));
        fromTstates = tstates;
        oldFrame = nFrame;
    }

    synchronized int flush(int p) {
//        while (bufp < 1920 )
//            buf[bufp++] = 0;
//        System.out.println(String.format("Samples: %d", bufp/2));
        SourceDataLine lineOut = line;
        if (lineOut != null) {
            if (lineOut.write(buf, 0, p) != p)
                System.out.println("Se escribieron menos bytes de los debidos!");
        }
        return 0;
    }

    synchronized void fill(int value) {
        byte POS_SIGNAL_MSB = (byte)0x40;
        byte POS_SIGNAL_LSB = (byte)0x00;

        while (bufp < 1920 ) {
            if( (value & 0x10) != 0 ) {
                buf[bufp++] = POS_SIGNAL_LSB;
                buf[bufp++] = POS_SIGNAL_MSB;
            } else {
                buf[bufp++] = 0;
                buf[bufp++] = 0;
            }
        }

    }

    synchronized void close() {
        SourceDataLine lineOut = line;
        if (lineOut != null) {
            line = null;
            lineOut.stop();
            lineOut.close();
        }
    }
}
