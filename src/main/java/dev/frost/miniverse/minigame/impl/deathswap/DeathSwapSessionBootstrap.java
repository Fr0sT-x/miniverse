package dev.frost.miniverse.minigame.impl.deathswap;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.text.Text;

import java.util.Properties;

public final class DeathSwapSessionBootstrap {
    private DeathSwapSessionBootstrap() {
    }

    static void register() {
        SessionBootstrapper.register(new SessionBootstrapper.Handler<DeathSwapMinigame>() {
            @Override
            public String gameId() {
                return DeathSwapDefinition.ID;
            }

            @Override
            public Class<DeathSwapMinigame> runtimeType() {
                return DeathSwapMinigame.class;
            }

            @Override
            public DeathSwapMinigame createRuntime() {
                return new DeathSwapMinigame();
            }

            @Override
            public void applySettings(DeathSwapMinigame minigame, Properties properties) {
                minigame.applySettings(DeathSwapSettings.fromProperties(properties));
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(DeathSwapMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(10)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal(minigame.getName()),
                        Text.literal("Survive random swaps. First to 5 points wins.")
                    );
            }

            @Override
            public boolean canStart(DeathSwapMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
