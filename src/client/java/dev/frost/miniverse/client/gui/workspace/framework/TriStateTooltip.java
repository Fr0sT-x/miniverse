package dev.frost.miniverse.client.gui.workspace.framework;

import java.util.function.Supplier;

public record TriStateTooltip(String onText, String offText, String defaultText) {
    public String resolve(TriState state) {
        return switch (state) {
            case FORCE_ON -> this.onText;
            case FORCE_OFF -> this.offText;
            case DEFAULT -> this.defaultText;
        };
    }


}
