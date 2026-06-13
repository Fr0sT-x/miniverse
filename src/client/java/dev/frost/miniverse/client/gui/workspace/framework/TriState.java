package dev.frost.miniverse.client.gui.workspace.framework;

public enum TriState {
    DEFAULT("DEFAULT"),
    FORCE_ON("FORCE ON"),
    FORCE_OFF("FORCE OFF");

    private final String label;

    TriState(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public TriState next() {
        return switch (this) {
            case DEFAULT -> FORCE_ON;
            case FORCE_ON -> FORCE_OFF;
            case FORCE_OFF -> DEFAULT;
        };
    }
}
