/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package z80core;

/**
 *
 * @author jsanchez
 */
public class IndexRegister {
    private int word16;   // representación en 16 bits
    
    IndexRegister() {
        word16 = 0;
    }
    
    IndexRegister(int word) {
        setWord(word);
    }
    
    IndexRegister(int high, int low) {
        setHigh(high);
        setLow(low);
    }

	IndexRegister(IndexRegister copia) {
			setWord(copia.getWord());
	}
    
    
    public int getHigh() {
        return (word16 >>> 8);
    }
    
    public int getLow() {
        return (word16 & 0x00ff);
    }
    
    public int getWord() {
        return word16;
    }
    
    public void setHigh(int value) {
        value &= 0xff;
        word16 &= 0x00ff;
        word16 |= (value << 8);
    }
    
    public void setLow(int value) {
        value &= 0xff;
        word16 &= 0xff00;
        word16 |= value;
    }
    
    public void setWord(int word) {
        word16 = word & 0xffff;
    }
    
    public void incWord() {
        word16++;
        word16 &= 0xffff;
    }
    
    public void decWord() {
        word16--;
        word16 &= 0xffff;
    }
    
    @Override
    public String toString() {
        String outStr =  "Word = " + Integer.toHexString(getWord()) + ", " +
               "High = " + Integer.toHexString(getHigh()) + ", " +
               "Low = " + Integer.toHexString(getLow());
        return outStr;
    }
    
    public static void main(String args[]) {
        IndexRegister reg = new IndexRegister();
        reg.setWord(0x12345);
		IndexRegister reg2 = new IndexRegister(reg);
        System.out.println(reg);
        reg.setHigh(0xFF56);
        System.out.println(reg);
        reg.setLow(0xFF78);
        System.out.println(reg);
		System.out.println("reg2 = " + reg2);
    }
}
