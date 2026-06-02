package dev.frost.miniverse.map;

import com.google.gson.JsonObject;

@FunctionalInterface
public interface MapConfigValidator {
    MapValidationResult validate(MapDescriptor map, JsonObject config);
}
