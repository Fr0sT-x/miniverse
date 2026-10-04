package dev.frost.miniverse.minigame.impl.hideandseek.combat;

import dev.frost.miniverse.minigame.core.item.ProtectedItemRule;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTags;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the endgame Seeker Echo Resonance Shard (formerly Sonar Compass):
 * - Right-clicking emits an auditory echo heard ONLY by the seeker.
 * - Volume (loudness) and pitch scale based on distance to the closest hider:
 *   - Far (> 35m): Very quiet, low-pitched hum
 *   - Medium (18–35m): Moderate chime
 *   - Close (7–18m): Crisp, loud resonance
 *   - Intense (< 7m): Maximum volume, high-pitched double chime
 * - Displays a qualitative visual signal bar on the action bar (no raw numbers).
 */
public class HideAndSeekSonarManager {
    public static final String TAG_SONAR_SHARD = "hider_sonar_shard";
    private static final int SONAR_COOLDOWN_SECONDS = 10;

    private final Map<UUID, Long> lastSonarTimes = new ConcurrentHashMap<>();

    public void initialize() {
        ProtectedItemService.getInstance().registerRule(
            ProtectedItemRule.builder(TAG_SONAR_SHARD)
                .preventDrop()
                .preventExternalStorage()
                .preventDeletion()
                .allowRearrange(false)
                .allowOffhandSwap(false)
                .build()
        );
    }

    public static ItemStack createSonarShard() {
        ItemStack item = new ItemStack(Items.ECHO_SHARD);
        item.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Echo Resonance Shard ").formatted(Formatting.AQUA, Formatting.BOLD)
            .append(Text.literal("§7(Right-Click)")));

        List<Text> lore = List.of(
            Text.literal("§7Listens for vibrations of hidden hiders."),
            Text.literal("§7The louder the resonance, the closer they are!"),
            Text.literal("§8(Audible only to you)")
        );
        item.set(DataComponentTypes.LORE, new LoreComponent(lore));
        ProtectedItemTags.mark(item, TAG_SONAR_SHARD, false, false, false);
        return item;
    }

    @Deprecated
    public static ItemStack createSonarCompass() {
        return createSonarShard();
    }

    public boolean ping(ServerPlayerEntity seeker, Collection<ServerPlayerEntity> hiders) {
        long now = System.currentTimeMillis();
        Long last = this.lastSonarTimes.get(seeker.getUuid());

        if (last != null) {
            long elapsed = (now - last) / 1000;
            if (elapsed < SONAR_COOLDOWN_SECONDS) {
                long left = SONAR_COOLDOWN_SECONDS - elapsed;
                seeker.sendMessage(Text.literal("§cEcho recharging (" + left + "s remaining)"), true);
                seeker.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5F, 1.2F);
                return false;
            }
        }

        if (hiders == null || hiders.isEmpty()) {
            seeker.sendMessage(Text.literal("§cNo hiders found nearby!").formatted(Formatting.RED), true);
            return false;
        }

        this.lastSonarTimes.put(seeker.getUuid(), now);

        double minDistanceSq = Double.MAX_VALUE;
        for (ServerPlayerEntity hider : hiders) {
            double dSq = seeker.squaredDistanceTo(hider);
            if (dSq < minDistanceSq) {
                minDistanceSq = dSq;
            }
        }

        double distance = Math.sqrt(minDistanceSq);
        float volume;
        float pitch;

        if (distance > 35.0) {
            // Far away: very quiet, low pitch
            volume = 0.25F;
            pitch = 0.6F;
        } else if (distance > 18.0) {
            // Medium range: moderate volume, normal pitch
            volume = 0.55F;
            pitch = 1.0F;
        } else if (distance > 7.0) {
            // Close: loud volume, high pitch
            volume = 0.85F;
            pitch = 1.5F;
        } else {
            // Very close: maximum volume, high alert pitch
            volume = 1.0F;
            pitch = 2.0F;
        }

        // Play auditory feedback ONLY to the seeker
        seeker.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, volume, pitch);
        seeker.playSoundToPlayer(SoundEvents.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.PLAYERS, volume * 0.7F, pitch);

        if (distance <= 7.0) {
            // Extra chime pulse for intense proximity
            seeker.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.9F, 1.8F);
        }

        return true;
    }

    public void clear() {
        ProtectedItemService.getInstance().removeRule(TAG_SONAR_SHARD);
        this.lastSonarTimes.clear();
    }
}
