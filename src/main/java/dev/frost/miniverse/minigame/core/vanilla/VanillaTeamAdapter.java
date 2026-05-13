package dev.frost.miniverse.minigame.core.vanilla;

import dev.frost.miniverse.team.TeamColorPalette;
import dev.frost.miniverse.team.TeamSnapshot;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Mirrors Miniverse custom team state into vanilla scoreboard teams.
 * Gameplay logic must continue reading custom minigame/session state, never scoreboard teams.
 */
public final class VanillaTeamAdapter {
    private final String namespace;
    private final Map<String, String> logicalToScoreboardName = new HashMap<>();
    private final Set<String> ownedScoreboardTeams = new HashSet<>();
    private boolean friendlyFireAllowed = true;
    private boolean teammateCollisionAllowed;

    public VanillaTeamAdapter(String namespace) {
        this.namespace = sanitize(namespace == null || namespace.isBlank() ? "game" : namespace);
    }

    public void setFriendlyFireAllowed(boolean friendlyFireAllowed) {
        this.friendlyFireAllowed = friendlyFireAllowed;
    }

    public void setTeammateCollisionAllowed(boolean teammateCollisionAllowed) {
        this.teammateCollisionAllowed = teammateCollisionAllowed;
    }

    public Formatting colorFor(String logicalId) {
        return TeamColorPalette.colorFor(logicalId);
    }

    public void sync(MinecraftServer server, Collection<VanillaTeamDescriptor> descriptors) {
        Scoreboard scoreboard = server.getScoreboard();
        Set<String> activeTeamNames = new HashSet<>();
        Set<String> activePlayerNames = new LinkedHashSet<>();

        for (VanillaTeamDescriptor descriptor : descriptors) {
            String teamName = this.scoreboardNameFor(descriptor.logicalId());
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.addTeam(teamName);
                this.ownedScoreboardTeams.add(teamName);
            }

            VanillaTeamOptions options = descriptor.options();
            team.setDisplayName(descriptor.displayName());
            team.setColor(options.color());
            team.setPrefix(options.prefix());
            team.setSuffix(options.suffix());
            team.setFriendlyFireAllowed(options.friendlyFireAllowed() && this.friendlyFireAllowed);
            team.setShowFriendlyInvisibles(options.showFriendlyInvisibles());
            team.setNameTagVisibilityRule(options.nameTagVisibility());
            team.setDeathMessageVisibilityRule(options.deathMessageVisibility());
            team.setCollisionRule(this.teammateCollisionAllowed ? options.collisionRule() : net.minecraft.scoreboard.AbstractTeam.CollisionRule.NEVER);

            Set<String> desiredPlayers = new HashSet<>();
            for (ServerPlayerEntity member : descriptor.members()) {
                desiredPlayers.add(member.getName().getString());
            }

            for (String existing : new ArrayList<>(team.getPlayerList())) {
                if (!desiredPlayers.contains(existing)) {
                    scoreboard.removeScoreHolderFromTeam(existing, team);
                }
            }
            for (String playerName : desiredPlayers) {
                Team currentTeam = scoreboard.getScoreHolderTeam(playerName);
                if (currentTeam != null && currentTeam != team && this.ownedScoreboardTeams.contains(currentTeam.getName())) {
                    scoreboard.removeScoreHolderFromTeam(playerName, currentTeam);
                }
                scoreboard.addScoreHolderToTeam(playerName, team);
                activePlayerNames.add(playerName);
            }

            activeTeamNames.add(teamName);
            scoreboard.updateScoreboardTeamAndPlayers(team);
        }

        for (String teamName : new ArrayList<>(this.ownedScoreboardTeams)) {
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                this.ownedScoreboardTeams.remove(teamName);
                continue;
            }
            if (!activeTeamNames.contains(teamName)) {
                scoreboard.removeTeam(team);
                this.ownedScoreboardTeams.remove(teamName);
                this.logicalToScoreboardName.values().removeIf(teamName::equals);
                continue;
            }
            for (String existing : new ArrayList<>(team.getPlayerList())) {
                if (!activePlayerNames.contains(existing)) {
                    scoreboard.removeScoreHolderFromTeam(existing, team);
                }
            }
        }
    }

    public void syncSnapshots(MinecraftServer server, Collection<TeamSnapshot> snapshots, Function<TeamSnapshot, VanillaTeamOptions> optionsFactory) {
        Function<TeamSnapshot, VanillaTeamOptions> resolvedFactory = optionsFactory == null
            ? ignored -> VanillaTeamOptions.defaults()
            : optionsFactory;
        List<VanillaTeamDescriptor> descriptors = snapshots.stream()
            .map(snapshot -> new VanillaTeamDescriptor(
                snapshot.id(),
                net.minecraft.text.Text.literal(TeamColorPalette.displayName(snapshot.id())),
                snapshot.liveMembers(server),
                resolvedFactory.apply(snapshot)
            ))
            .toList();
        this.sync(server, descriptors);
    }

    public void clear(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        for (String teamName : new ArrayList<>(this.ownedScoreboardTeams)) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                scoreboard.removeTeam(team);
            }
        }
        this.ownedScoreboardTeams.clear();
        this.logicalToScoreboardName.clear();
    }

    public void pruneNamespaceTeams(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        String prefix = this.namespace + "_";
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            String name = team.getName();
            if (name != null && name.startsWith(prefix)) {
                scoreboard.removeTeam(team);
            }
        }
        this.ownedScoreboardTeams.removeIf(teamName -> teamName.startsWith(prefix));
        this.logicalToScoreboardName.values().removeIf(teamName -> teamName.startsWith(prefix));
    }

    private String scoreboardNameFor(String logicalId) {
        return this.logicalToScoreboardName.computeIfAbsent(logicalId, id -> {
            String seed = this.namespace + "_" + sanitize(id);
            String hash = Integer.toUnsignedString(id.hashCode(), 36);
            String base = seed.length() <= 11 ? seed : seed.substring(0, 11);
            String candidate = base + "_" + hash;
            return candidate.length() <= 16 ? candidate : candidate.substring(0, 16);
        });
    }

    private static String sanitize(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        if (sanitized.startsWith("_")) {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.isBlank()) {
            return "team";
        }
        return sanitized;
    }
}
