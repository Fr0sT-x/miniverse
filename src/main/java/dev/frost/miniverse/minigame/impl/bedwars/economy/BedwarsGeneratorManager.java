package dev.frost.miniverse.minigame.impl.bedwars.economy;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMapConfig;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsSettings;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

public final class BedwarsGeneratorManager {
    private final List<BedwarsResourceGenerator> generators = new ArrayList<>();

    private final dev.frost.miniverse.minigame.impl.bedwars.HologramManager hologramManager;

    public BedwarsGeneratorManager(BedwarsMapConfig mapConfig, BedwarsSettings settings, dev.frost.miniverse.minigame.impl.bedwars.HologramManager hologramManager) {
        this.hologramManager = hologramManager;
        // Find iron/gold generators from team config (team islands)
        for (java.util.Map.Entry<String, BedwarsMapConfig.BedwarsTeamConfig> entry : mapConfig.teams().entrySet()) {
            String teamId = entry.getKey();
            BedwarsMapConfig.BedwarsTeamConfig teamConfig = entry.getValue();
            for (MapPosition pos : teamConfig.ironGens) {
                generators.add(new BedwarsResourceGenerator(pos, BedwarsCurrency.IRON, 20, 64, true, teamId));
            }
            for (MapPosition pos : teamConfig.goldGens) {
                generators.add(new BedwarsResourceGenerator(pos, BedwarsCurrency.GOLD, 160, 16, true, teamId));
            }
        }
        
        // Find diamond/emerald generators from markers
        for (MapPosition pos : mapConfig.midDiamondGens()) {
            BedwarsResourceGenerator gen = new BedwarsResourceGenerator(pos, BedwarsCurrency.DIAMOND, 500, 4, false, null);
            gen.setHologramManager(hologramManager);
            generators.add(gen);
        }
        for (MapPosition pos : mapConfig.midEmeraldGens()) {
            BedwarsResourceGenerator gen = new BedwarsResourceGenerator(pos, BedwarsCurrency.EMERALD, 700, 2, false, null);
            gen.setHologramManager(hologramManager);
            generators.add(gen);
        }
    }

    public void tick(ServerWorld world) {
        for (BedwarsResourceGenerator generator : generators) {
            generator.tick(world);
        }
    }

    public void setDiamondMultiplier(double multiplier) {
        for (BedwarsResourceGenerator gen : generators) {
            if (gen.getCurrency() == BedwarsCurrency.DIAMOND) {
                gen.setIntervalModifier(multiplier);
            }
        }
    }

    public void setEmeraldMultiplier(double multiplier) {
        for (BedwarsResourceGenerator gen : generators) {
            if (gen.getCurrency() == BedwarsCurrency.EMERALD) {
                gen.setIntervalModifier(multiplier);
            }
        }
    }

    public void setTeamForgeMultiplier(String teamId, double multiplier) {
        for (BedwarsResourceGenerator gen : generators) {
            if (teamId.equals(gen.getTeamId())) {
                gen.setIntervalModifier(multiplier);
            }
        }
    }
}
