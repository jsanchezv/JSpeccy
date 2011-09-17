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
public class FileFilterTape extends FileFilter {
    public boolean accept(File fIn) {
        return fIn.getName().toLowerCase().endsWith(".tap") ||
                fIn.getName().toLowerCase().endsWith(".tzx") ||
                fIn.isDirectory();
    }

    public String getDescription() {
        return java.util.ResourceBundle.getBundle("gui/Bundle").getString(
            "TAPE_TYPE"); // NOI18N
    }
}
