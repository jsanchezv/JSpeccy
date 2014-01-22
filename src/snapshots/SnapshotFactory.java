/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package snapshots;

import java.io.File;

/**
 *
 * @author jsanchez
 */
public class SnapshotFactory {
    public static SnapshotFile getSnapshot(File file) {
        String name = file.getName().toLowerCase();
        switch (name.substring(name.lastIndexOf("."), name.length())) {
            case ".sna":
                return new SnapshotSNA();
            case ".z80":
                return new SnapshotZ80();
            case ".szx":
                return new SnapshotSZX();
            case ".sp":
                return new SnapshotSP();
        }
        throw new IllegalArgumentException("No such snapshot format");
    }
}
