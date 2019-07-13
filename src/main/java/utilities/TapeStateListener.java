/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package utilities;

import utilities.Tape.TapeState;

/**
 *
 * @author jsanchez
 */
public interface TapeStateListener {
    public void stateChanged(final TapeState state);
}
