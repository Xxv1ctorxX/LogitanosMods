package com.theslave.mixin;

import com.theslave.persistencia.CerrojosJugador;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PacketCustodyMixin {
    @Inject(method="shouldHandleMessage",at=@At("HEAD"),cancellable=true)
    private void theslave$pausarPaquete(Packet<?> packet,CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof ServerGamePacketListenerImpl listener && CerrojosJugador.ocupada(listener.player.getUUID())) cir.setReturnValue(false);
    }
}
