package dev.frost.miniverse.map.editor;

import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapDescriptor;
import dev.frost.miniverse.map.MapValidationResult;

@FunctionalInterface
public interface MapEditorValidator {
    MapValidationResult validate(MapDescriptor map, JsonObject config, MapEditorExtension extension);
}
