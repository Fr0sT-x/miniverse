package dev.frost.miniverse.minigame.impl.deathshuffle.objective;

import net.minecraft.util.StringIdentifiable;

public enum DeathObjectiveSource implements StringIdentifiable {
    DATAPACK("datapack"),
    ENTITY("entity"),
    DAMAGE("damage"),
    CONDITION("condition");

    private final String name;

    DeathObjectiveSource(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return this.name;
    }
}
