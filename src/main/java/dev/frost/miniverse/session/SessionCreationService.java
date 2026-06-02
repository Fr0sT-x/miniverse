package dev.frost.miniverse.session;

import dev.frost.miniverse.session.plan.SessionPlan;
import dev.frost.miniverse.session.plan.TeamPlan;
import dev.frost.miniverse.map.MapStore;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SessionCreationService {
    private final SessionManager sessionManager;

    public SessionCreationService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public CreateResult create(MinecraftServer server, ServerPlayerEntity requester, String game, String name, net.minecraft.nbt.NbtCompound planNbt) {
        Optional<MinigameDefinition> definition = MinigameRegistry.get(game);
        if (definition.isEmpty()) {
            return CreateResult.failure("Unknown game type '" + game + "'.");
        }

        SessionPlan plan = SessionPlan.fromPayload(game, name, planNbt);
        if (plan.validationError().isPresent()) {
            return CreateResult.failure(plan.validationError().get());
        }

        SessionGameDescriptor gameType = SessionGameDescriptor.fromDefinition(definition.get());
        if (plan.explicitPlan() && !plan.matches(gameType)) {
            return CreateResult.failure("Session plan game '" + plan.plannedGame() + "' does not match requested game '" + gameType.getCommandName() + "'.");
        }

        Optional<String> mapValidationError = this.validateMapSelection(gameType, plan);
        if (mapValidationError.isPresent()) {
            return CreateResult.failure(mapValidationError.get());
        }

        SeedPlan seedPlan = plan.seedPlan().orElse(null);
        if (seedPlan == null) {
            return CreateResult.failure("No valid seed plan was provided.");
        }

        GameSession session = this.sessionManager.createSession(gameType, seedPlan);
        if (plan.explicitPlan()) {
            this.applyPlan(server, session, gameType, plan);
        } else {
            this.sessionManager.assignPlayer(session.getSessionId(), requester);
        }

        return CreateResult.success(session, gameType, plan.autoLaunch());
    }

    private Optional<String> validateMapSelection(SessionGameDescriptor gameType, SessionPlan plan) {
        NbtCompound settings = plan.settings();
        if (!settings.contains("mapId", NbtElement.STRING_TYPE)) {
            return Optional.empty();
        }
        String mapId = settings.getString("mapId").trim();
        if (mapId.isBlank()) {
            return Optional.of("Select a map before creating this session.");
        }
        MapValidationResult result = MapStore.validate(mapId, gameType.getCommandName());
        if (result.valid()) {
            return Optional.empty();
        }
        return Optional.of("Map '" + mapId + "' is not valid for " + gameType.getDisplayName() + ": " + String.join(", ", result.errors()));
    }

    private void applyPlan(MinecraftServer server, GameSession session, SessionGameDescriptor gameType, SessionPlan plan) {
        if (plan.matches(gameType)) {
            this.createPlannedGroups(server, session, gameType, plan);
        }
        session.setSettings(plan.settingsWithTeamRoles());
    }

    private void createPlannedGroups(MinecraftServer server, GameSession session, SessionGameDescriptor gameType, SessionPlan plan) {
        PlannedTeam roleTeam = this.plannedTeamFromRoleSettings(server, plan, gameType);
        if (roleTeam != null) {
            this.sessionManager.createGroup(session.getSessionId(), roleTeam);
            return;
        }
        if (plan.shouldAssignAllOnlinePlayers(gameType)) {
            List<ServerPlayerEntity> allPlayers = new ArrayList<>(server.getPlayerManager().getPlayerList());
            if (!allPlayers.isEmpty()) {
                this.sessionManager.createGroup(session.getSessionId(), gameType.getDisplayName(), allPlayers);
            }
            return;
        }

        for (TeamPlan team : plan.teams()) {
            PlannedTeam plannedTeam = this.resolveOnlineTeam(server, team);
            if (plannedTeam != null) {
                this.sessionManager.createGroup(session.getSessionId(), plannedTeam);
            }
        }
    }

    private PlannedTeam resolveOnlineTeam(MinecraftServer server, TeamPlan team) {
        List<SessionMembership> memberships = new ArrayList<>();
        for (dev.frost.miniverse.session.plan.PlayerRef member : team.members()) {
            ServerPlayerEntity resolvedPlayer = server.getPlayerManager().getPlayer(member.uuid());
            if (resolvedPlayer != null) {
                memberships.add(new SessionMembership(
                    resolvedPlayer.getUuid(),
                    resolvedPlayer.getName().getString(),
                    team.roleFor(resolvedPlayer.getUuid()).orElse("")
                ));
            }
        }
        return memberships.isEmpty() ? null : new PlannedTeam(team.label(), memberships);
    }

    private PlannedTeam plannedTeamFromRoleSettings(MinecraftServer server, SessionPlan plan, SessionGameDescriptor gameType) {
        if (!plan.explicitPlan() || !plan.teams().isEmpty()) {
            return null;
        }

        NbtCompound settings = plan.settings();
        NbtList roles = settings.getList("roles", NbtElement.COMPOUND_TYPE);
        if (roles.isEmpty()) {
            return null;
        }

        Map<UUID, SessionMembership> members = new LinkedHashMap<>();
        for (int i = 0; i < roles.size(); i++) {
            NbtCompound roleEntry = roles.getCompound(i);
            String uuidText = roleEntry.contains("uuid", NbtElement.STRING_TYPE) ? roleEntry.getString("uuid") : "";
            if (uuidText.isBlank()) {
                continue;
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(uuidText);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
                continue;
            }

            String role = roleEntry.contains("role", NbtElement.STRING_TYPE) ? roleEntry.getString("role").trim() : "";
            members.put(uuid, new SessionMembership(uuid, player.getName().getString(), role));
        }

        if (members.isEmpty()) {
            return null;
        }

        return new PlannedTeam(gameType.getDisplayName(), new ArrayList<>(members.values()));
    }

    public record CreateResult(GameSession session, SessionGameDescriptor gameType, boolean autoLaunch, String errorMessage) {
        public static CreateResult success(GameSession session, SessionGameDescriptor gameType, boolean autoLaunch) {
            return new CreateResult(session, gameType, autoLaunch, "");
        }

        public static CreateResult failure(String errorMessage) {
            return new CreateResult(null, null, false, errorMessage);
        }

        public boolean succeeded() {
            return this.session != null && this.gameType != null;
        }
    }
}
