package dev.frost.miniverse.client.gui.workspace.framework;

public record BinaryTooltip(String onText, String offText) {
    public String resolve(boolean state) {
        return state ? this.onText : this.offText;
    }
}
