package dev.frost.miniverse.minigame.impl.deathshuffle.objective.generator;

import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjective;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveSource;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DifficultyTier;
import net.minecraft.predicate.DamagePredicate;
import net.minecraft.predicate.entity.DamageSourcePredicate;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class DamageTypeObjectiveGenerator implements DeathObjectiveGenerator {

    @Override
    public Map<Identifier, DeathObjective> generate() {
        Map<Identifier, DeathObjective> generated = new LinkedHashMap<>();

        add(generated, "fall", "Fall Damage", "minecraft:iron_boots", "minecraft:is_fall", DifficultyTier.EASY, Formatting.RED);
        add(generated, "fire", "Fire", "minecraft:blaze_powder", "minecraft:is_fire", DifficultyTier.EASY, Formatting.GOLD);
        add(generated, "lava", "Lava", "minecraft:lava_bucket", "miniverse:is_lava", DifficultyTier.EASY, Formatting.GOLD);
        add(generated, "drowning", "Drowning", "minecraft:water_bucket", "minecraft:is_drowning", DifficultyTier.EASY, Formatting.BLUE);
        add(generated, "explosion", "Explosion", "minecraft:tnt", "minecraft:is_explosion", DifficultyTier.MEDIUM, Formatting.RED);
        add(generated, "freezing", "Freezing", "minecraft:powder_snow_bucket", "minecraft:is_freezing", DifficultyTier.MEDIUM, Formatting.AQUA);
        add(generated, "projectile", "Projectile", "minecraft:arrow", "minecraft:is_projectile", DifficultyTier.MEDIUM, Formatting.GRAY);
        add(generated, "lightning", "Lightning", "minecraft:lightning_rod", "minecraft:is_lightning", DifficultyTier.HARD, Formatting.YELLOW);
        add(generated, "magic", "Magic", "minecraft:potion", "minecraft:is_magic", DifficultyTier.MEDIUM, Formatting.DARK_PURPLE);
        add(generated, "void", "The Void", "minecraft:end_portal_frame", "minecraft:bypasses_invulnerability", DifficultyTier.HARD, Formatting.DARK_GRAY);
        
        add(generated, "sweet_berries", "Sweet Berries", "minecraft:sweet_berries", "miniverse:is_sweet_berries", DifficultyTier.EASY, Formatting.RED);
        add(generated, "cramming", "Entity Cramming", "minecraft:minecart", "miniverse:is_cramming", DifficultyTier.MEDIUM, Formatting.GRAY);
        add(generated, "suffocation", "Suffocation", "minecraft:sand", "miniverse:is_suffocation", DifficultyTier.MEDIUM, Formatting.YELLOW);
        add(generated, "cactus", "Cactus", "minecraft:cactus", "miniverse:is_cactus", DifficultyTier.EASY, Formatting.GREEN);
        add(generated, "fireworks", "Firework Rocket", "minecraft:firework_rocket", "miniverse:is_fireworks", DifficultyTier.MEDIUM, Formatting.WHITE);
        add(generated, "falling_anvil", "Falling Anvil", "minecraft:anvil", "miniverse:is_falling_anvil", DifficultyTier.HARD, Formatting.DARK_GRAY);

        add(generated, "starvation", "Starvation", "minecraft:rotten_flesh", "miniverse:is_starving", DifficultyTier.MEDIUM, Formatting.GREEN);
        add(generated, "kinetic_energy", "Kinetic Energy", "minecraft:elytra", "miniverse:is_kinetic", DifficultyTier.HARD, Formatting.GRAY);
        add(generated, "magma_block", "Magma Block", "minecraft:magma_block", "miniverse:is_magma", DifficultyTier.EASY, Formatting.GOLD);
        add(generated, "stalagmite", "Stalagmite", "minecraft:pointed_dripstone", "miniverse:is_stalagmite", DifficultyTier.MEDIUM, Formatting.GRAY);
        add(generated, "falling_stalactite", "Falling Stalactite", "minecraft:pointed_dripstone", "miniverse:is_falling_stalactite", DifficultyTier.MEDIUM, Formatting.GRAY);
        add(generated, "intentional_game_design", "Intentional Game Design", "minecraft:red_bed", "miniverse:is_bad_respawn", DifficultyTier.MEDIUM, Formatting.RED);
        add(generated, "wither_effect", "Wither Effect", "minecraft:wither_rose", "miniverse:is_wither", DifficultyTier.MEDIUM, Formatting.DARK_GRAY);
        add(generated, "instant_harming", "Instant Harming", "minecraft:potion", "miniverse:is_magic_indirect", DifficultyTier.MEDIUM, Formatting.DARK_PURPLE);
        add(generated, "ghast_fireball", "Ghast Fireball", "minecraft:fire_charge", "miniverse:is_fireball", DifficultyTier.MEDIUM, Formatting.GOLD);
        add(generated, "trident_impalement", "Trident Impalement", "minecraft:trident", "miniverse:is_trident", DifficultyTier.MEDIUM, Formatting.AQUA);

        // Ender Pearl (Fall damage from Ender Pearl entity)
        DamagePredicate enderPearlPredicate = DamagePredicate.Builder.create()
            .type(DamageSourcePredicate.Builder.create()
                .tag(net.minecraft.predicate.TagPredicate.expected(TagKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE, Identifier.of("minecraft:is_fall"))))
                .directEntity(net.minecraft.predicate.entity.EntityPredicate.Builder.create().type(net.minecraft.entity.EntityType.ENDER_PEARL))
                .build())
            .build();
        generated.put(Identifier.of("miniverse", "damage/ender_pearl"), new DeathObjective(
            Text.literal("Ender Pearl").formatted(Formatting.DARK_GREEN, Formatting.BOLD),
            Identifier.of("minecraft:ender_pearl"),
            Optional.of(Text.literal("Die from ender pearl teleportation damage")),
            Optional.of(enderPearlPredicate),
            Optional.empty(),
            DeathObjectiveSource.DAMAGE,
            DifficultyTier.MEDIUM
        ));

        return generated;
    }

    private void add(Map<Identifier, DeathObjective> map, String path, String name, String icon, String tag, DifficultyTier difficulty, Formatting color) {
        Identifier objId = Identifier.of("miniverse", "damage/" + path);
        
        Text displayName = Text.literal(name).formatted(color, Formatting.BOLD);
        Optional<Text> description = Optional.of(Text.literal("Die from " + name.toLowerCase()));

        DamagePredicate damagePredicate = DamagePredicate.Builder.create()
            .type(DamageSourcePredicate.Builder.create().tag(net.minecraft.predicate.TagPredicate.expected(TagKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE, Identifier.of(tag)))).build())
            .build();

        DeathObjective objective = new DeathObjective(
            displayName,
            Identifier.of(icon),
            description,
            Optional.of(damagePredicate),
            Optional.empty(),
            DeathObjectiveSource.DAMAGE,
            difficulty
        );

        map.put(objId, objective);
    }
}
