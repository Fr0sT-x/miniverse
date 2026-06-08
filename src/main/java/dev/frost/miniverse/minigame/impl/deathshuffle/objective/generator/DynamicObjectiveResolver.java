package dev.frost.miniverse.minigame.impl.deathshuffle.objective.generator;

import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjective;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveSource;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DifficultyTier;
import net.minecraft.predicate.entity.LocationPredicate;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.DamagePredicate;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Optional;

public class DynamicObjectiveResolver {

    public static DeathObjective resolve(Identifier id) {
        String path = id.getPath();
        String[] parts = path.split("/");

        if (parts.length >= 4 && parts[1].equals("y_level")) {
            String dir = parts[2];
            int y;
            try {
                y = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                return null;
            }

            boolean isAbove = dir.equals("above");
            
            NumberRange.DoubleRange range;
            if (isAbove) {
                range = NumberRange.DoubleRange.atLeast((double) y);
            } else {
                range = NumberRange.DoubleRange.atMost((double) y);
            }

            LocationPredicate loc = LocationPredicate.Builder.create().y(range).build();

            return new DeathObjective(
                Text.literal("Y-Level (" + (isAbove ? "Above " : "Below ") + y + ")").formatted(Formatting.AQUA, Formatting.BOLD),
                Identifier.of("minecraft:compass"),
                Optional.of(Text.literal("Die while " + (isAbove ? "above" : "below") + " Y-level " + y)),
                Optional.<DamagePredicate>empty(),
                Optional.of(loc),
                DeathObjectiveSource.CONDITION,
                DifficultyTier.MEDIUM
            );
        }

        return null;
    }
}
