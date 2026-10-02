package dev.frost.miniverse.minigame.impl.skywars;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Properties;

public final class SkywarsSessionBootstrap {
    private SkywarsSessionBootstrap() {}

    static void register() {
        MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<SkywarsMinigame>() {
            @Override
            public String gameId() {
                return SkywarsDefinition.ID;
            }

            @Override
            public Class<SkywarsMinigame> runtimeType() {
                return SkywarsMinigame.class;
            }

            @Override
            public SkywarsMinigame createRuntime() {
                return new SkywarsMinigame();
            }

            @Override
            public void applySettings(SkywarsMinigame minigame, Properties properties) {
                String mapConfig = properties.getProperty("mapConfig", properties.getProperty("skywars.mapConfig", "{}"));
                minigame.applySettings(SkywarsSettings.fromProperties(properties), mapConfig);
            }

            @Override
            public void onPlayerJoin(SkywarsMinigame minigame, ServerPlayerEntity player, Properties properties) {
                String teamKey = "player." + player.getUuid() + ".team";
                String displayKey = "player." + player.getUuid() + ".teamDisplayName";
                String team = properties.getProperty(displayKey, properties.getProperty(teamKey, "")).trim();
                if (!team.isBlank()) {
                    minigame.ensureTeamAssignment(player, team);
                } else {
                    minigame.ensureTeamAssignment(player, player.getName().getString());
                }
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(SkywarsMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(false)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal("SKYWARS"),
                        Text.literal("Prepare for battle!")
                    );
            }

            @Override
            public boolean canStart(SkywarsMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
