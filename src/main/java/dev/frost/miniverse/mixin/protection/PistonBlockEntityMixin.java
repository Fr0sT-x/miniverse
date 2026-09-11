package dev.frost.miniverse.mixin.protection;

import dev.frost.miniverse.minigame.core.protection.MapProtectionManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonBlockEntity.class)
public abstract class PistonBlockEntityMixin extends BlockEntity {

    @Shadow private Direction facing;
    @Shadow public abstract boolean isSource();

    protected PistonBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "finish", at = @At("HEAD"))
    private void onFinish(CallbackInfo ci) {
        if (this.world != null && !this.world.isClient && !this.isSource() && this.facing != null) {
            BlockPos sourcePos = this.pos.offset(this.facing.getOpposite());
            MapProtectionManager.onBlockMoved(sourcePos, this.pos);
        }
    }
}
