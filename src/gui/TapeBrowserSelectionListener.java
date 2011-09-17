/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gui;

import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import utilities.Tape;

/**
 *
 * @author jsanchez
 */
public class TapeBrowserSelectionListener implements ListSelectionListener {

    Tape tape;
    TapeBrowserSelectionListener(Tape device) {
        tape = device;
    }

    public void valueChanged(ListSelectionEvent event) {
        ListSelectionModel lsm = (ListSelectionModel)event.getSource();

        if (lsm.getValueIsAdjusting())
            return;

        if (event.getFirstIndex() != -1)
            tape.setSelectedBlock(event.getFirstIndex());
    }
}
