package dev.frost.miniverse.minigame.impl.deathshuffle.objective.generator;

import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjective;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveSource;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DifficultyTier;
import net.minecraft.entity.EntityType;
import net.minecraft.predicate.DamagePredicate;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class EntityObjectiveGenerator implements DeathObjectiveGenerator {

    @Override
    public Map<Identifier, DeathObjective> generate() {
        Map<Identifier, DeathObjective> generated = new LinkedHashMap<>();

        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            if (canBeDeathObjective(type)) {
                Identifier id = Registries.ENTITY_TYPE.getId(type);
                Identifier objId = Identifier.of("miniverse", "entity/" + id.getNamespace() + "/" + id.getPath());

                Text displayName = Text.translatable(type.getTranslationKey()).formatted(Formatting.RED, Formatting.BOLD);
                Optional<Text> description = Optional.of(Text.literal("Die to a ").append(Text.translatable(type.getTranslationKey())));

                DamagePredicate damagePredicate = DamagePredicate.Builder.create()
                    .sourceEntity(EntityPredicate.Builder.create().type(type).build())
                    .build();

                DifficultyTier difficulty = determineDifficulty(type);

                String path = id.getPath();
                Identifier icon;
                if (path.equals("wither")) {
                    icon = Identifier.of("minecraft", "wither_skeleton_skull");
                } else if (path.equals("ender_dragon")) {
                    icon = Identifier.of("minecraft", "dragon_head");
                } else if (path.equals("snow_golem")) {
                    icon = Identifier.of("minecraft", "carved_pumpkin");
                } else if (path.equals("iron_golem")) {
                    icon = Identifier.of("minecraft", "iron_block");
                } else {
                    icon = Identifier.of(id.getNamespace(), path + "_spawn_egg");
                }

                DeathObjective objective = new DeathObjective(
                    displayName,
                    icon,
                    description,
                    Optional.of(damagePredicate),
                    Optional.empty(),
                    DeathObjectiveSource.ENTITY,
                    difficulty
                );

                generated.put(objId, objective);
            }
        }

        // Add manually crafted variants
        addManual(generated, "baby_hoglin", "Baby Hoglin", "minecraft:hoglin_spawn_egg", EntityType.HOGLIN, true, DifficultyTier.MEDIUM, Formatting.RED);
        addManual(generated, "bee", "Bee", "minecraft:bee_spawn_egg", EntityType.BEE, false, DifficultyTier.EASY, Formatting.YELLOW);

        return generated;
    }

    private void addManual(Map<Identifier, DeathObjective> map, String path, String name, String icon, EntityType<?> type, boolean isBaby, DifficultyTier difficulty, Formatting color) {
        Identifier objId = Identifier.of("miniverse", "entity/custom/" + path);
        Text displayName = Text.literal(name).formatted(color, Formatting.BOLD);
        Optional<Text> description = Optional.of(Text.literal("Die to a " + name.toLowerCase()));

        net.minecraft.predicate.entity.EntityPredicate.Builder entityBuilder = net.minecraft.predicate.entity.EntityPredicate.Builder.create().type(type);
        if (isBaby) {
            entityBuilder.flags(net.minecraft.predicate.entity.EntityFlagsPredicate.Builder.create().isBaby(true));
        }

        DamagePredicate damagePredicate = DamagePredicate.Builder.create()
            .sourceEntity(entityBuilder.build())
            .build();

        DeathObjective objective = new DeathObjective(
            displayName,
            Identifier.of(icon),
            description,
            Optional.of(damagePredicate),
            Optional.empty(),
            DeathObjectiveSource.ENTITY,
            difficulty
        );

        map.put(objId, objective);
    }

    private boolean canBeDeathObjective(EntityType<?> type) {
        if (type == EntityType.ZOMBIE || type == EntityType.SKELETON || type == EntityType.CREEPER || 
            type == EntityType.SPIDER || type == EntityType.ENDERMAN || type == EntityType.BLAZE || 
            type == EntityType.GHAST || type == EntityType.WITCH || type == EntityType.PIGLIN || 
            type == EntityType.PIGLIN_BRUTE || type == EntityType.WARDEN || type == EntityType.SLIME || 
            type == EntityType.PHANTOM || type == EntityType.MAGMA_CUBE || type == EntityType.WITHER_SKELETON || 
            type == EntityType.CAVE_SPIDER || type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.ZOGLIN || 
            type == EntityType.HOGLIN || type == EntityType.BREEZE || type == EntityType.BOGGED || 
            type == EntityType.HUSK || type == EntityType.STRAY || type == EntityType.WITHER || 
            type == EntityType.ENDER_DRAGON || type == EntityType.DROWNED || type == EntityType.VINDICATOR || 
            type == EntityType.EVOKER || type == EntityType.PILLAGER || type == EntityType.RAVAGER || 
            type == EntityType.SHULKER || type == EntityType.SILVERFISH || type == EntityType.ENDERMITE || 
            type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN || type == EntityType.VEX || 
            type == EntityType.ZOMBIE_VILLAGER || type == EntityType.IRON_GOLEM || 
            type == EntityType.WOLF || type == EntityType.LLAMA || type == EntityType.PUFFERFISH || 
            type == EntityType.GOAT || type == EntityType.POLAR_BEAR || type == EntityType.DOLPHIN || 
            type == EntityType.PANDA) {
            return true;
        }
        return false;
    }

    private DifficultyTier determineDifficulty(EntityType<?> type) {
        if (type == EntityType.WARDEN || type == EntityType.WITHER || type == EntityType.ENDER_DRAGON || type == EntityType.ELDER_GUARDIAN || type == EntityType.EVOKER || type == EntityType.RAVAGER) {
            return DifficultyTier.HARD;
        } else if (type == EntityType.CREEPER || type == EntityType.ENDERMAN || type == EntityType.GHAST || 
                   type == EntityType.PIGLIN_BRUTE || type == EntityType.BREEZE || type == EntityType.VINDICATOR || type == EntityType.SHULKER || type == EntityType.PHANTOM || type == EntityType.BLAZE || type == EntityType.CAVE_SPIDER) {
            return DifficultyTier.MEDIUM;
        }
        return DifficultyTier.EASY;
    }
}
