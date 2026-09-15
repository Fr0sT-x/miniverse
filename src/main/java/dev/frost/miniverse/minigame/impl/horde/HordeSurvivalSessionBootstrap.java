package dev.frost.miniverse.minigame.impl.horde;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Properties;

public final class HordeSurvivalSessionBootstrap {
    private HordeSurvivalSessionBootstrap() {
    }

    static void register() {
        MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<HordeSurvivalMinigame>() {
            @Override
            public String gameId() {
                return HordeSurvivalDefinition.ID;
            }

            @Override
            public Class<HordeSurvivalMinigame> runtimeType() {
                return HordeSurvivalMinigame.class;
            }

            @Override
            public HordeSurvivalMinigame createRuntime() {
                return new HordeSurvivalMinigame();
            }

            @Override
            public void applySettings(HordeSurvivalMinigame minigame, Properties properties) {
                minigame.applySettings(HordeSurvivalSettings.fromProperties(properties));
            }

            @Override
            public void onPlayerJoin(HordeSurvivalMinigame minigame, ServerPlayerEntity player, Properties properties) {
                minigame.addParticipantMidGame(player, "", "");
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(HordeSurvivalMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults("Horde Survival")
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(10)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal("Horde Survival"),
                        Text.literal("Survive escalating mob waves, fuel military transmitter pods, and extract!")
                    );
            }

            @Override
            public boolean canStart(HordeSurvivalMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
