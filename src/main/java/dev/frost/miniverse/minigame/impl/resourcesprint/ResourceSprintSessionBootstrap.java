package dev.frost.miniverse.minigame.impl.resourcesprint;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Properties;

final class ResourceSprintSessionBootstrap {
    private ResourceSprintSessionBootstrap() {
    }

    static void register() {
        SessionBootstrapper.register(new SessionBootstrapper.Handler<ResourceSprintMinigame>() {
            @Override
            public String gameId() {
                return ResourceSprintDefinition.ID;
            }

            @Override
            public Class<ResourceSprintMinigame> runtimeType() {
                return ResourceSprintMinigame.class;
            }

            @Override
            public ResourceSprintMinigame createRuntime() {
                return new ResourceSprintMinigame();
            }

            @Override
            public void applySettings(ResourceSprintMinigame minigame, Properties properties) {
                minigame.applySettings(ResourceSprintSettings.fromProperties(properties));
            }

            @Override
            public void onPlayerJoin(ResourceSprintMinigame minigame, ServerPlayerEntity player, Properties properties) {
                minigame.setPlayerTeam(player, properties.getProperty("player." + player.getUuid() + ".team", properties.getProperty("groupLabel", "Team")));
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(ResourceSprintMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withStartTitle(
                        Text.literal(minigame.getName()),
                        Text.literal("Race through the objective chain. The fastest team to complete it wins.")
                    );
            }

            @Override
            public boolean canStart(ResourceSprintMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
