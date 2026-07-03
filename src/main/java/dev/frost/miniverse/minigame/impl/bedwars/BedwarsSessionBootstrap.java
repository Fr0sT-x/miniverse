package dev.frost.miniverse.minigame.impl.bedwars;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.Properties;

public final class BedwarsSessionBootstrap {
    private BedwarsSessionBootstrap() {
    }

    public static void register() {
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<BedwarsMinigame>() {
            @Override
            public String gameId() {
                return BedwarsDefinition.ID;
            }

            @Override
            public Class<BedwarsMinigame> runtimeType() {
                return BedwarsMinigame.class;
            }

            @Override
            public BedwarsMinigame createRuntime() {
                return new BedwarsMinigame();
            }

            @Override
            public void applySettings(BedwarsMinigame minigame, Properties properties) {
                minigame.applySettings(
                    BedwarsSettings.fromProperties(properties),
                    BedwarsMapConfig.fromJsonString(properties.getProperty("bedwars.mapConfig", "{}"))
                );
            }

            @Override
            public void onPlayerJoin(BedwarsMinigame minigame, net.minecraft.server.network.ServerPlayerEntity player, Properties properties) {
                String gameTeamKey = "player." + player.getUuid() + ".gameTeamId";
                String teamKey = "player." + player.getUuid() + ".team";
                String displayKey = "player." + player.getUuid() + ".teamDisplayName";
                String gameTeamId = properties.getProperty(gameTeamKey, "").trim();
                if (!gameTeamId.isBlank()) {
                    if (!minigame.assignConfiguredMapTeam(player, gameTeamId)) {
                        dev.frost.miniverse.Miniverse.LOGGER.warn("Bedwars could not assign {} to configured map team '{}'. Falling back to legacy team labels.",
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
            public MatchLifecycleOptions lifecycleOptions(BedwarsMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(15)
                    .withReturnSeconds(15)
                    .withStartTitle(
                        Text.literal(minigame.getName()).formatted(Formatting.GOLD, Formatting.BOLD),
                        Text.literal("Defend your bed and destroy others.")
                    );
            }

            @Override
            public Optional<Text> startFailureMessage(BedwarsMinigame minigame) {
                var validation = minigame.startValidation();
                if (validation.valid()) {
                    return Optional.empty();
                }
                return Optional.of(Text.literal("Bedwars match cancelled: " + String.join("; ", validation.errors())).formatted(Formatting.RED));
            }

            @Override
            public boolean canStart(BedwarsMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
