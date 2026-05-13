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
                return MatchLifecycleOptions.defaults(minigame.getName())
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
