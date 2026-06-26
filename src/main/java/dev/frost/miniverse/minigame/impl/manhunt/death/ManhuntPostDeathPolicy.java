package dev.frost.miniverse.minigame.impl.manhunt.death;

import dev.frost.miniverse.minigame.core.death.CancellationReason;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.TimedRespawnPolicy;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame.ManhuntRole;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;
import com.google.gson.JsonObject;

public class ManhuntPostDeathPolicy implements PostDeathPolicy {
    private final ManhuntMinigame minigame;
    private TimedRespawnPolicy delegate;
    private boolean isEliminated;
    private UUID victimId;

    public ManhuntPostDeathPolicy(ManhuntMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public void start(ServerPlayerEntity player, DeathContext context) {
        this.victimId = player.getUuid();
        ManhuntRole role = this.minigame.getPlayerRole(player);
        if (role == null) {
            this.minigame.getDeathLifecycleManager().executeRespawn(player);
            return;
        }

        int deaths = this.minigame.getDeaths(this.victimId, role);
        
        int maxLives = role == ManhuntRole.SPEEDRUNNER ? 
            this.minigame.getSettings().runnerLives() : 
            this.minigame.getSettings().hunterLives();

        int delaySeconds = role == ManhuntRole.SPEEDRUNNER ? 
            this.minigame.getSettings().runnerRespawnDelaySeconds() : 
            this.minigame.getSettings().hunterRespawnDelaySeconds();

        // We can just rely on the fact that if delay == 0, we respawn instantly.
        // If out of lives, eliminated!
        if (maxLives > 0 && deaths >= maxLives) {
            this.isEliminated = true;
            this.checkEndGameConditions();
            return;
        }

        if (delaySeconds > 0) {
            this.delegate = new TimedRespawnPolicy(this.minigame.getDeathLifecycleManager(), delaySeconds * 20);
            this.delegate.start(player, context);
        } else {
            this.minigame.getDeathLifecycleManager().executeRespawn(player);
        }
    }

    private void checkEndGameConditions() {
        // If there are no alive speedrunners, hunters win
        if (this.minigame.getAliveSpeedrunnerCount() == 0) {
            if (!this.minigame.isProgressionBlocked()) {
                this.minigame.endGameWithHunterVictory();
            }
        } else if (!this.minigame.hasHunterInReconnectGrace()) {
            this.minigame.endGameWithRunnerVictory(Text.literal("All hunters have been eliminated."));
        }
    }

    @Override
    public void cancel(CancellationReason reason) {
        if (this.delegate != null) {
            this.delegate.cancel(reason);
        }
    }

    @Override
    public void tick(MinecraftServer server) {
        if (this.isEliminated) {
            return;
        }

        if (this.delegate != null) {
            this.delegate.tick(server);
        }
    }
    
    @Override
    public JsonObject saveRuntimeState() {
        JsonObject root = new JsonObject();
        root.addProperty("isEliminated", this.isEliminated);
        if (this.victimId != null) root.addProperty("victimId", this.victimId.toString());
        if (this.delegate != null) {
            root.add("delegate", this.delegate.saveRuntimeState());
        }
        return root;
    }

    @Override
    public void loadRuntimeState(JsonObject state) {
        if (state == null) return;
        if (state.has("isEliminated")) this.isEliminated = state.get("isEliminated").getAsBoolean();
        if (state.has("victimId")) this.victimId = UUID.fromString(state.get("victimId").getAsString());
        if (state.has("delegate")) {
            // Need to recreate delegate with dummy delay because loadRuntimeState overwrites it
            this.delegate = new TimedRespawnPolicy(this.minigame.getDeathLifecycleManager(), 0);
            this.delegate.loadRuntimeState(state.getAsJsonObject("delegate"));
        }
    }
}
