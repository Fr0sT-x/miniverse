package dev.frost.miniverse.minigame.impl.hideandseek;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Properties;

public final class HideAndSeekSessionBootstrap {
    private HideAndSeekSessionBootstrap() {}

    static void register() {
        MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<HideAndSeekMinigame>() {
            @Override
            public String gameId() {
                return HideAndSeekDefinition.ID;
            }

            @Override
            public Class<HideAndSeekMinigame> runtimeType() {
                return HideAndSeekMinigame.class;
            }

            @Override
            public HideAndSeekMinigame createRuntime() {
                return new HideAndSeekMinigame();
            }

            @Override
            public void applySettings(HideAndSeekMinigame minigame, Properties properties) {
                String mapConfig = properties.getProperty("mapConfig", properties.getProperty("hideandseek.mapConfig", "{}"));
                String disguiseBlocks = properties.getProperty("hideandseek.disguiseBlocks", "");
                minigame.applySettings(HideAndSeekSettings.fromProperties(properties), mapConfig, disguiseBlocks);
            }

            @Override
            public void onPlayerJoin(HideAndSeekMinigame minigame, ServerPlayerEntity player, Properties properties) {
                String teamKey = "player." + player.getUuid() + ".team";
                String displayKey = "player." + player.getUuid() + ".teamDisplayName";
                String team = properties.getProperty(displayKey, properties.getProperty(teamKey, "")).trim();
                if (!team.isBlank()) {
                    minigame.ensureTeamAssignment(player, team);
                } else {
                    minigame.ensureTeamAssignment(player, HideAndSeekMinigame.TEAM_HIDERS);
                }
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(HideAndSeekMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(false)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal("HIDE AND SEEK"),
                        Text.literal("Blend in as a block!")
                    );
            }

            @Override
            public boolean canStart(HideAndSeekMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
