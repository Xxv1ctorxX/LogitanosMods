package com.ludopathyroulette;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** El bloque identifica la máquina. La vista pública nunca serializa las plantillas privadas. */
public final class RouletteBlockEntity extends BlockEntity {
    public String machineId=UUID.randomUUID().toString();
    public String publicJson="{}";
    public long snapshotTick;
    public RouletteBlockEntity(BlockPos p,BlockState s){super(RouletteMod.MACHINE_BE,p,s);}
    protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.putString("MachineId",machineId);}
    protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);
        machineId=in.getStringOr("MachineId",machineId);
        publicJson=in.getStringOr("Public","{}");
        snapshotTick=level==null?0:level.getGameTime();
    }
    public CompoundTag getUpdateTag(HolderLookup.Provider r){
        CompoundTag tag=new CompoundTag();tag.putString("MachineId",machineId);tag.putString("Public",publicJson);return tag;
    }
    public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    public void refresh(String json){publicJson=json;setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
}
