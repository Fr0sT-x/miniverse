package dev.frost.miniverse.client.gui.workspace.framework;

import dev.frost.miniverse.client.gui.ui.UiLayout;

public record StandardWorkspaceLayout(UiLayout.Rect workspace) {
    public static final int BUTTON_HEIGHT = 22;
    public static final int SETTINGS_FIELD_WIDTH = 170;

    public UiLayout.Rect mainPanel() {
        return this.workspace.inset(4);
    }

    public UiLayout.Rect contentArea() {
        return new UiLayout.Rect(this.mainPanel().x() + 14, this.mainPanel().y() + 84, this.mainPanel().width() - 28, this.mainPanel().height() - 106);
    }

    public UiLayout.Rect startButton() {
        return new UiLayout.Rect(this.mainPanel().x() + this.mainPanel().width() - 126, this.mainPanel().y() + 10, 112, BUTTON_HEIGHT);
    }

    public int actionY() {
        return this.mainPanel().y() + 50;
    }

    public int actionStartX() {
        return this.mainPanel().x() + 14;
    }
}
