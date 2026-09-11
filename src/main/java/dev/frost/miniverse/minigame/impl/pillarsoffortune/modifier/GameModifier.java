package dev.frost.miniverse.minigame.impl.pillarsoffortune.modifier;

import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.FrameworkModule;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.PillarsOfFortuneMinigame;

public interface GameModifier extends FrameworkModule {
    String getId();
    
    void tick(PillarsOfFortuneMinigame minigame);
    
    void loadState(JsonObject object);
    
    void saveState(JsonObject object);
}
