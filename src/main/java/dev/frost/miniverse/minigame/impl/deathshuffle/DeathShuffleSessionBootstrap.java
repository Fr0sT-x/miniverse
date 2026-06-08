package dev.frost.miniverse.minigame.impl.deathshuffle;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.Properties;

public final class DeathShuffleSessionBootstrap {
    private DeathShuffleSessionBootstrap() {
    }

    public static void register() {
        SessionBootstrapper.register(new SessionBootstrapper.Handler<DeathShuffleMinigame>() {
            @Override
            public String gameId() {
                return DeathShuffleDefinition.ID;
            }

            @Override
            public Class<DeathShuffleMinigame> runtimeType() {
                return DeathShuffleMinigame.class;
            }

            @Override
            public DeathShuffleMinigame createRuntime() {
                return new DeathShuffleMinigame();
            }

            @Override
            public void applySettings(DeathShuffleMinigame minigame, Properties properties) {
                DeathShuffleSettings settings = DeathShuffleSettings.fromProperties(properties);
                minigame.applySettings(settings);
            }

            @Override
            public void onPlayerJoin(DeathShuffleMinigame minigame, ServerPlayerEntity player, Properties properties) {
            }

            @Override
            public Optional<Text> startFailureMessage(DeathShuffleMinigame minigame) {
                return Optional.empty();
            }

            @Override
            public boolean canStart(DeathShuffleMinigame minigame) {
                return true;
            }
        });
    }
}
