/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import configuration.RecentFilesType;

import java.io.File;
import java.util.ArrayList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/**
 *
 * @author jsanchez
 */
public class RecentFilesMgr {
    private final RecentFilesType cfgFileList;
    private final JMenuItem itemList[];
    private final ArrayList<File> filesList;

    RecentFilesMgr(RecentFilesType cfgFiles, JMenu menu) {
        itemList = new JMenuItem[5];
        cfgFileList = cfgFiles;
        filesList = new ArrayList<>();

        for (int idx = 0; idx < 5; idx++) {
            itemList[idx] = menu.getItem(idx);
        }

        int idx = 0;
        for (String name : cfgFileList.getRecentFile()) {
            if (name == null || name.length() == 0)
                continue;

            File file = new File(name);
            if (file.exists()) {
                filesList.add(file);
                itemList[idx].setText(file.getName());
                itemList[idx].setToolTipText(file.getAbsolutePath());
                itemList[idx++].setEnabled(true);
            }
        }
    }

    public File getRecentFile(int index) {
        File fd = filesList.get(index);

        if (index > 0) {
            filesList.remove(index);
            filesList.add(0, fd);
        }

        updateRecentMenu();
        return fd;
    }

    public String getAbsolutePath(int index) {
        return filesList.get(index).getAbsolutePath();
    }

    public void addRecentFile(File fdnew) {
        File fdtmp = null;
        String newFile = fdnew.getAbsolutePath();

        for (File file : filesList) {
            if (file.getAbsolutePath().equals(newFile)) {
                fdtmp = file;
                break;
            }
        }

        if (fdtmp == null) {
            // El archivo es nuevo, de modo que no está en la lista
            filesList.add(0, fdnew);
        } else {
            // El archivo ya está en la lista, si no es el primero, se arregla el desaguisado
            if (filesList.indexOf(fdnew) > 0) {
                filesList.remove(fdnew);
                filesList.add(0, fdnew);
            }
        }

        // Si la lista tiene 6 elementos (más sería un error), quito el último
        if (filesList.size() > 5) {
            filesList.remove(5);
        }

        updateRecentMenu();
    }

    public int size() {
        return filesList.size();
    }

    private void updateRecentMenu() {
        int idx = 0;

        for (File file : filesList) {
            itemList[idx].setText(file.getName());
            itemList[idx].setToolTipText(file.getAbsolutePath());
            itemList[idx++].setEnabled(true);
        }
    }
}
