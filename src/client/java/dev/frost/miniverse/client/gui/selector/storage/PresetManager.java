package dev.frost.miniverse.client.gui.selector.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PresetManager {
    private final Map<String, List<Preset>> presetsByNamespace = new LinkedHashMap<>();

    public List<Preset> getPresets(String namespace) {
        return this.presetsByNamespace.getOrDefault(namespace, List.of());
    }

    public void addPreset(String namespace, Preset preset) {
        this.presetsByNamespace.computeIfAbsent(namespace, k -> new ArrayList<>()).add(preset);
    }

    public void removePreset(String namespace, String presetName) {
        List<Preset> presets = this.presetsByNamespace.get(namespace);
        if (presets != null) {
            presets.removeIf(p -> p.name().equals(presetName));
        }
    }

    public void renamePreset(String namespace, String oldName, String newName) {
        List<Preset> presets = this.presetsByNamespace.get(namespace);
        if (presets != null) {
            for (int i = 0; i < presets.size(); i++) {
                Preset p = presets.get(i);
                if (p.name().equals(oldName)) {
                    presets.set(i, new Preset(newName, p.entries()));
                    break;
                }
            }
        }
    }

    public Map<String, List<Preset>> getAll() {
        return this.presetsByNamespace;
    }

    public void load(Map<String, List<Preset>> loaded) {
        this.presetsByNamespace.clear();
        this.presetsByNamespace.putAll(loaded);
    }
}
