package com.theslave.item;

import com.theslave.ritual.Hogares;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Colocar tambien forma parte del WAL: vanilla no consume el item por separado. */
public final class LechoItem extends BlockItem {
    public LechoItem(Block block,Properties properties) { super(block,properties); }
    @Override public InteractionResult place(BlockPlaceContext context) {
        if(context.getLevel().isClientSide())return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer p))return InteractionResult.FAIL;
        return Hogares.colocar(p,context.getItemInHand(),context.getClickedPos(),context.getHorizontalDirection())?InteractionResult.SUCCESS:InteractionResult.FAIL;
    }
}
