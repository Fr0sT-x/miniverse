package dev.frost.miniverse.client.gui.workspace.framework;

import dev.frost.miniverse.client.gui.ui.UiLayout;

public record StandardWorkspaceLayout(UiLayout.Rect workspace, boolean rightSidebarOpen, int dockWidth) {
    public static final int BUTTON_HEIGHT = 22;
    public static final int SETTINGS_FIELD_WIDTH = 170;
    public static final int DEFAULT_DOCK_WIDTH = 220;
    public static final int MIN_DOCK_WIDTH = 170;
    public static final int MAX_DOCK_WIDTH = 380;
    public static final int PANEL_GAP = 6;

    public StandardWorkspaceLayout(UiLayout.Rect workspace, boolean rightSidebarOpen) {
        this(workspace, rightSidebarOpen, DEFAULT_DOCK_WIDTH);
    }

    public StandardWorkspaceLayout(UiLayout.Rect workspace) {
        this(workspace, true, DEFAULT_DOCK_WIDTH);
    }

    public boolean isRightSidebarOpen() {
        return this.rightSidebarOpen && this.dockWidth > 0;
    }

    public UiLayout.Rect mainPanel() {
        UiLayout.Rect inset = this.workspace.inset(4);
        if (!this.rightSidebarOpen || this.dockWidth <= 0) {
            return inset;
        }
        int mainWidth = Math.max(160, inset.width() - this.dockWidth - PANEL_GAP);
        return new UiLayout.Rect(inset.x(), inset.y(), mainWidth, inset.height());
    }

    public UiLayout.Rect rightSidebar() {
        if (!this.rightSidebarOpen || this.dockWidth <= 0) {
            return null;
        }
        UiLayout.Rect inset = this.workspace.inset(4);
        int dockX = inset.x() + this.mainPanel().width() + PANEL_GAP;
        int actualDockWidth = inset.width() - this.mainPanel().width() - PANEL_GAP;
        return new UiLayout.Rect(dockX, inset.y(), Math.max(1, actualDockWidth), inset.height());
    }

    public UiLayout.Rect contentArea() {
        return new UiLayout.Rect(this.mainPanel().x() + 14, this.mainPanel().y() + 84, this.mainPanel().width() - 28, this.mainPanel().height() - 106);
    }

    public UiLayout.Rect collapsedStartButton() {
        return new UiLayout.Rect(this.mainPanel().x() + this.mainPanel().width() - 106, this.mainPanel().y() + 9, 88, BUTTON_HEIGHT);
    }

    public UiLayout.Rect startButton() {
        if (!this.isRightSidebarOpen()) {
            return this.collapsedStartButton();
        }
        UiLayout.Rect dock = this.rightSidebar();
        return new UiLayout.Rect(dock.x() + 10, dock.y() + dock.height() - 32, dock.width() - 20, 24);
    }

    public int actionY() {
        return this.mainPanel().y() + 46;
    }

    public int actionStartX() {
        return this.mainPanel().x() + 14;
    }
}
