package dev.frost.miniverse.client.gui.workspace.framework;

import java.util.function.Supplier;

public class TriStateTooltip {
    public static Supplier<String> of(Supplier<TriState> stateSupplier, String onText, String offText, String defaultText) {
        return () -> {
            TriState state = stateSupplier.get();
            return switch (state) {
                case FORCE_ON -> onText;
                case FORCE_OFF -> offText;
                case DEFAULT -> defaultText;
            };
        };
    }
}
