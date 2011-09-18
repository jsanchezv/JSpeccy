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
public class FileFilterScreen extends FileFilter{
    @Override
    public boolean accept(File fIn) {
        return fIn.getName().toLowerCase().endsWith(".scr") ||
                fIn.isDirectory();
    }

    @Override
    public String getDescription() {
        return java.util.ResourceBundle.getBundle("gui/Bundle").getString(
            "SCR_TYPE"); // NOI18N
    }
}
