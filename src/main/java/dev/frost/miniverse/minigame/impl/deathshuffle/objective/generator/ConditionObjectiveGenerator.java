package dev.frost.miniverse.minigame.impl.deathshuffle.objective.generator;

import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjective;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveSource;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DifficultyTier;
import net.minecraft.predicate.DamagePredicate;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LocationPredicate;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ConditionObjectiveGenerator implements DeathObjectiveGenerator {

    @Override
    public Map<Identifier, DeathObjective> generate() {
        Map<Identifier, DeathObjective> generated = new LinkedHashMap<>();

        add(generated, "in_nether", "In the Nether", "minecraft:netherrack", DifficultyTier.MEDIUM, Formatting.DARK_RED, LocationPredicate.Builder.createDimension(net.minecraft.world.World.NETHER).build());
        add(generated, "in_end", "In The End", "minecraft:end_stone", DifficultyTier.HARD, Formatting.DARK_PURPLE, LocationPredicate.Builder.createDimension(net.minecraft.world.World.END).build());
        
        generated.put(Identifier.of("miniverse", "template/y_level"), new DeathObjective(
            Text.literal("Y-Level (Config)").formatted(Formatting.AQUA, Formatting.BOLD),
            Identifier.of("minecraft:compass"),
            Optional.of(Text.literal("Click to configure a specific Y-level death objective")),
            Optional.<DamagePredicate>empty(),
            Optional.<net.minecraft.predicate.entity.LocationPredicate>empty(),
            DeathObjectiveSource.CONDITION,
            DifficultyTier.MEDIUM
        ));

        return generated;
    }

    private void add(Map<Identifier, DeathObjective> map, String path, String name, String icon, DifficultyTier difficulty, Formatting color, LocationPredicate location) {
        Identifier objId = Identifier.of("miniverse", "condition/" + path);
        
        Text displayName = Text.literal(name).formatted(color, Formatting.BOLD);
        Optional<Text> description = Optional.of(Text.literal("Die while " + name.toLowerCase()));

        DeathObjective objective = new DeathObjective(
            displayName,
            Identifier.of(icon),
            description,
            Optional.<DamagePredicate>empty(),
            Optional.of(location),
            DeathObjectiveSource.CONDITION,
            difficulty
        );

        map.put(objId, objective);
    }
}
