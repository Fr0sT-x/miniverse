package dev.frost.miniverse.minigame.core.role;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import dev.frost.miniverse.minigame.core.FrameworkModule;

public class RoleManager implements FrameworkModule {
    private final Map<UUID, Role> playerRoles = new ConcurrentHashMap<>();

    public void assignRole(ServerPlayerEntity player, Role role) {
        Role oldRole = playerRoles.put(player.getUuid(), role);
        if (oldRole != null) {
            oldRole.onRemove(player);
        }
        if (role != null) {
            role.onAssign(player);
        }
    }

    public void removeRole(ServerPlayerEntity player) {
        Role oldRole = playerRoles.remove(player.getUuid());
        if (oldRole != null) {
            oldRole.onRemove(player);
        }
    }

    public Optional<Role> getRole(ServerPlayerEntity player) {
        return getRole(player.getUuid());
    }

    public Optional<Role> getRole(UUID playerId) {
        return Optional.ofNullable(playerRoles.get(playerId));
    }

    public boolean hasRole(ServerPlayerEntity player, Class<? extends Role> roleClass) {
        return getRole(player).map(roleClass::isInstance).orElse(false);
    }

    public boolean hasRole(UUID playerId, Class<? extends Role> roleClass) {
        return getRole(playerId).map(roleClass::isInstance).orElse(false);
    }

    public Collection<UUID> getPlayersWithRole(Class<? extends Role> roleClass) {
        return playerRoles.entrySet().stream()
            .filter(entry -> roleClass.isInstance(entry.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    public Collection<UUID> getAllPlayers() {
        return playerRoles.keySet();
    }

    @Override
    public void cleanup(net.minecraft.server.MinecraftServer server) {
        playerRoles.forEach((uuid, role) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                role.onRemove(player);
            }
        });
        playerRoles.clear();
    }
}
