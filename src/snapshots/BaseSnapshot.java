/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package snapshots;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;

/**
 *
 * @author jsanchez
 */
public abstract class BaseSnapshot {
    protected BufferedInputStream fIn;
    protected BufferedOutputStream fOut;
    protected SpectrumState spectrum;
    protected Z80State z80;
    protected MemoryState memory;
    protected AY8912State ay8912;
    
    public abstract SpectrumState load(File filename) throws SnapshotException;
    public abstract boolean save(File filename, SpectrumState state) throws SnapshotException;
}
