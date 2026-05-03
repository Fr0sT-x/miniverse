package dev.frost.miniverse.client.gui;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TeamDraft {
    public record Member(UUID uuid, String name) {
    }

    private final String label;
    private final List<Member> members = new ArrayList<>();

    public TeamDraft(String label) {
        this.label = label == null ? "" : label.trim();
    }

    public String label() {
        return this.label;
    }

    public List<Member> members() {
        return List.copyOf(this.members);
    }

    public int size() {
        return this.members.size();
    }

    public boolean isEmpty() {
        return this.members.isEmpty();
    }

    public boolean contains(UUID uuid) {
        return this.members.stream().anyMatch(member -> member.uuid().equals(uuid));
    }

    public void add(Member member) {
        this.remove(member.uuid());
        this.members.add(member);
    }

    public boolean remove(UUID uuid) {
        return this.members.removeIf(member -> member.uuid().equals(uuid));
    }

    public NbtCompound toPlanCompound(String fallbackLabel) {
        NbtCompound group = new NbtCompound();
        group.putString("label", this.label.isBlank() ? fallbackLabel : this.label);

        NbtList membersList = new NbtList();
        for (Member member : this.members) {
            NbtCompound compound = new NbtCompound();
            compound.putString("uuid", member.uuid().toString());
            compound.putString("name", member.name());
            membersList.add(compound);
        }
        group.put("members", membersList);
        return group;
    }
}



