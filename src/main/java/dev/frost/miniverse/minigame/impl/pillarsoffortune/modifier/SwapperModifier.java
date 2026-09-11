package dev.frost.miniverse.minigame.impl.pillarsoffortune.modifier;

import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.countdown.CountdownService;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.PillarsOfFortuneMinigame;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class SwapperModifier implements GameModifier {
    public static final String ID = "swapper";
    private final CountdownService countdownService = new CountdownService();
    private int ticksUntilSwap = 600;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void tick(PillarsOfFortuneMinigame minigame) {
        if (this.ticksUntilSwap > 0) {
            this.ticksUntilSwap--;
            int secondsRemaining = this.ticksUntilSwap / 20;
            if (this.ticksUntilSwap % 20 == 0 && secondsRemaining <= 5 && secondsRemaining > 0) {
                this.countdownService.announceVisibleCountdown(
                    minigame.getAliveParticipants(),
                    secondsRemaining,
                    5,
                    Text.literal("Swap!").formatted(Formatting.LIGHT_PURPLE),
                    Text.literal("Swapping in " + secondsRemaining + "s!").formatted(Formatting.YELLOW),
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()
                );
            }
            if (this.ticksUntilSwap == 0) {
                this.swapPlayers(minigame);
                this.ticksUntilSwap = 600;
                this.countdownService.reset();
            }
        }
    }

    private void swapPlayers(PillarsOfFortuneMinigame minigame) {
        List<ServerPlayerEntity> alivePlayers = new ArrayList<>(minigame.getAliveParticipants());
        if (alivePlayers.size() < 2) {
            return;
        }
        Collections.shuffle(alivePlayers);

        for (int i = 0; i < alivePlayers.size() - 1; i += 2) {
            ServerPlayerEntity p1 = alivePlayers.get(i);
            ServerPlayerEntity p2 = alivePlayers.get(i + 1);

            ServerWorld w1 = p1.getServerWorld();
            Vec3d pos1 = p1.getPos();
            float yaw1 = p1.getYaw();
            float pitch1 = p1.getPitch();

            ServerWorld w2 = p2.getServerWorld();
            Vec3d pos2 = p2.getPos();
            float yaw2 = p2.getYaw();
            float pitch2 = p2.getPitch();

            p1.teleport(w2, pos2.x, pos2.y, pos2.z, yaw2, pitch2);
            p2.teleport(w1, pos1.x, pos1.y, pos1.z, yaw1, pitch1);

            p1.sendMessage(Text.literal("🔄 You swapped positions with " + p2.getName().getString() + "!").formatted(Formatting.YELLOW), false);
            p2.sendMessage(Text.literal("🔄 You swapped positions with " + p1.getName().getString() + "!").formatted(Formatting.YELLOW), false);
        }

        minigame.broadcast(Text.literal("SWAP! All players have been swapped!").formatted(Formatting.GOLD, Formatting.BOLD));
    }

    @Override
    public void loadState(JsonObject object) {
        if (object.has("ticksUntilSwap")) {
            this.ticksUntilSwap = object.get("ticksUntilSwap").getAsInt();
        }
    }

    @Override
    public void saveState(JsonObject object) {
        object.addProperty("ticksUntilSwap", this.ticksUntilSwap);
    }

    @Override
    public void cleanup(MinecraftServer server) {
        this.countdownService.reset();
    }
}
