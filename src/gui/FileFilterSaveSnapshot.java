/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gui;

import java.io.File;
import javax.swing.filechooser.FileFilter;

/**
 *
 * @author jsanchez
 */
public class FileFilterSaveSnapshot extends FileFilter {
    @Override
    public boolean accept(File fIn) {
        return fIn.getName().toLowerCase().endsWith(".sna") ||
                fIn.getName().toLowerCase().endsWith(".z80") ||
                fIn.getName().toLowerCase().endsWith(".szx") ||
                fIn.isDirectory();
    }

    @Override
    public String getDescription() {
        return java.util.ResourceBundle.getBundle("gui/Bundle").getString(
            "SAVE_SNAPSHOT_TYPE"); // NOI18N
    }
}
