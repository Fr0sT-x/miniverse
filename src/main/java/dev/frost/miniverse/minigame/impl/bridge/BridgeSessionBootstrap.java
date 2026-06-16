package dev.frost.miniverse.minigame.impl.bridge;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.Properties;

final class BridgeSessionBootstrap {
    private BridgeSessionBootstrap() {
    }

    static void register() {
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<BridgeMinigame>() {
            @Override
            public String gameId() {
                return BridgeDefinition.ID;
            }

            @Override
            public Class<BridgeMinigame> runtimeType() {
                return BridgeMinigame.class;
            }

            @Override
            public BridgeMinigame createRuntime() {
                return new BridgeMinigame();
            }

            @Override
            public void applySettings(BridgeMinigame minigame, Properties properties) {
                minigame.applySettings(
                    BridgeSettings.fromProperties(properties),
                    BridgeMapConfig.fromJsonString(properties.getProperty("bridge.mapConfig", "{}"))
                );
            }

            @Override
            public void onPlayerJoin(BridgeMinigame minigame, net.minecraft.server.network.ServerPlayerEntity player, Properties properties) {
                // Determine team from session properties
                String teamKey = "player." + player.getUuid() + ".team";
                String team = properties.getProperty(teamKey, "").trim().toLowerCase();
                if (team.isBlank()) {
                    // Fallback: check groupLabel
                    team = properties.getProperty("groupLabel", "").trim().toLowerCase();
                }
                // Assign to team sets if not already assigned
                if (BridgeMinigame.RED_TEAM.equalsIgnoreCase(team)) {
                    minigame.ensureTeamAssignment(player, BridgeMinigame.RED_TEAM);
                } else if (BridgeMinigame.BLUE_TEAM.equalsIgnoreCase(team)) {
                    minigame.ensureTeamAssignment(player, BridgeMinigame.BLUE_TEAM);
                }
                // Teleport immediately to team spawn for void safety
                minigame.teleportToTeamSpawnSafe(player);
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(BridgeMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(10)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal(minigame.getName()),
                        Text.literal("Cross the bridge. Enter the enemy goal.")
                    );
            }

            @Override
            public Optional<Text> startFailureMessage(BridgeMinigame minigame) {
                var validation = minigame.startValidation();
                if (validation.valid()) {
                    return Optional.empty();
                }
                return Optional.of(Text.literal("Bridge match cancelled: " + String.join("; ", validation.errors())).formatted(Formatting.RED));
            }

            @Override
            public boolean canStart(BridgeMinigame minigame) {
                return minigame.canStartMatch();
            }
        });
    }
}
