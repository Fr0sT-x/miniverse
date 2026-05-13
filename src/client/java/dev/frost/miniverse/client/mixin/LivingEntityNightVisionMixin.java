package dev.frost.miniverse.client.mixin;

import dev.frost.miniverse.client.NightVisionToggle;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityNightVisionMixin {
	@Unique
	private static final StatusEffectInstance MINIVERSE_FAKE_NIGHT_VISION =
		new StatusEffectInstance(StatusEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false, false);

	@Inject(method = "hasStatusEffect", at = @At("HEAD"), cancellable = true)
	private void miniverse$fakeHasStatusEffect(RegistryEntry<StatusEffect> effect, CallbackInfoReturnable<Boolean> cir) {
		if (effect == StatusEffects.NIGHT_VISION && NightVisionToggle.isEnabled()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getStatusEffect", at = @At("HEAD"), cancellable = true)
	private void miniverse$fakeGetStatusEffect(RegistryEntry<StatusEffect> effect, CallbackInfoReturnable<StatusEffectInstance> cir) {
		if (effect == StatusEffects.NIGHT_VISION && NightVisionToggle.isEnabled()) {
			cir.setReturnValue(MINIVERSE_FAKE_NIGHT_VISION);
		}
	}
}
