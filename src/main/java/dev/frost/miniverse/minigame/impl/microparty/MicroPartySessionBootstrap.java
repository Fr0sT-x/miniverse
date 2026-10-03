package dev.frost.miniverse.minigame.impl.microparty;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.Properties;

public final class MicroPartySessionBootstrap {
    private MicroPartySessionBootstrap() {}

    public static void register() {
        MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<MicroPartyMinigame>() {
            @Override
            public String gameId() {
                return MicroPartyDefinition.ID;
            }

            @Override
            public Class<MicroPartyMinigame> runtimeType() {
                return MicroPartyMinigame.class;
            }

            @Override
            public MicroPartyMinigame createRuntime() {
                return new MicroPartyMinigame();
            }

            @Override
            public void applySettings(MicroPartyMinigame minigame, Properties properties) {
                String mapConfigStr = properties.getProperty("microparty.mapConfig", properties.getProperty("mapConfig", "{}"));
                minigame.applySettings(
                    MicroPartySettings.fromProperties(properties),
                    MicroPartyMapConfig.fromJsonString(mapConfigStr)
                );
            }

            @Override
            public void onPlayerJoin(MicroPartyMinigame minigame, ServerPlayerEntity player, Properties properties) {
                minigame.handlePlayerJoin(player);
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(MicroPartyMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(5)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal(minigame.getName()).formatted(Formatting.GOLD, Formatting.BOLD),
                        Text.literal("Think fast, react faster, survive the party!")
                    );
            }

            @Override
            public Optional<Text> startFailureMessage(MicroPartyMinigame minigame) {
                var validation = minigame.startValidation();
                if (validation.valid()) {
                    return Optional.empty();
                }
                return Optional.of(Text.literal("Micro Party match cancelled: " + String.join("; ", validation.errors())).formatted(Formatting.RED));
            }

            @Override
            public boolean canStart(MicroPartyMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
