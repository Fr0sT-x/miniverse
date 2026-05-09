package dev.frost.miniverse.network;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.GameSession;
import dev.frost.miniverse.session.PlayerAssignment;
import dev.frost.miniverse.session.SeedPlan;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionGameType;
import dev.frost.miniverse.session.SessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public final class SessionNetwork {
    private static boolean registered;

    private SessionNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.REQUEST_SESSIONS_ID, (payload, context) -> sendSessionList(context.server(), context.player()));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CREATE_SESSION_ID, (payload, context) -> handleCreate(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.LAUNCH_SESSION_ID, (payload, context) -> handleLaunch(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.STOP_SESSION_ID, (payload, context) -> handleStop(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.GRANT_OP_ID, (payload, context) -> handleGrantOp(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CLEANUP_PLAYER_ID, (payload, context) -> handleCleanupPlayer(context.server(), context.player(), payload));

        registered = true;
    }

    private static void handleCreate(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.CreateSessionPayload payload) {
        SessionGameType.fromString(payload.game())
            .ifPresentOrElse(gameType -> {
                NbtCompound plan = payload.plan();
                SeedPlan seedPlan = resolveSeedPlan(plan, player);
                if (seedPlan == null) {
                    return;
                }

                GameSession session = SessionManager.getInstance().createSession(gameType, seedPlan);
                boolean autoLaunch = false;
                if (plan != null && !plan.isEmpty()) {
                    NbtCompound settings = plan.getCompound("settings").orElseGet(NbtCompound::new);
                    String plannedGame = plan.getString("game", gameType.getCommandName());
                    if (gameType.getCommandName().equals(plannedGame)) {
                        NbtList groups = plan.getList("groups").orElseGet(NbtList::new);
                        NbtList plannedRoles = new NbtList();

                        // Groups become session assignments. The launcher decides whether those
                        // assignments share a backend or get isolated backend state.
                        if (gameType == SessionGameType.SPEEDRUN && !groups.isEmpty()) {
                            // SPEEDRUN: Create separate server instances per team
                            for (int i = 0; i < groups.size(); i++) {
                                NbtCompound group = groups.getCompoundOrEmpty(i);
                                NbtList roles = group.getList("roles").orElseGet(NbtList::new);
                                for (int r = 0; r < roles.size(); r++) {
                                    plannedRoles.add(roles.getCompoundOrEmpty(r).copy());
                                }
                                String label = group.getString("label", gameType.getDisplayName() + " " + (i + 1));
                                NbtList members = group.getList("members").orElseGet(NbtList::new);
                                List<ServerPlayerEntity> resolved = new java.util.ArrayList<>();
                                for (int m = 0; m < members.size(); m++) {
                                    NbtCompound member = members.getCompoundOrEmpty(m);
                                    String uuidString = member.getString("uuid", "");
                                    if (uuidString.isBlank()) {
                                        continue;
                                    }
                                    try {
                                        java.util.UUID uuid = java.util.UUID.fromString(uuidString);
                                        ServerPlayerEntity resolvedPlayer = server.getPlayerManager().getPlayer(uuid);
                                        if (resolvedPlayer != null) {
                                            resolved.add(resolvedPlayer);
                                        }
                                    } catch (IllegalArgumentException ignored) {
                                    }
                                }
                                if (!resolved.isEmpty()) {
                                    SessionManager.getInstance().assignPlayers(session.getSessionId(), label, resolved);
                                }
                            }
                        } else {
                            if (groups.isEmpty()) {
                                // No explicit groups were provided in the plan – treat this as a single-group
                                // session that should include all currently-online players so that everyone
                                // is transferred together.
                                List<ServerPlayerEntity> allPlayers = new java.util.ArrayList<>();
                                for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                                    allPlayers.add(online);
                                }
                                if (!allPlayers.isEmpty()) {
                                    SessionManager.getInstance().assignPlayers(session.getSessionId(), gameType.getDisplayName(), allPlayers);
                                }
                            } else {
                                // For non-SPEEDRUN gamemodes where groups were provided by the UI,
                                // keep one assignment per group. Resource Sprint launches these
                                // separately; most other game modes collocate them onto one backend.
                                for (int i = 0; i < groups.size(); i++) {
                                    NbtCompound group = groups.getCompoundOrEmpty(i);
                                    NbtList roles = group.getList("roles").orElseGet(NbtList::new);
                                    for (int r = 0; r < roles.size(); r++) {
                                        plannedRoles.add(roles.getCompoundOrEmpty(r).copy());
                                    }
                                    String label = group.getString("label", gameType.getDisplayName() + " " + (i + 1));
                                    NbtList members = group.getList("members").orElseGet(NbtList::new);
                                    List<ServerPlayerEntity> resolved = new java.util.ArrayList<>();
                                    for (int m = 0; m < members.size(); m++) {
                                        NbtCompound member = members.getCompoundOrEmpty(m);
                                        String uuidString = member.getString("uuid", "");
                                        if (uuidString.isBlank()) {
                                            continue;
                                        }
                                        try {
                                            java.util.UUID uuid = java.util.UUID.fromString(uuidString);
                                            ServerPlayerEntity resolvedPlayer = server.getPlayerManager().getPlayer(uuid);
                                            if (resolvedPlayer != null) {
                                                resolved.add(resolvedPlayer);
                                            }
                                        } catch (IllegalArgumentException ignored) {
                                        }
                                    }
                                    if (!resolved.isEmpty()) {
                                        SessionManager.getInstance().assignPlayers(session.getSessionId(), label, resolved);
                                    }
                                }
                            }
                        }

                        if (!plannedRoles.isEmpty()) {
                            settings.put("roles", plannedRoles);
                        }
                    }
                    session.setSettings(settings);
                    autoLaunch = plan.getBoolean("launch", false);
                } else {
                    SessionManager.getInstance().assignPlayer(session.getSessionId(), player);
                }
                player.sendMessage(Text.literal("Created session " + session.getSessionId() + " for " + gameType.getDisplayName() + "."), false);
                if (!autoLaunch) {
                    sendSessionList(server, player);
                    return;
                }

                player.sendMessage(Text.literal("Launching session " + session.getSessionId() + "..."), false);
                SessionManager.getInstance().launchSession(session.getSessionId()).whenComplete((launched, error) -> server.execute(() -> {
                    if (error != null) {
                        player.sendMessage(Text.literal("Failed to launch session " + session.getSessionId() + ": " + error.getMessage()), false);
                        sendSessionList(server, player);
                        return;
                    }

                    SessionManager.getInstance().transferAssignedPlayers(server, launched);
                    player.sendMessage(Text.literal("Launched session " + launched.getSessionId() + "."), false);
                    sendSessionList(server, player);
                }));
            }, () -> player.sendMessage(Text.literal("Unknown game type '" + payload.game() + "'."), false));
    }

    private static SeedPlan resolveSeedPlan(NbtCompound plan, ServerPlayerEntity player) {
        if (plan == null || plan.isEmpty()) {
            return SeedPlan.randomSameSeed();
        }

        NbtCompound settings = plan.getCompound("settings").orElseGet(NbtCompound::new);
        String seedMode = settings.getString("seedMode", "random");
        if (!"fixed".equalsIgnoreCase(seedMode)) {
            return SeedPlan.randomSameSeed();
        }

        return settings.getLong("seed")
            .map(SeedPlan::fixed)
            .orElseGet(() -> {
                player.sendMessage(Text.literal("Fixed seed was selected but no valid seed value was provided."), false);
                return null;
            });
    }

    private static void handleLaunch(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.LaunchSessionPayload payload) {
        SessionManager manager = SessionManager.getInstance();
        String sessionId = payload.sessionId();

        if (manager.getSession(sessionId).isEmpty()) {
            player.sendMessage(Text.literal("Unknown session '" + sessionId + "'."), false);
            return;
        }

        player.sendMessage(Text.literal("Launching session " + sessionId + "..."), false);
        manager.launchSession(sessionId).whenComplete((session, error) -> server.execute(() -> {
            if (error != null) {
                player.sendMessage(Text.literal("Failed to launch session " + sessionId + ": " + error.getMessage()), false);
                sendSessionList(server, player);
                return;
            }

            manager.transferAssignedPlayers(server, session);
            player.sendMessage(Text.literal("Launched session " + session.getSessionId() + "."), false);
            sendSessionList(server, player);
        }));
    }

    private static void handleStop(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.StopSessionPayload payload) {
        String sessionId = payload.sessionId();
        SessionRegistry.markStopRequested(sessionId);
        player.sendMessage(Text.literal("Stopping session " + sessionId + " and returning players to the main server..."), false);
        sendSessionList(server, player);
    }

    private static void handleGrantOp(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.GrantOpPayload payload) {
        // Grant operator permission to the player
        server.getPlayerManager().addToOperators(new net.minecraft.server.PlayerConfigEntry(player.getGameProfile()));
        player.sendMessage(Text.literal("You have been granted operator access."), false);
    }

    private static void handleCleanupPlayer(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.CleanupPlayerPayload payload) {
        // Clear the player's inventory
        player.getInventory().clear();
        
        // Fill the player's hunger
        player.getHungerManager().setFoodLevel(20);

        player.sendMessage(Text.literal("Your inventory has been cleaned and hunger restored."), false);
    }

    private static void sendSessionList(MinecraftServer server, ServerPlayerEntity player) {
        NbtCompound root = new NbtCompound();
        NbtList sessions = new NbtList();

        List<SessionRegistry.Snapshot> snapshots = SessionRegistry.loadSnapshots();
        if (snapshots.isEmpty()) {
            for (GameSession session : SessionManager.getInstance().getSessions()) {
                NbtCompound entry = new NbtCompound();
                entry.putString("id", session.getSessionId());
                entry.putString("game", session.getGameType().getDisplayName());
                entry.putString("state", session.getState().name());
                entry.putLong("seed", session.getSeedPlan().sharedSeed());

                NbtList players = new NbtList();
                session.getAssignments().forEach(assignment -> players.add(NbtString.of(assignment.getDisplayName())));
                entry.put("players", players);
                entry.putInt("playerCount", session.getAssignments().stream().mapToInt(PlayerAssignment::getPlayerCount).sum());
                sessions.add(entry);
            }
        } else {
            for (SessionRegistry.Snapshot snapshot : snapshots) {
                NbtCompound entry = new NbtCompound();
                entry.putString("id", snapshot.sessionId());
                entry.putString("game", snapshot.game());
                entry.putString("state", snapshot.state());
                entry.putLong("seed", snapshot.seed());

                NbtList players = new NbtList();
                for (String name : snapshot.players()) {
                    players.add(NbtString.of(name));
                }
                entry.put("players", players);
                entry.putInt("playerCount", snapshot.playerCount());
                sessions.add(entry);
            }
        }

        root.put("sessions", sessions);
        NbtList roster = new NbtList();
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("uuid", online.getUuidAsString());
            entry.putString("name", online.getName().getString());
            roster.add(entry);
        }
        root.put("players", roster);
        ServerPlayNetworking.send(player, new NetworkConstants.SessionListPayload(root));
    }
}
