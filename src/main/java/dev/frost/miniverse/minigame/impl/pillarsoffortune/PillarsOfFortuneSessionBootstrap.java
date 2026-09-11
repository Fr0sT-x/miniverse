package dev.frost.miniverse.minigame.impl.pillarsoffortune;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.text.Text;

import java.util.Properties;

public final class PillarsOfFortuneSessionBootstrap {
    private PillarsOfFortuneSessionBootstrap() {}

    static void register() {
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<PillarsOfFortuneMinigame>() {
            @Override
            public String gameId() {
                return PillarsOfFortuneDefinition.ID;
            }

            @Override
            public Class<PillarsOfFortuneMinigame> runtimeType() {
                return PillarsOfFortuneMinigame.class;
            }

            @Override
            public PillarsOfFortuneMinigame createRuntime() {
                return new PillarsOfFortuneMinigame();
            }

            @Override
            public void applySettings(PillarsOfFortuneMinigame minigame, Properties properties) {
                String mapConfig = properties.getProperty("mapConfig", properties.getProperty("pillarsoffortune.mapConfig", "{}"));
                minigame.applySettings(PillarsOfFortuneSettings.fromProperties(properties), mapConfig);
            }

            @Override
            public void onPlayerJoin(PillarsOfFortuneMinigame minigame, net.minecraft.server.network.ServerPlayerEntity player, Properties properties) {
                minigame.addParticipantMidGame(player, "", "");
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(PillarsOfFortuneMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(10)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal(minigame.getName()),
                        Text.literal("Survive on your pillar!")
                    );
            }

            @Override
            public boolean canStart(PillarsOfFortuneMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
