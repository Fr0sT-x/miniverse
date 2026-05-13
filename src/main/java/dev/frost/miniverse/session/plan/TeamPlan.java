package dev.frost.miniverse.session.plan;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record TeamPlan(String label, List<PlayerRef> members, List<PlayerRole> roles) {
    public TeamPlan {
        label = label == null || label.isBlank() ? "Team" : label.trim();
        members = members == null ? List.of() : List.copyOf(members);
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public static TeamPlan fromNbt(NbtCompound nbt, String fallbackLabel) {
        String label = nbt.getString("label", fallbackLabel);
        List<PlayerRef> members = new ArrayList<>();
        NbtList memberList = nbt.getList("members").orElseGet(NbtList::new);
        for (int i = 0; i < memberList.size(); i++) {
            PlayerRef.fromNbt(memberList.getCompoundOrEmpty(i)).ifPresent(members::add);
        }

        List<PlayerRole> roles = new ArrayList<>();
        NbtList roleList = nbt.getList("roles").orElseGet(NbtList::new);
        for (int i = 0; i < roleList.size(); i++) {
            PlayerRole.fromNbt(roleList.getCompoundOrEmpty(i)).ifPresent(roles::add);
        }

        return new TeamPlan(label, members, roles);
    }

    public boolean isEmpty() {
        return this.members.isEmpty();
    }

    public Optional<String> roleFor(UUID playerUuid) {
        return this.roles.stream()
            .filter(role -> role.playerUuid().equals(playerUuid))
            .map(PlayerRole::role)
            .findFirst();
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("label", this.label);

        NbtList memberList = new NbtList();
        for (PlayerRef member : this.members) {
            memberList.add(member.toNbt());
        }
        nbt.put("members", memberList);

        NbtList roleList = new NbtList();
        for (PlayerRole role : this.roles) {
            roleList.add(role.toNbt());
        }
        if (!roleList.isEmpty()) {
            nbt.put("roles", roleList);
        }

        return nbt;
    }
}
