package dev.frost.miniverse.minigame.impl.hideandseek.tracker;

import dev.frost.miniverse.minigame.core.item.ProtectedItemRule;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTags;
import dev.frost.miniverse.minigame.impl.hideandseek.disguise.BlockDisguiseManager;
import dev.frost.miniverse.minigame.impl.hideandseek.disguise.DisguiseType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the Seeker's Active Disguise Roster:
 * 1. Populates main inventory starting at Slot 9 (topmost left) with all active disguises,
 *    sorted descending by hider count. Stack size equals the number of hiders using that block.
 * 2. Continuously auto-cycles Slot 8 (last hotbar slot) every 3 seconds through active blocks,
 *    supporting immediate manual advancement on right-click.
 * 3. Protects all display and radar items via F13 Protected Items Framework.
 */
public class SeekerDisguiseTracker {
    public static final String TAG_DISGUISE_DISPLAY = "hider_disguise_display";
    public static final String TAG_DISGUISE_RADAR = "hider_disguise_radar";

    public static final int INVENTORY_ROSTER_START_SLOT = 9; // Top-left of 3x9 main inventory
    public static final int INVENTORY_ROSTER_END_SLOT = 35;   // Bottom-right of 3x9 main inventory
    public static final int HOTBAR_RADAR_SLOT = 8;           // 9th slot of hotbar

    public record DisguiseCount(DisguiseType disguise, int count) {}

    private final BlockDisguiseManager disguiseManager;
    private final int cycleIntervalTicks;
    private int currentCycleIndex = 0;
    private int lastDisguiseHash = -1;

    public SeekerDisguiseTracker(BlockDisguiseManager disguiseManager, int hotbarCycleSeconds) {
        this.disguiseManager = disguiseManager;
        this.cycleIntervalTicks = Math.max(20, hotbarCycleSeconds * 20);
    }

    public void initialize() {
        ProtectedItemService pis = ProtectedItemService.getInstance();
        pis.registerRule(
            ProtectedItemRule.builder(TAG_DISGUISE_DISPLAY)
                .preventDrop()
                .preventExternalStorage()
                .preventDeletion()
                .allowRearrange(false)
                .allowOffhandSwap(false)
                .build()
        );
        pis.registerRule(
            ProtectedItemRule.builder(TAG_DISGUISE_RADAR)
                .preventDrop()
                .preventExternalStorage()
                .preventDeletion()
                .allowRearrange(false)
                .allowOffhandSwap(false)
                .build()
        );
        this.currentCycleIndex = 0;
        this.lastDisguiseHash = -1;
    }

    public void tick(int gameTicks, List<ServerPlayerEntity> seekers, List<ServerPlayerEntity> aliveHiders) {
        if (seekers == null || seekers.isEmpty()) {
            return;
        }

        List<DisguiseCount> active = computeActiveDisguises(aliveHiders);
        int currentHash = computeDisguiseHash(active);

        // Update main inventory roster whenever disguise distribution changes, or every 2 seconds
        if (currentHash != this.lastDisguiseHash || gameTicks % 40 == 0) {
            this.lastDisguiseHash = currentHash;
            for (ServerPlayerEntity seeker : seekers) {
                updateInventoryRoster(seeker, active);
            }
        }

        // Auto-cycle Slot 8 every cycleIntervalTicks (default 60 ticks = 3s)
        if (gameTicks % this.cycleIntervalTicks == 0) {
            if (!active.isEmpty()) {
                this.currentCycleIndex = (this.currentCycleIndex + 1) % active.size();
            } else {
                this.currentCycleIndex = 0;
            }
            for (ServerPlayerEntity seeker : seekers) {
                updateSlot8(seeker, active);
            }
        }
    }

    public void updateAll(List<ServerPlayerEntity> seekers, List<ServerPlayerEntity> aliveHiders) {
        if (seekers == null || seekers.isEmpty()) {
            return;
        }
        List<DisguiseCount> active = computeActiveDisguises(aliveHiders);
        this.lastDisguiseHash = computeDisguiseHash(active);
        if (!active.isEmpty()) {
            this.currentCycleIndex %= active.size();
        } else {
            this.currentCycleIndex = 0;
        }

        for (ServerPlayerEntity seeker : seekers) {
            updateInventoryRoster(seeker, active);
            updateSlot8(seeker, active);
        }
    }

    public boolean handleManualCycle(ServerPlayerEntity seeker, List<ServerPlayerEntity> aliveHiders) {
        List<DisguiseCount> active = computeActiveDisguises(aliveHiders);
        if (active.isEmpty()) {
            return false;
        }
        this.currentCycleIndex = (this.currentCycleIndex + 1) % active.size();
        updateSlot8(seeker, active);
        seeker.playSoundToPlayer(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.4F, 1.8F);
        return true;
    }

    private void updateInventoryRoster(ServerPlayerEntity seeker, List<DisguiseCount> activeDisguises) {
        int slot = INVENTORY_ROSTER_START_SLOT;
        for (DisguiseCount entry : activeDisguises) {
            if (slot > INVENTORY_ROSTER_END_SLOT) {
                break;
            }
            ItemStack displayItem = createDisplayItem(entry);
            seeker.getInventory().setStack(slot, displayItem);
            slot++;
        }

        // Clear any remaining slots that previously held disguise display items
        while (slot <= INVENTORY_ROSTER_END_SLOT) {
            ItemStack existing = seeker.getInventory().getStack(slot);
            if (ProtectedItemTags.hasType(existing, TAG_DISGUISE_DISPLAY)) {
                seeker.getInventory().setStack(slot, ItemStack.EMPTY);
            }
            slot++;
        }
    }

