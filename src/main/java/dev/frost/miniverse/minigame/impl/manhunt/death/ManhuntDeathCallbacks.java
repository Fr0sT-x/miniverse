package dev.frost.miniverse.minigame.impl.manhunt.death;

import dev.frost.miniverse.minigame.core.death.CancellationReason;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame.ManhuntRole;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ManhuntDeathCallbacks implements DeathLifecycleCallbacks {
    private final ManhuntMinigame minigame;

    public ManhuntDeathCallbacks(ManhuntMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public void onDeath(ServerPlayerEntity victim, DeathContext context) {
        ManhuntRole role = this.minigame.getPlayerRole(victim);
        if (role == null) return;

        int deaths = this.minigame.incrementDeaths(victim.getUuid(), role);

        if (role == ManhuntRole.SPEEDRUNNER) {
            if (!this.hasLivesRemaining(this.minigame.getSettings().runnerLives(), deaths)) {
                this.minigame.broadcastMessage(
                    Text.literal(victim.getName().getString() + " (Speedrunner) is out of lives!").formatted(Formatting.RED)
                );
            } else {
                this.minigame.broadcastMessage(
                    Text.literal(victim.getName().getString() + " (Speedrunner) died and is waiting to respawn.").formatted(Formatting.RED)
                );
            }
        } else if (role == ManhuntRole.HUNTER) {
            if (!this.hasLivesRemaining(this.minigame.getSettings().hunterLives(), deaths)) {
                this.minigame.broadcastMessage(
                    Text.literal(victim.getName().getString() + " (Hunter) is out of lives!").formatted(Formatting.RED)
                );
            }
        }
    }

    @Override
    public void onRespawnBegin(ServerPlayerEntity victim, DeathContext context) {
    }

    @Override
    public void onRespawnComplete(ServerPlayerEntity victim, DeathContext context) {
        ManhuntRole role = this.minigame.getPlayerRole(victim);
        if (role == ManhuntRole.HUNTER) {
            if (this.minigame.getSettings().huntersCompassEnabled()) {
                this.minigame.grantHunterCompass(victim, false);
            }
        } else if (role == ManhuntRole.SPEEDRUNNER) {
            this.minigame.updateHunterCompasses();
        }
    }

    @Override
    public void onDeathFlowCancelled(ServerPlayerEntity victim, DeathContext context, CancellationReason reason) {
    }
    
    public boolean hasLivesRemaining(int maxLives, int deaths) {
        return maxLives <= 0 || deaths < maxLives;
    }
}
