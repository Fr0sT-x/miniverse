package dev.frost.miniverse.chat;

public enum ChatChannel {
    GLOBAL("ALL"),
    TEAM("TEAM");

    private final String label;

    ChatChannel(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public String prefix() {
        return "[" + this.label + "]";
    }
}

