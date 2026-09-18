package dev.frost.miniverse.minigame.impl.dropper;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.Properties;

public final class DropperSessionBootstrap {
    private DropperSessionBootstrap() {}

    public static void register() {
        MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<DropperMinigame>() {
            @Override
            public String gameId() {
                return DropperDefinition.ID;
            }

            @Override
            public Class<DropperMinigame> runtimeType() {
                return DropperMinigame.class;
            }

            @Override
            public DropperMinigame createRuntime() {
                return new DropperMinigame();
            }

            @Override
            public void applySettings(DropperMinigame minigame, Properties properties) {
                String mapConfigStr = properties.getProperty("dropper.mapConfig", properties.getProperty("mapConfig", "{}"));
                minigame.applySettings(
                    DropperSettings.fromProperties(properties),
                    DropperMapConfig.fromJsonString(mapConfigStr)
                );
            }

            @Override
            public void onPlayerJoin(DropperMinigame minigame, ServerPlayerEntity player, Properties properties) {
                minigame.handlePlayerJoin(player);
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(DropperMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(5)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal(minigame.getName()).formatted(Formatting.GOLD, Formatting.BOLD),
                        Text.literal("Drop and dodge obstacles to land safely!")
                    );
            }

            @Override
            public Optional<Text> startFailureMessage(DropperMinigame minigame) {
                var validation = minigame.startValidation();
                if (validation.valid()) {
                    return Optional.empty();
                }
                return Optional.of(Text.literal("Dropper match cancelled: " + String.join("; ", validation.errors())).formatted(Formatting.RED));
            }

            @Override
            public boolean canStart(DropperMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
