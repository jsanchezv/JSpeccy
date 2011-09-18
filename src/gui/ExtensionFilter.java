/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.io.File;
import java.util.ResourceBundle;
import javax.swing.filechooser.FileFilter;

/**
 *
 * @author jsanchez
 */
public class ExtensionFilter extends FileFilter {

    private String extensions[];
    private String description;
    private ResourceBundle bundle = ResourceBundle.getBundle("gui/Bundle"); // NOI18N

    public ExtensionFilter(String description, String extension) {
        this(description, new String[]{extension});
    }

    public ExtensionFilter(String description, String extensions[]) {
        this.description = description;
        this.extensions = (String[]) extensions.clone();
    }

    @Override
    public boolean accept(File file) {
        if (file.isDirectory()) {
            return true;
        }
        
        for (int idx = 0; idx < extensions.length; idx++) {
            if (file.getName().toLowerCase().endsWith(extensions[idx])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getDescription() {
        return (description == null ? bundle.getString(extensions[0]) :
            bundle.getString(description));
    }
}
