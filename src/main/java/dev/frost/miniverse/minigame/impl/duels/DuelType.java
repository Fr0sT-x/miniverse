package dev.frost.miniverse.minigame.impl.duels;

public record DuelType(
    String id, 
    String name,
    boolean knockbackOnly,
    boolean allowBuilding,
    boolean allowBreaking,
    boolean allowHunger,
    boolean naturalRegen
) {
}
