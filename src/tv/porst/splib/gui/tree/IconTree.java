package tv.porst.splib.gui.tree;

import java.awt.Component;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;

/**
 * Renderer used to display icons in tree nodes.
 */
class CustomTreeCellRenderer extends DefaultTreeCellRenderer {

	@Override
	public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel, final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
		if((value instanceof IconNode) && (value != null)) {
			setIcon(((IconNode)value).getIcon());
		}

		//we can not call super.getTreeCellRendererComponent method, since it overrides our setIcon call and cause rendering of labels to '...' when node expansion is done

		//so, we copy (and modify logic little bit) from super class method:

		final String stringValue = tree.convertValueToText(value, sel,
				expanded, leaf, row, hasFocus);

		this.hasFocus = hasFocus;
		setText(stringValue);
		if(sel) {
			setForeground(getTextSelectionColor());
		} else {
			setForeground(getTextNonSelectionColor());
		}

		if (!tree.isEnabled()) {
			setEnabled(false);
		}
		else {
			setEnabled(true);
		}
		setComponentOrientation(tree.getComponentOrientation());
		selected = sel;
		return this;

	}
}

/**
 * Represents a tree where the nodes are custom icons.
 */
public class IconTree extends JTree {

	/**
	 * Creates a new icon tree object.
	 * 
	 * @param model The model of the icon tree.
	 */
	public IconTree(final TreeModel model) {
		super(model);

		setCellRenderer(new CustomTreeCellRenderer());
	}
}