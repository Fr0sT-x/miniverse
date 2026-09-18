package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.impl.zombies.mob.ZombieType;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithHead;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeadFeatureRenderer.class)
public abstract class HeadFeatureRendererMixin<T extends LivingEntity, M extends EntityModel<T> & ModelWithHead> extends FeatureRenderer<T, M> {
    public HeadFeatureRendererMixin(FeatureRendererContext<T, M> context) {
        super(context);
    }

    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/block/entity/SkullBlockEntityRenderer;renderSkull(Lnet/minecraft/util/math/Direction;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/block/entity/SkullBlockEntityModel;Lnet/minecraft/client/render/RenderLayer;)V"
        )
    )
    private void miniverse$scaleGuardianHead(
        MatrixStack matrixStack,
        VertexConsumerProvider vertexConsumerProvider,
        int i,
        T livingEntity,
        float f,
        float g,
        float h,
        float j,
        float k,
        float l,
        CallbackInfo ci
    ) {
        ItemStack headStack = livingEntity.getEquippedStack(EquipmentSlot.HEAD);
        if (ZombieType.isGuardianHead(headStack)) {
            // Cancel the -0.5 offset, scale down by 20% (0.8F) at true center, and reapply offset
            matrixStack.translate(0.5, 0.0, 0.5);
            matrixStack.scale(0.8F, 0.8F, 0.8F);
            matrixStack.translate(-0.5, 0.0, -0.5);
        }
    }
}
