package dev.frost.miniverse.minigame.core.item;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ProtectedItemRule {
    private final String type;
    private final boolean preventDrop;
    private final boolean preventExternalStorage;
    private final boolean preventDeletion;
    private final boolean preventDeathLoss;
    private final boolean preventDuplication;
    private final boolean autoRestore;
    private final boolean allowRearrange;
    private final boolean allowOffhandSwap;
    private final int maxStacks;
    private final boolean cleanupWorldDrops;
    private final Predicate<ServerPlayerEntity> canHold;
    private final Predicate<ServerPlayerEntity> shouldHave;
    private final Consumer<ServerPlayerEntity> restoreAction;

    private ProtectedItemRule(Builder builder) {
        this.type = builder.type;
        this.preventDrop = builder.preventDrop;
        this.preventExternalStorage = builder.preventExternalStorage;
        this.preventDeletion = builder.preventDeletion;
        this.preventDeathLoss = builder.preventDeathLoss;
        this.preventDuplication = builder.preventDuplication;
        this.autoRestore = builder.autoRestore;
        this.allowRearrange = builder.allowRearrange;
        this.allowOffhandSwap = builder.allowOffhandSwap;
        this.maxStacks = builder.maxStacks;
        this.cleanupWorldDrops = builder.cleanupWorldDrops;
        this.canHold = builder.canHold;
        this.shouldHave = builder.shouldHave;
        this.restoreAction = builder.restoreAction;
    }

    public String type() {
        return this.type;
    }

    public boolean preventDrop() {
        return this.preventDrop;
    }

    public boolean preventExternalStorage() {
        return this.preventExternalStorage;
    }

    public boolean preventDeletion() {
        return this.preventDeletion;
    }

    public boolean preventDeathLoss() {
        return this.preventDeathLoss;
    }

    public boolean preventDuplication() {
        return this.preventDuplication;
    }

    public boolean autoRestore() {
        return this.autoRestore;
    }

    public boolean allowRearrange() {
        return this.allowRearrange;
    }

    public boolean allowOffhandSwap() {
        return this.allowOffhandSwap;
    }

    public int maxStacks() {
        return this.maxStacks;
    }

    public boolean cleanupWorldDrops() {
        return this.cleanupWorldDrops;
    }

    public boolean canHold(ServerPlayerEntity player) {
        return this.canHold == null || this.canHold.test(player);
    }

    public boolean shouldHave(ServerPlayerEntity player) {
        return this.shouldHave != null && this.shouldHave.test(player);
    }

    public boolean hasRestoreAction() {
        return this.restoreAction != null;
    }

    public void restore(ServerPlayerEntity player) {
        if (this.restoreAction != null) {
            this.restoreAction.accept(player);
        }
    }

    public static Builder builder(String type) {
        return new Builder(type);
    }

    public static final class Builder {
        private final String type;
        private boolean preventDrop;
        private boolean preventExternalStorage;
        private boolean preventDeletion;
        private boolean preventDeathLoss;
        private boolean preventDuplication;
        private boolean autoRestore;
        private boolean allowRearrange = true;
        private boolean allowOffhandSwap = true;
        private int maxStacks = 1;
        private boolean cleanupWorldDrops;
        private Predicate<ServerPlayerEntity> canHold = player -> true;
        private Predicate<ServerPlayerEntity> shouldHave = player -> false;
        private Consumer<ServerPlayerEntity> restoreAction;

        private Builder(String type) {
            String normalized = ProtectedItemTags.normalizeType(type);
            if (normalized == null) {
                throw new IllegalArgumentException("Protected item type is required");
            }
            this.type = normalized;
        }

        public Builder preventDrop() {
            this.preventDrop = true;
            return this;
        }

        public Builder preventExternalStorage() {
            this.preventExternalStorage = true;
            return this;
        }

        public Builder preventDeletion() {
            this.preventDeletion = true;
            return this;
        }

        public Builder preventDeathLoss() {
            this.preventDeathLoss = true;
            return this;
        }

        public Builder preventDuplication() {
            this.preventDuplication = true;
            return this;
        }

        public Builder autoRestore() {
            this.autoRestore = true;
            return this;
        }

        public Builder allowRearrange(boolean allow) {
            this.allowRearrange = allow;
            return this;
        }

        public Builder allowOffhandSwap(boolean allow) {
            this.allowOffhandSwap = allow;
            return this;
        }

        public Builder maxStacks(int maxStacks) {
            this.maxStacks = maxStacks;
            return this;
        }

        public Builder cleanupWorldDrops() {
            this.cleanupWorldDrops = true;
            return this;
        }

        public Builder canHold(Predicate<ServerPlayerEntity> predicate) {
            this.canHold = predicate;
            return this;
        }

        public Builder shouldHave(Predicate<ServerPlayerEntity> predicate) {
            this.shouldHave = predicate;
            return this;
        }

        public Builder restoreAction(Consumer<ServerPlayerEntity> action) {
            this.restoreAction = action;
            return this;
        }

        public ProtectedItemRule build() {
            return new ProtectedItemRule(this);
        }
    }
}

