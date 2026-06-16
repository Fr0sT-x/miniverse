package dev.frost.miniverse.minigame.impl.infection;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.text.Text;

import java.util.Properties;

final class InfectionSessionBootstrap {
    private InfectionSessionBootstrap() {
    }

    static void register() {
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<InfectionMinigame>() {
            @Override
            public String gameId() {
                return InfectionDefinition.ID;
            }

            @Override
            public Class<InfectionMinigame> runtimeType() {
                return InfectionMinigame.class;
            }

            @Override
            public InfectionMinigame createRuntime() {
                return new InfectionMinigame();
            }

            @Override
            public void applySettings(InfectionMinigame minigame, Properties properties) {
                minigame.applySettings(
                    InfectionSettings.fromProperties(properties),
                    InfectionMapConfig.fromJsonString(properties.getProperty("infection.mapConfig", "{}"))
                );
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(InfectionMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(10)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal(minigame.getName()),
                        Text.literal("Survivors win by lasting the timer. Infected win by converting everyone.")
                    );
            }

            @Override
            public boolean canStart(InfectionMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
