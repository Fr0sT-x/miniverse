package dev.frost.miniverse.minigame.core.visibility;

import dev.frost.miniverse.minigame.core.role.RoleManager;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamDescriptor;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import net.minecraft.scoreboard.AbstractTeam.VisibilityRule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.scoreboard.AbstractTeam;

import java.util.List;
import java.util.stream.Collectors;

public class VisibilityManager {
    private final VanillaTeamAdapter teamAdapter;
    private final RoleManager roleManager;

    public VisibilityManager(String namespace, RoleManager roleManager) {
        this.teamAdapter = new VanillaTeamAdapter(namespace);
        this.roleManager = roleManager;
        this.teamAdapter.setFriendlyFireAllowed(true);
        this.teamAdapter.setTeammateCollisionAllowed(true);
    }

    public void sync(MinecraftServer server) {
        List<ServerPlayerEntity> allPlayers = roleManager.getAllPlayers().stream()
            .map(uuid -> server.getPlayerManager().getPlayer(uuid))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());

        List<ServerPlayerEntity> spectators = allPlayers.stream()
            .filter(p -> roleManager.getRoles(p).stream().anyMatch(dev.frost.miniverse.minigame.core.role.Role::isSpectator))
            .collect(Collectors.toList());

        List<ServerPlayerEntity> activePlayers = allPlayers.stream()
            .filter(p -> !spectators.contains(p))
            .collect(Collectors.toList());

        VanillaTeamDescriptor activeDescriptor = new VanillaTeamDescriptor(
            "active",
            Text.literal("Active"),
            activePlayers,
            VanillaTeamOptions.defaults()
                .withColor(Formatting.WHITE)
                .withNameTagVisibility(AbstractTeam.VisibilityRule.NEVER)
        );

        VanillaTeamDescriptor spectatorDescriptor = new VanillaTeamDescriptor(
            "spectator",
            Text.literal("Spectator"),
            spectators,
            VanillaTeamOptions.defaults()
                .withNameTagVisibility(AbstractTeam.VisibilityRule.HIDE_FOR_OTHER_TEAMS)
                .withShowFriendlyInvisibles(true)
        );

        teamAdapter.sync(server, List.of(activeDescriptor, spectatorDescriptor));
    }

    public void clear(MinecraftServer server) {
        teamAdapter.clear(server);
    }
}
