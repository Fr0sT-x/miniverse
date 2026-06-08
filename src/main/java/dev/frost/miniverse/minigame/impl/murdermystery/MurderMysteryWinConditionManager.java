package dev.frost.miniverse.minigame.impl.murdermystery;

import dev.frost.miniverse.minigame.core.role.RoleManager;
import dev.frost.miniverse.minigame.impl.murdermystery.role.DetectiveRole;
import dev.frost.miniverse.minigame.impl.murdermystery.role.InnocentRole;
import dev.frost.miniverse.minigame.impl.murdermystery.role.MurdererRole;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class MurderMysteryWinConditionManager {
    private final RoleManager roleManager;

    public MurderMysteryWinConditionManager(RoleManager roleManager) {
        this.roleManager = roleManager;
    }

    public enum WinResult {
        NONE, MURDERER_WIN, INNOCENT_WIN
    }

    public WinResult checkWinCondition(List<ServerPlayerEntity> activePlayers, boolean timeExpired) {
        boolean murdererAlive = false;
        boolean innocentAlive = false;

        for (ServerPlayerEntity player : activePlayers) {
            if (roleManager.hasRole(player, MurdererRole.class)) {
                murdererAlive = true;
            } else if (roleManager.hasRole(player, InnocentRole.class) || roleManager.hasRole(player, DetectiveRole.class)) {
                innocentAlive = true;
            }
        }

        if (!murdererAlive) {
            return WinResult.INNOCENT_WIN;
        }
        
        if (!innocentAlive) {
            return WinResult.MURDERER_WIN;
        }

        if (timeExpired) {
            return WinResult.INNOCENT_WIN; // Innocents survive the time limit
        }

        return WinResult.NONE;
    }
}
