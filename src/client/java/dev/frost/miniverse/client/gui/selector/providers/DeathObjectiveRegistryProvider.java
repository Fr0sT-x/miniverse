package dev.frost.miniverse.client.gui.selector.providers;

import dev.frost.miniverse.client.gui.selector.RegistryCategory;
import dev.frost.miniverse.client.gui.selector.RegistryContentProvider;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjective;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DeathObjectiveRegistryProvider implements RegistryContentProvider<DeathObjective> {
    private final Registry<DeathObjective> registry;
    private final java.util.Map<Identifier, DeathObjective> dynamicEntries = new java.util.LinkedHashMap<>();

    public DeathObjectiveRegistryProvider(Registry<DeathObjective> registry) {
        this.registry = registry;
        this.loadDatapacks();
    }

    private void loadDatapacks() {
        this.dynamicEntries.clear();
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        this.dynamicEntries.putAll(dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveManager.getAll(client.getServer()));

        for (net.fabricmc.loader.api.ModContainer mod : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
            for (java.nio.file.Path root : mod.getRootPaths()) {
                scanDataDirectory(root.resolve("data"));
            }
        }
        if (client.getServer() != null) {
            java.nio.file.Path datapacksDir = client.getServer().getSavePath(net.minecraft.util.WorldSavePath.DATAPACKS);
            if (java.nio.file.Files.isDirectory(datapacksDir)) {
                 try (java.util.stream.Stream<java.nio.file.Path> dps = java.nio.file.Files.list(datapacksDir)) {
                     dps.forEach(dp -> {
                         scanDataDirectory(dp.resolve("data"));
                     });
                 } catch (Exception ignored) {}
            }
        }
    }

    private void scanDataDirectory(java.nio.file.Path dataPath) {
        if (!java.nio.file.Files.isDirectory(dataPath)) return;
        try (java.util.stream.Stream<java.nio.file.Path> namespaces = java.nio.file.Files.list(dataPath)) {
            namespaces.filter(java.nio.file.Files::isDirectory).forEach(nsPath -> {
                String namespace = nsPath.getFileName().toString();
                java.nio.file.Path objPath = nsPath.resolve("death_objective");
                if (java.nio.file.Files.isDirectory(objPath)) {
                    try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(objPath)) {
                        files.filter(p -> p.toString().endsWith(".json")).forEach(file -> {
                            try (java.io.InputStream is = java.nio.file.Files.newInputStream(file);
                                 java.io.InputStreamReader reader = new java.io.InputStreamReader(is)) {
                                com.google.gson.JsonElement json = com.google.gson.JsonParser.parseReader(reader);
                                DeathObjective.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                                    .resultOrPartial(err -> {})
                                    .ifPresent(obj -> {
                                        String name = file.getFileName().toString().replace(".json", "");
                                        dynamicEntries.put(Identifier.of(namespace, name), obj);
                                    });
                            } catch (Exception ignored) {}
                        });
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    @Override
    public Registry<DeathObjective> registry() {
        return this.registry;
    }

    @Override
    public Collection<DeathObjective> getEntries() {
        return this.dynamicEntries.values();
    }

    @Override
    public void renderIcon(DrawContext context, DeathObjective entry, int x, int y) {
        if (entry.icon() != null) {
            net.minecraft.item.Item item = Registries.ITEM.get(entry.icon());
            if (item != null) {
                context.drawItem(new ItemStack(item), x, y);
            }
        }
    }

    @Override
    public Text getDisplayName(DeathObjective entry) {
        return entry.displayName();
    }

    @Override
    public Identifier getId(DeathObjective entry) {
        for (java.util.Map.Entry<Identifier, DeathObjective> mapEntry : this.dynamicEntries.entrySet()) {
            if (mapEntry.getValue() == entry) {
                return mapEntry.getKey();
            }
        }
        return this.registry.getId(entry);
    }

    @Override
    public List<Text> getTooltip(DeathObjective entry) {
        List<Text> tooltip = new java.util.ArrayList<>();
        tooltip.add(entry.displayName());
        entry.description().ifPresent(desc -> tooltip.add(desc.copy().formatted(net.minecraft.util.Formatting.GRAY)));
        tooltip.add(Text.literal(getId(entry).toString()).formatted(net.minecraft.util.Formatting.DARK_GRAY));
        return tooltip;
    }

    @Override
    public Set<Identifier> getTags(DeathObjective entry) {
        return Set.of();
    }

    private static final RegistryCategory CAT_DATAPACK = new RegistryCategory("datapack", Text.literal("Datapack Objectives"), "▤");
    private static final RegistryCategory CAT_ENTITY = new RegistryCategory("entity", Text.literal("Entity Objectives"), "☠");
    private static final RegistryCategory CAT_DAMAGE = new RegistryCategory("damage", Text.literal("Damage Objectives"), "⚔");
    private static final RegistryCategory CAT_CONDITION = new RegistryCategory("condition", Text.literal("Condition Objectives"), "⚑");

    @Override
    public Set<RegistryCategory> getCategories(DeathObjective entry) {
        if (entry.source() == dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveSource.ENTITY) {
            return Set.of(CAT_ENTITY);
        } else if (entry.source() == dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveSource.DAMAGE) {
            return Set.of(CAT_DAMAGE);
        } else if (entry.source() == dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveSource.CONDITION) {
            return Set.of(CAT_CONDITION);
        }
        return Set.of(CAT_DATAPACK);
    }

    @Override
    public List<RegistryCategory> getAllCategories() {
        return List.of(CAT_DATAPACK, CAT_ENTITY, CAT_DAMAGE, CAT_CONDITION);
    }
}
