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
public class AY8912 {
    private static final int FineToneChnA = 0;
    private static final int CoarseToneChnA = 1;
    private static final int FineToneChnB = 2;
    private static final int CoarseToneChnB = 3;
    private static final int FineToneChnC = 4;
    private static final int CoarseToneChnC = 5;
    private static final int NoisePeriod = 6;
    private static final int Mixer = 7;
    private static final int AmplitudeChnA = 8;
    private static final int AmplitudeChnB = 9;
    private static final int AmplitudeChnC = 10;
    private static final int FineToneEnvelope = 11;
    private static final int CoarseToneEnvelope = 12;
    private static final int EnvelopeShapeCycle = 13;
    private static final int IOPortA = 14;
    private static final int IOPortB = 15;
    
    // Clock frequency for the AY
    private int clockFreq;
    // AY clock cycle
    private int ayCycle;
    // Channel periods
    private int toneChnA, toneChnB, toneChnC;
    // Channel period counters
    private int counterChnA, counterChnB, counterChnC;
    // Channel amplitudes
    private int ampChnA, ampChnB, ampChnC;
    // Noise period & counter
    private int noisePeriod, noiseCounter;
    /* Envelope shape cycles:
       C AtAlH
       0 0 x x  \_______
       0 1 x x  /|______
       1 0 0 0  \|\|\|\|
       1 0 0 1  \_______
       1 0 1 0  \/\/\/\/
       1 0 1 1  \|______
       1 1 0 0  /|/|/|/|
       1 1 0 1  /_______
       1 1 1 0  /\/\/\/\
       1 1 1 1  /|______
     
       The envelope counter on the AY-3-8910 has 16 steps. 
     */

    private int envelopeShape;
    private int maxAmplitude;
    
    // AY register index
    private int indexReg;
    // AY register set
    private int ayRegs[] = new int[16];

    private static final double volumeRate[] = {
        0.00000, 0.01375, 0.02046, 0.02905, 0.04234, 0.06184, 0.08472, 0.13690,
        0.16913, 0.26466, 0.35271, 0.44994, 0.57037, 0.68727, 0.84816, 1.000
    };
    private int volumeLevel[] = new int[16];

    AY8912() {
        clockFreq = 1773450;  // the Spectrum AY freq by default
        ayCycle = clockFreq >>> 4;
        toneChnA = toneChnB = toneChnC = 0;
        counterChnA = counterChnB = counterChnC = 0;
        ampChnA = ampChnB = ampChnC = 0;
        noisePeriod = noiseCounter = 0;
        envelopeShape = 0;
        maxAmplitude = 8000;
        indexReg = 0;
        Arrays.fill(ayRegs, 0);
    }
}
