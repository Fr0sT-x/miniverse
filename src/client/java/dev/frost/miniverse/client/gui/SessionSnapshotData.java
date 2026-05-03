package dev.frost.miniverse.client.gui;

import java.util.List;

public final class SessionSnapshotData {
    public record SessionSummary(String id, String game, String state, long seed, int players) {
    }

    public record RosterEntry(String uuid, String name) {
    }

    private static volatile List<SessionSummary> sessions = List.of();
    private static volatile List<RosterEntry> roster = List.of();

    private SessionSnapshotData() {
    }

    public static List<SessionSummary> sessions() {
        return sessions;
    }

    public static List<RosterEntry> roster() {
        return roster;
    }

    public static void update(List<SessionSummary> newSessions, List<RosterEntry> newRoster) {
        sessions = List.copyOf(newSessions);
        roster = List.copyOf(newRoster);
    }
}

