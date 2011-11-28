package tv.porst.splib.gui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * Helper class that contains GUI functions.
 */
public final class GuiHelpers {

	/**
	 * Returns the system monospace font.
	 * 
	 * @return The name of the system monospace font.
	 */
	public static String getMonospaceFont() {

		final GraphicsEnvironment localGraphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
		final Font[] fonts = localGraphicsEnvironment.getAllFonts();
		for (final Font font : fonts) {
			if (font.getName().equals("Courier New")) {
				return "Courier New";
			}
		}
		return "Monospaced";
	}
}
