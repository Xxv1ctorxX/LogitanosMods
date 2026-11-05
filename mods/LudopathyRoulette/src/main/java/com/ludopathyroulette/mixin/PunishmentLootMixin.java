package com.ludopathyroulette.mixin;

import net.minecraft.world.entity.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Solo criaturas marcadas por la ruleta: impide granjas de botín y XP por castigos. */
@Mixin(LivingEntity.class)
public abstract class PunishmentLootMixin {
    @Inject(method="dropAllDeathLoot",at=@At("HEAD"),cancellable=true)
    private void rouletteLoot(ServerLevel level,DamageSource damage,CallbackInfo ci){if(((Entity)(Object)this).entityTags().contains("ludopathyroulette:spawn"))ci.cancel();}
    @Inject(method="dropExperience",at=@At("HEAD"),cancellable=true)
    private void rouletteExperience(ServerLevel level,Entity source,CallbackInfo ci){if(((Entity)(Object)this).entityTags().contains("ludopathyroulette:spawn"))ci.cancel();}
}
