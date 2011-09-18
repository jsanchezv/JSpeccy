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
public class FileFilterImage extends FileFilter {
    @Override
    public boolean accept(File fIn) {
        return fIn.getName().toLowerCase().endsWith(".scr") ||
                fIn.getName().toLowerCase().endsWith(".png") ||
                fIn.isDirectory();
    }

    @Override
    public String getDescription() {
        return java.util.ResourceBundle.getBundle("gui/Bundle").getString(
            "IMAGE_TYPE"); // NOI18N
    }
}
