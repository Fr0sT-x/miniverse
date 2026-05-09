package dev.frost.miniverse.minigame.impl.deathswap;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DeathSwapManager {
    public List<ServerPlayerEntity> buildSwapOrder(List<ServerPlayerEntity> alivePlayers, boolean trioRotationEnabled) {
        if (alivePlayers == null || alivePlayers.isEmpty()) {
            return List.of();
        }

        List<ServerPlayerEntity> shuffled = new ArrayList<>(alivePlayers);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());

        List<ServerPlayerEntity> order = new ArrayList<>(Collections.nCopies(shuffled.size(), null));
        int count = shuffled.size();

        if (count == 1) {
            order.set(0, shuffled.get(0));
            return order;
        }

        if (count % 2 == 0 || !trioRotationEnabled || count < 3) {
            for (int i = 0; i + 1 < count; i += 2) {
                order.set(i, shuffled.get(i + 1));
                order.set(i + 1, shuffled.get(i));
            }
            if (count % 2 == 1) {
                order.set(count - 1, shuffled.get(count - 1));
            }
            return order;
        }

        int pairLimit = count - 3;
        for (int i = 0; i < pairLimit; i += 2) {
            order.set(i, shuffled.get(i + 1));
            order.set(i + 1, shuffled.get(i));
        }

        int a = count - 3;
        int b = count - 2;
        int c = count - 1;
        order.set(a, shuffled.get(b));
        order.set(b, shuffled.get(c));
        order.set(c, shuffled.get(a));
        return order;
    }

    public int countAliveTeams(Collection<UUID> alivePlayers, Map<UUID, String> playerTeams) {
        return this.getAliveTeamLabels(alivePlayers, playerTeams).size();
    }

    public String resolveWinningLabel(Collection<UUID> alivePlayers, Map<UUID, String> playerTeams) {
        Set<String> labels = this.getAliveTeamLabels(alivePlayers, playerTeams);
        if (labels.size() != 1) {
            return "";
        }

        return labels.iterator().next();
    }

    private Set<String> getAliveTeamLabels(Collection<UUID> alivePlayers, Map<UUID, String> playerTeams) {
        Set<String> labels = new LinkedHashSet<>();
        if (alivePlayers == null || playerTeams == null) {
            return labels;
        }

        for (UUID playerUuid : alivePlayers) {
            String label = playerTeams.get(playerUuid);
            if (label == null || label.isBlank()) {
                label = playerUuid.toString();
            }
            labels.add(label);
        }
        return labels;
    }
}


