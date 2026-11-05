package com.theslave.mixin;

import com.theslave.persistencia.CerrojosJugador;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerPlayer.class)
public abstract class PlayerCustodyMixin {
    @Inject(method={"tick","doTick"},at=@At("HEAD"),cancellable=true)
    private void theslave$pausar(CallbackInfo ci) {
        if (CerrojosJugador.ocupada(((ServerPlayer)(Object)this).getUUID())) ci.cancel();
    }
    @Inject(method="hurtServer",at=@At("HEAD"),cancellable=true)
    private void theslave$proteger(ServerLevel level,DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) {
        if (CerrojosJugador.ocupada(((ServerPlayer)(Object)this).getUUID())) cir.setReturnValue(false);
    }
    @Inject(method="die",at=@At("HEAD"),cancellable=true)
    private void theslave$protegerMuerte(DamageSource source,CallbackInfo ci) {
        if (CerrojosJugador.ocupada(((ServerPlayer)(Object)this).getUUID())) ci.cancel();
    }
}
