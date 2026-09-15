package dev.frost.miniverse.minigame.impl.horde.shop;

import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import dev.frost.miniverse.minigame.impl.horde.HordeSurvivalMinigame;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class EmergencyFlareItem {
    public static final String FLARE_ID = "horde_emergency_flare";

    private EmergencyFlareItem() {}

    public static ItemStack createStack() {
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Emergency Merchant Flare").formatted(Formatting.GOLD, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Right-click to summon a temporary spirit merchant.").formatted(Formatting.YELLOW),
            Text.literal("Allows exactly 1 purchase before vanishing!").formatted(Formatting.RED)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isFlare(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.FIREWORK_ROCKET) return false;
        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        return name != null && name.getString().contains("Emergency Merchant Flare");
    }

    public static void use(ServerPlayerEntity player, HordeSurvivalMinigame minigame) {
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0f, 1.2f);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        minigame.openEmergencyShop(player);
    }
}
