package dev.frost.miniverse.session;

import dev.frost.miniverse.minigame.core.MinigameDefinition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();

    private final SessionStore store;
    private final SessionProcessMonitor monitor;
    private final SessionLaunchPipeline pipeline;
    private final SessionPlayerRouter router;

    private SessionManager() {
        this.store = new SessionStore();
        ServerLauncher launcher = new ServerLauncher();
        this.monitor = new SessionProcessMonitor(this.store, launcher);
        this.pipeline = new SessionLaunchPipeline(this.store, this.monitor, launcher);
        this.router = new SessionPlayerRouter(this.store, this.pipeline);
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    // --- SessionStore Delegates ---

    public GameSession createSession(SessionGameDescriptor gameType) {
        return this.store.createSession(gameType);
    }

    public GameSession createSession(SessionGameDescriptor gameType, SeedPlan seedPlan) {
        return this.store.createSession(gameType, seedPlan);
    }

    public GameSession createSession(MinigameDefinition definition) {
        return this.store.createSession(definition);
    }

    public GameSession createSession(MinigameDefinition definition, SeedPlan seedPlan) {
        return this.store.createSession(definition, seedPlan);
    }

    public GameSession createSession(SessionGameDescriptor gameType, Collection<ServerPlayerEntity> players) {
        return this.store.createSession(gameType, players);
    }

    public List<GameSession> getSessions() {
        return this.store.getSessions();
    }

    public Optional<GameSession> getSession(String sessionId) {
        return this.store.getSession(sessionId);
    }

    public Optional<GameSession> getSessionForPlayer(UUID playerUuid) {
        return this.store.getSessionForPlayer(playerUuid);
    }

    public void removeSession(String sessionId) {
        this.monitor.stopSession(sessionId);
        this.store.removeSession(sessionId);
    }

    public void archiveSession(String sessionId) {
        this.monitor.stopSession(sessionId);
        this.store.archiveSession(sessionId);
    }

    public List<SessionGroup> getGroups(String sessionId) {
        return this.store.getGroups(sessionId);
    }

    public List<SessionGroup> getAssignments(String sessionId) {
        return this.store.getAssignments(sessionId);
    }

    public SessionGroup createGroup(String sessionId, String label, Collection<ServerPlayerEntity> players) {
        return this.store.createGroup(sessionId, label, players);
    }

    public SessionGroup createGroup(String sessionId, PlannedTeam plannedTeam) {
        return this.store.createGroup(sessionId, plannedTeam);
    }


    // --- SessionProcessMonitor Delegates ---

    public void stopSession(String sessionId) {
        this.monitor.stopSession(sessionId);
    }

    public void reapDeadBackends(MinecraftServer server) {
        this.monitor.reapDeadBackends(server);
    }

    public void setMaxConcurrentLaunches(int maxConcurrentLaunches) {
        this.monitor.setMaxConcurrentLaunches(maxConcurrentLaunches);
    }


    // --- SessionLaunchPipeline Delegates ---

    public CompletableFuture<GameSession> launchSession(String sessionId, MinecraftServer server) {
        return this.pipeline.launchSession(sessionId, server);
    }

    public CompletableFuture<GameSession> relaunchRetainedSession(String sessionId, MinecraftServer server) {
        return this.pipeline.relaunchRetainedSession(sessionId, server);
    }

    public CompletableFuture<GameSession> changeSeedAndRelaunchSession(String sessionId, MinecraftServer server) {
        return this.pipeline.changeSeedAndRelaunchSession(sessionId, server);
    }

    public CompletableFuture<GameSession> stageSeedChangeRelaunch(String sessionId, MinecraftServer server) {
        return this.pipeline.stageSeedChangeRelaunch(sessionId, server);
    }

    public void completeSeedChangeRelaunch(String sessionId) {
        this.pipeline.completeSeedChangeRelaunch(sessionId);
    }

    public List<String> expectedSeedChangeCompletionGroups(String sessionId) {
        return this.pipeline.expectedSeedChangeCompletionGroups(sessionId);
    }

    public CompletableFuture<ServerLauncher.InspectionLaunchResult> launchInspectionAsync(String sessionId, ServerPlayerEntity viewer) {
        return this.pipeline.launchInspectionAsync(sessionId, viewer);
    }

    public CompletableFuture<ServerLauncher.MapEditorLaunchResult> launchMapEditorAsync(String mapName, ServerPlayerEntity editor) {
        return this.pipeline.launchMapEditorAsync(mapName, editor);
    }

    public CompletableFuture<ServerLauncher.MapEditorLaunchResult> launchExistingMapEditorAsync(String mapId, ServerPlayerEntity editor) {
        return this.pipeline.launchExistingMapEditorAsync(mapId, editor);
    }


    // --- SessionPlayerRouter Delegates ---

    public SessionGroup assignPlayer(String sessionId, ServerPlayerEntity player) {
        return this.router.assignPlayer(sessionId, player);
    }

    public SessionGroup assignPlayers(String sessionId, String label, Collection<ServerPlayerEntity> players) {
        return this.router.assignPlayers(sessionId, label, players);
    }

    public SessionGroup assignPlayerMidGame(String sessionId, ServerPlayerEntity player, String teamLabel, String role) {
        return this.router.assignPlayerMidGame(sessionId, player, teamLabel, role);
    }

    public CompletableFuture<SessionGroup> assignPlayerMidGameAsync(String sessionId, ServerPlayerEntity player, String teamLabel, String role, MinecraftServer server) {
        return this.router.assignPlayerMidGameAsync(sessionId, player, teamLabel, role, server);
    }

    public @Nullable SessionGroup unassignPlayer(UUID playerUuid) {
        return this.router.unassignPlayer(playerUuid);
    }

    public void clearPlayerSessionsForSession(String sessionId) {
        this.router.clearPlayerSessionsForSession(sessionId);
    }

    public void clearSessionGroupsForSession(String sessionId) {
        this.router.clearSessionGroupsForSession(sessionId);
    }
}
