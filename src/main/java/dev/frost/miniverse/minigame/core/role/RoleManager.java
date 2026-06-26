package dev.frost.miniverse.minigame.core.role;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.frost.miniverse.minigame.core.FrameworkModule;

public class RoleManager implements FrameworkModule {
    private final Map<UUID, Set<Role>> playerRoles = new ConcurrentHashMap<>();
    private final Map<String, Supplier<Role>> roleRegistry = new ConcurrentHashMap<>();

    public void registerRoleType(String roleId, Supplier<Role> factory) {
        this.roleRegistry.put(roleId, factory);
    }

    public void addRole(ServerPlayerEntity player, Role role) {
        this.addRole(player.getUuid(), role, player.getServer());
    }

    public void addRole(UUID playerId, Role role, @Nullable MinecraftServer server) {
        Set<Role> roles = this.playerRoles.computeIfAbsent(playerId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        // If they already have this specific role instance or ID type, we should probably remove the old one first
        Role existing = roles.stream().filter(r -> r.getId().equals(role.getId())).findFirst().orElse(null);
        if (existing != null) {
            roles.remove(existing);
            if (server != null) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
                if (p != null) existing.onRemove(p);
            }
        }

        roles.add(role);
        if (server != null) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
            if (p != null) role.onAssign(p);
        }
    }

    public void removeRole(ServerPlayerEntity player, Class<? extends Role> roleClass) {
        this.removeRole(player.getUuid(), roleClass, player.getServer());
    }

    public void removeRole(UUID playerId, Class<? extends Role> roleClass, @Nullable MinecraftServer server) {
        Set<Role> roles = this.playerRoles.get(playerId);
        if (roles != null) {
            roles.stream().filter(roleClass::isInstance).toList().forEach(role -> {
                roles.remove(role);
                if (server != null) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
                    if (p != null) role.onRemove(p);
                }
            });
        }
    }

    public void clearRoles(ServerPlayerEntity player) {
        this.clearRoles(player.getUuid(), player.getServer());
    }

    public void clearRoles(UUID playerId, @Nullable MinecraftServer server) {
        Set<Role> roles = this.playerRoles.remove(playerId);
        if (roles != null && server != null) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
            if (p != null) {
                roles.forEach(role -> role.onRemove(p));
            }
        }
    }

    public Set<Role> getRoles(ServerPlayerEntity player) {
        return this.getRoles(player.getUuid());
    }

    public Set<Role> getRoles(UUID playerId) {
        return this.playerRoles.getOrDefault(playerId, Collections.emptySet());
    }

    public boolean hasRole(ServerPlayerEntity player, Class<? extends Role> roleClass) {
        return this.hasRole(player.getUuid(), roleClass);
    }

    public boolean hasRole(UUID playerId, Class<? extends Role> roleClass) {
        return this.getRoles(playerId).stream().anyMatch(roleClass::isInstance);
    }

    public Collection<UUID> getPlayersWithRole(Class<? extends Role> roleClass) {
        return this.playerRoles.entrySet().stream()
            .filter(entry -> entry.getValue().stream().anyMatch(roleClass::isInstance))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    public Collection<UUID> getAllPlayers() {
        return this.playerRoles.keySet();
    }

    @Override
    public void cleanup(MinecraftServer server) {
        this.playerRoles.forEach((uuid, roles) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                roles.forEach(role -> role.onRemove(player));
            }
        });
        this.playerRoles.clear();
    }

    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        this.playerRoles.forEach((uuid, roles) -> {
            com.google.gson.JsonArray rolesArray = new com.google.gson.JsonArray();
            for (Role role : roles) {
                com.google.gson.JsonObject roleObj = new com.google.gson.JsonObject();
                roleObj.addProperty("id", role.getId());
                roleObj.add("state", role.saveState());
                rolesArray.add(roleObj);
            }
            json.add(uuid.toString(), rolesArray);
        });
        return json;
    }

    public void loadRuntimeState(com.google.gson.JsonObject json) {
        this.playerRoles.clear();
        for (String key : json.keySet()) {
            try {
                UUID uuid = UUID.fromString(key);
                com.google.gson.JsonArray rolesArray = json.getAsJsonArray(key);
                Set<Role> roles = Collections.newSetFromMap(new ConcurrentHashMap<>());
                
                for (int i = 0; i < rolesArray.size(); i++) {
                    com.google.gson.JsonObject roleObj = rolesArray.get(i).getAsJsonObject();
                    String id = roleObj.get("id").getAsString();
                    Supplier<Role> factory = this.roleRegistry.get(id);
                    if (factory != null) {
                        Role role = factory.get();
                        if (roleObj.has("state")) {
                            role.loadState(roleObj.getAsJsonObject("state"));
                        }
                        roles.add(role);
                    } else {
                        System.err.println("[RoleManager] Warning: Role ID '" + id + "' not registered! Skipping role for player " + uuid);
                    }
                }
                
                if (!roles.isEmpty()) {
                    this.playerRoles.put(uuid, roles);
                }
            } catch (Exception e) {
                System.err.println("[RoleManager] Error loading roles for key " + key + ": " + e.getMessage());
            }
        }
    }
}
