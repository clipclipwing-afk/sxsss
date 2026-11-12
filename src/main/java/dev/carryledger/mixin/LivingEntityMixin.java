package dev.carryledger.mixin;

import dev.carryledger.BossTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-side death of a living entity = confirmed boss kill. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "die", at = @At("HEAD"), require = 0)
    private void carryledger$die(DamageSource source, CallbackInfo ci) {
        try {
            BossTracker.onDeath((LivingEntity) (Object) this);
        } catch (Throwable ignored) {
        }
    }
}
