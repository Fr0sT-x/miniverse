package dev.frost.miniverse.minigame.impl.manhunt;

import dev.frost.miniverse.minigame.core.LateJoinPolicy;
import dev.frost.miniverse.session.GameSession;

import java.util.Locale;

public class ManhuntLateJoinPolicy implements LateJoinPolicy {
    @Override
    public String resolveRole(String requestedRole) {
        String resolvedRole = requestedRole == null ? "" : requestedRole.trim();
        return switch (resolvedRole.toLowerCase(Locale.ROOT)) {
            case "speedrunner", "runner" -> "speedrunner";
            case "hunter" -> "hunter";
            default -> throw new IllegalArgumentException("Manhunt late joins require an explicit role: Speedrunner or Hunter.");
        };
    }

    @Override
    public String resolveTeam(GameSession session, String requestedTeam, String resolvedRole) {
        return switch (resolvedRole.toLowerCase(Locale.ROOT)) {
            case "speedrunner" -> "Speedrunners";
            case "hunter" -> "Hunters";
            default -> throw new IllegalArgumentException("Manhunt late joins require an explicit role: Speedrunner or Hunter.");
        };
    }

    @Override
    public boolean validateRole(String role) {
        if (role == null) return false;
        String lower = role.trim().toLowerCase(Locale.ROOT);
        return lower.equals("speedrunner") || lower.equals("runner") || lower.equals("hunter");
    }
}
