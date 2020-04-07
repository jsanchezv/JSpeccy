package tv.porst.splib.gui.tree;

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Represents a node in an icon tree.
 */
public class IconNode extends DefaultMutableTreeNode {

	/**
	 * The icon shown for the node.
	 */
	protected Icon icon;

	/**
	 * Name of the icon.
	 */
	protected String iconName;

	/**
	 * Creates a new icon node.
	 */
	public IconNode() {
		this(null);
	}

	/**
	 * Creates a new icon node.
	 * 
	 * @param userObject The user object associated with the node.
	 */
	public IconNode(final Object userObject) {
		this(userObject, true, null);
	}

	/**
	 * Creates a new icon node.
	 * 
	 * @param userObject The user object associated with the node.
	 * @param allowsChildren Flag that says whether the node can have children or not.
	 * @param icon The icon shown in the node.
	 */
	public IconNode(final Object userObject, final boolean allowsChildren, final Icon icon) {
		super(userObject, allowsChildren);
		this.icon = icon;
	}

	/**
	 * Returns the icon of the node.
	 * 
	 * @return The icon of the node.
	 */
	public Icon getIcon() {
		return icon;
	}

	/**
	 * Returns the icon name of the node.
	 * 
	 * @return The icon name of the node.
	 */
	public String getIconName() {
		if (iconName != null) {
			return iconName;
		} else {
			final String str = userObject.toString();
			int index = str.lastIndexOf(".");
			if (index != -1) {
				return str.substring(++index);
			} else {
				return null;
			}
		}
	}
}