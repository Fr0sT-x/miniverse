package dev.frost.miniverse.minigame.impl.zombies;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Properties;

public final class ZombiesSessionBootstrap {
    private ZombiesSessionBootstrap() {}

    public static void register() {
        MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<ZombiesMinigame>() {
            @Override
            public String gameId() {
                return ZombiesDefinition.ID;
            }

            @Override
            public Class<ZombiesMinigame> runtimeType() {
                return ZombiesMinigame.class;
            }

            @Override
            public ZombiesMinigame createRuntime() {
                return new ZombiesMinigame();
            }

            @Override
            public void applySettings(ZombiesMinigame minigame, Properties properties) {
                minigame.applySettings(
                    ZombiesSettings.fromProperties(properties),
                    dev.frost.miniverse.minigame.impl.zombies.map.ZombiesMapConfig.fromJsonString(properties.getProperty("zombies.mapConfig", "{}"))
                );
            }

            @Override
            public void onPlayerJoin(ZombiesMinigame minigame, ServerPlayerEntity player, Properties properties) {
                minigame.addParticipantMidGame(player, ZombiesMinigame.TEAM_SURVIVORS, "Survivors");
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(ZombiesMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(ZombiesDefinition.DISPLAY_NAME)
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(5)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal("ZOMBIES").formatted(Formatting.RED, Formatting.BOLD),
                        Text.literal("Survive 30 waves of the undead!").formatted(Formatting.YELLOW)
                    );
            }

            @Override
            public boolean canStart(ZombiesMinigame minigame) {
                return true;
            }
        });
    }
}
