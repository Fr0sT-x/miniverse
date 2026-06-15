package dev.frost.miniverse.minigame.impl.manhunt;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame.ManhuntRole;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Properties;

final class ManhuntSessionBootstrap {
    private ManhuntSessionBootstrap() {
    }

    static void register() {
        SessionBootstrapper.register(new SessionBootstrapper.Handler<ManhuntMinigame>() {
            @Override
            public String gameId() {
                return ManhuntDefinition.ID;
            }

            @Override
            public Class<ManhuntMinigame> runtimeType() {
                return ManhuntMinigame.class;
            }

            @Override
            public ManhuntMinigame createRuntime() {
                return new ManhuntMinigame();
            }

            @Override
            public void applySettings(ManhuntMinigame minigame, Properties properties) {
                minigame.applySettings(ManhuntSettings.fromProperties(properties));
            }

            @Override
            public void onPlayerJoin(ManhuntMinigame minigame, ServerPlayerEntity player, Properties properties) {
                ManhuntRole role = parseRole(properties.getProperty("manhunt.role." + player.getUuid()));
                if (role != null) {
                    minigame.setPlayerRole(player, role);
                }
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(ManhuntMinigame minigame, Properties properties) {
                ManhuntSettings settings = minigame.getSettings();
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withDisconnectGraceSeconds(settings.disconnectGraceSeconds())
                    .withDisconnectGraceHandler(new DisconnectGraceHandler() {
                        @Override
                        public boolean isCritical(dev.frost.miniverse.minigame.core.MinigameRuntime runtime, ServerPlayerEntity player) {
                            if (!minigame.getState().isActive()) return false;
                            if (minigame.getActiveHunters().isEmpty()) return true;
                            return minigame.getPlayerRole(player) == ManhuntMinigame.ManhuntRole.SPEEDRUNNER
                                && minigame.getAliveSpeedrunnerCount() <= 1;
                        }

                        @Override
                        public void onGraceExpired(dev.frost.miniverse.minigame.core.MinigameRuntime runtime, java.util.List<java.util.UUID> pendingPlayers) {
                            minigame.handleDisconnectGraceExpired(pendingPlayers);
                        }
                    })
                    .withStartTitle(
                        Text.literal(minigame.getName()),
                        Text.literal("Speedrunners defeat the dragon. Hunters win by stopping every runner.")
                    );
            }

            @Override
            public boolean canStart(ManhuntMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }

    private static ManhuntRole parseRole(String role) {
        if (role == null) {
            return null;
        }
        return switch (role.toLowerCase()) {
            case "speedrunner", "runner" -> ManhuntRole.SPEEDRUNNER;
            case "hunter" -> ManhuntRole.HUNTER;
            default -> null;
        };
    }
}
