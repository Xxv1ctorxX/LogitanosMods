package com.theslave.mixin;

import com.theslave.ritual.ItemCustody;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class ItemCustodyMixin implements ItemCustody {
    @Unique private UUID theslave$op;
    @Unique private boolean theslave$custodia;
    @Unique private Vec3 theslave$ancla;
    @Override public UUID theslave$operacion() { return theslave$op; }
    @Override public boolean theslave$custodiado() { return theslave$custodia; }
    @Override public void theslave$custodiar(UUID operacion) {
        var item=(ItemEntity)(Object)this;
        theslave$op=operacion;theslave$custodia=true;theslave$ancla=item.position();
        item.setNeverPickUp();item.setDeltaMovement(Vec3.ZERO);item.setNoGravity(true);
    }
    @Override public void theslave$liberar() {
        theslave$custodia=false;theslave$ancla=null;
        var item=(ItemEntity)(Object)this;
        item.setDefaultPickUpDelay();item.setNoGravity(false);
    }
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void theslave$freeze(CallbackInfo ci) {
        if(!theslave$custodia)return;
        var item=(ItemEntity)(Object)this;
        if(theslave$ancla!=null)item.setPos(theslave$ancla);
        item.setDeltaMovement(Vec3.ZERO);ci.cancel();
    }
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)
    private void theslave$pickup(Player player,CallbackInfo ci) { if(theslave$custodia)ci.cancel(); }
    @Inject(method="hurtServer",at=@At("HEAD"),cancellable=true)
    private void theslave$hurt(ServerLevel level,DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) {
        if(theslave$custodia)cir.setReturnValue(false);
    }
    @Inject(method="isMergable",at=@At("HEAD"),cancellable=true)
    private void theslave$merge(CallbackInfoReturnable<Boolean> cir) { if(theslave$custodia)cir.setReturnValue(false); }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void theslave$save(ValueOutput out,CallbackInfo ci) {
        if(theslave$op!=null)out.putString("TheSlaveBotinOperacion",theslave$op.toString());
        out.putBoolean("TheSlaveBotinCustodia",theslave$custodia);
        if(theslave$custodia && theslave$ancla!=null) {
            out.putDouble("TheSlaveBotinX",theslave$ancla.x);out.putDouble("TheSlaveBotinY",theslave$ancla.y);out.putDouble("TheSlaveBotinZ",theslave$ancla.z);
        }
    }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void theslave$load(ValueInput in,CallbackInfo ci) {
        String op=in.getStringOr("TheSlaveBotinOperacion","");
        theslave$op=op.isEmpty()?null:UUID.fromString(op);
        theslave$custodia=in.getBooleanOr("TheSlaveBotinCustodia",false);
        if(theslave$custodia) {
            if(theslave$op==null)throw new IllegalStateException("Botin en custodia sin operacion");
            var item=(ItemEntity)(Object)this;
            double x=in.getDoubleOr("TheSlaveBotinX",item.getX()),y=in.getDoubleOr("TheSlaveBotinY",item.getY()),z=in.getDoubleOr("TheSlaveBotinZ",item.getZ());
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalStateException("Ancla de botin corrupta");
            theslave$ancla=new Vec3(x,y,z);item.setPos(theslave$ancla);item.setNeverPickUp();item.setNoGravity(true);
        }
    }
}
