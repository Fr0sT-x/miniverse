package dev.frost.miniverse.minigame.core.freeze;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FreezeService {
    private static final FreezeService INSTANCE = new FreezeService();

    private final Map<UUID, EnumSet<FreezeReason>> reasonsByPlayer = new HashMap<>();

    private FreezeService() {
    }

    public static FreezeService getInstance() {
        return INSTANCE;
    }

    public synchronized void freeze(ServerPlayerEntity player, FreezeReason reason) {
        if (player == null || reason == null) {
            return;
        }
        boolean wasFrozen = this.isFrozen(player);
        this.reasonsByPlayer
            .computeIfAbsent(player.getUuid(), ignored -> EnumSet.noneOf(FreezeReason.class))
            .add(reason);
        if (!wasFrozen) {
            this.sendFreezeState(player, true);
        }
    }

    public synchronized void unfreeze(ServerPlayerEntity player, FreezeReason reason) {
        if (player == null || reason == null) {
            return;
        }
        EnumSet<FreezeReason> reasons = this.reasonsByPlayer.get(player.getUuid());
        if (reasons == null) {
            return;
        }
        reasons.remove(reason);
        if (reasons.isEmpty()) {
            this.reasonsByPlayer.remove(player.getUuid());
            this.sendFreezeState(player, false);
        }
    }

    public synchronized void clearAll() {
        this.reasonsByPlayer.clear();
    }

    private synchronized boolean isFrozen(ServerPlayerEntity player) {
        return player != null && this.reasonsByPlayer.containsKey(player.getUuid());
    }

    private void sendFreezeState(ServerPlayerEntity player, boolean frozen) {
        if (player == null || player.networkHandler == null) {
            return;
        }
        ServerPlayNetworking.send(player, new NetworkConstants.FreezeStatePayload(frozen));
    }
}

