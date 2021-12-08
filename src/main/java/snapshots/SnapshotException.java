/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package snapshots;

/**
 *
 * @author jsanchez
 */
public class SnapshotException extends Exception {
    public SnapshotException() {

    }

    public SnapshotException(String message) {
        super(message);
    }

    public SnapshotException(Throwable throwable) {
        super(throwable);
    }

    public SnapshotException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
