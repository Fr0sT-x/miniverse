package dev.frost.miniverse.minigame.impl.microfrenzy;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.Properties;

public final class MicroFrenzySessionBootstrap {
    private MicroFrenzySessionBootstrap() {}

    public static void register() {
        MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<MicroFrenzyMinigame>() {
            @Override
            public String gameId() {
                return MicroFrenzyDefinition.ID;
            }

            @Override
            public Class<MicroFrenzyMinigame> runtimeType() {
                return MicroFrenzyMinigame.class;
            }

            @Override
            public MicroFrenzyMinigame createRuntime() {
                return new MicroFrenzyMinigame();
            }

            @Override
            public void applySettings(MicroFrenzyMinigame minigame, Properties properties) {
                String mapConfigStr = properties.getProperty("microfrenzy.mapConfig", properties.getProperty("mapConfig", "{}"));
                minigame.applySettings(
                    MicroFrenzySettings.fromProperties(properties),
                    MicroFrenzyMapConfig.fromJsonString(mapConfigStr)
                );
            }

            @Override
            public void onPlayerJoin(MicroFrenzyMinigame minigame, ServerPlayerEntity player, Properties properties) {
                minigame.handlePlayerJoin(player);
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(MicroFrenzyMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(5)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal(minigame.getName()).formatted(Formatting.GOLD, Formatting.BOLD),
                        Text.literal("Think fast, react faster, survive the frenzy!")
                    );
            }

            @Override
            public Optional<Text> startFailureMessage(MicroFrenzyMinigame minigame) {
                var validation = minigame.startValidation();
                if (validation.valid()) {
                    return Optional.empty();
                }
                return Optional.of(Text.literal("Micro-Frenzy match cancelled: " + String.join("; ", validation.errors())).formatted(Formatting.RED));
            }

            @Override
            public boolean canStart(MicroFrenzyMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
