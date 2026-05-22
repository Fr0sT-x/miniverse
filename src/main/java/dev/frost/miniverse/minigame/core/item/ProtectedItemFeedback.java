package dev.frost.miniverse.minigame.core.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectedItemFeedback {
    private static final Text RULE_BLOCKED_MESSAGE = Text.literal("This special item cannot be moved there.");
    private static final int MESSAGE_COOLDOWN_TICKS = 20;
    private static final Map<UUID, Integer> LAST_MESSAGE_TICK = new ConcurrentHashMap<>();

    private ProtectedItemFeedback() {
    }

    public static void sendRuleBlockedMessage(PlayerEntity player) {
        if (player == null) {
            return;
        }

        int currentTick = player.age;
        Integer lastTick = LAST_MESSAGE_TICK.get(player.getUuid());
        if (lastTick != null && currentTick - lastTick < MESSAGE_COOLDOWN_TICKS) {
            return;
        }

        LAST_MESSAGE_TICK.put(player.getUuid(), currentTick);
        player.sendMessage(RULE_BLOCKED_MESSAGE, true);
    }
}
