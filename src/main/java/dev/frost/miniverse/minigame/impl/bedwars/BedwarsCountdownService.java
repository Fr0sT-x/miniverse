package dev.frost.miniverse.minigame.impl.bedwars;

import dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsGeneratorManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.LinkedList;
import java.util.Queue;

public class BedwarsCountdownService {
    private final BedwarsMinigame minigame;
    private final BedwarsGeneratorManager generatorManager;
    private final Queue<Phase> phases = new LinkedList<>();
    private Phase currentPhase;
    private int ticksRemainingInPhase;

    public BedwarsCountdownService(BedwarsMinigame minigame, BedwarsGeneratorManager generatorManager) {
        this.minigame = minigame;
        this.generatorManager = generatorManager;
        
        // Define phases (lengths in ticks, 20 ticks = 1 second)
        // Hypixel approx timings:
        // Diamond II (6 mins)
        // Emerald II (6 mins)
        // Diamond III (6 mins)
        // Emerald III (6 mins)
        // Bed Destruction (10 mins)
        // Sudden Death (10 mins) -> Game Over
        
        phases.add(new Phase("Diamond II", 6 * 60 * 20, () -> {
            generatorManager.setDiamondMultiplier(0.5); // Double speed
        }));
        phases.add(new Phase("Emerald II", 6 * 60 * 20, () -> {
            generatorManager.setEmeraldMultiplier(0.5);
        }));
        phases.add(new Phase("Diamond III", 6 * 60 * 20, () -> {
            generatorManager.setDiamondMultiplier(0.25); // Quadruple speed
        }));
        phases.add(new Phase("Emerald III", 6 * 60 * 20, () -> {
            generatorManager.setEmeraldMultiplier(0.25);
        }));
        phases.add(new Phase("Bed Destruction", 10 * 60 * 20, () -> {
            minigame.destroyAllBeds();
        }));
        phases.add(new Phase("Sudden Death", 10 * 60 * 20, () -> {
            // End game in a draw if it gets this far
            minigame.endMatchInDraw();
        }));
        
        nextPhase();
    }

    private void nextPhase() {
        if (!phases.isEmpty()) {
            currentPhase = phases.poll();
            ticksRemainingInPhase = currentPhase.durationTicks;
        } else {
            currentPhase = null;
        }
    }

    public void tick(MinecraftServer server) {
        if (currentPhase == null) return;
        
        ticksRemainingInPhase--;
        
        if (ticksRemainingInPhase <= 0) {
            currentPhase.onComplete.run();
            minigame.broadcast(Text.literal(currentPhase.name + " has started!").formatted(net.minecraft.util.Formatting.AQUA, net.minecraft.util.Formatting.BOLD));
            nextPhase();
        }
    }

    public String getCurrentPhaseDisplay() {
        if (currentPhase == null) return "Game Over";
        int secondsLeft = Math.max(0, ticksRemainingInPhase / 20);
        int m = secondsLeft / 60;
        int s = secondsLeft % 60;
        return currentPhase.name + " in " + String.format("%02d:%02d", m, s);
    }
    
    private static class Phase {
        String name;
        int durationTicks;
        Runnable onComplete;

        Phase(String name, int durationTicks, Runnable onComplete) {
            this.name = name;
            this.durationTicks = durationTicks;
            this.onComplete = onComplete;
        }
    }
}
