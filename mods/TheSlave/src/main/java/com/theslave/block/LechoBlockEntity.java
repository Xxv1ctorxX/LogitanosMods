package com.theslave.block;

import com.theslave.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Etiqueta fisica; Hogares mantiene la autoridad durable de la ubicacion. */
public final class LechoBlockEntity extends BlockEntity {
    public UUID dueno,vinculo;
    public LechoBlockEntity(BlockPos pos,BlockState state) { super(ModBlocks.LECHO_BE,pos,state); }
    public void vincular(UUID dueno,UUID vinculo) { this.dueno=dueno;this.vinculo=vinculo;setChanged(); }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if(dueno!=null)out.putString("Dueno",dueno.toString());
        if(vinculo!=null)out.putString("Vinculo",vinculo.toString());
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        String d=in.getStringOr("Dueno",""),v=in.getStringOr("Vinculo","");
        dueno=d.isEmpty()?null:UUID.fromString(d);vinculo=v.isEmpty()?null:UUID.fromString(v);
    }
}
