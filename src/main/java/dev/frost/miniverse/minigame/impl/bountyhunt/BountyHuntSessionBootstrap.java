package dev.frost.miniverse.minigame.impl.bountyhunt;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.DisconnectGraceHandler;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Properties;

final class BountyHuntSessionBootstrap {
    private BountyHuntSessionBootstrap() {
    }

    static void register() {
        SessionBootstrapper.register(new SessionBootstrapper.Handler<BountyHuntMinigame>() {
            @Override
            public String gameId() {
                return BountyHuntDefinition.ID;
            }

            @Override
            public Class<BountyHuntMinigame> runtimeType() {
                return BountyHuntMinigame.class;
            }

            @Override
            public BountyHuntMinigame createRuntime() {
                return new BountyHuntMinigame();
            }

            @Override
            public void applySettings(BountyHuntMinigame minigame, Properties properties) {
                minigame.applySettings(BountyHuntSettings.fromProperties(properties));
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(BountyHuntMinigame minigame, Properties properties) {
                BountyHuntSettings settings = minigame.getSettings();
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withDisconnectGraceSeconds(settings.disconnectGraceSeconds())
                    .withDisconnectGraceHandler(new DisconnectGraceHandler() {
                        @Override
                        public boolean isCritical(dev.frost.miniverse.minigame.core.MinigameRuntime runtime, ServerPlayerEntity player) {
                            return minigame.getState().isActive()
                                && minigame.isActiveParticipant(player)
                                && minigame.getActiveParticipantCount() <= 2;
                        }

                        @Override
                        public void onGraceExpired(dev.frost.miniverse.minigame.core.MinigameRuntime runtime, java.util.List<java.util.UUID> pendingPlayers) {
                            minigame.handleDisconnectGraceExpired(pendingPlayers);
                        }
                    })
                    .withStartTitle(
                        Text.literal(minigame.getName()),
                        Text.literal("Eliminate your assigned targets. First to " + settings.scoreToWin() + " points wins.")
                    );
            }

            @Override
            public boolean canStart(BountyHuntMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
