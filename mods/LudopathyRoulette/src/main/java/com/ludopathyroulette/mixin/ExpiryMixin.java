package com.ludopathyroulette.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Caducidad sin tickets ni lista global de entidades. La marca se guarda con la entidad. */
@Mixin(Entity.class)
public abstract class ExpiryMixin {
    @Inject(method="commonTick",at=@At("HEAD"),cancellable=true)
    private void rouletteExpire(CallbackInfo ci){
        Entity entity=(Entity)(Object)this;
        if(entity.level().isClientSide()||!entity.entityTags().contains("ludopathyroulette:spawn"))return;
        for(String tag:entity.entityTags())if(tag.startsWith("lr_expire:")){
            try{if(entity.level().getGameTime()>=Long.parseLong(tag.substring(10))){entity.discard();ci.cancel();}}catch(NumberFormatException e){entity.discard();ci.cancel();}break;
        }
    }
}
