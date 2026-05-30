package dev.frost.miniverse.minigame.impl.manhunt;

import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.persistence.SessionSerializer;

public final class ManhuntSessionSerializer implements SessionSerializer<ManhuntSessionData> {
    @Override
    public String gameType() {
        return ManhuntDefinition.ID;
    }

    @Override
    public JsonObject serialize(ManhuntSessionData data) {
        return data.toJson();
    }

    @Override
    public ManhuntSessionData deserialize(JsonObject root) {
        return ManhuntSessionData.fromJson(root);
    }
}
