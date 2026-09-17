package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesMinigame;
import dev.frost.miniverse.minigame.impl.zombies.mob.ZombieEntityManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SlimeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlimeEntity.class)
public class SlimeEntityMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void miniverse$preventZombieSlimeSplit(Entity.RemovalReason reason, CallbackInfo ci) {
        SlimeEntity self = (SlimeEntity) (Object) this;
        if (self.getCommandTags().contains(ZombieEntityManager.TAG_ZOMBIE_MOB)) {
            Minigame active = MinigameManager.getInstance().getActiveMinigame();
            if (active instanceof ZombiesMinigame zm && zm.getState() == GameState.RUNNING) {
                // Setting size to 1 prevents vanilla remove() from executing the `size > 1` child spawn block
                self.setSize(1, false);
            }
        }
    }
}
