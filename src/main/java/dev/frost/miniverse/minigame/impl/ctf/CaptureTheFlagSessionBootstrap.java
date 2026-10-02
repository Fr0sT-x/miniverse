package dev.frost.miniverse.minigame.impl.ctf;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.Properties;

public final class CaptureTheFlagSessionBootstrap {
    private CaptureTheFlagSessionBootstrap() {
    }

    public static void register() {
        MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<CaptureTheFlagMinigame>() {
            @Override
            public String gameId() {
                return CaptureTheFlagDefinition.ID;
            }

            @Override
            public Class<CaptureTheFlagMinigame> runtimeType() {
                return CaptureTheFlagMinigame.class;
            }

            @Override
            public CaptureTheFlagMinigame createRuntime() {
                return new CaptureTheFlagMinigame();
            }

            @Override
            public void applySettings(CaptureTheFlagMinigame minigame, Properties properties) {
                minigame.applySettings(
                    CaptureTheFlagSettings.fromProperties(properties),
                    CaptureTheFlagMapConfig.fromJsonString(properties.getProperty("ctf.mapConfig", "{}"))
                );
            }

            @Override
            public void onPlayerJoin(CaptureTheFlagMinigame minigame, ServerPlayerEntity player, Properties properties) {
                String gameTeamKey = "player." + player.getUuid() + ".gameTeamId";
                String teamKey = "player." + player.getUuid() + ".team";
                String displayKey = "player." + player.getUuid() + ".teamDisplayName";
                String gameTeamId = properties.getProperty(gameTeamKey, "").trim();
                if (!gameTeamId.isBlank()) {
                    if (!minigame.assignConfiguredMapTeam(player, gameTeamId)) {
                        Miniverse.LOGGER.warn("CTF could not assign {} to configured map team '{}'. Falling back to legacy team labels.",
                            player.getName().getString(), gameTeamId);
                    } else {
                        return;
                    }
                }

                String team = properties.getProperty(displayKey, properties.getProperty(teamKey, "")).trim();
                if (!team.isBlank()) {
                    minigame.ensureTeamAssignment(player, team);
                }
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(CaptureTheFlagMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(10)
                    .withReturnSeconds(15)
                    .withStartTitle(
                        Text.literal(minigame.getName()).formatted(Formatting.GOLD, Formatting.BOLD),
                        Text.literal("Capture enemy flags and protect your base!")
                    );
            }

            @Override
            public Optional<Text> startFailureMessage(CaptureTheFlagMinigame minigame) {
                var validation = minigame.startValidation();
                if (validation.valid()) {
                    return Optional.empty();
                }
                return Optional.of(Text.literal("CTF match cancelled: " + String.join("; ", validation.errors())).formatted(Formatting.RED));
            }

            @Override
            public boolean canStart(CaptureTheFlagMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
