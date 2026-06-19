package dev.frost.miniverse.minigame.impl.murdermystery;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;

import java.util.Properties;

public class MurderMysterySessionBootstrap {
    public static void bootstrap() {
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<MurderMysteryMinigame>() {
            @Override
            public String gameId() {
                return MurderMysteryDefinition.ID;
            }

            @Override
            public Class<MurderMysteryMinigame> runtimeType() {
                return MurderMysteryMinigame.class;
            }

            @Override
            public MurderMysteryMinigame createRuntime() {
                return new MurderMysteryMinigame();
            }

            @Override
            public void applySettings(MurderMysteryMinigame minigame, Properties properties) {
                MurderMysterySettings settings = MurderMysterySettings.fromProperties(properties);
                String preSerializedMapConfig = properties.getProperty("murdermystery.mapConfig", "");
                minigame.applySettings(settings, preSerializedMapConfig);
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(MurderMysteryMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(MurderMysteryDefinition.DISPLAY_NAME)
                    .withFreezeEnabled(true)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        net.minecraft.text.Text.literal(MurderMysteryDefinition.DISPLAY_NAME),
                        net.minecraft.text.Text.literal("Roles have been assigned.")
                    );
            }

            @Override
            public boolean canStart(MurderMysteryMinigame minigame) {
                return true;
            }
        });
    }
}
