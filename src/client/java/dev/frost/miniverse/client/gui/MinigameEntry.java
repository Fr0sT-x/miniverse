package dev.frost.miniverse.client.gui;

import net.minecraft.text.Text;

import java.util.function.Consumer;

public record MinigameEntry(
    String id,
    String name,
    String description,
    String icon,
    boolean enabled,
    String setupKind,
    java.util.List<SessionSnapshotData.SetupField> fields,
    Consumer<SessionScreen> clickAction
) {
    public Text buttonLabel() {
        return Text.literal(this.enabled ? this.icon + "  " + this.name : "Coming Soon");
    }

    public void activate(SessionScreen screen) {
        if (this.enabled && this.clickAction != null) {
            this.clickAction.accept(screen);
        } else if (this.enabled) {
            screen.openGenericSetup(this);
        }
    }
}

