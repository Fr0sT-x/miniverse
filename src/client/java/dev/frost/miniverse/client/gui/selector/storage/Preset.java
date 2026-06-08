package dev.frost.miniverse.client.gui.selector.storage;

import net.minecraft.util.Identifier;

import java.util.Set;

public record Preset(String name, Set<Identifier> entries) {
}
