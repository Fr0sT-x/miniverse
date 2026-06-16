package dev.frost.miniverse.minigame.impl.duels;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.Properties;

final class DuelsSessionBootstrap {
    private DuelsSessionBootstrap() {
    }

    static void register() {
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<DuelsMinigame>() {
            @Override
            public String gameId() {
                return DuelsDefinition.ID;
            }

            @Override
            public Class<DuelsMinigame> runtimeType() {
                return DuelsMinigame.class;
            }

            @Override
            public DuelsMinigame createRuntime() {
                return new DuelsMinigame();
            }

            @Override
            public void applySettings(DuelsMinigame minigame, Properties properties) {
                minigame.applySettings(properties);
            }

            @Override
            public void onPlayerJoin(DuelsMinigame minigame, net.minecraft.server.network.ServerPlayerEntity player, Properties properties) {
                minigame.onPlayerJoin(player, properties);
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(DuelsMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(DuelsDefinition.DISPLAY_NAME)
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(3)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal(DuelsDefinition.DISPLAY_NAME),
                        Text.literal("Selected arena, kit, and teams are ready.")
                    );
            }

            @Override
            public Optional<Text> startFailureMessage(DuelsMinigame minigame) {
                if (minigame.canStartMatch()) {
                    return Optional.empty();
                }
                return Optional.of(Text.literal("Duel match cancelled: missing valid duel type, map arena, kit, or both teams.").formatted(Formatting.RED));
            }

            @Override
            public boolean canStart(DuelsMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
