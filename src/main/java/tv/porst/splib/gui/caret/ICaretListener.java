package tv.porst.splib.gui.caret;

/**
 * Interface to be implemented by classes that want to be notified about
 * changed in the caret.
 */
public interface ICaretListener
{
	/**
	 * Invoked after the caret status changed.
	 * 
	 * @param caret The caret whose status changed.
	 */
	public void caretStatusChanged(JCaret caret);
}
