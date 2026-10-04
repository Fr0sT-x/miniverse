package dev.frost.miniverse.minigame.impl.hideandseek.disguise;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class DisguiseSelectGui {
    private DisguiseSelectGui() {}

    public static void open(ServerPlayerEntity player, BlockDisguiseManager disguiseManager) {
        List<DisguiseType> disguises = disguiseManager.getAvailableDisguises();
        boolean large = disguises.size() > 14;
        int totalSlots = large ? 54 : 27;
        int rows = large ? 6 : 3;
        ScreenHandlerType<GenericContainerScreenHandler> type = large ? ScreenHandlerType.GENERIC_9X6 : ScreenHandlerType.GENERIC_9X3;

        SimpleInventory inventory = new SimpleInventory(totalSlots);
        DisguiseType current = disguiseManager.getDisguise(player.getUuid());

        // Fill background borders with grey stained glass pane
        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < totalSlots; i++) {
            inventory.setStack(i, border.copy());
        }

        List<Integer> availableSlots = new ArrayList<>();
        if (large) {
            // Rows 1, 2, 3, 4 with columns 1..7 (inner 4x7 = 28 slots)
            for (int r = 1; r <= 4; r++) {
                for (int c = 1; c <= 7; c++) {
                    availableSlots.add(r * 9 + c);
                }
            }
        } else {
            // Rows 1 and 2 with columns 1..7 (inner 2x7 = 14 slots)
            for (int r = 1; r <= 2; r++) {
                for (int c = 1; c <= 7; c++) {
                    availableSlots.add(r * 9 + c);
                }
            }
        }

        for (int i = 0; i < disguises.size() && i < availableSlots.size(); i++) {
            DisguiseType disguise = disguises.get(i);
            int slot = availableSlots.get(i);
            ItemStack icon = disguise.icon();

            boolean isCurrent = disguise.equals(current);
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(disguise.displayName()).formatted(isCurrent ? Formatting.GREEN : Formatting.YELLOW, Formatting.BOLD));

            List<Text> lore = new ArrayList<>();
            if (isCurrent) {
                lore.add(Text.literal("✔ ACTIVE DISGUISE").formatted(Formatting.GREEN, Formatting.BOLD));
            } else {
                lore.add(Text.literal("Click to disguise as this block!").formatted(Formatting.GRAY));
            }
            icon.set(DataComponentTypes.LORE, new LoreComponent(lore));

            inventory.setStack(slot, icon);
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, p) -> new GenericContainerScreenHandler(type, syncId, playerInv, inventory, rows) {
                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity p2) {
                    if (slotIndex >= 0 && slotIndex < totalSlots) {
                        for (int i = 0; i < disguises.size() && i < availableSlots.size(); i++) {
                            if (slotIndex == availableSlots.get(i)) {
                                DisguiseType chosen = disguises.get(i);
                                disguiseManager.applyDisguise(player, chosen);
                                player.closeHandledScreen();
                                return;
                            }
                        }
                    }
                }
            },
            Text.literal("Choose Your Disguise").formatted(Formatting.DARK_GREEN, Formatting.BOLD)
        ));
    }
}
