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
public class FileFilterRom extends FileFilter {
    @Override
    public boolean accept(File fIn) {
        return fIn.getName().toLowerCase().endsWith(".rom") ||
                fIn.isDirectory();
    }

    @Override
    public String getDescription() {
        return java.util.ResourceBundle.getBundle("gui/Bundle").getString(
            "ROM_TYPE"); // NOI18N
    }
}
