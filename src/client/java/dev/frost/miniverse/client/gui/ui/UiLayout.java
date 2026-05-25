package dev.frost.miniverse.client.gui.ui;

public final class UiLayout {
    private UiLayout() {
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
        }

        public Rect inset(int amount) {
            return new Rect(this.x + amount, this.y + amount, this.width - amount * 2, this.height - amount * 2);
        }

        public Rect below(int gap, int height) {
            return new Rect(this.x, this.y + this.height + gap, this.width, height);
        }
    }

    public static Rect centered(int screenWidth, int screenHeight, int maxWidth, int maxHeight, int margin) {
        int width = Math.min(maxWidth, Math.max(1, screenWidth - margin * 2));
        int height = Math.min(maxHeight, Math.max(1, screenHeight - margin * 2));
        return new Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    public static Rect stackVertical(Rect area, int index, int itemHeight, int gap) {
        return new Rect(area.x, area.y + index * (itemHeight + gap), area.width, itemHeight);
    }

    public static Rect grid(Rect area, int index, int columns, int itemHeight, int gap) {
        int column = index % columns;
        int row = index / columns;
        int itemWidth = (area.width - gap * (columns - 1)) / columns;
        return new Rect(area.x + column * (itemWidth + gap), area.y + row * (itemHeight + gap), itemWidth, itemHeight);
    }
}
