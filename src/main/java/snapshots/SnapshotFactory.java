/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package snapshots;

import lombok.NonNull;

import java.io.File;

/**
 * @author jsanchez
 */
public class SnapshotFactory {

    private SnapshotFactory() {
        // This private constructor is intended to hide the implicit public constructor
    }

    public static SnapshotFile getSnapshot(@NonNull File file) {

        String name = file.getName().toLowerCase();
        String format = name.substring(name.lastIndexOf("."));
        return switch (format) {
            case ".sna" -> new SnapshotSNA();
            case ".z80" -> new SnapshotZ80();
            case ".szx" -> new SnapshotSZX();
            case ".sp" -> new SnapshotSP();
            default -> throw new IllegalArgumentException(String.format("Unknown snapshot format: '%s'", format));
        };
    }

}
