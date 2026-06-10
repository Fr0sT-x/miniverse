package dev.frost.miniverse.client.gui.workspace.framework;

import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

public class SessionPayloadBuilder {
    private final String gameId;
    private final String sessionName;
    private final NbtCompound settings = new NbtCompound();
    private final NbtList groups = new NbtList();

    public SessionPayloadBuilder(String gameId, String sessionName) {
        this.gameId = gameId;
        this.sessionName = sessionName;
    }

    public NbtCompound settings() {
        return this.settings;
    }

    public void addGroup(String groupId, String groupName, Iterable<SessionSnapshotData.RosterEntry> members) {
        java.util.Map<String, Iterable<SessionSnapshotData.RosterEntry>> map = new java.util.HashMap<>();
        map.put(null, members);
        this.addGroupWithRoles(groupId, groupName, map);
    }

    public void addGroupWithRole(String groupId, String groupName, Iterable<SessionSnapshotData.RosterEntry> members, String role) {
        java.util.Map<String, Iterable<SessionSnapshotData.RosterEntry>> map = new java.util.HashMap<>();
        map.put(role, members);
        this.addGroupWithRoles(groupId, groupName, map);
    }

    public void addGroupWithRoles(String groupId, String groupName, java.util.Map<String, Iterable<SessionSnapshotData.RosterEntry>> rolesToMembers) {
        NbtCompound group = new NbtCompound();
        group.putString("id", groupId);
        group.putString("name", groupName);
        
        NbtList memberList = new NbtList();
        NbtList roleList = new NbtList();
        
        for (java.util.Map.Entry<String, Iterable<SessionSnapshotData.RosterEntry>> roleEntry : rolesToMembers.entrySet()) {
            String role = roleEntry.getKey();
            for (SessionSnapshotData.RosterEntry entry : roleEntry.getValue()) {
                NbtCompound memberCompound = new NbtCompound();
                memberCompound.putString("uuid", entry.uuid());
                memberCompound.putString("name", entry.name());
                memberList.add(memberCompound);
                
                if (role != null) {
                    NbtCompound roleCompound = new NbtCompound();
                    roleCompound.putString("uuid", entry.uuid());
                    roleCompound.putString("role", role);
                    roleList.add(roleCompound);
                }
            }
        }
        
        group.put("members", memberList);
        if (!roleList.isEmpty()) {
            group.put("roles", roleList);
        }
        this.groups.add(group);
    }

    public void dispatch() {
        NbtCompound plan = new NbtCompound();
        plan.putString("game", this.gameId);
        plan.putString("name", this.sessionName);
        plan.putBoolean("launch", true);
        plan.put("settings", this.settings);
        if (!this.groups.isEmpty()) {
            plan.put("groups", this.groups);
        }
        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(this.gameId, this.sessionName, plan));
    }
}
