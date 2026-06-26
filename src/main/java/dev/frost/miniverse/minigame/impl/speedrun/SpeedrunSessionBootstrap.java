package dev.frost.miniverse.minigame.impl.speedrun;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Properties;

final class SpeedrunSessionBootstrap {
    private SpeedrunSessionBootstrap() {
    }

    static void register() {
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<SpeedrunMinigame>() {
            @Override
            public String gameId() {
                return SpeedrunDefinition.ID;
            }

            @Override
            public Class<SpeedrunMinigame> runtimeType() {
                return SpeedrunMinigame.class;
            }

            @Override
            public SpeedrunMinigame createRuntime() {
                return new SpeedrunMinigame();
            }

            @Override
            public void applySettings(SpeedrunMinigame minigame, Properties properties) {
                try {
                    int minutes = Integer.parseInt(properties.getProperty("timeLimitMinutes", "0"));
                    if (minutes > 0) {
                        minigame.setTimeLimitTicks(minutes * 60 * 20);
                    }
                } catch (NumberFormatException ignored) {}
            }

            @Override
            public void onPlayerJoin(SpeedrunMinigame minigame, ServerPlayerEntity player, Properties properties) {
                if (minigame.getState() == GameState.WAITING_FOR_PLAYERS) {
                    minigame.addParticipantMidGame(player, "", "");
                    return;
                }
                minigame.syncLateParticipant(player);
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(SpeedrunMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(10)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal(minigame.getName()),
                        Text.literal("Beat Minecraft as fast as possible.")
                    );
            }

            @Override
            public boolean canStart(SpeedrunMinigame minigame) {
                return minigame.canStartRun();
            }
        });
    }
}
