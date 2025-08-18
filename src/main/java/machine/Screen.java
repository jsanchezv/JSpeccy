package machine;

import java.awt.Rectangle;

public interface Screen {

	void repaintScreen(Rectangle area);

	int getZoom();

	default void borderUpdated(int border) {
	}

}
