package dev.frost.miniverse.minigame.core.item;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectedItemService {
    private static final ProtectedItemService INSTANCE = new ProtectedItemService();

    private final Map<String, ProtectedItemRule> rules = new ConcurrentHashMap<>();
    private int tickCounter;

    private ProtectedItemService() {
    }

    public static ProtectedItemService getInstance() {
        return INSTANCE;
    }

    public void registerRule(ProtectedItemRule rule) {
        if (rule == null) {
            return;
        }
        this.rules.put(rule.type(), rule);
    }

    public void removeRule(String type) {
        if (type == null) {
            return;
        }
        this.rules.remove(type);
    }

    public void clearRules() {
        this.rules.clear();
        this.tickCounter = 0;
    }

    public boolean shouldCancelDrop(ServerPlayerEntity player, ItemStack stack) {
        ProtectedItemRule rule = ruleFor(stack);
        if (rule == null) {
            return false;
        }
        if (!rule.canHold(player)) {
            return true;
        }
        return rule.preventDrop() || rule.preventDeletion();
    }

    public boolean shouldCancelInventoryAction(ServerPlayerEntity player, ScreenHandler handler, int slotId, int button, SlotActionType actionType) {
        ItemStack cursorStack = handler.getCursorStack();
        Slot slot = null;
        ItemStack slotStack = ItemStack.EMPTY;
        boolean slotValid = slotId >= 0 && slotId < handler.slots.size();
        if (slotValid) {
            slot = handler.slots.get(slotId);
            slotStack = slot.getStack();
        }

        ProtectedItemRule cursorRule = ruleFor(cursorStack);
        ProtectedItemRule slotRule = ruleFor(slotStack);
        if (cursorRule == null && slotRule == null) {
            return false;
        }

        if (!slotValid) {
            return cursorRule != null && shouldCancelOutside(cursorRule, player, actionType);
        }

        boolean slotIsPlayer = slot.inventory == player.getInventory();
        if (cursorRule != null && shouldCancelForRule(cursorRule, player, actionType, button, slotIsPlayer, true)) {
            return true;
        }
        return slotRule != null && shouldCancelForRule(slotRule, player, actionType, button, slotIsPlayer, false);
    }

    public void tick(MinecraftServer server) {
        if (this.rules.isEmpty()) {
            return;
        }
        this.tickCounter++;
        if (this.tickCounter % 20 != 0) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            enforcePlayerInventory(player);
        }

        if (shouldCleanupWorldDrops()) {
            cleanupWorldDrops(server);
        }
    }

    public void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (this.rules.isEmpty()) {
            return;
        }
        for (ProtectedItemRule rule : this.rules.values()) {
            if (!rule.preventDeathLoss()) {
                continue;
            }
            if (!rule.shouldHave(newPlayer) || !rule.canHold(newPlayer)) {
                continue;
            }
            if (playerHasProtectedItem(newPlayer, rule)) {
                continue;
            }
            rule.restore(newPlayer);
        }
    }

    private void enforcePlayerInventory(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
        Map<String, Integer> counts = new HashMap<>();
        boolean changed = false;
        boolean handlerChanged = false;

        ItemStack cursorStack = handler == null ? ItemStack.EMPTY : handler.getCursorStack();
        ProtectedItemRule cursorRule = ruleFor(cursorStack);
        if (cursorRule != null) {
            if (!cursorRule.canHold(player)) {
                handler.setCursorStack(ItemStack.EMPTY);
                handlerChanged = true;
            } else {
                int current = counts.getOrDefault(cursorRule.type(), 0) + 1;
                counts.put(cursorRule.type(), current);

                if (cursorRule.preventDuplication() && cursorRule.maxStacks() > 0 && current > cursorRule.maxStacks()) {
                    handler.setCursorStack(ItemStack.EMPTY);
                    handlerChanged = true;
                } else if (cursorRule.preventDuplication() && cursorRule.maxStacks() == 1 && cursorStack.getCount() > 1) {
                    cursorStack.setCount(1);
                    handlerChanged = true;
                }
            }
        }

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            ProtectedItemRule rule = ruleFor(stack);
            if (rule == null) {
                continue;
            }

            if (!rule.canHold(player)) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
                continue;
            }

            int maxStacks = rule.maxStacks();
            int current = counts.getOrDefault(rule.type(), 0) + 1;
            counts.put(rule.type(), current);

            if (rule.preventDuplication() && maxStacks > 0 && current > maxStacks) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
                continue;
            }

            if (rule.preventDuplication() && maxStacks == 1 && stack.getCount() > 1) {
                stack.setCount(1);
                changed = true;
            }
        }

        for (ProtectedItemRule rule : this.rules.values()) {
            if (!rule.autoRestore() || !rule.shouldHave(player) || !rule.canHold(player)) {
                continue;
            }
            int count = counts.getOrDefault(rule.type(), 0);
            if (count > 0) {
                continue;
            }
            if (rule.hasRestoreAction()) {
                rule.restore(player);
                changed = true;
            }
        }

        if (changed) {
            inventory.markDirty();
        }
        if (handlerChanged) {
            handler.syncState();
        }
    }

    private boolean shouldCleanupWorldDrops() {
        for (ProtectedItemRule rule : this.rules.values()) {
            if (rule.cleanupWorldDrops()) {
                return true;
            }
        }
        return false;
    }

    private void cleanupWorldDrops(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            List<? extends ItemEntity> items = world.getEntitiesByType(TypeFilter.instanceOf(ItemEntity.class),
                entity -> shouldDiscardWorldItem(entity.getStack()));
            for (ItemEntity item : items) {
                item.discard();
            }
        }
    }

    private boolean shouldDiscardWorldItem(ItemStack stack) {
        ProtectedItemRule rule = ruleFor(stack);
        return rule != null && rule.cleanupWorldDrops();
    }

    private boolean playerHasProtectedItem(ServerPlayerEntity player, ProtectedItemRule rule) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (rule.type().equals(ProtectedItemTags.getType(inventory.getStack(slot)))) {
                return true;
            }
        }
        return false;
    }

    private ProtectedItemRule ruleFor(ItemStack stack) {
        String type = ProtectedItemTags.getType(stack);
        if (type == null) {
            return null;
        }
        return this.rules.get(type);
    }

    private boolean shouldCancelOutside(ProtectedItemRule rule, ServerPlayerEntity player, SlotActionType actionType) {
        if (!rule.canHold(player)) {
            return true;
        }
        if (actionType == SlotActionType.THROW || actionType == SlotActionType.PICKUP) {
            return rule.preventDrop() || rule.preventDeletion();
        }
        return rule.preventDeletion();
    }

    private boolean shouldCancelForRule(ProtectedItemRule rule, ServerPlayerEntity player, SlotActionType actionType, int button,
                                        boolean slotIsPlayer, boolean stackIsCursor) {
        if (!rule.canHold(player)) {
            return true;
        }

        boolean externalSlot = !slotIsPlayer;

        if (actionType == SlotActionType.THROW) {
            return rule.preventDrop() || rule.preventDeletion();
        }

        if (actionType == SlotActionType.CLONE) {
            return rule.preventDuplication();
        }

        if (actionType == SlotActionType.QUICK_MOVE) {
            if (slotIsPlayer) {
                if (!rule.allowRearrange()) {
                    return true;
                }
                if (rule.preventExternalStorage()) {
                    return true;
                }
            }
            return false;
        }

        if (actionType == SlotActionType.SWAP) {
            if (button == 40 && !rule.allowOffhandSwap()) {
                return true;
            }
            if (!rule.allowRearrange() && slotIsPlayer) {
                return true;
            }
            return rule.preventExternalStorage() && externalSlot;
        }

        if (actionType == SlotActionType.QUICK_CRAFT) {
            if (!rule.allowRearrange() && slotIsPlayer) {
                return true;
            }
            return rule.preventExternalStorage() && externalSlot;
        }

        if (!rule.allowRearrange() && slotIsPlayer) {
            return true;
        }

        if (externalSlot && rule.preventExternalStorage()) {
            return true;
        }

        if (stackIsCursor && actionType == SlotActionType.PICKUP_ALL && rule.preventDuplication()) {
            return true;
        }

        return false;
    }
}

