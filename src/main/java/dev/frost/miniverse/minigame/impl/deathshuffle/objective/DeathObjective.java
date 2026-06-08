package dev.frost.miniverse.minigame.impl.deathshuffle.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.predicate.DamagePredicate;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

import java.util.Optional;

public record DeathObjective(
    Text displayName, 
    Identifier icon, 
    Optional<Text> description, 
    Optional<DamagePredicate> damageCondition,
    Optional<net.minecraft.predicate.entity.LocationPredicate> locationCondition,
    DeathObjectiveSource source,
    DifficultyTier difficulty
) {
    public static final Codec<DeathObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        TextCodecs.CODEC.fieldOf("display_name").forGetter(DeathObjective::displayName),
        Identifier.CODEC.fieldOf("icon").forGetter(DeathObjective::icon),
        TextCodecs.CODEC.optionalFieldOf("description").forGetter(DeathObjective::description),
        DamagePredicate.CODEC.optionalFieldOf("damage_condition").forGetter(DeathObjective::damageCondition),
        net.minecraft.predicate.entity.LocationPredicate.CODEC.optionalFieldOf("location_condition").forGetter(DeathObjective::locationCondition),
        StringIdentifiable.createCodec(DeathObjectiveSource::values).optionalFieldOf("source", DeathObjectiveSource.DATAPACK).forGetter(DeathObjective::source),
        StringIdentifiable.createCodec(DifficultyTier::values).optionalFieldOf("difficulty", DifficultyTier.MEDIUM).forGetter(DeathObjective::difficulty)
    ).apply(instance, DeathObjective::new));

    public static final RegistryKey<Registry<DeathObjective>> REGISTRY_KEY = RegistryKey.ofRegistry(Identifier.of("miniverse", "death_objective"));
}
