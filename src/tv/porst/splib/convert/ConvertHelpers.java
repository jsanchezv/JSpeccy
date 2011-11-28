package tv.porst.splib.convert;

import java.awt.event.KeyEvent;

/**
 * Converts helper functions for working with different data types.
 */
public final class ConvertHelpers {

	/**
	 * Tests whether a given character is a valid decimal character.
	 *
	 * @param c The character to test.
	 *
	 * @return True, if the given character is a valid decimal character.
	 */
	public static boolean isDecCharacter(final char c)
	{
		return c >= '0' && c <= '9';
	}

	/**
	 * Tests whether a character is a valid character of a hexadecimal string.
	 *
	 * @param c The character to test.
	 *
	 * @return True, if the character is a hex character. False, otherwise.
	 */
	public static boolean isHexCharacter(final char c)
	{
		return isDecCharacter(c) || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
	}

	/**
	 * Tests whether a character is a printable ASCII character.
	 *
	 * @param c The character to test.
	 *
	 * @return True, if the character is a printable ASCII character. False,
	 *         otherwise.
	 */
	public static boolean isPrintableCharacter(final char c)
	{
		final Character.UnicodeBlock block = Character.UnicodeBlock.of(c);

		return !Character.isISOControl(c) &&
		c != KeyEvent.CHAR_UNDEFINED &&
		block != null &&
		block != Character.UnicodeBlock.SPECIALS;
	}
}
