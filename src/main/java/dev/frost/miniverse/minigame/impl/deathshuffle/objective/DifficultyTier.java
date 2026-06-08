package dev.frost.miniverse.minigame.impl.deathshuffle.objective;

import net.minecraft.util.StringIdentifiable;

public enum DifficultyTier implements StringIdentifiable {
    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard");

    private final String name;

    DifficultyTier(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return this.name;
    }
}
