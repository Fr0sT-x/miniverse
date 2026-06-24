package dev.frost.miniverse.minigame.impl.manhunt.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

public class ManhuntRespawnStrategy implements RespawnStrategy {
    private final ManhuntMinigame minigame;

    public ManhuntRespawnStrategy(ManhuntMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public RespawnLocation resolve(DeathContext context, @Nullable SpectatorSession spectatorSession) {
        net.minecraft.server.MinecraftServer server = this.minigame.getServer();
        if (server == null) return null;
        
        ManhuntMinigame.ManhuntRole role = this.minigame.getPlayerRole(this.minigame.getPlayerByUuid(context.victimId()));
        boolean respawnAtTeammate = false;
        if (role == ManhuntMinigame.ManhuntRole.SPEEDRUNNER) {
            respawnAtTeammate = this.minigame.getSettings().runnerRespawnAtTeammate();
        } else if (role == ManhuntMinigame.ManhuntRole.HUNTER) {
            respawnAtTeammate = this.minigame.getSettings().hunterRespawnAtTeammate();
        }
        
        if (respawnAtTeammate && spectatorSession != null && spectatorSession.targetId() != null) {
            net.minecraft.entity.Entity target = dev.frost.miniverse.minigame.core.spectator.SpectatorUtils.findEntity(server, spectatorSession.targetId());
            if (target != null && target.getWorld() instanceof ServerWorld targetWorld) {
                return new RespawnLocation(targetWorld, target.getPos(), target.getYaw(), target.getPitch());
            }
        }
        
        ServerWorld world = server.getOverworld();
        return RespawnStrategy.resolveVanillaSpawn(server, context.victimId(), world);
    }
}
