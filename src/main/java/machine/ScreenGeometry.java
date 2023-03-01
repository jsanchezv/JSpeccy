package machine;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true, chain = true)
@Builder(toBuilder = true, builderMethodName = "aScreenGeometry", setterPrefix = "with")
public class ScreenGeometry {

    Border border;
    int width;
    int height;

    @Value
    @Accessors(fluent = true, chain = true)
    @Builder(toBuilder = true, builderMethodName = "aBorder", setterPrefix = "with")
    public static class Border {
        int left;
        int right;
        int top;
        int bottom;

        public static class BorderBuilder {

            int left = 32;
            int right = 32;
            int top = 24;
            int bottom = 24;

            public BorderBuilder withNoBorder() {
                left = right = top = bottom = 0;
                return this;
            }

            public BorderBuilder withStandardBorder() {
                left = 32;
                right = 32;
                top = 24;
                bottom = 24;
                return this;
            }

            public BorderBuilder withFullBorder() {
                left = 48;
                right = 48;
                top = 48;
                bottom = 56;
                return this;
            }

            public BorderBuilder withHugeBorder() {
                left = 64;
                right = 64;
                top = 56;
                bottom = 56;
                return this;
            }

        }
    }

    public static class ScreenGeometryBuilder {

        public static final int CANVAS_WIDTH = 256;
        public static final int CANVAS_HEIGHT = 192;
        Border border = Border.aBorder().build();
        int width;
        int height;

        public ScreenGeometryBuilder withBorder(final Border border) {
            this.border = border;
            this.width = border.left() + CANVAS_WIDTH + border.right();
            this.height = border.top() + CANVAS_HEIGHT + border.bottom();
            return this;
        }

        public ScreenGeometryBuilder withNoBorder() {
            return withBorder(Border.aBorder().withNoBorder().build());
        }

        public ScreenGeometryBuilder withStandardBorder() {
            return withBorder(Border.aBorder().withStandardBorder().build());
        }

        public ScreenGeometryBuilder withFullBorder() {
            return withBorder(Border.aBorder().withFullBorder().build());
        }

        public ScreenGeometryBuilder withHugeBorder() {
            return withBorder(Border.aBorder().withHugeBorder().build());
        }

    }

}