    private void updateSlot8(ServerPlayerEntity seeker, List<DisguiseCount> activeDisguises) {
        if (activeDisguises.isEmpty()) {
            ItemStack currentSlot8 = seeker.getInventory().getStack(HOTBAR_RADAR_SLOT);
            if (ProtectedItemTags.hasType(currentSlot8, TAG_DISGUISE_RADAR)) {
                seeker.getInventory().setStack(HOTBAR_RADAR_SLOT, ItemStack.EMPTY);
            }
            return;
        }

        int index = Math.floorMod(this.currentCycleIndex, activeDisguises.size());
        DisguiseCount current = activeDisguises.get(index);
        ItemStack radarItem = createRadarItem(current, index, activeDisguises.size());
        seeker.getInventory().setStack(HOTBAR_RADAR_SLOT, radarItem);
    }

    private ItemStack createDisplayItem(DisguiseCount entry) {
        ItemStack stack = entry.disguise().icon();
        stack.setCount(Math.min(64, Math.max(1, entry.count())));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(entry.disguise().displayName())
            .formatted(Formatting.YELLOW, Formatting.BOLD)
            .append(Text.literal(" §7(Active Disguise)")));

        List<Text> lore = List.of(
            Text.literal("§7Alive Hiders: §a" + entry.count() + " " + (entry.count() == 1 ? "player" : "players")),
            Text.literal("§8This block is hidden somewhere on the map."),
            Text.literal("§8(Locked information display)")
        );
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        ProtectedItemTags.mark(stack, TAG_DISGUISE_DISPLAY, false, false, false);
        return stack;
    }

    private ItemStack createRadarItem(DisguiseCount entry, int index, int total) {
        ItemStack stack = entry.disguise().icon();
        stack.setCount(Math.min(64, Math.max(1, entry.count())));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Disguise Radar §7| ")
            .formatted(Formatting.GOLD, Formatting.BOLD)
            .append(Text.literal(entry.disguise().displayName()).formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" §8[" + entry.count() + " Hider" + (entry.count() > 1 ? "s" : "") + "]").formatted(Formatting.GREEN)));

        List<Text> lore = List.of(
            Text.literal("§7Target Block: §f" + entry.disguise().displayName() + " §8(" + (index + 1) + "/" + total + ")"),
            Text.literal("§7Alive as this block: §a" + entry.count()),
            Text.empty(),
            Text.literal("§e» Auto-cycles every 3s"),
            Text.literal("§b» Right-Click to cycle now"),
            Text.literal("§8(Check top-left inventory for full list)")
        );
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        ProtectedItemTags.mark(stack, TAG_DISGUISE_RADAR, false, false, false);
        return stack;
    }

    public List<DisguiseCount> computeActiveDisguises(Collection<ServerPlayerEntity> aliveHiders) {
        if (aliveHiders == null || aliveHiders.isEmpty()) {
            return List.of();
        }

        Map<DisguiseType, Integer> counts = new LinkedHashMap<>();
        for (ServerPlayerEntity hider : aliveHiders) {
            if (hider == null || hider.isDisconnected() || hider.isSpectator()) {
                continue;
            }
            DisguiseType type = this.disguiseManager != null ? this.disguiseManager.getDisguise(hider.getUuid()) : null;
            if (type != null) {
                counts.merge(type, 1, Integer::sum);
            }
        }

        List<DisguiseCount> list = new ArrayList<>();
        for (Map.Entry<DisguiseType, Integer> e : counts.entrySet()) {
            list.add(new DisguiseCount(e.getKey(), e.getValue()));
        }

        // Sort descending by count, then alphabetically by name for consistency
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.count(), a.count());
            if (cmp != 0) return cmp;
            return a.disguise().displayName().compareTo(b.disguise().displayName());
        });

        return list;
    }

    private int computeDisguiseHash(List<DisguiseCount> list) {
        int hash = 1;
        for (DisguiseCount dc : list) {
            hash = 31 * hash + dc.disguise().id().hashCode();
            hash = 31 * hash + dc.count();
        }
        return hash;
    }

    public void clear(Collection<ServerPlayerEntity> seekers) {
        ProtectedItemService pis = ProtectedItemService.getInstance();
        pis.removeRule(TAG_DISGUISE_DISPLAY);
        pis.removeRule(TAG_DISGUISE_RADAR);

        if (seekers != null) {
            for (ServerPlayerEntity seeker : seekers) {
                if (seeker == null || seeker.isDisconnected()) {
                    continue;
                }
                // Clear hotbar slot 8 if radar
                ItemStack slot8 = seeker.getInventory().getStack(HOTBAR_RADAR_SLOT);
                if (ProtectedItemTags.hasType(slot8, TAG_DISGUISE_RADAR)) {
                    seeker.getInventory().setStack(HOTBAR_RADAR_SLOT, ItemStack.EMPTY);
                }
                // Clear main inventory roster slots if disguise display
                for (int i = INVENTORY_ROSTER_START_SLOT; i <= INVENTORY_ROSTER_END_SLOT; i++) {
                    ItemStack stack = seeker.getInventory().getStack(i);
                    if (ProtectedItemTags.hasType(stack, TAG_DISGUISE_DISPLAY)) {
                        seeker.getInventory().setStack(i, ItemStack.EMPTY);
                    }
                }
            }
        }
        this.currentCycleIndex = 0;
        this.lastDisguiseHash = -1;
    }
}
